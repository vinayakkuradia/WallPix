package app.wallpix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.wallpix.core.AppController
import app.wallpix.logic.Labels
import app.wallpix.logic.Wallpaper
import app.wallpix.model.UNGROUPED
import app.wallpix.platform.DesktopActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Apply from the live feed (downloading if needed) or from the local copy. */
private suspend fun AppController.applyWallpaper(w: Wallpaper) {
    val item = w.item
    if (item != null) applyItem(item) else applyEntry(w.key)
}

/** Favourite from the local recents when there is a copy on disk, otherwise straight from the feed. */
private suspend fun AppController.favouriteWallpaper(w: Wallpaper, group: String) {
    val item = w.item
    if (w.onDisk) addFavourite(w.key, group) else if (item != null) addFavourite(item, group)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallpaperCard(w: Wallpaper, ui: UiContext, showGroupMove: Boolean = false) {
    val applied = ui.state.currentKey == w.key
    Card(Modifier.fillMaxWidth()) {
        Column {
            Thumbnail(ui.controller, w.key, w.thumbUrl, w.title, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(w.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    w.date,
                    w.copyright.ifBlank { null },
                    if (w.favourite && w.group != UNGROUPED) w.group else null,
                    if (w.favourite) "favourite" else null,
                ).joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { ui.scope.launch { ui.controller.applyWallpaper(w) } },
                        enabled = !applied && !ui.busy,
                    ) { Text(if (applied) "Current wallpaper" else "Set wallpaper") }
                    FavouriteButton(w, ui)
                    if (showGroupMove && w.favourite) {
                        Dropdown(
                            selected = w.group,
                            options = listOf(UNGROUPED) + ui.state.groups,
                            label = { "Group: " + Labels.group(it) },
                            onSelect = { g -> ui.scope.launch { ui.controller.moveToGroup(w.key, g) } },
                        )
                    }
                    TextButton(onClick = { DesktopActions.openUrl(w.pageUrl) }) { Text("Peapix page") }
                }
            }
        }
    }
}

@Composable
private fun FavouriteButton(w: Wallpaper, ui: UiContext) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }
    val hasGroups = ui.state.groups.isNotEmpty()

    Box {
        OutlinedButton(
            onClick = {
                when {
                    w.favourite -> confirmOpen = true
                    hasGroups -> menuOpen = true
                    else -> ui.scope.launch { ui.controller.favouriteWallpaper(w, UNGROUPED) }
                }
            },
        ) { Text(if (w.favourite) "★ Favourite" else "☆ Favourite") }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            (listOf(UNGROUPED) + ui.state.groups).forEach { group ->
                DropdownMenuItem(
                    text = { Text("Add to " + Labels.group(group)) },
                    onClick = {
                        menuOpen = false
                        ui.scope.launch { ui.controller.favouriteWallpaper(w, group) }
                    },
                )
            }
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Remove from favourites?") },
            text = {
                Text(
                    "“${w.title}” will be moved to your Trash / Recycle Bin, so you can still restore it from there.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmOpen = false
                    ui.scope.launch { ui.controller.removeFavourite(w.key) }
                }) { Text("Move to Trash") }
            },
            dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("Cancel") } },
        )
    }
}
