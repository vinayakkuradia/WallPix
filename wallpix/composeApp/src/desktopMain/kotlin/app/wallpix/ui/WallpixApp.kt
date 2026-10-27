package app.wallpix.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.wallpix.core.AppController
import app.wallpix.platform.DesktopActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.file.Path

private enum class Page(val title: String) {
    LATEST("Latest"),
    DOWNLOADED("Downloaded"),
    FAVOURITES("Favourites"),
    SETTINGS("Settings"),
}

/** The whole window: header, status, page switcher, the current page and the attribution footer. */
@Composable
fun WallpixApp(
    controller: AppController,
    scope: CoroutineScope,
    defaultFavouritesDir: Path,
    /** The latest message from the engine ("New wallpaper: …", errors), shown under the header. */
    message: String?,
) {
    val state by controller.state.collectAsState()
    val settings by controller.settings.collectAsState()
    val feed by controller.feed.collectAsState()
    val busy by controller.busy.collectAsState()
    val ui = UiContext(controller, scope, state, settings, busy != null)

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var page by remember { mutableStateOf(Page.LATEST) }
            Column(Modifier.fillMaxSize()) {
                Header(ui)
                Status(busy, message)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Page.entries.forEach { p ->
                        val count = when (p) {
                            Page.LATEST -> null
                            Page.DOWNLOADED -> state.entries.count { !it.favourite && !it.pendingTrash && it.localPath != null }
                            Page.FAVOURITES -> state.entries.count { it.favourite && !it.pendingTrash }
                            Page.SETTINGS -> null
                        }
                        FilterChip(
                            selected = page == p,
                            onClick = { page = p },
                            label = { Text(if (count == null) p.title else "${p.title} ($count)") },
                        )
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    when (page) {
                        Page.LATEST -> LatestScreen(ui, feed)
                        Page.DOWNLOADED -> DownloadedScreen(ui)
                        Page.FAVOURITES -> FavouritesScreen(ui)
                        Page.SETTINGS -> SettingsScreen(ui, defaultFavouritesDir)
                    }
                }
                Footer()
            }
        }
    }
}

@Composable
private fun Header(ui: UiContext) {
    val current = ui.state.entries.firstOrNull { it.key == ui.state.currentKey }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("WallPix", style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.weight(1f)) {
            Text(
                if (ui.state.lastCheckMs > 0) "Last checked ${DesktopActions.formatTime(ui.state.lastCheckMs)}" else "Not checked yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (current != null) {
                Text(
                    "Now showing: ${current.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(onClick = { ui.scope.launch { ui.controller.checkNow() } }, enabled = !ui.busy) {
            Text("Check now")
        }
    }
}

@Composable
private fun Status(busy: String?, message: String?) {
    if (busy != null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            busy,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
    } else if (!message.isNullOrBlank()) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Footer() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Images are provided by Peapix for wallpaper use only. Copyright stays with the photographers.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { DesktopActions.openUrl("https://peapix.com") }) { Text("peapix.com") }
    }
}
