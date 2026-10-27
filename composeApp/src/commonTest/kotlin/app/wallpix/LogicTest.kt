package app.wallpix

import app.wallpix.logic.Codec
import app.wallpix.logic.LinuxDe
import app.wallpix.logic.LinuxWallpaper
import app.wallpix.logic.MacWallpaper
import app.wallpix.logic.ResolutionPicker
import app.wallpix.logic.Retention
import app.wallpix.logic.Schedule
import app.wallpix.logic.SlideshowPlanner
import app.wallpix.logic.UpdatePlanner
import app.wallpix.model.AppState
import app.wallpix.model.FeedItem
import app.wallpix.model.GroupMix
import app.wallpix.model.GroupNames
import app.wallpix.model.LibraryEntry
import app.wallpix.model.Naming
import app.wallpix.model.ResolutionMode
import app.wallpix.model.Settings
import app.wallpix.model.SlideshowOrder
import app.wallpix.model.Source
import app.wallpix.model.UNGROUPED
import app.wallpix.model.Variant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun entry(
    key: String,
    seq: Long,
    fav: Boolean = false,
    group: String = UNGROUPED,
    title: String = key,
    path: String? = "/tmp/$key.jpg",
    pending: Boolean = false,
) = LibraryEntry(
    key = key, seq = seq, source = Source.BING, country = "us", title = title, copyright = "", pageUrl = "",
    date = null, firstSeenMs = 0, fullUrl = "", thumbUrl = "", imageUrl = "", localPath = path,
    favourite = fav, group = group, pendingTrash = pending,
)

private fun item(key: String, date: String? = null) = FeedItem(
    Source.BING, "us", key, "", "https://img.peapix.com/${key}_1920.jpg",
    "https://img.peapix.com/${key}_640.jpg", "https://img.peapix.com/$key.jpg", "", date,
)

class NamingTest {
    @Test fun keyIsSameAcrossVariants() {
        val k = "cac8e3179c614f6ea52927828d5a65c3"
        assertEquals(k, Naming.imageKey("https://img.peapix.com/${k}_1920.jpg"))
        assertEquals(k, Naming.imageKey("https://img.peapix.com/${k}_640.jpg"))
        assertEquals(k, Naming.imageKey("https://img.peapix.com/$k.jpg"))
        assertEquals(k, Naming.imageKey("https://img.peapix.com/$k.jpg?x=1#y"))
    }

    @Test fun keyValidationRejectsTraversal() {
        assertTrue(Naming.isValidKey("cac8e3179c614f6ea52927828d5a65c3"))
        assertFalse(Naming.isValidKey("../etc/passwd"))
        assertFalse(Naming.isValidKey("a b"))
        assertFalse(Naming.isValidKey("abc"))
        assertFalse(Naming.isValidKey(""))
    }

    @Test fun onlyPeapixHttpsIsTrusted() {
        assertTrue(Naming.isTrustedUrl("https://img.peapix.com/a.jpg"))
        assertTrue(Naming.isTrustedUrl("https://peapix.com/bing/feed"))
        assertFalse(Naming.isTrustedUrl("http://img.peapix.com/a.jpg"))
        assertFalse(Naming.isTrustedUrl("https://evil.com/a.jpg"))
        assertFalse(Naming.isTrustedUrl("https://peapix.com.evil.com/a.jpg"))
        assertFalse(Naming.isTrustedUrl("https://peapix.com@evil.com/a.jpg"))
        assertFalse(Naming.isTrustedUrl("https://notpeapix.com/a.jpg"))
        assertFalse(Naming.isTrustedUrl("https://localhost:8080/a.jpg"))
    }

    @Test fun slugAndFileName() {
        val slug = Naming.slug("Dolbadarn Castle, Llanberis, Eryri National Park (Snowdonia), Wales")
        assertEquals("dolbadarn-castle-llanberis-eryri-nationa", slug) // hard cap of 40 characters
        assertEquals(40, slug.length)
        assertEquals("a-b", Naming.slug("  a -- b  "))
        assertEquals("wallpaper", Naming.slug("!!!"))
        assertEquals("wallpaper", Naming.slug("太湖の桜"))
        assertEquals(
            "2026-10-18_mount-sneffels_72425a3c.jpg",
            Naming.fileName("2026-10-18", "Mount Sneffels", "72425a3ce31c4e67b79c017d8e76a620"),
        )
        assertEquals("undated_x_abcdef12.jpg", Naming.fileName(null, "X", "abcdef1234"))
        assertEquals("undated_x_abcdef12.jpg", Naming.fileName("../../x", "X", "abcdef1234"))
    }

