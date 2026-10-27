package app.wallpix.logic

import app.wallpix.model.UNGROUPED

/** Human-readable text for settings values. Pure, so it is tested on every platform. */
object Labels {
    /** "1 minute", "45 minutes", "1 hour", "6 hours", "24 hours". Non-round values fall back to minutes. */
    fun minutes(m: Int): String = when {
        m == 1 -> "1 minute"
        m < 60 -> "$m minutes"
        m % 60 == 0 -> if (m == 60) "1 hour" else "${m / 60} hours"
        else -> "$m minutes"
    }

    /** The folder-root pseudo group has no name of its own. */
    fun group(group: String): String = if (group == UNGROUPED) "No group" else group

    /**
     * [presets] plus [current], sorted and de-duplicated, so a value that was set by an older version or by hand
     * still shows up in a dropdown instead of silently disappearing.
     */
    fun withCurrent(presets: List<Int>, current: Int): List<Int> = (presets + current).distinct().sorted()
}
