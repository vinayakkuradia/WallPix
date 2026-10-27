package app.wallpix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.wallpix.core.AppController
import app.wallpix.model.AppState
import app.wallpix.model.FeedItem
import app.wallpix.model.Settings
import app.wallpix.platform.Os
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.file.Path

enum class WpPage(val id: String, val title: String) {
    TODAY("today", "Today"),
    LATEST("latest", "Latest"),
    DOWNLOADED("downloaded", "Downloaded"),
    FAVOURITES("favourites", "Favourites"),
    SETTINGS("settings", "Settings"),
}

/** Everything the window needs besides the engine: which page is open and the latest message. */
@Stable
class WpUi(
    val controller: AppController,
    val scope: CoroutineScope,
    val defaultFavouritesDir: Path,
    val os: Os,
) {
    var page by mutableStateOf(WpPage.TODAY)

    /** Latest engine message ("New wallpaper: …", errors). Cleared by the banner after a while. */
    var message by mutableStateOf<String?>(null)

    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    fun checkNow() = launch { controller.checkNow() }
}

/** One consistent read of the controller's flows for a single composition pass. */
@Immutable
class WpSnapshot(
    val state: AppState,
    val settings: Settings,
    val feed: List<FeedItem>,
    val busy: String?,
) {
    val busyNow: Boolean get() = busy != null
    val downloadedCount: Int get() = state.entries.count { !it.favourite && !it.pendingTrash && it.localPath != null }
    val favouriteCount: Int get() = state.entries.count { it.favourite && !it.pendingTrash }
    val current get() = state.entries.firstOrNull { it.key == state.currentKey }
}

@Composable
fun WpUi.rememberSnapshot(): WpSnapshot {
    val state by controller.state.collectAsState()
    val settings by controller.settings.collectAsState()
    val feed by controller.feed.collectAsState()
    val busy by controller.busy.collectAsState()
    return WpSnapshot(state, settings, feed, busy)
}
