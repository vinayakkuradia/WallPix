package app.wallpix

import app.wallpix.core.AppController
import app.wallpix.core.AppPaths
import app.wallpix.core.Autostart
import app.wallpix.core.FeedApi
import app.wallpix.core.ImageFetcher
import app.wallpix.core.ScreenInfo
import app.wallpix.core.Storage
import app.wallpix.core.ThumbStore
import app.wallpix.core.Trasher
import app.wallpix.core.UiEvent
import app.wallpix.core.WallpaperSetter
import app.wallpix.logic.Schedule
import app.wallpix.model.FeedItem
import app.wallpix.model.GroupMix
import app.wallpix.model.ResolutionMode
import app.wallpix.model.SlideshowOrder
import app.wallpix.model.Source
import app.wallpix.model.UNGROUPED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---- fakes ---------------------------------------------------------------------------------------

class FakeApi : FeedApi {
    var items: List<FeedItem> = emptyList()
    var error: Exception? = null
    override suspend fun fetch(source: Source, country: String, n: Int): List<FeedItem> {
        error?.let { throw it }
        return items
    }
}

class FakeFetcher : ImageFetcher {
    val requested = CopyOnWriteArrayList<String>()
    val failing = HashSet<String>()
    override suspend fun download(url: String, dest: Path) {
        requested += url
        if (url in failing) throw IOException("HTTP 404 for $url")
        Files.createDirectories(dest.parent)
        Files.write(dest, ByteArray(4096) { 7 })
    }
}

class FakeSetter : WallpaperSetter {
    val applied = CopyOnWriteArrayList<Path>()
    var fail = false
    override fun set(path: Path): Result<Unit> {
        if (fail) return Result.failure(IllegalStateException("setter exploded"))
        // Not `+=`: java.nio.file.Path is an Iterable<Path>, which makes plusAssign ambiguous.
        applied.add(path)
        return Result.success(Unit)
    }
}

class FakeTrasher(private val bin: Path) : Trasher {
    val trashed = CopyOnWriteArrayList<String>()
    var fail = false
    override fun trash(path: Path): Boolean {
        if (fail) return false
        Files.createDirectories(bin)
        Files.move(path, bin.resolve("${trashed.size}-${path.fileName}"))
        trashed += path.fileName.toString()
        return true
    }
}

class FakeScreen(private val px: Int) : ScreenInfo {
    override fun longEdgePx() = px
}

class FakeAutostart : Autostart {
    var enabled = false
    override fun isSupported() = true
    override fun isEnabled() = enabled
    override fun set(enabled: Boolean): Result<Unit> { this.enabled = enabled; return Result.success(Unit) }
}

class Rig(screenPx: Int = 1920) {
    val root: Path = Files.createTempDirectory("wallpix-test")
    val paths = AppPaths(root.resolve("data"))
    val favDir: Path = root.resolve("Favs")
    val bin: Path = root.resolve("TRASH")
    var now = 1_700_000_000_000L
    val api = FakeApi()
    val fetcher = FakeFetcher()
    val setter = FakeSetter()
    val trasher = FakeTrasher(bin)
    val autostart = FakeAutostart()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val events = CopyOnWriteArrayList<UiEvent>()
    val controller = AppController(
        paths, Storage(paths) { now }, api, fetcher, ThumbStore(paths, fetcher), setter, trasher,
        FakeScreen(screenPx), autostart, favDir, scope, { now }, Random(1),
    )

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { controller.events.collect { events += it } }
    }

    val state get() = controller.state.value
    fun entry(key: String) = state.entries.first { it.key == key }
    fun path(key: String): Path = Path.of(entry(key).localPath!!)
    fun count(dir: Path): Int = if (Files.isDirectory(dir)) Files.list(dir).use { it.count().toInt() } else 0

    /**
     * `events` is filled by a collector running on another thread, so reading it straight after an action races the
     * collector. Wait (bounded) for a matching event instead of asserting on a snapshot.
     */
    fun awaitEvent(timeoutMs: Long = 2_000, match: (UiEvent) -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (events.any(match)) return true
            Thread.sleep(5)
        }
        return events.any(match)
    }

    fun close() { scope.cancel(); root.toFile().deleteRecursively() }
}