    @Test fun groupNames() {
        assertEquals("Travel", GroupNames.sanitize("  Travel  "))
        assertEquals("ab", GroupNames.sanitize("a/b:\\"))
        assertNull(GroupNames.sanitize(".."))
        assertNull(GroupNames.sanitize("   "))
        assertNull(GroupNames.sanitize("CON"))
        assertNull(GroupNames.sanitize("///"))
        assertEquals(50, GroupNames.sanitize("x".repeat(80))!!.length)
    }
}

class ResolutionTest {
    @Test fun autoFollowsScreen() {
        assertEquals(Variant.FULL, ResolutionPicker.variantFor(ResolutionMode.AUTO, 1920))
        assertEquals(Variant.FULL, ResolutionPicker.variantFor(ResolutionMode.AUTO, 1366))
        assertEquals(Variant.ORIGINAL, ResolutionPicker.variantFor(ResolutionMode.AUTO, 2560))
        assertEquals(Variant.ORIGINAL, ResolutionPicker.variantFor(ResolutionMode.AUTO, 3840))
    }

    @Test fun explicitModesIgnoreScreen() {
        assertEquals(Variant.FULL, ResolutionPicker.variantFor(ResolutionMode.HD, 5120))
        assertEquals(Variant.ORIGINAL, ResolutionPicker.variantFor(ResolutionMode.ORIGINAL, 1024))
    }

    @Test fun fallbackChain() {
        assertEquals(listOf(Variant.ORIGINAL, Variant.FULL), ResolutionPicker.chain(Variant.ORIGINAL))
        assertEquals(listOf(Variant.FULL, Variant.ORIGINAL), ResolutionPicker.chain(Variant.FULL))
        assertEquals("f", ResolutionPicker.urlFor(Variant.FULL, "f", "t", "o"))
        assertEquals("t", ResolutionPicker.urlFor(Variant.THUMB, "f", "t", "o"))
        assertEquals("o", ResolutionPicker.urlFor(Variant.ORIGINAL, "f", "t", "o"))
    }
}

class RetentionTest {
    private val recents = (1L..12L).map { entry("k$it", it) }

    @Test fun keepsNewestN() {
        val gone = Retention.evict(recents, 10, emptySet())
        assertEquals(setOf("k1", "k2"), gone.toSet())
    }

    @Test fun neverEvictsFavourites() {
        val all = recents + entry("fav", 0, fav = true)
        assertFalse("fav" in Retention.evict(all, 1, emptySet()))
    }

    @Test fun favouritesDoNotCountAgainstCap() {
        val all = recents.take(3) + (100L..110L).map { entry("f$it", it, fav = true) }
        assertEquals(emptyList(), Retention.evict(all, 3, emptySet()))
    }

    @Test fun protectsCurrentWallpaper() {
        val gone = Retention.evict(recents, 10, setOf("k1"))
        assertEquals(listOf("k2"), gone)
    }

    @Test fun capIsClampedToRange() {
        assertEquals(11, Retention.evict(recents, 1, emptySet()).size) // min 1
        assertEquals(0, Retention.evict(recents, 99, emptySet()).size) // max 30 > 12
        val many = (1L..40L).map { entry("m$it", it) }
        assertEquals(10, Retention.evict(many, 99, emptySet()).size) // clamped to 30
    }

    @Test fun pendingTrashWaitsForProtection() {
        val all = listOf(entry("a", 1, pending = true), entry("b", 2, pending = true))
        assertEquals(listOf("b"), Retention.pendingTrash(all, setOf("a")))
        assertEquals(listOf("a", "b"), Retention.pendingTrash(all, emptySet()))
    }
}

class UpdatePlannerTest {
    @Test fun firstRunDownloadsAllAppliesNewest() {
        val feed = listOf(item("newest1"), item("middle1"), item("oldest1")) // newest first
        val plan = UpdatePlanner.plan(feed, emptySet(), autoApply = true, slideshowOn = false)
        assertEquals(listOf("oldest1", "middle1", "newest1"), plan.newItems.map { it.key })
        assertEquals("newest1", plan.applyKey)
    }

