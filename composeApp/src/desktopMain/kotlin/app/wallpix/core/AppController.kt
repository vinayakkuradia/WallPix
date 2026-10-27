package app.wallpix.core

import app.wallpix.logic.ResolutionPicker
import app.wallpix.logic.Retention
import app.wallpix.logic.Schedule
import app.wallpix.logic.SlideshowPlanner
import app.wallpix.logic.UpdatePlanner
import app.wallpix.model.AppState
import app.wallpix.model.FeedItem
import app.wallpix.model.GroupNames
import app.wallpix.model.LibraryEntry
import app.wallpix.model.Naming
import app.wallpix.model.SEEN_LIMIT
import app.wallpix.model.Settings
import app.wallpix.model.Source
import app.wallpix.model.UNGROUPED
import app.wallpix.model.toEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** A message for the user. [notify] asks the shell to raise a tray notification as well. */
data class UiEvent(val message: String, val notify: Boolean = false)

/**
 * Owns all state and every side effect. Public operations are serialised by one mutex, so the UI,
 * the tray and the scheduler can call in freely from any thread.
 *
 * Rules enforced here (see DESIGN.md):
 *  - new daily wallpapers are always downloaded; applied only if the toggle is on and no slideshow runs
 *  - only the N most recent non-favourites are kept; the rest are moved to the OS trash
 *  - favourites live in the favourites folder (sub-folders = groups) and are never auto-cleaned
 *  - the wallpaper currently on the desktop is never trashed; its deletion is deferred
 *  - moving a file that is on the desktop re-applies it from its new path
 */
