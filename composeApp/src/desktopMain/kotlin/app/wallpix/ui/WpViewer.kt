package app.wallpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wallpix.logic.Browse
import app.wallpix.logic.Wallpaper
import app.wallpix.model.MAX_RECENTS
import app.wallpix.model.UNGROUPED
import app.wallpix.platform.DesktopActions
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Full-window picture viewer, in the spirit of Picasa's: a dark stage, the picture as large as it fits, big arrows at
 * the sides, a filmstrip underneath. Left / Right (or Space) page, Home / End jump, Esc closes.
 *
 * [topReserve] keeps a strip at the top free for chrome that must stay usable (Windows caption buttons, GNOME window
 * buttons); macOS passes nothing because its traffic lights float over the stage.
 */
@Composable
fun WpViewerLayer(topReserve: Dp = 0.dp) {
    val ui = LocalWpUi.current
    val state = ui.viewer ?: return
    val snap = LocalWpSnapshot.current

    // Fresh Wallpaper objects on every change, so the heart and "Now showing" stay truthful while the viewer is open.
    val byKey = remember(snap.state, snap.feed) {
        buildMap<String, Wallpaper> {
            Browse.favourites(snap.state.entries).forEach { put(it.key, it) }
            Browse.downloaded(snap.state.entries).forEach { put(it.key, it) }
            Browse.latest(snap.feed, snap.state.entries, MAX_RECENTS).forEach { put(it.key, it) }
        }
    }
    val keys = state.keys
    val index = state.index
    val w = byKey[keys.getOrNull(index)]
    if (w == null) {
        LaunchedEffect(Unit) { ui.closeViewer() }
        return
    }

    val p = LocalWpPalette.current
    val applied = snap.state.currentKey == w.key
    var panel by remember(w.key) { mutableStateOf(FavPanel.None) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .padding(top = topReserve)
            .background(Color(0xF2080808))
            // Swallow clicks so nothing behind the stage fires.
            .pointerInput(Unit) { detectTapGestures { } }
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (e.key) {
                        Key.Escape -> {
                            if (panel != FavPanel.None) panel = FavPanel.None else ui.closeViewer()
                        }
                        Key.DirectionLeft, Key.PageUp -> state.go(index - 1)
                        Key.DirectionRight, Key.PageDown, Key.Spacebar -> state.go(index + 1)
                        Key.MoveHome -> state.go(0)
                        Key.MoveEnd -> state.go(keys.size - 1)
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            // ---- stage ---------------------------------------------------------------------------------------
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ViewerPicture(w, Modifier.fillMaxSize().padding(horizontal = 84.dp, vertical = 20.dp))
                if (index > 0) {
                    StageButton(WpIcons.ChevronLeft, "Previous", Modifier.align(Alignment.CenterStart).padding(start = 18.dp)) {
                        state.go(index - 1)
                    }
                }
                if (index < keys.size - 1) {
                    StageButton(WpIcons.ChevronRight, "Next", Modifier.align(Alignment.CenterEnd).padding(end = 18.dp)) {
                        state.go(index + 1)
                    }
                }
                StageButton(WpIcons.Close, "Close", Modifier.align(Alignment.TopEnd).padding(14.dp)) { ui.closeViewer() }
            }

            // ---- caption and actions -------------------------------------------------------------------------
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    WpText(w.title, TextRole.Heading, color = Color.White, maxLines = 1, bold = true)
                    val meta = listOfNotNull(
                        w.date,
                        w.copyright.ifBlank { null },
                        if (w.favourite && w.group != UNGROUPED) w.group else null,
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) WpText(meta, TextRole.Caption, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                }
                WpText("${index + 1} / ${keys.size}", TextRole.Caption, color = Color.White.copy(alpha = 0.7f))
                WpButton(
                    if (applied) "Current" else "Set wallpaper",
                    { ui.launch { ui.controller.applyWallpaper(w) } },
                    icon = WpIcons.Monitor,
                    primary = !applied,
                    enabled = !applied && !snap.busyNow,
                )
                WpIconButton(
                    if (w.favourite) WpIcons.HeartFilled else WpIcons.Heart,
                    if (w.favourite) "Remove from favourites" else "Add to favourites",
                    { onHeart(w, ui, snap.state.groups) { panel = it } },
                )
                WpIconButton(WpIcons.ExternalLink, "Open on Peapix", { DesktopActions.openUrl(w.pageUrl, ui.os) })
            }

            // ---- filmstrip -----------------------------------------------------------------------------------
            val listState = rememberLazyListState()
            LaunchedEffect(index) { listState.animateScrollToItem((index - 3).coerceAtLeast(0)) }
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(keys.size, key = { keys[it] }) { i ->
                    val t = byKey[keys[i]]
                    val selected = i == index
                    val shape = RoundedCornerShape(6.dp)
                    Box(
                        Modifier
                            .size(width = 112.dp, height = 70.dp)
                            .clip(shape)
                            .border(2.dp, if (selected) p.accent else Color.Transparent, shape)
                            .clickable { state.go(i) },
                    ) {
                        if (t != null) WpThumbnail(t.key, t.thumbUrl, t.title, Modifier.fillMaxSize())
                        if (!selected) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
                    }
                }
            }
        }

        FavouritePanel(w, panel, onDismiss = { panel = FavPanel.None }, modifier = Modifier.fillMaxSize())
    }
}

/**
 * The picture, fitted (never cropped). Shows the thumbnail at once, then the real file: the copy on disk if there is
 * one, otherwise a 1920 px preview fetched for the occasion.
 */
@Composable
private fun ViewerPicture(w: Wallpaper, modifier: Modifier) {
    val ui = LocalWpUi.current
    val thumb by produceState<ImageBitmap?>(null, w.key, w.thumbUrl) {
        val path = ui.controller.thumbFile(w.key, w.thumbUrl)
        value = if (path == null) null else decode(path)
    }
    val local = w.entry?.localPath
    val fullUrl = w.item?.fullUrl
    val full by produceState<ImageBitmap?>(null, w.key, local, fullUrl) {
        value = null
        val onDisk = local?.let { Paths.get(it) }?.takeIf { Files.exists(it) }
        val path = onDisk ?: fullUrl?.let { ui.controller.previewFile(w.key, it) }
        value = if (path == null) null else decode(path)
    }
    Picture(full ?: thumb, w.title, modifier, ContentScale.Fit)
}

/** A round, theme-independent control for the dark stage. */
@Composable
private fun StageButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        WpIcon(icon, Color.White, size = 22.dp)
    }
}
