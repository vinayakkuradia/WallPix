package app.wallpix.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import app.wallpix.ui.LocalWpKit
import app.wallpix.ui.LocalWpPalette
import app.wallpix.ui.LocalWpSnapshot
import app.wallpix.ui.LocalWpUi
import app.wallpix.ui.TextRole
import app.wallpix.ui.WpIcons
import app.wallpix.ui.WpKit
import app.wallpix.ui.WpPage
import app.wallpix.ui.WpPageHost
import app.wallpix.ui.WpPalette
import app.wallpix.ui.WpSnapshot
import app.wallpix.ui.WpUi
import app.wallpix.ui.rememberSnapshot
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode
import dev.nucleusframework.macoscompose.components.ColumnWidth
import dev.nucleusframework.macoscompose.components.IconButton as MacIconButton
import dev.nucleusframework.macoscompose.components.IconButtonStyle
import dev.nucleusframework.macoscompose.components.LinearProgress
import dev.nucleusframework.macoscompose.components.PopupButton
import dev.nucleusframework.macoscompose.components.PushButton
import dev.nucleusframework.macoscompose.components.PushButtonStyle
import dev.nucleusframework.macoscompose.components.Scaffold
import dev.nucleusframework.macoscompose.components.SegmentedControl
import dev.nucleusframework.macoscompose.components.Sidebar
import dev.nucleusframework.macoscompose.components.SidebarItem
import dev.nucleusframework.macoscompose.components.Slider as MacSlider
import dev.nucleusframework.macoscompose.components.Switch as MacSwitch
import dev.nucleusframework.macoscompose.components.Text as MacText
import dev.nucleusframework.macoscompose.components.TextField as MacTextField
import dev.nucleusframework.macoscompose.components.TitleBar
import dev.nucleusframework.macoscompose.components.TitleBarButtonGroup
import dev.nucleusframework.macoscompose.components.TitleBarGroupButton
import dev.nucleusframework.macoscompose.components.WindowTitle
import dev.nucleusframework.macoscompose.icons.Icon as MacIcon
import dev.nucleusframework.macoscompose.theme.MacosTheme
import dev.nucleusframework.macoscompose.window.MacosDecoratedWindow

/**
 * macOS 26 "Liquid Glass" kit, built on `compose-macos-ui`.
 *
 * The window is full-size-content with the real AppKit traffic lights; the sidebar runs to the top of the window with
 * the lights floating over it, and the glass toolbar floats above the content, exactly like a SwiftUI
 * `NavigationSplitView`. Every control is the toolkit's own.
 */
object MacosKit : WpKit {
    override val id: String = "macOS Liquid Glass"
    override val pageTitleInContent: Boolean = false

    @Composable
    override fun Text(text: String, role: TextRole, modifier: Modifier, color: Color, maxLines: Int, bold: Boolean) {
        val t = MacosTheme.typography
        val style = when (role) {
            TextRole.Display -> t.title1
            TextRole.Title -> t.title2
            TextRole.Heading -> t.headline
            TextRole.Body -> t.body
            TextRole.Caption -> t.caption1
        }
        MacText(
            text = text,
            modifier = modifier,
            color = color,
            fontWeight = if (bold) FontWeight.SemiBold else null,
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines,
            style = style,
        )
    }

    @Composable
    override fun Button(text: String, onClick: () -> Unit, modifier: Modifier, icon: ImageVector?, primary: Boolean, enabled: Boolean) {
        PushButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            icon = icon,
            style = if (primary) PushButtonStyle.Default else PushButtonStyle.Neutral,
            enabled = enabled,
        )
    }

    @Composable
    override fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
        MacIconButton(icon = icon, onClick = onClick, modifier = modifier, style = IconButtonStyle.Bordered, enabled = enabled)
    }

    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
        MacSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }

    @Composable
    override fun Slider(
        value: Float,
        onValueChange: (Float) -> Unit,
        onValueChangeFinished: () -> Unit,
        range: ClosedFloatingPointRange<Float>,
        steps: Int,
        modifier: Modifier,
    ) {
        MacSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            valueRange = range,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
    }

    @Composable
    override fun Dropdown(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier, enabled: Boolean) {
        PopupButton(items = options, selectedIndex = selectedIndex, onSelectedChange = onSelect, modifier = modifier, enabled = enabled)
    }

    @Composable
    override fun ChipBar(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
        Box(modifier.horizontalScroll(rememberScrollState())) {
            SegmentedControl(options = labels, selectedIndex = selectedIndex, onSelectedIndexChange = onSelect)
        }
    }

    @Composable
    override fun TextInput(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier) {
        MacTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            placeholder = { MacText(placeholder) },
            singleLine = true,
        )
    }

    @Composable
    override fun ProgressBar(modifier: Modifier) {
        LinearProgress(indeterminate = true, modifier = modifier)
    }

    @Composable
    override fun Divider(modifier: Modifier) {
        Box(modifier.fillMaxWidth().height(1.dp).background(MacosTheme.colorScheme.borderSubtle))
    }
}