class AppController(
    private val paths: AppPaths,
    private val storage: Storage,
    private val api: FeedApi,
    private val fetcher: ImageFetcher,
    private val thumbs: ThumbStore,
    private val setter: WallpaperSetter,
    private val trasher: Trasher,
    private val screen: ScreenInfo,
    private val autostart: Autostart,
    private val defaultFavouritesDir: Path,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val random: Random = Random.Default,
) {
    private val mutex = Mutex()
    private val _settings = MutableStateFlow(storage.loadSettings())
    private val _state = MutableStateFlow(storage.loadState())
    private val _feed = MutableStateFlow<List<FeedItem>>(emptyList())
    private val _busy = MutableStateFlow<String?>(null)
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 32)
    private val history = ArrayDeque<String>()

    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** The live Peapix feed, newest first (typically about 7 items). */
    val feed: StateFlow<List<FeedItem>> = _feed.asStateFlow()

    /** Non-null while a long operation is running. */
    val busy: StateFlow<String?> = _busy.asStateFlow()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val autostartSupported: Boolean get() = autostart.isSupported()

    fun favouritesDir(): Path =
        _settings.value.favouritesDir?.takeIf { it.isNotBlank() }?.let { Paths.get(it) } ?: defaultFavouritesDir

    // ---- lifecycle ---------------------------------------------------------------------------

    /** Creates folders, drops entries whose files vanished, and picks up group folders created by hand. */
    suspend fun initialize() = mutex.withLock {
        withContext(Dispatchers.IO) {
            Files.createDirectories(paths.recents)
            Files.createDirectories(paths.thumbs)
            runCatching { Files.createDirectories(favouritesDir()) }

            val st = _state.value
            val kept = st.entries.filter { fileExists(it) }
            val onDisk = runCatching {
                Files.list(favouritesDir()).use { s ->
                    s.filter { Files.isDirectory(it) && !it.fileName.toString().startsWith(".") }
                        .map { it.fileName.toString() }.toList()
                }
            }.getOrDefault(emptyList())
            val groups = (st.groups + onDisk + kept.filter { it.favourite }.map { it.group })
                .filter { it != UNGROUPED }.distinct().sorted()
            mutate { it.copy(entries = kept, groups = groups) }
            thumbs.prune(kept.map { it.key }.toSet())

            if (autostart.isSupported()) {
                val actual = autostart.isEnabled()
                if (actual != _settings.value.launchAtLogin) setSettings(_settings.value.copy(launchAtLogin = actual))
            }
        }
    }

    /** Called every ~30 s by the shell. Uses wall-clock deadlines, so sleep and wake need no special care. */
    suspend fun tick() {
        val s = _settings.value
        if (Schedule.updateDue(clock(), _state.value.lastCheckMs, s.checkIntervalMin)) checkNow()
        if (s.slideshowEnabled) {
            if (_state.value.nextSlideshowMs == 0L) scheduleNext(s)
            if (Schedule.slideshowDue(clock(), _state.value.nextSlideshowMs)) advanceSlideshow(1)
        }
    }

    // ---- daily update ------------------------------------------------------------------------

    suspend fun checkNow() = mutex.withLock { doCheck() }

    private suspend fun doCheck() {
        val s = _settings.value
        _busy.value = "Checking for new wallpapers…"
        try {
            val items = try {
                api.fetch(s.source, s.country, FEED_N)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutate { it.copy(lastCheckMs = Schedule.retryStamp(clock(), s.checkIntervalMin)) }
                event("Couldn't reach Peapix (${e.message ?: e::class.simpleName}). Will retry shortly.")
                return
            }
            _feed.value = items

            val plan = UpdatePlanner.plan(items, _state.value.seen.toHashSet(), s.autoApplyDaily, s.slideshowEnabled)
            var toApply: LibraryEntry? = null
            var failures = 0
            for (item in plan.newItems) {
                try {
                    val entry = ingest(item, s, favouriteGroup = null)
                    if (item.key == plan.applyKey) toApply = entry
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failures++
                }
            }
            toApply?.let { if (applyLocked(it)) event("New wallpaper: ${it.title}", notify = true) }
            cleanupLocked()
            // After a failed download, check again soon; the item was not marked as seen, so it is retried.
            val stamp = if (failures == 0) clock() else Schedule.retryStamp(clock(), s.checkIntervalMin)
            mutate { it.copy(lastCheckMs = stamp) }
            if (failures > 0) event("$failures image(s) failed to download and will be retried.")
        } finally {
            _busy.value = null
        }
    }

    /**
     * Downloads [item] (to the recents folder, or straight into a favourites group when
     * [favouriteGroup] is non-null) and records it. Throws if no variant could be downloaded.
     */
    private suspend fun ingest(item: FeedItem, s: Settings, favouriteGroup: String?): LibraryEntry {
        val fav = favouriteGroup != null
        val dir = if (fav) groupDir(favouriteGroup) else paths.recents
        val dest = withContext(Dispatchers.IO) {
            Files.createDirectories(dir)
            uniqueTarget(dir, Naming.fileName(item.date ?: today(), item.title, item.key))
        }
        downloadBest(item, dest, s)
        thumbs.ensure(item.key, item.thumbUrl)
        val entry = item.toEntry(
            seq = _state.value.nextSeq, nowMs = clock(), localPath = dest.toString(),
            favourite = fav, group = favouriteGroup ?: UNGROUPED,
        )
        mutate {
            it.copy(
                entries = it.entries.filter { e -> e.key != entry.key } + entry,
                seen = (it.seen.filter { k -> k != entry.key } + entry.key).takeLast(SEEN_LIMIT),
                nextSeq = it.nextSeq + 1,
            )
        }
        return entry
    }

    private suspend fun downloadBest(item: FeedItem, dest: Path, s: Settings) {
        val preferred = ResolutionPicker.variantFor(s.resolution, withContext(Dispatchers.IO) { screen.longEdgePx() })
        var last: Exception? = null
        for (v in ResolutionPicker.chain(preferred)) {
            try {
                fetcher.download(ResolutionPicker.urlFor(v, item.fullUrl, item.thumbUrl, item.imageUrl), dest)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Download failed")
    }

    // ---- applying ----------------------------------------------------------------------------

    suspend fun applyEntry(key: String): Boolean = mutex.withLock {
        val e = _state.value.entries.find { it.key == key } ?: return@withLock false
        applyLocked(e)
    }

    /** Applies an online item, downloading it into the recents first if needed. */
    suspend fun applyItem(item: FeedItem): Boolean = mutex.withLock {
        val existing = _state.value.entries.find { it.key == item.key }
        val entry = if (existing != null && fileExists(existing)) {
            existing
        } else {
            try {
                ingest(item, _settings.value, favouriteGroup = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                event("Couldn't download that wallpaper: ${e.message}")
                return@withLock false
            }
        }
        applyLocked(entry).also { cleanupLocked() }
    }

    private suspend fun applyLocked(e: LibraryEntry): Boolean {
        val path = e.localPath?.let { Paths.get(it) }
        if (path == null || !Files.exists(path)) {
            event("The file for “${e.title}” is missing.")
            return false
        }
        val r = withContext(Dispatchers.IO) { setter.set(path) }
        if (r.isFailure) {
            event("Couldn't set the wallpaper: ${r.exceptionOrNull()?.message ?: "unknown error"}")
            return false
        }
        mutate { it.copy(currentKey = e.key) }
        // Something else is now on the desktop, so deferred deletions can proceed.
        cleanupLocked()
        return true
    }

    // ---- retention ---------------------------------------------------------------------------

    private suspend fun cleanupLocked() {
        val st = _state.value
        val protectedKeys = setOfNotNull(st.currentKey)
        val doomed = (
            Retention.evict(st.entries, _settings.value.recentsCount, protectedKeys) +
                Retention.pendingTrash(st.entries, protectedKeys)
            ).toSet()
        if (doomed.isEmpty()) return

        val removed = HashSet<String>()
        withContext(Dispatchers.IO) {
            for (e in st.entries.filter { it.key in doomed }) {
                val p = e.localPath?.let { Paths.get(it) }
                // A file that cannot be trashed is kept (never permanently deleted) and retried next time.
                if (p == null || !Files.exists(p) || trasher.trash(p)) {
                    removed += e.key
                    thumbs.delete(e.key)
                }
            }
        }
        if (removed.isNotEmpty()) mutate { it.copy(entries = it.entries.filter { e -> e.key !in removed }) }
    }

    // ---- favourites --------------------------------------------------------------------------

    /** Favourite an online item: downloads it straight into the favourites folder (or moves the local copy). */
    suspend fun addFavourite(item: FeedItem, group: String = UNGROUPED): Boolean = mutex.withLock {
        val g = normalizeGroup(group)
        val existing = _state.value.entries.find { it.key == item.key }
        if (existing != null && existing.favourite && !existing.pendingTrash) return@withLock true
        try {
            ensureGroup(g)
            if (existing != null && fileExists(existing)) {
                moveIntoGroup(existing, g, favourite = true)
            } else {
                ingest(item, _settings.value, favouriteGroup = g)
            }
            event("Added “${item.title}” to favourites")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            event("Couldn't save the favourite: ${e.message}")
            false
        }
    }

    /** Favourite something already in the recents. */
    suspend fun addFavourite(key: String, group: String = UNGROUPED): Boolean = mutex.withLock {
        val e = _state.value.entries.find { it.key == key } ?: return@withLock false
        if (e.favourite && !e.pendingTrash) return@withLock true
        val g = normalizeGroup(group)
        try {
            ensureGroup(g)
            moveIntoGroup(e, g, favourite = true)
            event("Added “${e.title}” to favourites")
            true
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            event("Couldn't save the favourite: ${ex.message}")
            false
        }
    }

    /** Un-favourite = delete, but to the Trash / Recycle Bin, never permanently. */
    suspend fun removeFavourite(key: String): Unit = mutex.withLock {
        val e = _state.value.entries.find { it.key == key && it.favourite } ?: return@withLock
        if (_state.value.currentKey == key) {
            mutate { st -> st.copy(entries = st.entries.map { if (it.key == key) it.copy(favourite = false, pendingTrash = true) else it }) }
            event("It's your current wallpaper, so it will go to the Trash as soon as you switch to another.")
            return@withLock
        }
        val p = e.localPath?.let { Paths.get(it) }
        val ok = withContext(Dispatchers.IO) { p == null || !Files.exists(p) || trasher.trash(p) }
        if (ok) {
            thumbs.delete(key)
            mutate { st -> st.copy(entries = st.entries.filter { it.key != key }) }
            event("Moved “${e.title}” to the Trash")
        } else {
            event("Couldn't move it to the Trash, so the file was left where it is.")
        }
    }

    suspend fun moveToGroup(key: String, group: String): Boolean = mutex.withLock {
        val e = _state.value.entries.find { it.key == key && it.favourite } ?: return@withLock false
        val g = normalizeGroup(group)
        if (g == e.group) return@withLock true
        try {
            ensureGroup(g)
            moveIntoGroup(e, g, favourite = true)
            true
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            event("Couldn't move the file: ${ex.message}")
            false
        }
    }

    /** Creates a group (a folder inside the favourites folder). Returns the sanitised name, or null. */
    suspend fun createGroup(name: String): String? = mutex.withLock {
        val g = GroupNames.sanitize(name)
        if (g == null) {
            event("That isn't a usable folder name.")
            return@withLock null
        }
        try {
            withContext(Dispatchers.IO) { Files.createDirectories(groupDir(g)) }
            ensureGroup(g)
            g
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            event("Couldn't create the group: ${e.message}")
            null
        }
    }

    /** Removes an empty group. Groups that still hold favourites are left alone. */
    suspend fun deleteGroup(name: String): Boolean = mutex.withLock {
        if (_state.value.entries.any { it.favourite && it.group == name }) {
            event("Move or remove the favourites in “$name” first.")
            return@withLock false
        }
        withContext(Dispatchers.IO) {
            // Only ever removes an empty folder; Files.delete refuses non-empty ones.
            runCatching { Files.deleteIfExists(groupDir(name)) }
        }
        mutate { it.copy(groups = it.groups.filter { g -> g != name }) }
        setSettings(_settings.value.copy(slideshowGroups = _settings.value.slideshowGroups - name))
        true
    }

    /** Moves every favourite into [newDir] (keeping groups) and makes it the favourites folder. */
    suspend fun relocateFavourites(newDir: Path): Boolean = mutex.withLock {
        val old = favouritesDir().toAbsolutePath().normalize()
        val target = newDir.toAbsolutePath().normalize()
        if (old == target) return@withLock true
        if (target.startsWith(old) || old.startsWith(target)) {
            event("Pick a folder that isn't inside the current favourites folder, or the other way round.")
            return@withLock false
        }
        val favs = _state.value.entries.filter { it.favourite && fileExists(it) }
        val moved = HashMap<String, String>()
        var failed = 0
        withContext(Dispatchers.IO) {
            Files.createDirectories(target)
            _state.value.groups.forEach { runCatching { Files.createDirectories(target.resolve(it)) } }
            for (e in favs) {
                try {
                    val src = Paths.get(e.localPath!!)
                    val dir = if (e.group == UNGROUPED) target else target.resolve(e.group)
                    Files.createDirectories(dir)
                    val t = uniqueTarget(dir, src.fileName.toString())
                    Files.move(src, t)
                    moved[e.key] = t.toString()
                } catch (_: Exception) {
                    failed++
                }
            }
        }
        mutate { st -> st.copy(entries = st.entries.map { e -> moved[e.key]?.let { e.copy(localPath = it) } ?: e }) }
        val current = _state.value.currentKey?.let { k -> _state.value.entries.find { it.key == k } }
        if (current != null && current.key in moved) applyLocked(current)
        if (failed == 0) {
            setSettings(_settings.value.copy(favouritesDir = target.toString()))
            event("Favourites moved to $target")
        } else {
            event("$failed file(s) couldn't be moved, so the favourites folder was not changed.")
        }
        failed == 0
    }

    private suspend fun moveIntoGroup(e: LibraryEntry, group: String, favourite: Boolean): LibraryEntry {
        val src = Paths.get(e.localPath!!)
        val dir = groupDir(group)
        val newPath = withContext(Dispatchers.IO) {
            Files.createDirectories(dir)
            if (src.toAbsolutePath().normalize().parent == dir.toAbsolutePath().normalize()) {
                src
            } else {
                uniqueTarget(dir, src.fileName.toString()).also { Files.move(src, it) }
            }
        }
        val updated = e.copy(
            localPath = newPath.toString(), favourite = favourite,
            group = if (favourite) group else UNGROUPED, pendingTrash = false,
        )
        mutate { st -> st.copy(entries = st.entries.map { if (it.key == e.key) updated else it }) }
        // The OS still points at the old path, so re-point it.
        if (_state.value.currentKey == e.key) applyLocked(updated)
        return updated
    }

    // ---- slideshow ---------------------------------------------------------------------------

    suspend fun advanceSlideshow(direction: Int = 1): Boolean = mutex.withLock { advanceLocked(direction) }

    private suspend fun advanceLocked(direction: Int): Boolean {
        val s = _settings.value
        scheduleNext(s)
        val st = _state.value
        val pool = SlideshowPlanner.pool(st.entries, s.slideshowGroups).filter { fileExists(it) }
        if (pool.isEmpty()) {
            event("The slideshow has no favourites to show. Star some wallpapers first.")
            return false
        }
        val target: LibraryEntry? = if (direction < 0) {
            var found: LibraryEntry? = null
            while (found == null && history.isNotEmpty()) {
                val k = history.removeLast()
                found = st.entries.find { it.key == k && !it.pendingTrash && fileExists(it) }
            }
            found
        } else {
            SlideshowPlanner.next(pool, st.currentKey, s.slideshowOrder, s.groupMix, random)
        }
        if (target == null) {
            event("No earlier wallpaper to go back to.")
            return false
        }
        val previous = st.currentKey
        if (!applyLocked(target)) return false
        if (direction >= 0 && previous != null && previous != target.key) {
            history.addLast(previous)
            while (history.size > 50) history.removeFirst()
        }
        return true
    }

    private fun scheduleNext(s: Settings) {
        mutate { it.copy(nextSlideshowMs = Schedule.nextSlideshow(clock(), s.slideshowIntervalMin)) }
    }

    // ---- settings ----------------------------------------------------------------------------

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        val old = _settings.value
        val new = transform(old).sanitized()
        if (new == old) return
        setSettings(new)

        if (new.launchAtLogin != old.launchAtLogin) {
            val r = withContext(Dispatchers.IO) { autostart.set(new.launchAtLogin) }
            if (r.isFailure) {
                setSettings(_settings.value.copy(launchAtLogin = old.launchAtLogin))
                event("Couldn't change launch-at-login: ${r.exceptionOrNull()?.message}")
            }
        }
        if (new.source != old.source || (new.source == Source.BING && new.country != old.country)) {
            scope.launch { checkNow() }
        }
        if (new.recentsCount < old.recentsCount) mutex.withLock { cleanupLocked() }
        if (new.slideshowEnabled != old.slideshowEnabled) {
            if (new.slideshowEnabled) {
                scope.launch { advanceSlideshow(1) }
            } else {
                mutate { it.copy(nextSlideshowMs = 0) }
            }
        } else if (new.slideshowEnabled && new.slideshowIntervalMin != old.slideshowIntervalMin) {
            scheduleNext(new)
        }
    }

    // ---- UI helpers --------------------------------------------------------------------------

    /** Ensures the 640 px thumbnail for [key] is on disk and returns it (null if unavailable). */
    suspend fun thumbFile(key: String, url: String): Path? = thumbs.ensure(key, url)

    /** A 1920 px picture for the full-screen viewer, cached with the thumbnails. Null when unavailable. */
    suspend fun previewFile(key: String, fullUrl: String): Path? = thumbs.ensurePreview(key, fullUrl)

    // ---- internals ---------------------------------------------------------------------------

    private fun setSettings(s: Settings) {
        _settings.value = s
        storage.saveSettings(s)
    }

    private fun mutate(block: (AppState) -> AppState) {
        val n = block(_state.value)
        _state.value = n
        storage.saveState(n)
    }

    private fun event(message: String, notify: Boolean = false) {
        _events.tryEmit(UiEvent(message, notify))
    }

    private fun fileExists(e: LibraryEntry): Boolean = e.localPath?.let { Files.exists(Paths.get(it)) } == true

    private fun today(): String = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun normalizeGroup(g: String): String = if (g == UNGROUPED) UNGROUPED else GroupNames.sanitize(g) ?: UNGROUPED

    private fun groupDir(g: String): Path = if (g == UNGROUPED) favouritesDir() else favouritesDir().resolve(g)

    private fun ensureGroup(g: String) {
        if (g != UNGROUPED && g !in _state.value.groups) mutate { it.copy(groups = (it.groups + g).sorted()) }
    }

    private fun uniqueTarget(dir: Path, fileName: String): Path {
        var t = dir.resolve(fileName)
        if (!Files.exists(t)) return t
        val base = fileName.substringBeforeLast('.')
        val ext = if ('.' in fileName) "." + fileName.substringAfterLast('.') else ""
        var i = 1
        while (true) {
            t = dir.resolve("$base-$i$ext")
            if (!Files.exists(t)) return t
            i++
        }
    }

    companion object {
        /** Peapix serves about 7 items whatever `n` is; ask for a few more in case that changes. */
        const val FEED_N = 10
    }
}
