package app.wallpix.platform

import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.swing.JFileChooser

/** Small desktop integrations used by the UI. Plain AWT/Swing, so nothing here depends on Compose. */
object DesktopActions {
    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    fun formatTime(epochMillis: Long): String =
        timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    /** Opens an https link in the default browser. Anything else is ignored. */
    fun openUrl(url: String) {
        if (!url.startsWith("https://")) return
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url))
            }
        }
    }

    /** Shows a folder in the file manager. The folder is created first so the button always does something. */
    fun openFolder(path: Path) {
        runCatching {
            Files.createDirectories(path)
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile())
            }
        }
    }

    /** Folder picker. Call it from a click handler, which runs on the UI thread. Null if cancelled. */
    fun chooseFolder(initial: Path, title: String): Path? {
        val chooser = JFileChooser(initial.toFile().takeIf { it.isDirectory }).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = title
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
    }
}
