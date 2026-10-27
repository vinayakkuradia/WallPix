package app.wallpix.logic

enum class LinuxDe(val label: String) {
    GNOME("GNOME"),
    CINNAMON("Cinnamon"),
    MATE("MATE"),
    KDE("KDE Plasma"),
    XFCE("XFCE"),
    UNKNOWN("unknown"),
}

/** One external command. A failed non-required command does not fail the whole operation. */
data class Cmd(val argv: List<String>, val required: Boolean = true)

/** Pure command builders for the Linux wallpaper setters, kept free of I/O so they are testable. */
object LinuxWallpaper {
    private val HEX = "0123456789ABCDEF"

    fun detect(xdgCurrentDesktop: String?, desktopSession: String?): LinuxDe {
        val tokens = listOfNotNull(xdgCurrentDesktop, desktopSession)
            .flatMap { it.lowercase().split(':', ';', ' ') }
            .filter { it.isNotBlank() }
        fun has(vararg needles: String) = tokens.any { t -> needles.any { t.contains(it) } }
        return when {
            has("cinnamon") -> LinuxDe.CINNAMON
            has("mate") -> LinuxDe.MATE
            has("kde", "plasma") -> LinuxDe.KDE
            has("xfce") -> LinuxDe.XFCE
            has("gnome", "ubuntu", "unity", "budgie", "pop", "pantheon", "zorin") -> LinuxDe.GNOME
            else -> LinuxDe.UNKNOWN
        }
    }

    /** Percent-encodes an absolute path into a `file://` URI (UTF-8, RFC 3986 unreserved kept). */
    fun toFileUri(absPath: String): String {
        val sb = StringBuilder("file://")
        for (b in absPath.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (c < 128 && (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch in "/-_.~")) {
                sb.append(ch)
            } else {
                sb.append('%').append(HEX[c shr 4]).append(HEX[c and 15])
            }
        }
        return sb.toString()
    }

    fun commands(de: LinuxDe, absPath: String): List<Cmd> {
        val uri = toFileUri(absPath)
        return when (de) {
            LinuxDe.GNOME -> listOf(
                Cmd(listOf("gsettings", "set", "org.gnome.desktop.background", "picture-options", "zoom"), required = false),
                // picture-uri-dark does not exist before GNOME 42, so it must not fail the operation.
                Cmd(listOf("gsettings", "set", "org.gnome.desktop.background", "picture-uri-dark", uri), required = false),
                Cmd(listOf("gsettings", "set", "org.gnome.desktop.background", "picture-uri", uri)),
            )
            LinuxDe.CINNAMON -> listOf(
                Cmd(listOf("gsettings", "set", "org.cinnamon.desktop.background", "picture-options", "zoom"), required = false),
                Cmd(listOf("gsettings", "set", "org.cinnamon.desktop.background", "picture-uri", uri)),
            )
            LinuxDe.MATE -> listOf(
                Cmd(listOf("gsettings", "set", "org.mate.background", "picture-options", "zoom"), required = false),
                Cmd(listOf("gsettings", "set", "org.mate.background", "picture-filename", absPath)),
            )
            LinuxDe.KDE -> listOf(Cmd(listOf("plasma-apply-wallpaperimage", absPath)))
            LinuxDe.XFCE, LinuxDe.UNKNOWN -> emptyList()
        }
    }

    /** XFCE keeps one `last-image` property per monitor/workspace; each must be set. */
    fun xfceListCommand(): List<String> = listOf("xfconf-query", "-c", "xfce4-desktop", "-l")

    fun xfceLastImageProps(listOutput: String): List<String> =
        listOutput.lineSequence().map { it.trim() }.filter { it.endsWith("/last-image") }.toList()

    fun xfceSetCommands(props: List<String>, absPath: String): List<Cmd> =
        if (props.isEmpty()) {
            listOf(
                Cmd(
                    listOf(
                        "xfconf-query", "-c", "xfce4-desktop",
                        "-p", "/backdrop/screen0/monitor0/workspace0/last-image",
                        "--create", "-t", "string", "-s", absPath,
                    ),
                ),
            )
        } else {
            props.map { Cmd(listOf("xfconf-query", "-c", "xfce4-desktop", "-p", it, "-s", absPath)) }
        }

    /** Fallback for Plasma older than 5.25, which lacks plasma-apply-wallpaperimage. */
    fun kdeScript(absPath: String): String {
        val uri = toFileUri(absPath)
        return "var ds = desktops(); for (var i = 0; i < ds.length; i++) { var d = ds[i]; " +
            "d.wallpaperPlugin = 'org.kde.image'; " +
            "d.currentConfigGroup = ['Wallpaper', 'org.kde.image', 'General']; " +
            "d.writeConfig('Image', '$uri'); }"
    }
}

/** AppleScript snippets for macOS. */
object MacWallpaper {
    fun escape(path: String): String = path.replace("\\", "\\\\").replace("\"", "\\\"")

    /** Sets the picture on every display's current desktop. */
    fun systemEventsScript(path: String): String =
        "tell application \"System Events\" to tell every desktop to set picture to POSIX file \"${escape(path)}\""

    fun finderScript(path: String): String =
        "tell application \"Finder\" to set desktop picture to POSIX file \"${escape(path)}\""
}
