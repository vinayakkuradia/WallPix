package app.wallpix.model

/** Group name used for favourites that live directly in the favourites folder. */
const val UNGROUPED: String = ""

const val MIN_RECENTS = 1
const val MAX_RECENTS = 30
const val DEFAULT_RECENTS = 10

/** Peapix caches its API for 30 minutes, so polling more often is pointless. */
const val MIN_CHECK_MINUTES = 30
const val DEFAULT_CHECK_MINUTES = 60

/** How many image keys we remember so evicted wallpapers are never re-downloaded. */
const val SEEN_LIMIT = 1000

enum class Source(val label: String) {
    BING("Bing daily"),
    SPOTLIGHT("Windows Spotlight"),
}

enum class ResolutionMode(val label: String) {
    AUTO("Auto (match screen)"),
    HD("1920 px"),
    ORIGINAL("Original (highest)"),
}

enum class Variant { THUMB, FULL, ORIGINAL }

enum class SlideshowOrder(val label: String) {
    SEQUENTIAL("In order"),
    SHUFFLE("Shuffle"),
}

enum class GroupMix(val label: String) {
    POOLED("All selected favourites pooled"),
    EVEN("Even share per group"),
}

/** Bing regions accepted by the Peapix API (code to display name). */
val BING_COUNTRIES: Map<String, String> = linkedMapOf(
    "au" to "Australia",
    "br" to "Brazil",
    "ca" to "Canada",
    "cn" to "China",
    "fr" to "France",
    "de" to "Germany",
    "in" to "India",
    "it" to "Italy",
    "jp" to "Japan",
    "es" to "Spain",
    "gb" to "United Kingdom",
    "us" to "United States",
)

/** One item as returned by the Peapix feed. */
data class FeedItem(
    val source: Source,
    val country: String?,
    val title: String,
    val copyright: String,
    val fullUrl: String,
    val thumbUrl: String,
    val imageUrl: String,
    val pageUrl: String,
    /** yyyy-MM-dd, Bing only. Spotlight has no date. */
    val date: String?,
) {
    /** Stable identity: the hash in the image file name, identical across all size variants. */
    val key: String get() = Naming.imageKey(imageUrl)
}

/** A wallpaper WallPix has recorded locally (recent or favourite). */
data class LibraryEntry(
    val key: String,
    /** Monotonic insertion number; higher = more recent. */
    val seq: Long,
    val source: Source,
    val country: String?,
    val title: String,
    val copyright: String,
    val pageUrl: String,
    val date: String?,
    val firstSeenMs: Long,
    val fullUrl: String,
    val thumbUrl: String,
    val imageUrl: String,
    val localPath: String?,
    val favourite: Boolean = false,
    val group: String = UNGROUPED,
    /** Un-favourited while it was the active wallpaper: trash it once something else is applied. */
    val pendingTrash: Boolean = false,
)

fun FeedItem.toEntry(seq: Long, nowMs: Long, localPath: String?, favourite: Boolean = false, group: String = UNGROUPED) =
    LibraryEntry(
        key = key, seq = seq, source = source, country = country, title = title, copyright = copyright,
        pageUrl = pageUrl, date = date, firstSeenMs = nowMs, fullUrl = fullUrl, thumbUrl = thumbUrl,
        imageUrl = imageUrl, localPath = localPath, favourite = favourite, group = group,
    )

data class Settings(
    val source: Source = Source.BING,
    val country: String = "us",
    /** Tray toggle: apply each new daily wallpaper. Downloads continue when off. */
    val autoApplyDaily: Boolean = true,
    val recentsCount: Int = DEFAULT_RECENTS,
    val resolution: ResolutionMode = ResolutionMode.AUTO,
    /** null = platform default (Downloads/WallPix). */
    val favouritesDir: String? = null,
    val checkIntervalMin: Int = DEFAULT_CHECK_MINUTES,
    val slideshowEnabled: Boolean = false,
    val slideshowIntervalMin: Int = 30,
    val slideshowOrder: SlideshowOrder = SlideshowOrder.SHUFFLE,
    val groupMix: GroupMix = GroupMix.POOLED,
    /** Groups included in the slideshow. Empty = every group. [UNGROUPED] stands for the root folder. */
    val slideshowGroups: Set<String> = emptySet(),
    val launchAtLogin: Boolean = false,
) {
    fun sanitized(): Settings = copy(
        country = if (country in BING_COUNTRIES) country else "us",
        recentsCount = recentsCount.coerceIn(MIN_RECENTS, MAX_RECENTS),
        checkIntervalMin = checkIntervalMin.coerceAtLeast(MIN_CHECK_MINUTES),
        slideshowIntervalMin = slideshowIntervalMin.coerceAtLeast(1),
        favouritesDir = favouritesDir?.trim()?.ifEmpty { null },
    )
}

data class AppState(
    val entries: List<LibraryEntry> = emptyList(),
    /** Every key ever recorded, oldest first, capped at [SEEN_LIMIT]. */
    val seen: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val currentKey: String? = null,
    val nextSeq: Long = 1,
    val lastCheckMs: Long = 0,
    val nextSlideshowMs: Long = 0,
)
