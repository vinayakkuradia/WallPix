package app.wallpix

import app.wallpix.logic.Browse
import app.wallpix.model.FeedItem
import app.wallpix.model.LibraryEntry
import app.wallpix.model.Source
import app.wallpix.model.UNGROUPED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun feedItem(key: String, title: String = key, date: String? = "2026-10-01") = FeedItem(
    Source.BING, "us", title, "© test", "https://img.peapix.com/${key}_1920.jpg",
    "https://img.peapix.com/${key}_640.jpg", "https://img.peapix.com/$key.jpg", "https://peapix.com/bing/1", date,
)

private fun entry(
    key: String,
    seq: Long,
    title: String = key,
    favourite: Boolean = false,
    group: String = UNGROUPED,
    pendingTrash: Boolean = false,
    localPath: String? = "/tmp/$key.jpg",
) = LibraryEntry(
    key = key, seq = seq, source = Source.BING, country = "us", title = title, copyright = "© test",
    pageUrl = "https://peapix.com/bing/1", date = "2026-09-01", firstSeenMs = 0,
    fullUrl = "https://img.peapix.com/${key}_1920.jpg", thumbUrl = "https://img.peapix.com/${key}_640.jpg",
    imageUrl = "https://img.peapix.com/$key.jpg", localPath = localPath,
    favourite = favourite, group = group, pendingTrash = pendingTrash,
)

class BrowseTest {

    @Test fun latestStartsWithTheLiveFeedInServedOrder() {
        val feed = listOf(feedItem("newest0001"), feedItem("middle0002"), feedItem("oldest0003"))
        val out = Browse.latest(feed, emptyList(), 10)
        assertEquals(listOf("newest0001", "middle0002", "oldest0003"), out.map { it.key })
        assertTrue(out.all { it.item != null && it.entry == null })
    }

    @Test fun latestTopsUpWithLocalHistoryNewestFirstWithoutDuplicates() {
        val feed = listOf(feedItem("feedaaaa01"), feedItem("feedbbbb02"))
        val entries = listOf(
            entry("feedbbbb02", seq = 5), // also in the feed: shown once, as a feed item that knows its local copy
            entry("hist000003", seq = 3),
            entry("hist000004", seq = 4),
            entry("hist000001", seq = 1),
        )
        val out = Browse.latest(feed, entries, 10)
        assertEquals(listOf("feedaaaa01", "feedbbbb02", "hist000004", "hist000003", "hist000001"), out.map { it.key })
        val shared = out.first { it.key == "feedbbbb02" }
        assertNotNull(shared.item)
        assertNotNull(shared.entry)
        assertTrue(shared.onDisk)
        assertNull(out.first { it.key == "hist000003" }.item)
    }

    @Test fun latestIsCappedAtTheConfiguredCountAndClamped() {
        val feed = (1..7).map { feedItem("feed00000$it") }
        val entries = (1..40).map { entry("hist" + it.toString().padStart(6, '0'), seq = it.toLong()) }
        assertEquals(10, Browse.latest(feed, entries, 10).size)
        assertEquals(5, Browse.latest(feed, entries, 5).size)
        assertEquals(30, Browse.latest(feed, entries, 99).size)   // hard cap is 30
        assertEquals(1, Browse.latest(feed, entries, 0).size)     // and at least one
    }

    @Test fun latestWorksOfflineFromHistoryAlone() {
        val out = Browse.latest(emptyList(), listOf(entry("aaaaaaaaaa", 1), entry("bbbbbbbbbb", 2)), 10)
        assertEquals(listOf("bbbbbbbbbb", "aaaaaaaaaa"), out.map { it.key })
    }

    @Test fun pendingTrashEntriesNeverAppearAnywhere() {
        val doomed = entry("doomed0001", seq = 9, pendingTrash = true)
        val feed = listOf(feedItem("doomed0001"))
        val latest = Browse.latest(feed, listOf(doomed), 10)
        assertEquals(listOf("doomed0001"), latest.map { it.key })
        assertNull(latest[0].entry)                       // treated as not stored, so it is not "on disk"
        assertFalse(latest[0].favourite)
        assertTrue(Browse.downloaded(listOf(doomed)).isEmpty())
        assertTrue(Browse.favourites(listOf(doomed.copy(favourite = true))).isEmpty())
    }

    @Test fun downloadedListsOnlyNonFavouritesOnDiskNewestFirst() {
        val entries = listOf(
            entry("old0000001", seq = 1),
            entry("fav0000002", seq = 2, favourite = true),
            entry("new0000003", seq = 3),
            entry("gone000004", seq = 4, localPath = null),
        )
        assertEquals(listOf("new0000003", "old0000001"), Browse.downloaded(entries).map { it.key })
    }

    @Test fun favouritesAreSortedByGroupThenTitleAndCanBeFiltered() {
        val entries = listOf(
            entry("k1", 1, title = "Zebra", favourite = true, group = "Beach"),
            entry("k2", 2, title = "apple", favourite = true, group = "Alps"),
            entry("k3", 3, title = "Mango", favourite = true, group = UNGROUPED),
            entry("k4", 4, title = "Berry", favourite = true, group = "Alps"),
            entry("k5", 5, title = "Not a favourite"),
        )
        assertEquals(listOf("k3", "k2", "k4", "k1"), Browse.favourites(entries).map { it.key })
        assertEquals(listOf("k2", "k4"), Browse.favourites(entries, "Alps").map { it.key })
        assertEquals(listOf("k3"), Browse.favourites(entries, UNGROUPED).map { it.key })
        assertTrue(Browse.favourites(entries, "Nowhere").isEmpty())
    }

    @Test fun wallpaperReportsFavouriteAndGroupFromItsEntry() {
        val fav = Browse.favourites(listOf(entry("favkey0001", 1, favourite = true, group = "Alps"))).single()
        assertTrue(fav.favourite)
        assertEquals("Alps", fav.group)
        val plain = Browse.latest(listOf(feedItem("plainkey01")), emptyList(), 5).single()
        assertFalse(plain.favourite)
        assertFalse(plain.onDisk)
        assertEquals(UNGROUPED, plain.group)
    }
}
