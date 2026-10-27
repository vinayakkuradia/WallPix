package app.wallpix.logic

import app.wallpix.model.FeedItem
import app.wallpix.model.GroupMix
import app.wallpix.model.LibraryEntry
import app.wallpix.model.MAX_RECENTS
import app.wallpix.model.MIN_CHECK_MINUTES
import app.wallpix.model.MIN_RECENTS
import app.wallpix.model.ResolutionMode
import app.wallpix.model.SlideshowOrder
import app.wallpix.model.Variant
import kotlin.random.Random

/** Chooses which image size to download. */
object ResolutionPicker {
    /** Screens whose long edge exceeds this (physical pixels) get the original image. */
    const val AUTO_THRESHOLD_PX = 1920

    fun variantFor(mode: ResolutionMode, screenLongEdgePx: Int): Variant = when (mode) {
        ResolutionMode.HD -> Variant.FULL
        ResolutionMode.ORIGINAL -> Variant.ORIGINAL
        ResolutionMode.AUTO -> if (screenLongEdgePx > AUTO_THRESHOLD_PX) Variant.ORIGINAL else Variant.FULL
    }

    /** Variants to try in order; if the preferred one fails (404 etc.) the other is used. */
    fun chain(preferred: Variant): List<Variant> = when (preferred) {
        Variant.ORIGINAL -> listOf(Variant.ORIGINAL, Variant.FULL)
        Variant.FULL -> listOf(Variant.FULL, Variant.ORIGINAL)
        Variant.THUMB -> listOf(Variant.THUMB)
    }

    fun urlFor(variant: Variant, fullUrl: String, thumbUrl: String, imageUrl: String): String = when (variant) {
        Variant.THUMB -> thumbUrl
        Variant.FULL -> fullUrl
        Variant.ORIGINAL -> imageUrl
    }
}

/** Decides which non-favourite wallpapers to clear. */
object Retention {
    /**
     * Keeps the [keep] most recent non-favourites and returns the keys of the rest.
     * Protected keys (the wallpaper currently on the desktop) are never returned.
     */
    fun evict(entries: List<LibraryEntry>, keep: Int, protectedKeys: Set<String>): List<String> =
        entries.filter { !it.favourite && !it.pendingTrash }
            .sortedByDescending { it.seq }
            .drop(keep.coerceIn(MIN_RECENTS, MAX_RECENTS))
            .map { it.key }
            .filter { it !in protectedKeys }

    /** Un-favourited items that were still on the desktop and can now be trashed. */
    fun pendingTrash(entries: List<LibraryEntry>, protectedKeys: Set<String>): List<String> =
        entries.filter { it.pendingTrash && it.key !in protectedKeys }.map { it.key }
}

object UpdatePlanner {
    data class Plan(
        /** Oldest first, so the newest item receives the highest sequence number. */
        val newItems: List<FeedItem>,
        /** Key to apply once downloaded, or null to download only. */
        val applyKey: String?,
    )

    /**
     * [feed] is newest first, as served. New = key never seen before. We never compare against today's
     * date because the feed can lag by a day (observed for Bing US).
     */
    fun plan(feed: List<FeedItem>, seen: Set<String>, autoApply: Boolean, slideshowOn: Boolean): Plan {
        val fresh = feed.filter { it.key !in seen }.distinctBy { it.key }.reversed()
        // Only the feed's newest item may be applied, and only if it is new. A stale item whose download
        // failed earlier must not replace a newer wallpaper once it finally succeeds.
        val newest = feed.firstOrNull()?.key
        val apply = if (autoApply && !slideshowOn && newest != null && fresh.any { it.key == newest }) newest else null
        return Plan(fresh, apply)
    }
}

object SlideshowPlanner {
    private val cmp = compareBy<LibraryEntry>({ it.group }, { it.title.lowercase() }, { it.key })

    /** Favourites that are usable by the slideshow, filtered by the selected groups (empty = all). */
    fun pool(entries: List<LibraryEntry>, groups: Set<String>): List<LibraryEntry> =
        entries.filter {
            it.favourite && !it.pendingTrash && it.localPath != null &&
                (groups.isEmpty() || it.group in groups)
        }

    fun sequence(pool: List<LibraryEntry>, mix: GroupMix): List<LibraryEntry> = when (mix) {
        GroupMix.POOLED -> pool.sortedWith(cmp)
        GroupMix.EVEN -> roundRobin(pool)
    }

    private fun roundRobin(pool: List<LibraryEntry>): List<LibraryEntry> {
        val lists = pool.groupBy { it.group }.entries.sortedBy { it.key }.map { e -> e.value.sortedWith(cmp) }
        val longest = lists.maxOfOrNull { it.size } ?: 0
        val out = ArrayList<LibraryEntry>(pool.size)
        for (i in 0 until longest) for (l in lists) l.getOrNull(i)?.let(out::add)
        return out
    }

    /** Next wallpaper after [currentKey]. Returns null only when the pool is empty. */
    fun next(
        pool: List<LibraryEntry>,
        currentKey: String?,
        order: SlideshowOrder,
        mix: GroupMix,
        random: Random = Random.Default,
    ): LibraryEntry? {
        if (pool.isEmpty()) return null
        if (pool.size == 1) return pool[0]
        return when (order) {
            SlideshowOrder.SEQUENTIAL -> {
                val list = sequence(pool, mix)
                val i = list.indexOfFirst { it.key == currentKey }
                list[(i + 1) % list.size]
            }
            SlideshowOrder.SHUFFLE -> {
                val candidates = pool.filter { it.key != currentKey }
                when (mix) {
                    GroupMix.POOLED -> candidates.random(random)
                    GroupMix.EVEN -> {
                        val byGroup = candidates.groupBy { it.group }
                        val groups = byGroup.keys.sorted()
                        byGroup.getValue(groups.random(random)).random(random)
                    }
                }
            }
        }
    }
}

object Schedule {
    private fun intervalMs(min: Int): Long = maxOf(min, MIN_CHECK_MINUTES) * 60_000L

    /** Due when never checked, when the clock went backwards, or when the interval has elapsed. */
    fun updateDue(nowMs: Long, lastCheckMs: Long, intervalMin: Int): Boolean =
        lastCheckMs <= 0L || nowMs < lastCheckMs || nowMs - lastCheckMs >= intervalMs(intervalMin)

    /** A `lastCheckMs` stamp that makes the next check due in [retryMin] minutes (used after a failure). */
    fun retryStamp(nowMs: Long, intervalMin: Int, retryMin: Int = 5): Long =
        nowMs - intervalMs(intervalMin) + retryMin * 60_000L

    fun slideshowDue(nowMs: Long, nextAtMs: Long): Boolean = nextAtMs > 0L && nowMs >= nextAtMs

    fun nextSlideshow(nowMs: Long, intervalMin: Int): Long = nowMs + maxOf(1, intervalMin) * 60_000L
}