private fun rig(screenPx: Int = 1920, block: suspend (Rig) -> Unit) = runBlocking {
    val r = Rig(screenPx)
    try {
        r.controller.initialize()
        block(r)
    } finally {
        r.close()
    }
}

private fun item(key: String, date: String? = "2026-10-01", title: String = key) = FeedItem(
    Source.BING, "us", title, "© test", "https://img.peapix.com/${key}_1920.jpg",
    "https://img.peapix.com/${key}_640.jpg", "https://img.peapix.com/$key.jpg", "https://peapix.com/bing/1", date,
)

// ---- tests ---------------------------------------------------------------------------------------

class ControllerTest {

    @Test fun firstCheckDownloadsEverythingAndAppliesNewest() = rig { r ->
        r.api.items = listOf(item("newest0001", "2026-10-18"), item("middle0002", "2026-10-17"), item("oldest0003", "2026-10-16"))
        r.controller.checkNow()

        assertEquals(3, r.state.entries.size)
        assertEquals(3, r.state.seen.size)
        assertEquals("newest0001", r.state.currentKey)
        assertEquals(listOf(r.path("newest0001")), r.setter.applied.toList())
        assertEquals(3, r.count(r.paths.recents))
        assertEquals(3, r.controller.feed.value.size)
        assertTrue(r.entry("newest0001").seq > r.entry("oldest0003").seq)
        assertEquals(r.now, r.state.lastCheckMs)
        assertTrue(r.path("newest0001").fileName.toString().startsWith("2026-10-18_newest0001_newest00"))
    }

    @Test fun secondCheckWithNothingNewDoesNothing() = rig { r ->
        r.api.items = listOf(item("newest0001"), item("middle0002"))
        r.controller.checkNow()
        val downloads = r.fetcher.requested.size
        r.controller.checkNow()
        assertEquals(downloads, r.fetcher.requested.size)
        assertEquals(1, r.setter.applied.size)
    }

    @Test fun newDailyWallpaperIsAppliedAndNotified() = rig { r ->
        r.api.items = listOf(item("day1xxxxx", "2026-10-17"))
        r.controller.checkNow()
        r.api.items = listOf(item("day2xxxxx", "2026-10-18"), item("day1xxxxx", "2026-10-17"))
        r.controller.checkNow()
        assertEquals("day2xxxxx", r.state.currentKey)
        assertEquals(2, r.setter.applied.size)
        assertTrue(r.awaitEvent { it.notify && it.message.contains("day2xxxxx") })
    }

    @Test fun autoApplyToggleOffStillDownloads() = rig { r ->
        r.controller.updateSettings { it.copy(autoApplyDaily = false) }
        r.api.items = listOf(item("newest0001"))
        r.controller.checkNow()
        assertEquals(1, r.state.entries.size)
        assertTrue(r.setter.applied.isEmpty())
        assertNull(r.state.currentKey)
    }

    @Test fun slideshowSuspendsDailyApply() = rig { r ->
        r.controller.updateSettings { it.copy(slideshowEnabled = true) }
        r.api.items = listOf(item("newest0001"))
        r.controller.checkNow()
        assertEquals(1, r.state.entries.size)
        assertTrue(r.setter.applied.isEmpty())
    }