    @Test fun skipsSeenAndNeverReDownloads() {
        val feed = listOf(item("newest1"), item("middle1"))
        val plan = UpdatePlanner.plan(feed, setOf("middle1"), true, false)
        assertEquals(listOf("newest1"), plan.newItems.map { it.key })
    }

    @Test fun nothingNewMeansNothingApplied() {
        val plan = UpdatePlanner.plan(listOf(item("aaaaaa")), setOf("aaaaaa"), true, false)
        assertTrue(plan.newItems.isEmpty())
        assertNull(plan.applyKey)
    }

    @Test fun togglesSuppressApplyButNotDownload() {
        val feed = listOf(item("newest1"))
        val off = UpdatePlanner.plan(feed, emptySet(), autoApply = false, slideshowOn = false)
        assertEquals(1, off.newItems.size); assertNull(off.applyKey)
        val slideshow = UpdatePlanner.plan(feed, emptySet(), autoApply = true, slideshowOn = true)
        assertEquals(1, slideshow.newItems.size); assertNull(slideshow.applyKey)
    }

    @Test fun onlyTheNewestFeedItemIsEverApplied() {
        // "newest1" is already known, "older01" is new (e.g. its download failed last time).
        val plan = UpdatePlanner.plan(listOf(item("newest1"), item("older01")), setOf("newest1"), true, false)
        assertEquals(listOf("older01"), plan.newItems.map { it.key })
        assertNull(plan.applyKey)
    }

    @Test fun duplicateKeysInFeedAreCollapsed() {
        val plan = UpdatePlanner.plan(listOf(item("dup111"), item("dup111")), emptySet(), true, false)
        assertEquals(1, plan.newItems.size)
    }
}

class SlideshowTest {
    private val fav = listOf(
        entry("a1", 1, fav = true, group = "Alps", title = "A1"),
        entry("a2", 2, fav = true, group = "Alps", title = "A2"),
        entry("a3", 3, fav = true, group = "Alps", title = "A3"),
        entry("b1", 4, fav = true, group = "Beach", title = "B1"),
        entry("u1", 5, fav = true, group = UNGROUPED, title = "U1"),
    )

    @Test fun poolOnlyFavouritesWithFiles() {
        val all = fav + entry("r1", 9) + entry("nofile", 10, fav = true, path = null) +
            entry("pend", 11, fav = false, pending = true)
        assertEquals(5, SlideshowPlanner.pool(all, emptySet()).size)
    }

    @Test fun poolFiltersByGroup() {
        assertEquals(setOf("a1", "a2", "a3"), SlideshowPlanner.pool(fav, setOf("Alps")).map { it.key }.toSet())
        assertEquals(setOf("u1", "b1"), SlideshowPlanner.pool(fav, setOf(UNGROUPED, "Beach")).map { it.key }.toSet())
    }

    @Test fun sequentialPooledOrderWraps() {
        val order = SlideshowPlanner.sequence(fav, GroupMix.POOLED).map { it.key }
        assertEquals(listOf("u1", "a1", "a2", "a3", "b1"), order) // ungrouped "" sorts first
        val n = SlideshowPlanner.next(fav, "b1", SlideshowOrder.SEQUENTIAL, GroupMix.POOLED)
        assertEquals("u1", n!!.key)
        val start = SlideshowPlanner.next(fav, "not-in-pool", SlideshowOrder.SEQUENTIAL, GroupMix.POOLED)
        assertEquals("u1", start!!.key)
    }

    @Test fun sequentialEvenInterleavesGroups() {
        val order = SlideshowPlanner.sequence(fav, GroupMix.EVEN).map { it.key }
        assertEquals(listOf("u1", "a1", "b1", "a2", "a3"), order)
    }

    @Test fun shuffleNeverRepeatsCurrent() {
        val rnd = Random(42)
        repeat(300) {
            val n = SlideshowPlanner.next(fav, "a1", SlideshowOrder.SHUFFLE, GroupMix.POOLED, rnd)!!
            assertTrue(n.key != "a1")
        }
    }

