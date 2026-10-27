package app.wallpix.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wallpix.logic.Browse
import app.wallpix.logic.Labels
import app.wallpix.logic.Wallpaper
import app.wallpix.model.BING_COUNTRIES
import app.wallpix.model.GroupMix
import app.wallpix.model.MAX_RECENTS
import app.wallpix.model.MIN_RECENTS
import app.wallpix.model.ResolutionMode
import app.wallpix.model.Settings
import app.wallpix.model.SlideshowOrder
import app.wallpix.model.Source
import app.wallpix.model.UNGROUPED
import app.wallpix.platform.DesktopActions

private val SLIDESHOW_MINUTES = listOf(1, 5, 10, 15, 30, 60, 120, 360, 720, 1440)
private val CHECK_MINUTES = listOf(30, 60, 120, 360, 720, 1440)

/**
 * Hosts the open page plus the floating status line. [contentPadding] reserves room for a title bar that floats over
 * the content (macOS glass toolbar, Fluent caption bar) so pictures scroll underneath it.
 */
@Composable
fun WpPageHost(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val ui = LocalWpUi.current
    Box(modifier.fillMaxSize()) {
        Crossfade(targetState = ui.page, animationSpec = tween(160)) { page ->
            when (page) {
                WpPage.TODAY -> TodayPage(contentPadding)
                WpPage.LATEST -> LatestPage(contentPadding)
                WpPage.DOWNLOADED -> DownloadedPage(contentPadding)
                WpPage.FAVOURITES -> FavouritesPage(contentPadding)
                WpPage.SETTINGS -> SettingsPage(contentPadding)
            }
        }
        WpStatusBanner(Modifier.align(Alignment.BottomCenter).padding(16.dp).widthIn(max = 560.dp))
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String?, actions: (@Composable () -> Unit)? = null) {
    val kit = LocalWpKit.current
    val p = LocalWpPalette.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (kit.pageTitleInContent) WpText(title, TextRole.Display, bold = true)
            if (subtitle != null) WpText(subtitle, TextRole.Body, color = p.textSecondary)
        }
        if (actions != null) actions()
    }
}

private fun PaddingValues.top(): Dp = calculateTopPadding()

// ================================================================================ Today

@Composable
private fun TodayPage(padding: PaddingValues) {
    val ui = LocalWpUi.current
    val snap = LocalWpSnapshot.current
    val p = LocalWpPalette.current
    val s = snap.settings
    val cur = snap.current

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 1100.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = padding.top() + 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageHeader(
                "Today",
                if (snap.state.lastCheckMs > 0) "Last checked ${DesktopActions.formatTime(snap.state.lastCheckMs)}" else "Not checked yet",
            ) {
                WpButton("Check now", { ui.checkNow() }, icon = WpIcons.Refresh, enabled = !snap.busyNow)
            }

            if (cur == null) {
                WpCard {
                    WpEmptyState(
                        if (snap.busyNow) "Getting today’s wallpaper…" else "No wallpaper yet",
                        "WallPix downloads Bing’s picture of the day and sets it as your desktop.",
                        action = { WpButton("Check now", { ui.checkNow() }, primary = true, enabled = !snap.busyNow) },
                    )
                }
            } else {
                Hero(cur.toWallpaper())
            }

            val recent = Browse.latest(snap.feed, snap.state.entries, 6)
            if (recent.size > 1) {
                WpSection("Recent") {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        recent.forEach { w ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(16f / 10f)
                                    .clip(RoundedCornerShape(p.tileRadius))
                                    .clickable(enabled = !snap.busyNow && snap.state.currentKey != w.key) {
                                        ui.launch { ui.controller.applyWallpaper(w) }
                                    },
                            ) {
                                WpThumbnail(w.key, w.thumbUrl, w.title, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }

            WpSection("Quick settings") {
                WpSettingRow("Apply each new daily wallpaper", divider = false) {
                    WpSwitch(s.autoApplyDaily, { on -> ui.launch { ui.controller.updateSettings { it.copy(autoApplyDaily = on) } } })
                }
                WpSettingRow("Slideshow of favourites", description = if (snap.favouriteCount == 0) "Add a favourite first." else null) {
                    WpSwitch(
                        s.slideshowEnabled,
                        { on -> ui.launch { ui.controller.updateSettings { it.copy(slideshowEnabled = on) } } },
                        enabled = snap.favouriteCount > 0 || s.slideshowEnabled,
                    )
                }
            }
        }
    }
}

private fun app.wallpix.model.LibraryEntry.toWallpaper() = Wallpaper(
    key = key, title = title, copyright = copyright, pageUrl = pageUrl, date = date,
    thumbUrl = thumbUrl, item = null, entry = this,
)

@Composable
private fun Hero(w: Wallpaper) {
    val ui = LocalWpUi.current
    val snap = LocalWpSnapshot.current
    val p = LocalWpPalette.current
    var panel by remember { mutableStateOf(FavPanel.None) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 8.2f)
            .clip(RoundedCornerShape(p.cardRadius + 4.dp)),
    ) {
        WpFullImage(w.entry?.localPath, w.key, w.thumbUrl, w.title, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))),
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WpPill("Now showing", background = p.accent, content = p.onAccent)
                WpText(w.title, TextRole.Display, color = Color.White, maxLines = 2, bold = true)
                val meta = listOfNotNull(w.date, w.copyright.ifBlank { null }).joinToString(" · ")
                if (meta.isNotEmpty()) WpText(meta, TextRole.Body, color = Color.White.copy(alpha = 0.86f), maxLines = 2)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (snap.favouriteCount > 0) {
                    WpIconButton(WpIcons.ChevronLeft, "Previous favourite", { ui.launch { ui.controller.advanceSlideshow(-1) } })
                    WpIconButton(WpIcons.ChevronRight, "Next favourite", { ui.launch { ui.controller.advanceSlideshow(1) } })
                }
                WpIconButton(
                    if (w.favourite) WpIcons.HeartFilled else WpIcons.Heart,
                    if (w.favourite) "Remove from favourites" else "Add to favourites",
                    { onHeart(w, ui, snap.state.groups) { panel = it } },
                )
                WpIconButton(WpIcons.ExternalLink, "Open on Peapix", { DesktopActions.openUrl(w.pageUrl, ui.os) })
            }
        }
        FavouritePanel(w, panel, onDismiss = { panel = FavPanel.None }, modifier = Modifier.fillMaxSize())
    }
}