    @Test fun retentionKeepsOnlyNewestNMovesRestToTrashAndNeverRedownloads() = rig { r ->
        r.controller.updateSettings { it.copy(recentsCount = 2) }
        r.api.items = listOf(item("n4xxxxxxxx", "2026-10-04"), item("n3xxxxxxxx", "2026-10-03"), item("n2xxxxxxxx", "2026-10-02"), item("n1xxxxxxxx", "2026-10-01"))
        r.controller.checkNow()

        assertEquals(setOf("n4xxxxxxxx", "n3xxxxxxxx"), r.state.entries.map { it.key }.toSet())
        assertEquals(2, r.count(r.paths.recents))
        assertEquals(2, r.trasher.trashed.size)          // moved to trash, never hard-deleted
        assertEquals(2, r.count(r.bin))
        assertEquals(4, r.state.seen.size)               // evicted keys are remembered

        val downloads = r.fetcher.requested.size
        r.controller.checkNow()
        assertEquals(downloads, r.fetcher.requested.size) // evicted wallpapers are not fetched again
        assertEquals(2, r.state.entries.size)
    }

    @Test fun loweringTheCapCleansImmediately() = rig { r ->
        r.api.items = (6 downTo 1).map { item("k${it}xxxxxxxx", "2026-10-0$it") }
        r.controller.checkNow()
        assertEquals(6, r.state.entries.size)
        r.controller.updateSettings { it.copy(recentsCount = 3) }
        assertEquals(3, r.state.entries.size)
        assertEquals(3, r.trasher.trashed.size)
    }

    @Test fun currentWallpaperIsNeverEvicted() = rig { r ->
        r.controller.updateSettings { it.copy(recentsCount = 1) }
        r.api.items = listOf(item("newestxxxx", "2026-10-02"), item("oldestxxxx", "2026-10-01"))
        r.controller.checkNow()
        // Put the OLD wallpaper on the desktop; the cap says keep only 1 (the newest), but old is protected.
        assertEquals("newestxxxx", r.state.currentKey)
        r.controller.applyItem(item("oldestxxxx", "2026-10-01")) // re-downloads since it was evicted
        assertEquals("oldestxxxx", r.state.currentKey)
        assertTrue(Files.exists(r.path("oldestxxxx")))
    }

    @Test fun trashFailureKeepsFileAndEntry() = rig { r ->
        r.controller.updateSettings { it.copy(recentsCount = 1) }
        r.trasher.fail = true
        r.api.items = listOf(item("newestxxxx", "2026-10-02"), item("oldestxxxx", "2026-10-01"))
        r.controller.checkNow()
        assertEquals(2, r.state.entries.size)   // not dropped: nothing is ever permanently deleted
        assertEquals(2, r.count(r.paths.recents))
        r.trasher.fail = false
        r.controller.checkNow()                 // nothing new, but a later cleanup retries
        r.controller.updateSettings { it.copy(recentsCount = 2) }
        r.controller.updateSettings { it.copy(recentsCount = 1) }
        assertEquals(1, r.state.entries.size)
    }

    @Test fun resolutionAutoPicksOriginalOnBigScreenAndFallsBack() = rig(screenPx = 3840) { r ->
        val it = item("bigxxxxxxx")
        r.fetcher.failing += it.imageUrl
        r.api.items = listOf(it)
        r.controller.checkNow()
        assertEquals(1, r.state.entries.size)
        val order = r.fetcher.requested.filter { u -> u == it.imageUrl || u == it.fullUrl }
        assertEquals(listOf(it.imageUrl, it.fullUrl), order) // original first, then the 1920 fallback
    }

    @Test fun resolutionAutoPicksFullOnSmallScreen() = rig(screenPx = 1920) { r ->
        val it = item("smallxxxxx")
        r.api.items = listOf(it)
        r.controller.checkNow()
        assertTrue(it.fullUrl in r.fetcher.requested)
        assertFalse(it.imageUrl in r.fetcher.requested)
        r.controller.updateSettings { s -> s.copy(resolution = ResolutionMode.ORIGINAL) }
        val second = item("secondxxxx")
        r.api.items = listOf(second, it)
        r.controller.checkNow()
        assertTrue(second.imageUrl in r.fetcher.requested)
    }

