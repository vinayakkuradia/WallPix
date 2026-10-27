package app.wallpix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.wallpix.logic.Labels
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
import kotlinx.coroutines.launch
import java.nio.file.Path

private val SLIDESHOW_MINUTES = listOf(1, 5, 10, 15, 30, 60, 120, 360, 720, 1440)
private val CHECK_MINUTES = listOf(30, 60, 120, 360, 720, 1440)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun SettingRow(label: String, description: String? = null, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        control()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(ui: UiContext, defaultFavouritesDir: Path) {
    val s = ui.settings
    val controller = ui.controller
    fun update(change: (Settings) -> Settings) {
        ui.scope.launch { controller.updateSettings(change) }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)
            .widthIn(max = 900.dp),
    ) {
        Section("Wallpapers") {
            SettingRow(
                "Source",
                "Spotlight images carry no date, so they are listed in the order Peapix delivers them.",
            ) {
                Dropdown(
                    selected = s.source,
                    options = Source.entries.toList(),
                    label = { it.label },
                    onSelect = { choice -> update { it.copy(source = choice) } },
                )
            }
            SettingRow(
                "Region",
                if (s.source == Source.BING) "Bing publishes a different picture per region." else "Spotlight has no regions.",
            ) {
                if (s.source == Source.BING) {
                    Dropdown(
                        selected = s.country,
                        options = BING_COUNTRIES.keys.toList(),
                        label = { BING_COUNTRIES[it] ?: it },
                        onSelect = { choice -> update { it.copy(country = choice) } },
                    )
                }
            }
            SettingRow(
                "Resolution",
                "Auto downloads the original (up to 4K) when your screen is wider than 1920 px, otherwise the 1920 px version.",
            ) {
                Dropdown(
                    selected = s.resolution,
                    options = ResolutionMode.entries.toList(),
                    label = { it.label },
                    onSelect = { choice -> update { it.copy(resolution = choice) } },
                )
            }
        }

        HorizontalDivider()

        Section("Daily update") {
            SettingRow(
                "Apply each new wallpaper",
                "New images are always downloaded; this decides whether they also become your desktop. " +
                    "It is also in the tray menu.",
            ) {
                Switch(checked = s.autoApplyDaily, onCheckedChange = { on -> update { it.copy(autoApplyDaily = on) } })
            }
            SettingRow("Check for new wallpapers", "Peapix refreshes its feed about every 30 minutes.") {
                Dropdown(
                    selected = s.checkIntervalMin,
                    options = Labels.withCurrent(CHECK_MINUTES, s.checkIntervalMin),
                    label = { "every " + Labels.minutes(it) },
                    onSelect = { choice -> update { it.copy(checkIntervalMin = choice) } },
                )
            }
            var draft by remember(s.recentsCount) { mutableFloatStateOf(s.recentsCount.toFloat()) }
            SettingRow(
                "Recent wallpapers to keep: ${draft.toInt()}",
                "The newest non-favourites stay on disk and in the Latest list; older ones are moved to the " +
                    "Trash. Maximum $MAX_RECENTS.",
            ) {
                Slider(
                    value = draft,
                    onValueChange = { draft = it },
                    onValueChangeFinished = { update { it.copy(recentsCount = draft.toInt()) } },
                    valueRange = MIN_RECENTS.toFloat()..MAX_RECENTS.toFloat(),
                    steps = MAX_RECENTS - MIN_RECENTS - 1,
                    modifier = Modifier.widthIn(min = 220.dp, max = 320.dp),
                )
            }
        }

        HorizontalDivider()

        Section("Favourites") {
            val folder = controller.favouritesDir()
            SettingRow(
                "Favourites folder",
                "$folder\nChanging it moves your existing favourites (and their groups) to the new place.",
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val picked = DesktopActions.chooseFolder(folder, "Choose the favourites folder")
                        if (picked != null) ui.scope.launch { controller.relocateFavourites(picked) }
                    }) { Text("Choose…") }
                    OutlinedButton(onClick = { DesktopActions.openFolder(folder) }) { Text("Open") }
                    if (folder != defaultFavouritesDir) {
                        OutlinedButton(onClick = { ui.scope.launch { controller.relocateFavourites(defaultFavouritesDir) } }) {
                            Text("Reset")
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        Section("Slideshow of favourites") {
            SettingRow(
                "Enable slideshow",
                "Rotates through your favourites. While it runs, the daily wallpaper is still downloaded but not applied.",
            ) {
                Switch(checked = s.slideshowEnabled, onCheckedChange = { on -> update { it.copy(slideshowEnabled = on) } })
            }
            SettingRow("Change wallpaper") {
                Dropdown(
                    selected = s.slideshowIntervalMin,
                    options = Labels.withCurrent(SLIDESHOW_MINUTES, s.slideshowIntervalMin),
                    label = { "every " + Labels.minutes(it) },
                    onSelect = { choice -> update { it.copy(slideshowIntervalMin = choice) } },
                )
            }
            SettingRow("Order") {
                Dropdown(
                    selected = s.slideshowOrder,
                    options = SlideshowOrder.entries.toList(),
                    label = { it.label },
                    onSelect = { choice -> update { it.copy(slideshowOrder = choice) } },
                )
            }
            SettingRow(
                "Mixing groups",
                "“Even share” gives every selected group the same airtime, however many wallpapers it holds.",
            ) {
                Dropdown(
                    selected = s.groupMix,
                    options = GroupMix.entries.toList(),
                    label = { it.label },
                    onSelect = { choice -> update { it.copy(groupMix = choice) } },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Groups to play", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Select none to play every group. Create groups on the Favourites page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (listOf(UNGROUPED) + ui.state.groups).forEach { group ->
                        val on = group in s.slideshowGroups
                        FilterChip(
                            selected = on,
                            onClick = {
                                update { cur ->
                                    cur.copy(slideshowGroups = if (on) cur.slideshowGroups - group else cur.slideshowGroups + group)
                                }
                            },
                            label = { Text(Labels.group(group)) },
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        Section("System") {
            SettingRow(
                "Launch at login",
                if (controller.autostartSupported) {
                    "Starts WallPix hidden in the tray."
                } else {
                    "Only available from an installed copy of WallPix, not when run from Gradle."
                },
            ) {
                Switch(
                    checked = s.launchAtLogin,
                    enabled = controller.autostartSupported,
                    onCheckedChange = { on -> update { it.copy(launchAtLogin = on) } },
                )
            }
        }
    }
}
