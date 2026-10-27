package app.wallpix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.wallpix.core.AppController
import app.wallpix.logic.Browse
import app.wallpix.logic.Labels
import app.wallpix.logic.Wallpaper
import app.wallpix.model.AppState
import app.wallpix.model.BING_COUNTRIES
import app.wallpix.model.FeedItem
import app.wallpix.model.Settings
import app.wallpix.model.Source
import app.wallpix.model.UNGROUPED
import app.wallpix.platform.DesktopActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Everything a screen needs, bundled so it can be passed around as one value. */
class UiContext(
    val controller: AppController,
    val scope: CoroutineScope,
    val state: AppState,
    val settings: Settings,
    /** True while a long operation (check, download) runs. */
    val busy: Boolean,
)

@Composable
private fun WallpaperGrid(
    wallpapers: List<Wallpaper>,
    emptyText: String,
    ui: UiContext,
    showGroupMove: Boolean = false,
) {
    if (wallpapers.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(wallpapers, key = { it.key }) { w ->
            WallpaperCard(w, ui, showGroupMove)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The online "latest N" page: the live feed, topped up with what WallPix remembers. */
@Composable
fun LatestScreen(ui: UiContext, feed: List<FeedItem>) {
    val s = ui.settings
    val items = remember(feed, ui.state.entries, s.recentsCount) { Browse.latest(feed, ui.state.entries, s.recentsCount) }
    val where = if (s.source == Source.BING) "Bing (${BING_COUNTRIES[s.country] ?: s.country})" else "Windows Spotlight"
    Column(Modifier.fillMaxSize()) {
        Hint(
            "Latest ${s.recentsCount} from $where. Peapix serves only about a week at a time, so WallPix tops " +
                "the list up with wallpapers it has already seen.",
        )
        Box(Modifier.weight(1f)) {
            WallpaperGrid(
                wallpapers = items,
                emptyText = if (ui.busy) "Fetching the latest wallpapers…" else "Nothing yet. Press “Check now”.",
                ui = ui,
            )
        }
    }
}

/** Recents on disk (non-favourites). Only the newest N are kept; older ones go to the Trash. */
@Composable
fun DownloadedScreen(ui: UiContext) {
    val items = remember(ui.state.entries) { Browse.downloaded(ui.state.entries) }
    Column(Modifier.fillMaxSize()) {
        Hint(
            "Recent downloads, newest first. Only the latest ${ui.settings.recentsCount} are kept; older ones are " +
                "moved to your Trash. Star one to keep it for good.",
        )
        Box(Modifier.weight(1f)) {
            WallpaperGrid(wallpapers = items, emptyText = "No downloaded wallpapers yet.", ui = ui)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavouritesScreen(ui: UiContext) {
    // null = all groups; UNGROUPED ("") = the favourites folder itself.
    var selected by remember { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }

    val all = remember(ui.state.entries) { Browse.favourites(ui.state.entries) }
    val rootCount = all.count { it.group == UNGROUPED }
    // A group that has just been removed must not stay selected.
    val active = selected?.takeIf { it == UNGROUPED || it in ui.state.groups }
    val shown = if (active == null) all else all.filter { it.group == active }
    val removable = active != null && active != UNGROUPED && all.none { it.group == active }
    val folder = ui.controller.favouritesDir()

    Column(Modifier.fillMaxSize()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(selected = active == null, onClick = { selected = null }, label = { Text("All (${all.size})") })
            if (rootCount > 0) {
                FilterChip(
                    selected = active == UNGROUPED,
                    onClick = { selected = UNGROUPED },
                    label = { Text("No group ($rootCount)") },
                )
            }
            ui.state.groups.forEach { group ->
                val count = all.count { it.group == group }
                FilterChip(selected = active == group, onClick = { selected = group }, label = { Text("$group ($count)") })
            }
            AssistChip(onClick = { addOpen = true }, label = { Text("+ New group") })
            if (removable) {
                TextButton(onClick = {
                    val name = active
                    selected = null
                    if (name != null) ui.scope.launch { ui.controller.deleteGroup(name) }
                }) { Text("Remove empty group") }
            }
            TextButton(onClick = { DesktopActions.openFolder(folder) }) { Text("Open folder") }
        }
        Box(Modifier.weight(1f)) {
            WallpaperGrid(
                wallpapers = shown,
                emptyText = "No favourites here yet. Star a wallpaper on the Latest or Downloaded page and it is " +
                    "saved to $folder.",
                ui = ui,
                showGroupMove = true,
            )
        }
    }

    if (addOpen) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text("New group") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    label = { Text("Group name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val raw = name
                        addOpen = false
                        ui.scope.launch {
                            val created = ui.controller.createGroup(raw)
                            if (created != null) selected = created
                        }
                    },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { addOpen = false }) { Text("Cancel") } },
        )
    }
}