// ================================================================================ Grids

@Composable
private fun GridPage(
    title: String,
    subtitle: String?,
    padding: PaddingValues,
    items: List<Wallpaper>,
    showGroupMove: Boolean,
    headerActions: (@Composable () -> Unit)? = null,
    belowHeader: (@Composable () -> Unit)? = null,
    empty: @Composable () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(250.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = padding.top() + 12.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { PageHeader(title, subtitle, headerActions) }
        if (belowHeader != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { belowHeader() }
        }
        if (items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { empty() }
        } else {
            items(items, key = { it.key }) { w -> WallpaperCard(w, showGroupMove) }
        }
    }
}

@Composable
private fun LatestPage(padding: PaddingValues) {
    val ui = LocalWpUi.current
    val snap = LocalWpSnapshot.current
    val s = snap.settings
    val items = Browse.latest(snap.feed, snap.state.entries, s.recentsCount)
    val where = if (s.source == Source.BING) "Bing, ${BING_COUNTRIES[s.country] ?: s.country}" else "Windows Spotlight"
    GridPage(
        "Latest",
        "The newest ${s.recentsCount} from $where",
        padding, items, showGroupMove = false,
    ) {
        WpEmptyState(
            "Nothing here yet",
            "The list fills once WallPix has checked Peapix.",
            action = { WpButton("Check now", { ui.checkNow() }, primary = true, enabled = !snap.busyNow) },
        )
    }
}

@Composable
private fun DownloadedPage(padding: PaddingValues) {
    val snap = LocalWpSnapshot.current
    val items = Browse.downloaded(snap.state.entries)
    GridPage(
        "Downloaded",
        "${items.size} on disk. WallPix keeps the newest ${snap.settings.recentsCount} and moves older ones to the Trash.",
        padding, items, showGroupMove = false,
    ) {
        WpEmptyState("Nothing downloaded yet", "Wallpapers appear here after they have been downloaded.")
    }
}