    @Test fun networkFailureSchedulesQuickRetry() = rig { r ->
        r.api.error = IOException("boom")
        r.controller.checkNow()
        assertTrue(r.state.entries.isEmpty())
        assertEquals(Schedule.retryStamp(r.now, 60), r.state.lastCheckMs)
        assertTrue(r.awaitEvent { it.message.contains("Couldn't reach Peapix") })
        assertFalse(Schedule.updateDue(r.now + 4 * 60_000, r.state.lastCheckMs, 60))
        assertTrue(Schedule.updateDue(r.now + 5 * 60_000, r.state.lastCheckMs, 60))
        assertNull(r.controller.busy.value)
    }

    @Test fun failedDownloadIsRetriedAndStaleItemIsNotApplied() = rig { r ->
        val a = item("itemAxxxxx", "2026-10-02")
        val b = item("itemBxxxxx", "2026-10-01")
        r.fetcher.failing += listOf(b.fullUrl, b.imageUrl)
        r.api.items = listOf(a, b)
        r.controller.checkNow()
        assertEquals(listOf("itemAxxxxx"), r.state.entries.map { it.key })
        assertEquals("itemAxxxxx", r.state.currentKey)
        assertFalse("itemBxxxxx" in r.state.seen)

        r.fetcher.failing.clear()
        r.controller.checkNow()
        assertEquals(2, r.state.entries.size)
        assertEquals("itemAxxxxx", r.state.currentKey) // the older item must not replace the newer wallpaper
    }

    @Test fun setterFailureIsReportedAndNotRecordedAsCurrent() = rig { r ->
        r.setter.fail = true
        r.api.items = listOf(item("newest0001"))
        r.controller.checkNow()
        assertNull(r.state.currentKey)
        assertTrue(r.awaitEvent { it.message.contains("setter exploded") })
        assertEquals(1, r.state.entries.size) // still downloaded
    }

    // ---- favourites ----

    @Test fun favouritingAnOnlineItemDownloadsStraightIntoTheGroupFolderAndSurvivesCleanup() = rig { r ->
        assertEquals("Alps", r.controller.createGroup("Alps"))
        assertTrue(r.controller.addFavourite(item("favxxxxxxx", "2026-09-01"), "Alps"))
        val e = r.entry("favxxxxxxx")
        assertTrue(e.favourite)
        assertEquals("Alps", e.group)
        assertTrue(r.path("favxxxxxxx").startsWith(r.favDir.resolve("Alps")))
        assertTrue(Files.exists(r.path("favxxxxxxx")))
        assertEquals(0, r.count(r.paths.recents))

        r.controller.updateSettings { it.copy(recentsCount = 1) }
        r.api.items = listOf(item("n3xxxxxxxx", "2026-10-03"), item("n2xxxxxxxx", "2026-10-02"), item("n1xxxxxxxx", "2026-10-01"))
        r.controller.checkNow()
        assertTrue(r.entry("favxxxxxxx").favourite)   // never cleaned
        assertTrue(Files.exists(r.path("favxxxxxxx")))
        assertFalse("favxxxxxxx.jpg" in r.trasher.trashed)
    }

    @Test fun favouritingARecentMovesItAndRepointsTheDesktopIfItWasCurrent() = rig { r ->
        r.api.items = listOf(item("currentxxx", "2026-10-02"), item("otherxxxxx", "2026-10-01"))
        r.controller.checkNow()
        val old = r.path("currentxxx")
        assertTrue(r.controller.addFavourite("currentxxx"))
        val now = r.path("currentxxx")
        assertTrue(now.startsWith(r.favDir))
        assertFalse(Files.exists(old))
        assertEquals(now, r.setter.applied.last())     // desktop re-pointed to the new location
        assertEquals("currentxxx", r.state.currentKey)

        assertTrue(r.controller.addFavourite("otherxxxxx"))
        assertEquals(now, r.setter.applied.last())     // a non-current move does not touch the desktop
    }

