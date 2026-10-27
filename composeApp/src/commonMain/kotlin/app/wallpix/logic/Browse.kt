package app.wallpix.logic

import app.wallpix.model.FeedItem
import app.wallpix.model.LibraryEntry
import app.wallpix.model.MAX_RECENTS
import app.wallpix.model.MIN_RECENTS
import app.wallpix.model.UNGROUPED

/** A wallpaper as the UI shows it: from the live feed, from the local library, or both. */
data class Wallpaper(
    val key: String,
    val title: String,
    val copyright: String,
    val pageUrl: String,
    /** yyyy-MM-dd; Spotlight images have none. */
    val date: String?,
    val thumbUrl: String,
    /** Present while the image is in the live Peapix feed. */
    val item: FeedItem?,
    /** Present once WallPix has recorded it locally. */
    val entry: LibraryEntry?,
) {
    val favourite: Boolean get() = entry != null && entry.favourite && !entry.pendingTrash
    val onDisk: Boolean get() = entry?.localPath != null
    val group: String get() = entry?.group ?: UNGROUPED
}

/** Pure view-models for the three browsing pages. */
object Browse {

    /**
     * The "latest N" page. Peapix serves only about a week, so the live feed (as served, newest first) is topped up
     * with wallpapers WallPix remembers (newest first) until [limit] is reached. The list therefore grows with use.
     */
    fun latest(feed: List<FeedItem>, entries: List<LibraryEntry>, limit: Int): List<Wallpaper> {
        val usable = entries.filter { !it.pendingTrash }
        val byKey = usable.associateBy { it.key }
        val out = LinkedHashMap<String, Wallpaper>()
        for (item in feed) {
            if (item.key !in out) out[item.key] = fromItem(item, byKey[item.key])
        }
        for (e in usable.sortedByDescending { it.seq }) {
            if (e.key !in out) out[e.key] = fromEntry(e)
        }
        return out.values.take(limit.coerceIn(MIN_RECENTS, MAX_RECENTS))
    }

    /** Non-favourite wallpapers kept on disk, newest first. These are the "recents" that retention manages. */
    fun downloaded(entries: List<LibraryEntry>): List<Wallpaper> =
        entries.filter { !it.favourite && !it.pendingTrash && it.localPath != null }
            .sortedByDescending { it.seq }
            .map(::fromEntry)

    /** Favourites, optionally restricted to one group ([UNGROUPED] means the root folder), sorted by group then title. */
    fun favourites(entries: List<LibraryEntry>, group: String? = null): List<Wallpaper> =
        entries.filter { it.favourite && !it.pendingTrash && (group == null || it.group == group) }
            .sortedWith(compareBy({ it.group.lowercase() }, { it.title.lowercase() }, { it.key }))
            .map(::fromEntry)

    private fun fromItem(i: FeedItem, e: LibraryEntry?) = Wallpaper(
        key = i.key, title = i.title, copyright = i.copyright, pageUrl = i.pageUrl,
        date = i.date, thumbUrl = i.thumbUrl, item = i, entry = e,
    )

    private fun fromEntry(e: LibraryEntry) = Wallpaper(
        key = e.key, title = e.title, copyright = e.copyright, pageUrl = e.pageUrl,
        date = e.date, thumbUrl = e.thumbUrl, item = null, entry = e,
    )
}
