package app.wallpix.platform

import app.wallpix.core.Autostart
import app.wallpix.core.ScreenInfo
import app.wallpix.core.Trasher
import app.wallpix.logic.NativeCommands
import java.io.Closeable
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Moves files to the Recycle Bin / Trash through the OS itself (Finder, the shell's recycle API, gio / trash-cli / KDE).
 * If that is impossible the file is kept, never hard-deleted. No AWT: the Tao window backend has none.
 */
class SystemTrasher(private val os: Os = Os.current()) : Trasher {
    override fun trash(path: Path): Boolean {
        if (!Files.exists(path)) return true
        val abs = path.toAbsolutePath().toString()
        for (cmd in NativeCommands.trash(os.key, abs)) {
            if (Proc.run(cmd, timeoutSec = 30).ok && !Files.exists(path)) return true
        }
        return !Files.exists(path)
    }
}

/** Longest screen edge in physical pixels, read from the OS. Cached briefly because the probe spawns a process. */
class DesktopScreenInfo(private val os: Os = Os.current()) : ScreenInfo {
    @Volatile private var cached: Int = 0
    @Volatile private var cachedAtMs: Long = 0

    override fun longEdgePx(): Int {
        val now = System.currentTimeMillis()
        if (cached > 0 && now - cachedAtMs < CACHE_MS) return cached
        val probed = runCatching {
            val r = Proc.run(NativeCommands.screenProbe(os.key), timeoutSec = 15)
            if (r.ok) NativeCommands.parseLongestEdge(os.key, r.output) else null
        }.getOrNull()
        val value = probed ?: FALLBACK
        if (probed != null) {
            cached = value
            cachedAtMs = now
        }
        return value
    }

    private companion object {
        const val FALLBACK = 1920
        const val CACHE_MS = 5 * 60_000L
    }
}

/**
 * Launch at login. Only available for an installed build: when running from Gradle the launcher is
 * `java`, which cannot be registered sensibly.
 */
class SystemAutostart(
    private val os: Os = Os.current(),
    private val launcher: String? = detectLauncher(),
    private val home: Path = Paths.get(System.getProperty("user.home")),
) : Autostart {

    override fun isSupported(): Boolean = launcher != null

    override fun isEnabled(): Boolean = when (os) {
        Os.WINDOWS -> Proc.run(listOf("reg", "query", RUN_KEY, "/v", NAME)).ok
        Os.MAC -> Files.exists(macPlist())
        Os.LINUX -> Files.exists(linuxDesktopFile())
    }

    override fun set(enabled: Boolean): Result<Unit> = runCatching {
        val exe = launcher ?: error("Launch at login is only available in the installed app.")
        when (os) {
            Os.WINDOWS -> if (enabled) {
                val r = Proc.run(listOf("reg", "add", RUN_KEY, "/v", NAME, "/t", "REG_SZ", "/d", "\"$exe\" --background", "/f"))
                check(r.ok) { r.output }
            } else {
                // Exit code 1 simply means the value was not there.
                Proc.run(listOf("reg", "delete", RUN_KEY, "/v", NAME, "/f"))
            }
            Os.MAC -> if (enabled) {
                val f = macPlist()
                Files.createDirectories(f.parent)
                Files.writeString(f, plist(exe))
            } else {
                Files.deleteIfExists(macPlist())
            }
            Os.LINUX -> if (enabled) {
                val f = linuxDesktopFile()
                Files.createDirectories(f.parent)
                Files.writeString(f, desktopEntry(exe))
            } else {
                Files.deleteIfExists(linuxDesktopFile())
            }
        }
    }

    private fun macPlist(): Path = home.resolve("Library/LaunchAgents/app.wallpix.plist")

    private fun linuxDesktopFile(): Path {
        val cfg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let { Paths.get(it) } ?: home.resolve(".config")
        return cfg.resolve("autostart/wallpix.desktop")
    }

    private fun plist(exe: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
            <key>Label</key><string>app.wallpix</string>
            <key>ProgramArguments</key>
            <array><string>${xml(exe)}</string><string>--background</string></array>
            <key>RunAtLoad</key><true/>
        </dict>
        </plist>
    """.trimIndent() + "\n"

    private fun desktopEntry(exe: String): String = """
        [Desktop Entry]
        Type=Application
        Name=WallPix
        Comment=Daily wallpapers from Peapix
        Exec="${exe.replace("\\", "\\\\").replace("\"", "\\\"")}" --background
        X-GNOME-Autostart-enabled=true
        Terminal=false
    """.trimIndent() + "\n"

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        private const val NAME = "WallPix"

        /** Path of the packaged launcher, or null when running under `java` / Gradle. */
        fun detectLauncher(): String? {
            val cmd = ProcessHandle.current().info().command().orElse(null) ?: return null
            val name = Paths.get(cmd).fileName?.toString()?.lowercase() ?: return null
            return if (name.startsWith("java")) null else cmd
        }
    }
}

/** Ensures only one WallPix runs per user, using an OS file lock that is released if the process dies. */
object SingleInstance {
    private var held: Closeable? = null

    /** Returns false if another instance already holds the lock. */
    fun acquire(lockFile: Path): Boolean {
        Files.createDirectories(lockFile.parent)
        val ch = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val lock = try {
            ch.tryLock()
        } catch (e: OverlappingFileLockException) {
            null
        }
        if (lock == null) {
            ch.close()
            return false
        }
        held = Closeable { runCatching { lock.release() }; runCatching { ch.close() } }
        return true
    }
}