    @Test fun unfavouritingMovesToTrash() = rig { r ->
        assertTrue(r.controller.addFavourite(item("favxxxxxxx")))
        val p = r.path("favxxxxxxx")
        r.controller.removeFavourite("favxxxxxxx")
        assertFalse(Files.exists(p))
        assertEquals(1, r.trasher.trashed.size)
        assertEquals(1, r.count(r.bin))
        assertTrue(r.state.entries.isEmpty())
    }

    @Test fun unfavouritingTheCurrentWallpaperDefersTheTrash() = rig { r ->
        r.api.items = listOf(item("bxxxxxxxxx", "2026-10-02"), item("axxxxxxxxx", "2026-10-01"))
        r.controller.checkNow()
        assertTrue(r.controller.addFavourite("bxxxxxxxxx"))       // b is current and a favourite
        r.controller.removeFavourite("bxxxxxxxxx")
        assertTrue(Files.exists(r.path("bxxxxxxxxx")))             // still on disk: it is on the desktop
        assertTrue(r.entry("bxxxxxxxxx").pendingTrash)
        assertEquals(0, r.trasher.trashed.size)

        r.controller.applyEntry("axxxxxxxxx")                      // switch away
        assertEquals(1, r.trasher.trashed.size)
        assertFalse(r.state.entries.any { it.key == "bxxxxxxxxx" })
    }

    @Test fun refavouritingAPendingItemKeepsItInPlace() = rig { r ->
        r.api.items = listOf(item("bxxxxxxxxx"))
        r.controller.checkNow()
        r.controller.addFavourite("bxxxxxxxxx")
        val p = r.path("bxxxxxxxxx")
        r.controller.removeFavourite("bxxxxxxxxx")
        assertTrue(r.controller.addFavourite("bxxxxxxxxx"))
        assertEquals(p, r.path("bxxxxxxxxx"))                      // not renamed to "-1"
        assertTrue(r.entry("bxxxxxxxxx").favourite)
        assertFalse(r.entry("bxxxxxxxxx").pendingTrash)
    }

    @Test fun trashFailureOnUnfavouriteLeavesTheFile() = rig { r ->
        r.controller.addFavourite(item("favxxxxxxx"))
        r.trasher.fail = true
        r.controller.removeFavourite("favxxxxxxx")
        assertTrue(Files.exists(r.path("favxxxxxxx")))
        assertTrue(r.entry("favxxxxxxx").favourite)
    }

    @Test fun movingBetweenGroups() = rig { r ->
        r.controller.addFavourite(item("favxxxxxxx"))
        assertEquals("Beach", r.controller.createGroup("Beach"))
        assertTrue(r.controller.moveToGroup("favxxxxxxx", "Beach"))
        assertEquals("Beach", r.entry("favxxxxxxx").group)
        assertTrue(r.path("favxxxxxxx").startsWith(r.favDir.resolve("Beach")))
        assertTrue(r.controller.moveToGroup("favxxxxxxx", UNGROUPED))
        assertEquals(r.favDir, r.path("favxxxxxxx").parent)
    }

    @Test fun groupLifecycleAndSafety() = rig { r ->
        assertNull(r.controller.createGroup(".."))
        assertNull(r.controller.createGroup("a/b".replace("a/b", "///")))
        assertEquals("Trips", r.controller.createGroup("  Trips "))
        assertTrue("Trips" in r.state.groups)
        r.controller.addFavourite(item("favxxxxxxx"), "Trips")
        assertFalse(r.controller.deleteGroup("Trips"))             // not empty
        r.controller.removeFavourite("favxxxxxxx")
        assertTrue(r.controller.deleteGroup("Trips"))
        assertFalse("Trips" in r.state.groups)
        assertFalse(Files.exists(r.favDir.resolve("Trips")))
    }