@Composable
private fun FavouritesPage(padding: PaddingValues) {
    val ui = LocalWpUi.current
    val snap = LocalWpSnapshot.current
    val entries = snap.state.entries
    val groups = snap.state.groups
    val all = Browse.favourites(entries)

    var selected by remember { mutableStateOf(0) }
    val maxIndex = groups.size + 1
    val index = selected.coerceIn(0, maxIndex)
    val labels = buildList {
        add("All (${all.size})")
        add("No group (${all.count { it.group == UNGROUPED }})")
        groups.forEach { g -> add("$g (${all.count { it.group == g }})") }
    }
    val items = when (index) {
        0 -> all
        1 -> Browse.favourites(entries, UNGROUPED)
        else -> Browse.favourites(entries, groups[index - 2])
    }
    val folder = ui.controller.favouritesDir()
    var creating by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    GridPage(
        "Favourites",
        "${all.size} saved to $folder",
        padding, items, showGroupMove = true,
        headerActions = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WpButton("Open folder", { DesktopActions.openFolder(folder, ui.os) }, icon = WpIcons.Folder)
            }
        },
        belowHeader = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WpChipBar(labels, index, { selected = it }, Modifier.weight(1f, fill = false))
                    WpButton("New group", { creating = !creating }, icon = WpIcons.Plus)
                    if (index >= 2) {
                        val name = groups[index - 2]
                        WpButton("Delete “$name”", {
                            ui.launch { if (ui.controller.deleteGroup(name)) selected = 0 }
                        })
                    }
                }
                if (creating) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WpTextInput(draft, { draft = it }, "Group name", Modifier.widthIn(min = 220.dp, max = 320.dp))
                        WpButton("Create", {
                            val name = draft
                            ui.launch {
                                val made = ui.controller.createGroup(name)
                                if (made != null) {
                                    draft = ""
                                    creating = false
                                }
                            }
                        }, primary = true, enabled = draft.isNotBlank())
                        WpButton("Cancel", { creating = false; draft = "" })
                    }
                }
            }
        },
    ) {
        WpEmptyState(
            if (all.isEmpty()) "No favourites yet" else "Nothing in this group",
            if (all.isEmpty()) "Press the heart on any wallpaper to keep it in your favourites folder." else "Move favourites here from the group menu on their card.",
        )
    }
}

// ================================================================================ Settings

@Composable
private fun <T> WpChoice(options: List<T>, current: T, label: (T) -> String, onPick: (T) -> Unit, enabled: Boolean = true) {
    WpDropdown(
        options = options.map(label),
        selectedIndex = options.indexOf(current).coerceAtLeast(0),
        onSelect = { onPick(options[it]) },
        modifier = Modifier.widthIn(min = 180.dp),
        enabled = enabled,
    )
}

