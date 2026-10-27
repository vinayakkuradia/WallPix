package app.wallpix.platform

import app.wallpix.core.WallpaperSetter
import app.wallpix.logic.Cmd
import app.wallpix.logic.LinuxDe
import app.wallpix.logic.LinuxWallpaper
import app.wallpix.logic.MacWallpaper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.win32.W32APIOptions
import java.nio.file.Files
import java.nio.file.Path

object WallpaperSetters {
    fun forOs(os: Os = Os.current()): WallpaperSetter = when (os) {
        Os.WINDOWS -> WindowsWallpaperSetter()
        Os.MAC -> MacWallpaperSetter()
        Os.LINUX -> LinuxWallpaperSetter()
    }
}

/** Windows: SystemParametersInfoW(SPI_SETDESKWALLPAPER). Applies to all monitors, "Fill" style. */
class WindowsWallpaperSetter : WallpaperSetter {
    private interface User32 : Library {
        fun SystemParametersInfoW(uiAction: Int, uiParam: Int, pvParam: String, fWinIni: Int): Boolean
    }

    override fun set(path: Path): Result<Unit> = runCatching {
        val abs = path.toAbsolutePath().toString()
        // Style 10 = Fill, TileWallpaper 0 = no tiling. Both are read when the wallpaper is applied.
        val key = "HKCU\\Control Panel\\Desktop"
        Proc.run(listOf("reg", "add", key, "/v", "WallpaperStyle", "/t", "REG_SZ", "/d", "10", "/f"))
        Proc.run(listOf("reg", "add", key, "/v", "TileWallpaper", "/t", "REG_SZ", "/d", "0", "/f"))
        val user32 = Native.load("user32", User32::class.java, W32APIOptions.UNICODE_OPTIONS)
        val ok = user32.SystemParametersInfoW(SPI_SETDESKWALLPAPER, 0, abs, SPIF_UPDATEINIFILE or SPIF_SENDCHANGE)
        check(ok) { "Windows rejected the wallpaper (SystemParametersInfo failed)" }
    }

    private companion object {
        const val SPI_SETDESKWALLPAPER = 0x0014
        const val SPIF_UPDATEINIFILE = 0x01
        const val SPIF_SENDCHANGE = 0x02
    }
}

/**
 * macOS: AppleScript via System Events, which sets every display's current desktop. The first use
 * triggers an "automation" permission prompt. Falls back to Finder.
 */
class MacWallpaperSetter : WallpaperSetter {
    override fun set(path: Path): Result<Unit> = runCatching {
        val abs = path.toAbsolutePath().toString()
        val first = Proc.run(listOf("osascript", "-e", MacWallpaper.systemEventsScript(abs)), timeoutSec = 45)
        if (!first.ok) {
            val second = Proc.run(listOf("osascript", "-e", MacWallpaper.finderScript(abs)), timeoutSec = 45)
            check(second.ok) {
                "osascript failed: ${first.output.ifBlank { second.output }}. " +
                    "If macOS asked for permission, allow WallPix to control System Events in " +
                    "System Settings > Privacy & Security > Automation."
            }
        }
    }
}

/** Linux: GNOME-family, Cinnamon, MATE, KDE Plasma and XFCE. Wayland compositors such as sway are not covered. */
class LinuxWallpaperSetter(
    private val de: LinuxDe = LinuxWallpaper.detect(System.getenv("XDG_CURRENT_DESKTOP"), System.getenv("DESKTOP_SESSION")),
) : WallpaperSetter {

    override fun set(path: Path): Result<Unit> = runCatching {
        val abs = path.toAbsolutePath().toString()
        check(Files.exists(path)) { "File not found: $abs" }
        when (de) {
            LinuxDe.XFCE -> runAll(LinuxWallpaper.xfceSetCommands(xfceProps(), abs))
            LinuxDe.KDE -> setKde(abs)
            LinuxDe.UNKNOWN -> {
                // Best effort: many unlisted desktops (Budgie, Pantheon, Unity, ...) honour the GNOME keys.
                val r = runCatching { runAll(LinuxWallpaper.commands(LinuxDe.GNOME, abs)) }
                if (r.isFailure) {
                    error(
                        "Unsupported desktop environment (XDG_CURRENT_DESKTOP=" +
                            "${System.getenv("XDG_CURRENT_DESKTOP") ?: "unset"}). " +
                            "Supported: GNOME, Cinnamon, MATE, KDE Plasma, XFCE.",
                    )
                }
            }
            else -> runAll(LinuxWallpaper.commands(de, abs))
        }
    }

    private fun xfceProps(): List<String> {
        val r = Proc.run(LinuxWallpaper.xfceListCommand())
        return if (r.ok) LinuxWallpaper.xfceLastImageProps(r.output) else emptyList()
    }

    private fun setKde(abs: String) {
        val primary = Proc.run(listOf("plasma-apply-wallpaperimage", abs))
        if (primary.ok) return
        // Plasma < 5.25: evaluate a script through the shell's D-Bus interface.
        val script = LinuxWallpaper.kdeScript(abs)
        for (bin in listOf("qdbus", "qdbus6", "qdbus-qt5")) {
            val r = Proc.run(listOf(bin, "org.kde.plasmashell", "/PlasmaShell", "org.kde.PlasmaShell.evaluateScript", script))
            if (r.ok) return
        }
        error("plasma-apply-wallpaperimage failed: ${primary.output}")
    }

    private fun runAll(cmds: List<Cmd>) {
        check(cmds.isNotEmpty()) { "No wallpaper command available for ${de.label}" }
        for (c in cmds) {
            val r = Proc.run(c.argv)
            if (!r.ok && c.required) error("${c.argv.first()} failed: ${r.output.ifBlank { "exit ${r.exit}" }}")
        }
    }
}