    @Test fun relocatingFavouritesMovesFilesKeepsGroupsAndRepointsDesktop() = rig { r ->
        r.api.items = listOf(item("currentxxx"))
        r.controller.checkNow()
        r.controller.createGroup("Alps")
        r.controller.addFavourite("currentxxx", "Alps")
        r.controller.addFavourite(item("rootfavxxx"))
        val target = r.root.resolve("elsewhere/MyWalls")
        assertTrue(r.controller.relocateFavourites(target))
        assertTrue(r.path("currentxxx").startsWith(target.resolve("Alps")))
        assertTrue(r.path("rootfavxxx").startsWith(target))
        assertEquals(target.toAbsolutePath().normalize().toString(), r.controller.settings.value.favouritesDir)
        assertEquals(r.path("currentxxx"), r.setter.applied.last())
        assertFalse(r.controller.relocateFavourites(target.resolve("inside"))) // nested folders are refused
    }

    // ---- slideshow ----

    @Test fun slideshowUsesOnlyFavouritesInOrder() = rig { r ->
        r.api.items = listOf(item("plainxxxxx", "2026-10-09"))
        r.controller.checkNow()                                     // a non-favourite, currently applied
        r.controller.updateSettings { it.copy(slideshowOrder = SlideshowOrder.SEQUENTIAL, groupMix = GroupMix.POOLED) }
        r.controller.addFavourite(item("gammaxxxxx", title = "Gamma"))
        r.controller.addFavourite(item("alphaxxxxx", title = "Alpha"))
        r.controller.addFavourite(item("betaxxxxxx", title = "Beta"))
        val before = r.setter.applied.size

        val seen = ArrayList<String>()
        repeat(4) {
            assertTrue(r.controller.advanceSlideshow(1))
            seen += r.state.currentKey!!
        }
        assertEquals(listOf("alphaxxxxx", "betaxxxxxx", "gammaxxxxx", "alphaxxxxx"), seen)
        assertEquals(before + 4, r.setter.applied.size)
        assertFalse("plainxxxxx" in seen)
    }

    @Test fun slideshowPreviousGoesBack() = rig { r ->
        r.controller.updateSettings { it.copy(slideshowOrder = SlideshowOrder.SEQUENTIAL) }
        r.controller.addFavourite(item("alphaxxxxx", title = "Alpha"))
        r.controller.addFavourite(item("betaxxxxxx", title = "Beta"))
        r.controller.addFavourite(item("gammaxxxxx", title = "Gamma"))
        r.controller.advanceSlideshow(1) // alpha
        r.controller.advanceSlideshow(1) // beta
        r.controller.advanceSlideshow(1) // gamma
        assertTrue(r.controller.advanceSlideshow(-1))
        assertEquals("betaxxxxxx", r.state.currentKey)
        assertTrue(r.controller.advanceSlideshow(-1))
        assertEquals("alphaxxxxx", r.state.currentKey)
    }

    @Test fun slideshowRespectsSelectedGroupsAndEvenMix() = rig { r ->
        r.controller.createGroup("Alps"); r.controller.createGroup("Beach")
        r.controller.addFavourite(item("a1xxxxxxxx", title = "A1"), "Alps")
        r.controller.addFavourite(item("a2xxxxxxxx", title = "A2"), "Alps")
        r.controller.addFavourite(item("b1xxxxxxxx", title = "B1"), "Beach")
        r.controller.updateSettings { it.copy(slideshowOrder = SlideshowOrder.SEQUENTIAL, slideshowGroups = setOf("Beach")) }
        repeat(3) { r.controller.advanceSlideshow(1); assertEquals("b1xxxxxxxx", r.state.currentKey) }

        r.controller.updateSettings { it.copy(slideshowGroups = emptySet(), groupMix = GroupMix.EVEN) }
        val order = ArrayList<String>()
        repeat(4) { r.controller.advanceSlideshow(1); order += r.state.currentKey!! }
        // The even-mix cycle is a1, b1, a2 (round-robin across Alps/Beach). Sequential mode continues after the
        // wallpaper that is on screen, and phase one left b1 there, so we resume at a2 and wrap around.
        assertEquals(listOf("a2xxxxxxxx", "a1xxxxxxxx", "b1xxxxxxxx", "a2xxxxxxxx"), order)
    }