    @Test fun evenShuffleGivesEachGroupAFairShare() {
        val rnd = Random(7)
        val counts = HashMap<String, Int>()
        repeat(3000) {
            val n = SlideshowPlanner.next(fav, null, SlideshowOrder.SHUFFLE, GroupMix.EVEN, rnd)!!
            counts[n.group] = (counts[n.group] ?: 0) + 1
        }
        // 3 groups, each should land near 1000. Pooled would give Alps ~60%.
        for (g in listOf("Alps", "Beach", UNGROUPED)) {
            val c = counts.getValue(g)
            assertTrue(c in 850..1150, "group '$g' got $c")
        }
    }

    @Test fun emptyAndSingletonPools() {
        assertNull(SlideshowPlanner.next(emptyList(), null, SlideshowOrder.SHUFFLE, GroupMix.POOLED))
        val one = listOf(entry("only", 1, fav = true))
        assertEquals("only", SlideshowPlanner.next(one, "only", SlideshowOrder.SHUFFLE, GroupMix.EVEN)!!.key)
    }
}

class ScheduleTest {
    private val min = 60_000L

    @Test fun updateDueRules() {
        assertTrue(Schedule.updateDue(1_000_000, 0, 60))
        assertFalse(Schedule.updateDue(100 * min, 80 * min, 60))
        assertTrue(Schedule.updateDue(141 * min, 80 * min, 60))
        assertTrue(Schedule.updateDue(10 * min, 80 * min, 60)) // clock went backwards
    }

    @Test fun intervalNeverBelowApiCache() {
        // Asking for 1 minute still waits 30 (Peapix caches for 30 min).
        assertFalse(Schedule.updateDue(100 * min + 29 * min, 100 * min, 1))
        assertTrue(Schedule.updateDue(100 * min + 30 * min, 100 * min, 1))
    }

    @Test fun retryStampMakesCheckDueInFiveMinutes() {
        val now = 1_000 * min
        val stamp = Schedule.retryStamp(now, 60)
        assertFalse(Schedule.updateDue(now + 4 * min, stamp, 60))
        assertTrue(Schedule.updateDue(now + 5 * min, stamp, 60))
    }

    @Test fun slideshowTiming() {
        assertFalse(Schedule.slideshowDue(5, 0)) // unscheduled
        assertFalse(Schedule.slideshowDue(5, 10))
        assertTrue(Schedule.slideshowDue(10, 10))
        assertTrue(Schedule.slideshowDue(5 * min, 1 * min)) // woke from sleep long after the deadline
        assertEquals(10 * min + 30 * min, Schedule.nextSlideshow(10 * min, 30))
        assertEquals(10 * min + 1 * min, Schedule.nextSlideshow(10 * min, 0))
    }
}

class LinuxCommandsTest {
    @Test fun detection() {
        assertEquals(LinuxDe.GNOME, LinuxWallpaper.detect("ubuntu:GNOME", "ubuntu"))
        assertEquals(LinuxDe.GNOME, LinuxWallpaper.detect("GNOME", null))
        assertEquals(LinuxDe.GNOME, LinuxWallpaper.detect("Budgie:GNOME", null))
        assertEquals(LinuxDe.GNOME, LinuxWallpaper.detect("pop:GNOME", null))
        assertEquals(LinuxDe.KDE, LinuxWallpaper.detect("KDE", null))
        assertEquals(LinuxDe.XFCE, LinuxWallpaper.detect("XFCE", null))
        assertEquals(LinuxDe.CINNAMON, LinuxWallpaper.detect("X-Cinnamon", null))
        assertEquals(LinuxDe.MATE, LinuxWallpaper.detect("MATE", null))
        assertEquals(LinuxDe.KDE, LinuxWallpaper.detect(null, "plasma"))
        assertEquals(LinuxDe.UNKNOWN, LinuxWallpaper.detect("sway", null))
        assertEquals(LinuxDe.UNKNOWN, LinuxWallpaper.detect(null, null))
    }

    @Test fun fileUriEncoding() {
        assertEquals("file:///home/u/My%20Pics/a.jpg", LinuxWallpaper.toFileUri("/home/u/My Pics/a.jpg"))
        assertEquals("file:///home/u/%C3%BCber/%27q%27.jpg", LinuxWallpaper.toFileUri("/home/u/über/'q'.jpg"))
        assertEquals("file:///a/b-c_d.e~f", LinuxWallpaper.toFileUri("/a/b-c_d.e~f"))
    }

