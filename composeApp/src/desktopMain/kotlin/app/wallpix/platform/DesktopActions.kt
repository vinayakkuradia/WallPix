package app.wallpix.platform

import app.wallpix.logic.NativeCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Small desktop integrations used by the UI. Everything goes through external commands: the Nucleus (Tao)
 * window backend has no AWT, so `java.awt.Desktop` and Swing dialogs are not available.
 */
object DesktopActions {
    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    fun formatTime(epochMillis: Long): String =
        timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    /** Opens an https link in the default browser. Anything else is ignored. */
    fun openUrl(url: String, os: Os = Os.current()) {
        val cmd = NativeCommands.openUrl(os.key, url) ?: return
        launchDetached(cmd)
    }

    /** Shows a folder in the file manager. The folder is created first so the button always does something. */
    fun openFolder(path: Path, os: Os = Os.current()) {
        runCatching {
            Files.createDirectories(path)
            launchDetached(NativeCommands.openFolder(os.key, path.toAbsolutePath().toString()))
        }
    }

    /**
     * Native folder picker (Finder sheet, Explorer dialog, GTK / KDE chooser). Suspends until the user answers, so
     * call it from a coroutine. Null if cancelled or if no picker is installed.
     */
    suspend fun chooseFolder(initial: Path, title: String, os: Os = Os.current()): Path? = withContext(Dispatchers.IO) {
        val start = (if (Files.isDirectory(initial)) initial else initial.parent ?: Paths.get(System.getProperty("user.home")))
            .toAbsolutePath().toString()
        for (cmd in NativeCommands.pickFolder(os.key, start, title)) {
            val r = Proc.run(cmd, timeoutSec = 15 * 60)
            if (r.ok) {
                val picked = NativeCommands.parsePickedFolder(r.output) ?: continue
                return@withContext runCatching { Paths.get(picked) }.getOrNull()
            }
            // 127 = command not found: try the next picker. On macOS a failure other than "User canceled (-128)"
            // means the default location was rejected, so the next command retries without it. Anything else
            // is the user cancelling.
            val retry = r.exit == 127 || (os == Os.MAC && !r.output.contains("-128"))
            if (!retry) return@withContext null
        }
        null
    }

    /** Fire and forget: the opened app must not block, and its exit code is irrelevant. */
    private fun launchDetached(argv: List<String>) {
        runCatching {
            ProcessBuilder(argv)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
        }
    }
}