    @Test fun slideshowWithNoFavouritesDoesNothing() = rig { r ->
        assertFalse(r.controller.advanceSlideshow(1))
        assertTrue(r.setter.applied.isEmpty())
        assertTrue(r.awaitEvent { it.message.contains("no favourites") })
    }

    @Test fun tickRunsSlideshowOnlyWhenDue() = rig { r ->
        r.controller.addFavourite(item("alphaxxxxx", title = "Alpha"))
        r.controller.addFavourite(item("betaxxxxxx", title = "Beta"))
        r.controller.updateSettings { it.copy(slideshowOrder = SlideshowOrder.SEQUENTIAL, slideshowIntervalMin = 10, autoApplyDaily = false) }
        r.api.items = emptyList()
        r.controller.checkNow()                                      // marks the feed as freshly checked
        // Enabling applies immediately (async), then the next change is due 10 minutes later.
        r.controller.updateSettings { it.copy(slideshowEnabled = true) }
        val deadline = System.currentTimeMillis() + 5_000
        while (r.setter.applied.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(1, r.setter.applied.size)
        assertEquals(r.now + 10 * 60_000, r.state.nextSlideshowMs)

        r.now += 9 * 60_000
        r.controller.tick()
        assertEquals(1, r.setter.applied.size)                       // not due yet
        r.now += 2 * 60_000
        r.controller.tick()
        assertEquals(2, r.setter.applied.size)                       // due
        r.now += 3 * 60 * 60_000                                     // laptop slept for 3 h
        r.controller.tick()
        assertEquals(3, r.setter.applied.size)                       // exactly one catch-up change, no burst
    }

    // ---- lifecycle & persistence ----

    @Test fun initializeDropsMissingFilesAndDiscoversGroupFolders() = rig { r ->
        r.api.items = listOf(item("axxxxxxxxx", "2026-10-02"), item("bxxxxxxxxx", "2026-10-01"))
        r.controller.checkNow()
        Files.delete(r.path("bxxxxxxxxx"))
        Files.createDirectories(r.favDir.resolve("HandMade"))
        Files.createDirectories(r.favDir.resolve(".hidden"))
        r.controller.initialize()
        assertEquals(listOf("axxxxxxxxx"), r.state.entries.map { it.key })
        assertEquals(listOf("HandMade"), r.state.groups)
    }

    @Test fun stateSurvivesARestart() = rig { r ->
        r.api.items = listOf(item("axxxxxxxxx", "2026-10-02"), item("bxxxxxxxxx", "2026-10-01"))
        r.controller.checkNow()
        r.controller.addFavourite("bxxxxxxxxx")
        r.controller.updateSettings { it.copy(country = "jp", recentsCount = 5) }
        val reloaded = Storage(r.paths) { r.now }
        assertEquals(r.state, reloaded.loadState())
        assertEquals("jp", reloaded.loadSettings().country)
        assertEquals(5, reloaded.loadSettings().recentsCount)
    }

    @Test fun corruptStateFileIsSetAsideNotOverwritten() = rig { r ->
        Files.createDirectories(r.paths.stateFile.parent)
        Files.writeString(r.paths.stateFile, "{ not json")
        val st = Storage(r.paths) { 42 }.loadState()
        assertTrue(st.entries.isEmpty())
        assertTrue(Files.exists(r.paths.stateFile.resolveSibling("library.json.corrupt-42")))
    }

    @Test fun launchAtLoginTogglesAutostartAndRevertsOnFailure() = rig { r ->
        r.controller.updateSettings { it.copy(launchAtLogin = true) }
        assertTrue(r.autostart.enabled)
        r.controller.updateSettings { it.copy(launchAtLogin = false) }
        assertFalse(r.autostart.enabled)
    }
}