@Composable
private fun SettingsPage(padding: PaddingValues) {
    val ui = LocalWpUi.current
    val snap = LocalWpSnapshot.current
    val kit = LocalWpKit.current
    val p = LocalWpPalette.current
    val s = snap.settings
    val controller = ui.controller
    fun update(change: (Settings) -> Settings) = ui.launch { controller.updateSettings(change) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 780.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = padding.top() + 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            PageHeader("Settings", null)

            WpSection("Wallpapers") {
                WpSettingRow(
                    "Source",
                    description = "Spotlight images carry no date, so they are listed in the order Peapix delivers them.",
                    divider = false,
                ) {
                    WpChoice(Source.entries.toList(), s.source, { it.label }, { c -> update { it.copy(source = c) } })
                }
                if (s.source == Source.BING) {
                    WpSettingRow("Region", description = "Bing publishes a different picture per region.") {
                        WpChoice(BING_COUNTRIES.keys.toList(), s.country, { BING_COUNTRIES[it] ?: it }, { c -> update { it.copy(country = c) } })
                    }
                }
                WpSettingRow(
                    "Resolution",
                    description = "Auto downloads the original (up to 4K) when your screen is wider than 1920 px, otherwise the 1920 px version.",
                ) {
                    WpChoice(ResolutionMode.entries.toList(), s.resolution, { it.label }, { c -> update { it.copy(resolution = c) } })
                }
            }

            WpSection("Daily update") {
                WpSettingRow(
                    "Apply each new wallpaper",
                    description = "New images are always downloaded; this decides whether they also become your desktop. It is also in the tray menu.",
                    divider = false,
                ) {
                    WpSwitch(s.autoApplyDaily, { on -> update { it.copy(autoApplyDaily = on) } })
                }
                WpSettingRow("Check for new wallpapers", description = "Peapix refreshes its feed about every 30 minutes.") {
                    WpChoice(
                        Labels.withCurrent(CHECK_MINUTES, s.checkIntervalMin), s.checkIntervalMin,
                        { "every " + Labels.minutes(it) }, { c -> update { it.copy(checkIntervalMin = c) } },
                    )
                }
                var draft by remember(s.recentsCount) { mutableFloatStateOf(s.recentsCount.toFloat()) }
                WpSettingRow(
                    "Recent wallpapers to keep: ${draft.toInt()}",
                    description = "The newest non-favourites stay on disk and in the Latest list; older ones go to the Trash. Maximum $MAX_RECENTS.",
                ) {
                    WpSlider(
                        value = draft,
                        onValueChange = { draft = it },
                        onValueChangeFinished = { update { it.copy(recentsCount = draft.toInt()) } },
                        range = MIN_RECENTS.toFloat()..MAX_RECENTS.toFloat(),
                        steps = MAX_RECENTS - MIN_RECENTS - 1,
                        modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
                    )
                }
            }

            WpSection("Favourites") {
                val folder = controller.favouritesDir()
                WpSettingRow(
                    "Favourites folder",
                    description = "$folder\nChanging it moves your existing favourites, and their groups, to the new place.",
                    divider = false,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WpButton("Choose…", {
                            ui.launch {
                                val picked = DesktopActions.chooseFolder(folder, "Choose the favourites folder", ui.os)
                                if (picked != null) controller.relocateFavourites(picked)
                            }
                        })
                        WpButton("Open", { DesktopActions.openFolder(folder, ui.os) })
                        if (folder != ui.defaultFavouritesDir) {
                            WpButton("Reset", { ui.launch { controller.relocateFavourites(ui.defaultFavouritesDir) } })
                        }
                    }
                }
            }

            WpSection("Slideshow of favourites") {
                WpSettingRow(
                    "Enable slideshow",
                    description = "Rotates through your favourites. While it runs, the daily wallpaper is still downloaded but not applied.",
                    divider = false,
                ) {
                    WpSwitch(s.slideshowEnabled, { on -> update { it.copy(slideshowEnabled = on) } })
                }
                WpSettingRow("Change wallpaper") {
                    WpChoice(
                        Labels.withCurrent(SLIDESHOW_MINUTES, s.slideshowIntervalMin), s.slideshowIntervalMin,
                        { "every " + Labels.minutes(it) }, { c -> update { it.copy(slideshowIntervalMin = c) } },
                    )
                }
                WpSettingRow("Order") {
                    WpChoice(SlideshowOrder.entries.toList(), s.slideshowOrder, { it.label }, { c -> update { it.copy(slideshowOrder = c) } })
                }
                WpSettingRow(
                    "Mixing groups",
                    description = "“Even share” gives every selected group the same airtime, however many wallpapers it holds.",
                ) {
                    WpChoice(GroupMix.entries.toList(), s.groupMix, { it.label }, { c -> update { it.copy(groupMix = c) } })
                }
                WpSettingRow(
                    "Groups to play",
                    description = "Select none to play every group. Create groups on the Favourites page.",
                ) {}
                (listOf(UNGROUPED) + snap.state.groups).forEach { group ->
                    val on = group in s.slideshowGroups
                    WpSettingRow(Labels.group(group)) {
                        WpSwitch(on, {
                            update { cur ->
                                cur.copy(slideshowGroups = if (on) cur.slideshowGroups - group else cur.slideshowGroups + group)
                            }
                        })
                    }
                }
            }

            WpSection("System") {
                WpSettingRow(
                    "Launch at login",
                    description = if (controller.autostartSupported) {
                        "Starts WallPix hidden in the tray."
                    } else {
                        "Only available from an installed copy of WallPix, not when run from Gradle."
                    },
                    divider = false,
                ) {
                    WpSwitch(s.launchAtLogin, { on -> update { it.copy(launchAtLogin = on) } }, enabled = controller.autostartSupported)
                }
            }

            WpSection("About") {
                WpSettingRow(
                    "WallPix · ${kit.id}",
                    description = "Images are provided by Peapix for wallpaper use only. Copyright stays with the photographers.",
                    divider = false,
                ) {
                    WpButton("peapix.com", { DesktopActions.openUrl("https://peapix.com", ui.os) }, icon = WpIcons.ExternalLink)
                }
            }
        }
    }
}