/** The macOS window: Liquid Glass chrome around the shared pages. */
@Composable
fun NucleusApplicationScope.MacosWindow(
    ui: WpUi,
    visible: Boolean,
    state: WindowState,
    icon: Painter?,
    onCloseRequest: () -> Unit,
) {
    MacosDecoratedWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        visible = visible,
        title = "WallPix",
        icon = icon,
        minimumSize = DpSize(860.dp, 580.dp),
    ) {
        MacosTheme(darkTheme = isSystemInDarkMode(), liquidGlass = true) {
            val scheme = MacosTheme.colorScheme
            val palette = remember(scheme) {
                WpPalette(
                    isDark = scheme.isDark,
                    background = scheme.background,
                    surface = scheme.backgroundElevated,
                    surfaceAlt = scheme.muted,
                    text = scheme.textPrimary,
                    textSecondary = scheme.textSecondary,
                    accent = scheme.accent,
                    onAccent = scheme.onAccent,
                    outline = scheme.borderSubtle,
                    danger = scheme.destructive,
                    cardRadius = 12.dp,
                    tileRadius = 10.dp,
                    cardBorder = false,
                )
            }
            val snapshot = ui.rememberSnapshot()
            CompositionLocalProvider(
                LocalWpKit provides MacosKit,
                LocalWpPalette provides palette,
                LocalWpUi provides ui,
                LocalWpSnapshot provides snapshot,
            ) {
                MacosShell(ui, snapshot)
            }
        }
    }
}

private fun subtitleFor(page: WpPage, snap: WpSnapshot): String? = when (page) {
    WpPage.DOWNLOADED -> "${snap.downloadedCount} on disk"
    WpPage.FAVOURITES -> "${snap.favouriteCount} saved"
    else -> null
}

@Composable
private fun MacosShell(ui: WpUi, snap: WpSnapshot) {
    val items = remember(ui) {
        listOf(
            SidebarItem("Today", { ui.page = WpPage.TODAY }, icon = WpIcons.Sun, group = "Wallpapers", id = WpPage.TODAY.id),
            SidebarItem("Latest", { ui.page = WpPage.LATEST }, icon = WpIcons.Clock, group = "Wallpapers", id = WpPage.LATEST.id),
            SidebarItem("Downloaded", { ui.page = WpPage.DOWNLOADED }, icon = WpIcons.Download, group = "Wallpapers", id = WpPage.DOWNLOADED.id),
            SidebarItem("Favourites", { ui.page = WpPage.FAVOURITES }, icon = WpIcons.Heart, group = "Wallpapers", id = WpPage.FAVOURITES.id),
            SidebarItem("Settings", { ui.page = WpPage.SETTINGS }, icon = WpIcons.Sliders, group = "General", id = WpPage.SETTINGS.id),
        )
    }
    Scaffold(
        sidebar = { Sidebar(items = items, activeItem = ui.page.id) },
        sidebarWidth = ColumnWidth.Fixed(224.dp),
        titleBar = {
            TitleBar(
                title = { WindowTitle(ui.page.title, subtitle = subtitleFor(ui.page, snap)) },
                actions = {
                    TitleBarButtonGroup {
                        TitleBarGroupButton(onClick = { ui.checkNow() }, enabled = !snap.busyNow) {
                            MacIcon(WpIcons.Refresh, modifier = Modifier.size(16.dp))
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        WpPageHost(contentPadding = contentPadding)
    }
}