    @Test fun gnomeCommandsAndRequiredFlags() {
        val cmds = LinuxWallpaper.commands(LinuxDe.GNOME, "/p/a b.jpg")
        val uriCmd = cmds.single { it.argv.contains("picture-uri") }
        assertTrue(uriCmd.required)
        assertEquals("file:///p/a%20b.jpg", uriCmd.argv.last())
        assertFalse(cmds.single { it.argv.contains("picture-uri-dark") }.required)
    }

    @Test fun otherDesktops() {
        assertEquals(
            listOf("plasma-apply-wallpaperimage", "/p/a.jpg"),
            LinuxWallpaper.commands(LinuxDe.KDE, "/p/a.jpg").single().argv,
        )
        assertEquals("/p/a b.jpg", LinuxWallpaper.commands(LinuxDe.MATE, "/p/a b.jpg").last().argv.last())
        assertTrue(LinuxWallpaper.commands(LinuxDe.CINNAMON, "/p/a.jpg").last().argv.contains("org.cinnamon.desktop.background"))
        assertTrue(LinuxWallpaper.commands(LinuxDe.XFCE, "/p/a.jpg").isEmpty())
        assertTrue(LinuxWallpaper.commands(LinuxDe.UNKNOWN, "/p/a.jpg").isEmpty())
    }

    @Test fun xfceProps() {
        val out = "/backdrop/screen0/monitor0/workspace0/last-image\n" +
            "/backdrop/screen0/monitor0/workspace0/image-style\n" +
            "/backdrop/screen0/monitoreDP-1/workspace1/last-image\n"
        val props = LinuxWallpaper.xfceLastImageProps(out)
        assertEquals(2, props.size)
        val cmds = LinuxWallpaper.xfceSetCommands(props, "/p/a.jpg")
        assertEquals(2, cmds.size)
        assertEquals("/p/a.jpg", cmds[0].argv.last())
        val fallback = LinuxWallpaper.xfceSetCommands(emptyList(), "/p/a.jpg").single().argv
        assertTrue(fallback.contains("--create"))
    }

    @Test fun kdeFallbackScriptEmbedsUri() {
        assertTrue(LinuxWallpaper.kdeScript("/p/a b.jpg").contains("file:///p/a%20b.jpg"))
    }

    @Test fun macScriptsEscapeQuotesAndBackslashes() {
        val s = MacWallpaper.systemEventsScript("/Users/x/My \"Pics\"/a.jpg")
        assertTrue(s.contains("POSIX file \"/Users/x/My \\\"Pics\\\"/a.jpg\""))
        assertTrue(MacWallpaper.finderScript("/a.jpg").startsWith("tell application \"Finder\""))
    }
}

class CodecTest {
    // Real payloads captured from the Peapix API on 2026-10-20 (trimmed).
    private val bing = """[{"title":"Dolbadarn Castle, Llanberis, Eryri National Park (Snowdonia), Wales","copyright":"\u00A9 joe daniel price/Getty Image","fullUrl":"https://img.peapix.com/cac8e3179c614f6ea52927828d5a65c3_1920.jpg","thumbUrl":"https://img.peapix.com/cac8e3179c614f6ea52927828d5a65c3_640.jpg","imageUrl":"https://img.peapix.com/cac8e3179c614f6ea52927828d5a65c3.jpg","pageUrl":"https://peapix.com/bing/57992","date":"2026-10-18"},{"title":"View of the Wroclaw Market Square from the Witches\u0027 Bridge in Wroclaw, Poland","copyright":"\u00A9 Piotr Mitelski/Shutterstoc","fullUrl":"https://img.peapix.com/6b01b73013df40ff84a6ba99740d7802_1920.jpg","thumbUrl":"https://img.peapix.com/6b01b73013df40ff84a6ba99740d7802_640.jpg","imageUrl":"https://img.peapix.com/6b01b73013df40ff84a6ba99740d7802.jpg","pageUrl":"https://peapix.com/bing/57956","date":"2026-10-15"}]"""

