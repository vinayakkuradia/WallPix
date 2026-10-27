package app.wallpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.wallpix.logic.Labels
import app.wallpix.logic.Wallpaper
import app.wallpix.model.UNGROUPED

/** Which inline panel (if any) covers a wallpaper while the user answers a question about its favourite state. */
enum class FavPanel { None, Confirm, Pick }

/** Heart pressed: un-favourite asks first, favouriting asks for a group only if there are groups. */
fun onHeart(w: Wallpaper, ui: WpUi, groups: List<String>, setPanel: (FavPanel) -> Unit) {
    when {
        w.favourite -> setPanel(FavPanel.Confirm)
        groups.isNotEmpty() -> setPanel(FavPanel.Pick)
        else -> ui.launch { ui.controller.favouriteWallpaper(w, UNGROUPED) }
    }
}

/**
 * Covers its parent with a question, instead of a pop-up dialog: same behaviour in every toolkit, and the answer
 * stays visually attached to the picture it is about.
 */
@Composable
fun FavouritePanel(w: Wallpaper, panel: FavPanel, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (panel == FavPanel.None) return
    val ui = LocalWpUi.current
    val snap = LocalWpSnapshot.current
    val p = LocalWpPalette.current
    Box(
        modifier
            .background(p.surface.copy(alpha = 0.95f))
            // Swallow clicks so nothing underneath fires.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (panel) {
                FavPanel.Confirm -> {
                    WpText("Remove from favourites?", TextRole.Heading, bold = true)
                    WpText(
                        "“${w.title}” moves to your Trash / Recycle Bin, so you can still restore it.",
                        TextRole.Caption,
                        color = p.textSecondary,
                        maxLines = 3,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WpButton("Cancel", onDismiss)
                        WpButton("Move to Trash", {
                            onDismiss()
                            ui.launch { ui.controller.removeFavourite(w.key) }
                        }, primary = true)
                    }
                }
                FavPanel.Pick -> {
                    WpText("Add to which group?", TextRole.Heading, bold = true)
                    (listOf(UNGROUPED) + snap.state.groups).forEach { group ->
                        WpButton(Labels.group(group), {
                            onDismiss()
                            ui.launch { ui.controller.favouriteWallpaper(w, group) }
                        })
                    }
                    WpButton("Cancel", onDismiss)
                }
                FavPanel.None -> Unit
            }
        }
    }
}

/** A wallpaper tile: picture with hover actions, then title and details. */
@Composable
fun WallpaperCard(w: Wallpaper, showGroupMove: Boolean, modifier: Modifier = Modifier) {
    val ui = LocalWpUi.current
    val snap = LocalWpSnapshot.current
    val p = LocalWpPalette.current
    val applied = snap.state.currentKey == w.key
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var panel by remember { mutableStateOf(FavPanel.None) }
    val shape = RoundedCornerShape(p.tileRadius)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(shape)
                .hoverable(interaction),
        ) {
            WpThumbnail(w.key, w.thumbUrl, w.title, Modifier.fillMaxSize())

            if (applied) {
                WpPill(
                    "Now showing",
                    Modifier.align(Alignment.TopStart).padding(10.dp),
                    background = p.accent,
                    content = p.onAccent,
                )
            }
            if (w.favourite) {
                WpBadge(WpIcons.HeartFilled, Color.White, Modifier.align(Alignment.TopEnd).padding(10.dp))
            }

            if (hovered && panel == FavPanel.None) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))))
                        .padding(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 10.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        WpButton(
                            if (applied) "Current" else "Set wallpaper",
                            { ui.launch { ui.controller.applyWallpaper(w) } },
                            icon = WpIcons.Monitor,
                            primary = !applied,
                            enabled = !applied && !snap.busyNow,
                        )
                        Box(Modifier.weight(1f))
                        WpIconButton(
                            if (w.favourite) WpIcons.HeartFilled else WpIcons.Heart,
                            if (w.favourite) "Remove from favourites" else "Add to favourites",
                            { onHeart(w, ui, snap.state.groups) { panel = it } },
                        )
                        WpIconButton(WpIcons.ExternalLink, "Open on Peapix", { app.wallpix.platform.DesktopActions.openUrl(w.pageUrl, ui.os) })
                    }
                }
            }

            FavouritePanel(w, panel, onDismiss = { panel = FavPanel.None }, modifier = Modifier.fillMaxSize())
        }

        Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            WpText(w.title, TextRole.Body, maxLines = 1, bold = true)
            val meta = listOfNotNull(
                w.date,
                w.copyright.ifBlank { null },
                if (w.favourite && w.group != UNGROUPED) w.group else null,
            ).joinToString(" · ")
            if (meta.isNotEmpty()) WpText(meta, TextRole.Caption, color = p.textSecondary, maxLines = 2)
        }

        if (showGroupMove && w.favourite) {
            val groups = listOf(UNGROUPED) + snap.state.groups
            WpDropdown(
                options = groups.map { Labels.group(it) },
                selectedIndex = groups.indexOf(w.group).coerceAtLeast(0),
                onSelect = { i -> ui.launch { ui.controller.moveToGroup(w.key, groups[i]) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
