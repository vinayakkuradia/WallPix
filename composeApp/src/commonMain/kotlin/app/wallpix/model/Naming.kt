package app.wallpix.model

/** File and key naming rules. Everything here is pure so it can be unit tested on any platform. */
object Naming {
    private val KEY_RE = Regex("^[A-Za-z0-9_-]{6,80}$")
    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** `https://img.peapix.com/abc_1920.jpg` -> `abc`. Same key for the thumb, full and original URLs. */
    fun imageKey(url: String): String =
        url.substringBefore('?').substringBefore('#')
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .removeSuffix("_1920")
            .removeSuffix("_640")

    /** Keys become file names, so only a conservative alphabet is accepted (no path traversal). */
    fun isValidKey(key: String): Boolean = KEY_RE.matches(key)

    /** Only https URLs on peapix.com or one of its subdomains are ever fetched. */
    fun isTrustedUrl(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val host = url.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.contains('@') || host.contains(':')) return false
        return host == "peapix.com" || host.endsWith(".peapix.com")
    }

    fun slug(title: String, max: Int = 40): String {
        val sb = StringBuilder()
        var lastDash = true
        for (c in title.lowercase()) {
            if (c in 'a'..'z' || c in '0'..'9') {
                sb.append(c); lastDash = false
            } else if (!lastDash) {
                sb.append('-'); lastDash = true
            }
        }
        return sb.toString().trim('-').take(max).trim('-').ifEmpty { "wallpaper" }
    }

    /** e.g. `2026-10-18_dolbadarn-castle-llanberis_cac8e317.jpg` */
    fun fileName(date: String?, title: String, key: String, ext: String = "jpg"): String {
        val d = date?.takeIf { DATE_RE.matches(it) } ?: "undated"
        return "${d}_${slug(title)}_${key.take(8)}.$ext"
    }
}

/** Favourite groups are plain sub-folders, so names must be valid folder names on every OS. */
object GroupNames {
    private val ILLEGAL = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
    private val WINDOWS_RESERVED = setOf(
        "con", "prn", "aux", "nul",
        "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
        "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
    )

    /** Returns a safe folder name, or null if nothing usable remains. */
    fun sanitize(raw: String): String? {
        val cleaned = raw.filter { it !in ILLEGAL && !it.isISOControl() }
            .trim()
            .trimEnd('.', ' ')
            .take(50)
            .trim()
        if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return null
        if (cleaned.lowercase() in WINDOWS_RESERVED) return null
        return cleaned
    }
}