    private val spotlight = """[{"title":"Morning mist over Kr\u00FCn valley, Bavarian Alps, Germany","copyright":"\u00A9 Robert Seitz/imageBROKER/Getty Images","fullUrl":"https://img.peapix.com/c9b337e8229f4fb3a04c8a8388ed80b1_1920.jpg","thumbUrl":"https://img.peapix.com/c9b337e8229f4fb3a04c8a8388ed80b1_640.jpg","imageUrl":"https://img.peapix.com/c9b337e8229f4fb3a04c8a8388ed80b1.jpg","pageUrl":"https://peapix.com/spotlight/12691"}]"""

    @Test fun parsesRealBingFeed() {
        val items = Codec.parseFeed(bing, Source.BING, "us")
        assertEquals(2, items.size)
        val first = items[0]
        assertEquals("cac8e3179c614f6ea52927828d5a65c3", first.key)
        assertEquals("2026-10-18", first.date)
        assertEquals("\u00A9 joe daniel price/Getty Image", first.copyright)
        assertTrue(items[1].title.contains("Witches' Bridge"))
        assertEquals("us", first.country)
    }

    @Test fun parsesSpotlightWithoutDate() {
        val items = Codec.parseFeed(spotlight, Source.SPOTLIGHT, null)
        assertEquals(1, items.size)
        assertNull(items[0].date)
        assertTrue(items[0].title.contains("Kr\u00FCn"))
    }

    @Test fun dropsHostileItems() {
        val evil = """[
          {"title":"a","imageUrl":"https://evil.com/abcdefgh.jpg"},
          {"title":"b","imageUrl":"https://img.peapix.com/..%2F..%2Fx.jpg"},
          {"title":"c","imageUrl":"https://img.peapix.com/abcdefgh1234.jpg","fullUrl":"http://img.peapix.com/x.jpg"},
          {"title":"d"},
          "string",
          {"title":"ok","imageUrl":"https://img.peapix.com/abcdefgh1234.jpg"}
        ]"""
        val items = Codec.parseFeed(evil, Source.BING, "us")
        assertEquals(listOf("ok"), items.map { it.title })
        assertEquals("https://img.peapix.com/abcdefgh1234.jpg", items[0].fullUrl) // falls back to imageUrl
    }

    @Test fun nonArrayFeedIsEmpty() {
        assertEquals(0, Codec.parseFeed("""{"error":"x"}""", Source.BING, "us").size)
    }

    @Test fun settingsRoundTripAndSanitize() {
        val s = Settings(
            source = Source.SPOTLIGHT, country = "jp", autoApplyDaily = false, recentsCount = 12,
            resolution = ResolutionMode.ORIGINAL, favouritesDir = "/x/y", slideshowEnabled = true,
            slideshowIntervalMin = 15, slideshowOrder = SlideshowOrder.SEQUENTIAL, groupMix = GroupMix.EVEN,
            slideshowGroups = setOf("Alps", UNGROUPED), launchAtLogin = true,
        )
        assertEquals(s, Codec.decodeSettings(Codec.encodeSettings(s)))

        val wild = """{"recentsCount":500,"country":"zz","checkIntervalMin":1,"slideshowIntervalMin":-4,"source":"NOPE","extra":1}"""
        val d = Codec.decodeSettings(wild)
        assertEquals(30, d.recentsCount)
        assertEquals("us", d.country)
        assertEquals(30, d.checkIntervalMin)
        assertEquals(1, d.slideshowIntervalMin)
        assertEquals(Source.BING, d.source)
        assertNotNull(Codec.decodeSettings("{}"))
    }

    @Test fun stateRoundTrip() {
        val st = AppState(
            entries = listOf(entry("abcdefgh", 3, fav = true, group = "Alps"), entry("ijklmnop", 4, pending = true)),
            seen = listOf("abcdefgh", "ijklmnop", "qrstuvwx"),
            groups = listOf("Alps"), currentKey = "abcdefgh", nextSeq = 5, lastCheckMs = 123, nextSlideshowMs = 456,
        )
        assertEquals(st, Codec.decodeState(Codec.encodeState(st)))
    }

    @Test fun stateDecodingToleratesJunkEntries() {
        val text = """{"entries":[{"key":"../bad"},{"seq":1},{"key":"goodkey1","seq":7}],"seen":["a",1,null]}"""
        val st = Codec.decodeState(text)
        assertEquals(listOf("goodkey1"), st.entries.map { it.key })
        assertEquals(8, st.nextSeq)
        assertEquals(listOf("a"), st.seen)
    }
}
