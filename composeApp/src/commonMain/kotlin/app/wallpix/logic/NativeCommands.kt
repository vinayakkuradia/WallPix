package app.wallpix.logic

/**
 * Pure command builders and output parsers for the OS integrations that used to go through AWT
 * (trash, open link or folder, folder picker, screen size).
 *
 * The Nucleus window backend (Tao) has no AWT, so none of `java.awt` / `javax.swing` may be touched. Every
 * integration is an external command instead, and everything that can be decided without I/O lives here so it is
 * covered by unit tests on every platform.
 */
object NativeCommands {

    // ---------------------------------------------------------------- quoting

    /** PowerShell single-quoted literal: only the quote itself needs doubling. */
    fun psQuote(s: String): String = "'" + s.replace("'", "''") + "'"

    /** AppleScript string literal. */
    fun asQuote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun powershell(script: String): List<String> =
        listOf("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script)

    // ------------------------------------------------------------------ trash

    /** Candidate commands, tried in order, that move [path] to the Trash / Recycle Bin. */
    fun trash(os: String, path: String): List<List<String>> = when (os) {
        "mac" -> listOf(
            listOf("osascript", "-e", "tell application \"Finder\" to delete POSIX file ${asQuote(path)}"),
        )
        "windows" -> listOf(
            powershell(
                "Add-Type -AssemblyName Microsoft.VisualBasic; " +
                    "[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile(${psQuote(path)}, " +
                    "'OnlyErrorDialogs', 'SendToRecycleBin')",
            ),
        )
        else -> listOf(
            listOf("gio", "trash", path),
            listOf("trash-put", path),
            listOf("kioclient5", "move", path, "trash:/"),
        )
    }

    // ------------------------------------------------------------- open / show

    /** Opens an https link in the default browser. Anything that is not https yields no command. */
    fun openUrl(os: String, url: String): List<String>? {
        if (!url.startsWith("https://") || url.any { it.isISOControl() || it == ' ' }) return null
        return when (os) {
            "mac" -> listOf("open", url)
            "windows" -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
            else -> listOf("xdg-open", url)
        }
    }

    fun openFolder(os: String, path: String): List<String> = when (os) {
        "mac" -> listOf("open", path)
        "windows" -> listOf("explorer.exe", path)
        else -> listOf("xdg-open", path)
    }

    // ----------------------------------------------------------- folder picker

    /** Candidate commands, tried in order, that print the chosen folder on stdout and exit non-zero on cancel. */
    fun pickFolder(os: String, initial: String, title: String): List<List<String>> = when (os) {
        "mac" -> listOf(
            listOf(
                "osascript", "-e",
                "POSIX path of (choose folder with prompt ${asQuote(title)} " +
                    "default location POSIX file ${asQuote(initial)})",
            ),
            // The initial folder may not exist yet; retry without a default location.
            listOf("osascript", "-e", "POSIX path of (choose folder with prompt ${asQuote(title)})"),
        )
        "windows" -> listOf(
            // OpenFileDialog with a placeholder file name gives the modern Explorer-style dialog; the folder is the
            // directory part of what it returns. FolderBrowserDialog would show the legacy tree control.
            powershell(
                "Add-Type -AssemblyName System.Windows.Forms; " +
                    "\$d = New-Object System.Windows.Forms.OpenFileDialog; " +
                    "\$d.Title = ${psQuote(title)}; " +
                    "\$d.InitialDirectory = ${psQuote(initial)}; " +
                    "\$d.ValidateNames = \$false; \$d.CheckFileExists = \$false; \$d.CheckPathExists = \$true; " +
                    "\$d.FileName = 'Select this folder'; " +
                    "if (\$d.ShowDialog() -eq 'OK') { [System.IO.Path]::GetDirectoryName(\$d.FileName) } else { exit 1 }",
            ),
        )
        else -> listOf(
            listOf("zenity", "--file-selection", "--directory", "--title=$title", "--filename=${initial.trimEnd('/')}/"),
            listOf("kdialog", "--title", title, "--getexistingdirectory", initial),
        )
    }

    /** Extracts the chosen folder from a picker's stdout; null when blank. */
    fun parsePickedFolder(output: String): String? {
        val line = output.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: return null
        // AppleScript prints a trailing slash; the favourites folder is stored without one.
        return if (line.length > 1) line.trimEnd('/', '\\').ifEmpty { line } else line
    }

    // -------------------------------------------------------------- screen size

    /** One command per OS that lists display resolutions in physical pixels. */
    fun screenProbe(os: String): List<String> = when (os) {
        "mac" -> listOf("system_profiler", "SPDisplaysDataType")
        "windows" -> powershell(
            "Get-CimInstance Win32_VideoController | ForEach-Object { " +
                "'' + \$_.CurrentHorizontalResolution + ' x ' + \$_.CurrentVerticalResolution }",
        )
        else -> listOf("xrandr", "--current")
    }

    private val MAC_RES = Regex("""Resolution:\s*(\d{3,5})\s*x\s*(\d{3,5})""")
    private val WIN_RES = Regex("""^\s*(\d{3,5})\s*x\s*(\d{3,5})\s*$""")
    private val XRANDR_ACTIVE = Regex("""^\s+(\d{3,5})x(\d{3,5})\S*\s.*\*""")

    /** Longest edge in pixels across all displays reported in [output], or null when none could be read. */
    fun parseLongestEdge(os: String, output: String): Int? {
        val re = when (os) {
            "mac" -> MAC_RES
            "windows" -> WIN_RES
            else -> XRANDR_ACTIVE
        }
        var best = 0
        for (line in output.lineSequence()) {
            val m = re.find(line) ?: continue
            val w = m.groupValues[1].toInt()
            val h = m.groupValues[2].toInt()
            best = maxOf(best, w, h)
        }
        return best.takeIf { it > 0 }
    }
}
