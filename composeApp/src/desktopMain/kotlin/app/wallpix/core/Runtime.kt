package app.wallpix.core

import app.wallpix.platform.DesktopScreenInfo
import app.wallpix.platform.Os
import app.wallpix.platform.SystemAutostart
import app.wallpix.platform.SystemTrasher
import app.wallpix.platform.WallpaperSetters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path

/**
 * Wires the real implementations together and runs the background loop. Kept free of any UI code so the
 * whole start-up path can be compiled and exercised without Compose.
 */
class WallPixRuntime(
    val controller: AppController,
    val scope: CoroutineScope,
    val paths: AppPaths,
    /** `<Downloads>/WallPix`: where favourites go unless the user picks another folder. */
    val defaultFavouritesDir: Path,
) {
    private var loop: Job? = null

    /**
     * Loads state, then calls [AppController.tick] every [TICK_MS]. The controller compares wall-clock
     * deadlines, so a laptop that slept through midnight simply catches up on the first tick after waking.
     */
    @Synchronized
    fun start() {
        if (loop != null) return
        loop = scope.launch {
            try {
                controller.initialize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Folders may be unwritable; the controller reports per-operation problems itself.
            }
            while (isActive) {
                try {
                    controller.tick()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One bad tick must never stop the loop.
                }
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    companion object {
        const val TICK_MS = 30_000L

        fun create(os: Os = Os.current()): WallPixRuntime {
            val paths = AppPaths.default(os)
            Files.createDirectories(paths.dataDir)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val fetcher = HttpImageFetcher()
            val favourites = AppPaths.defaultFavourites(os)
            val controller = AppController(
                paths = paths,
                storage = Storage(paths),
                api = PeapixApi(),
                fetcher = fetcher,
                thumbs = ThumbStore(paths, fetcher),
                setter = WallpaperSetters.forOs(os),
                trasher = SystemTrasher(os),
                screen = DesktopScreenInfo(),
                autostart = SystemAutostart(os),
                defaultFavouritesDir = favourites,
                scope = scope,
            )
            return WallPixRuntime(controller, scope, paths, favourites)
        }
    }
}
