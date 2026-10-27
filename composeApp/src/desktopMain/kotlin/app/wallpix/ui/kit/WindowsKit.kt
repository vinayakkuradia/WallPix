@file:OptIn(ExperimentalFluentApi::class, ExperimentalFoundationApi::class)

package app.wallpix.ui.kit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import app.wallpix.platform.Os
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
import app.wallpix.ui.WpViewerLayer
import app.wallpix.ui.rememberSnapshot
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode
import dev.nucleusframework.systemcolor.systemAccentColor
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.LocalWindowChromeInsets
import dev.nucleusframework.window.TitleBarPlacement
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowBackground
import dev.nucleusframework.window.WindowControls
import dev.nucleusframework.window.WindowScaffold
import dev.nucleusframework.window.WindowsBackdrop
import dev.nucleusframework.window.WindowsBackdropStyle
import dev.nucleusframework.window.noWindowDrag
import dev.nucleusframework.window.windowDragArea
import dev.nucleusframework.window.fluent.FluentDecoratedWindow
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.FluentThemeConfiguration
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button as FluentButton
import io.github.composefluent.component.ComboBox
import io.github.composefluent.component.Icon
import io.github.composefluent.component.MenuItem
import io.github.composefluent.component.NavigationDisplayMode
import io.github.composefluent.component.NavigationView
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.ProgressBar as FluentProgressBar
import io.github.composefluent.component.Slider as FluentSlider
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text as FluentText
import io.github.composefluent.component.TextField
import io.github.composefluent.component.TooltipBox
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors

/**
 * Windows 11 kit, built on Compose Fluent.
 *
 * Every control is Fluent's own (accent button, Switcher, ComboBox, pill chips, slider with value tooltip). The window
 * is a Nucleus decorated window: the native Mica backdrop shows through, the caption buttons are the real Windows
 * ones with the Snap Layouts flyout, and the navigation pane is Fluent's `NavigationView` with its selection pill.
 */
object WindowsKit : WpKit {
    override val id: String = "Windows Fluent"
    override val pageTitleInContent: Boolean = true

    @Composable
    override fun Text(text: String, role: TextRole, modifier: Modifier, color: Color, maxLines: Int, bold: Boolean) {
        val t = FluentTheme.typography
        val style = when (role) {
            TextRole.Display -> t.title
            TextRole.Title -> t.subtitle
            TextRole.Heading -> t.bodyStrong
            TextRole.Body -> t.body
            TextRole.Caption -> t.caption
        }
        FluentText(
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
        val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            FluentText(text)
        }
        if (primary) {
            AccentButton(onClick = onClick, modifier = modifier, disabled = !enabled, content = content)
        } else {
            FluentButton(onClick = onClick, modifier = modifier, disabled = !enabled, content = content)
        }
    }

    @Composable
    override fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
        TooltipBox(tooltip = { FluentText(label) }) {
            SubtleButton(onClick = onClick, modifier = modifier, disabled = !enabled, iconOnly = true) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
            }
        }
    }

    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
        Switcher(checked = checked, onCheckStateChange = onCheckedChange, enabled = enabled)
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
        FluentSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            valueRange = range,
            steps = steps,
            onValueChangeFinished = { onValueChangeFinished() },
        )
    }

    @Composable
    override fun Dropdown(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier, enabled: Boolean) {
        ComboBox(
            modifier = modifier,
            disabled = !enabled,
            items = options,
            selected = selectedIndex,
            onSelectionChange = { index, _ -> onSelect(index) },
        )
    }

    @Composable
    override fun ChipBar(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
        Row(
            modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { i, label ->
                PillButton(selected = i == selectedIndex, onSelectedChanged = { if (it) onSelect(i) }) {
                    FluentText(label, maxLines = 1)
                }
            }
        }
    }

    @Composable
    override fun TextInput(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            singleLine = true,
            placeholder = { FluentText(placeholder) },
        )
    }

    @Composable
    override fun ProgressBar(modifier: Modifier) {
        FluentProgressBar(modifier = modifier)
    }

    @Composable
    override fun Divider(modifier: Modifier) {
        Box(modifier.fillMaxWidth().height(1.dp).background(FluentTheme.colors.stroke.divider.default))
    }
}

/** The Windows window: Mica, native caption buttons and a Fluent navigation pane around the shared pages. */
@Composable
fun NucleusApplicationScope.WindowsWindow(
    ui: WpUi,
    visible: Boolean,
    state: WindowState,
    icon: Painter?,
    onCloseRequest: () -> Unit,
) {
    val dark = isSystemInDarkMode()
    // The user's own Windows accent colour, so selection pills, switches and the primary button match the desktop.
    val accent = systemAccentColor() ?: Color(0xFF0078D4)
    val colors = remember(dark, accent) { if (dark) darkColors(accent) else lightColors(accent) }
    // FluentDecoratedWindow reads the ambient colours for its chrome, so they must wrap the call.
    FluentThemeConfiguration(colors = colors) {
        FluentDecoratedWindow(
            onCloseRequest = onCloseRequest,
            state = state,
            visible = visible,
            title = "WallPix",
            icon = icon,
            minimumSize = DpSize(900.dp, 600.dp),
        ) {
            // Each Tao window owns its composition, so the theme is provided again inside it.
            FluentTheme(colors = colors, useAcrylicPopup = true) {
                WindowsChrome(ui, icon)
            }
        }
    }
}

@Composable
private fun DecoratedWindowScope.WindowsChrome(ui: WpUi, icon: Painter?) {
    val colors = FluentTheme.colors
    val onWindows = Os.current() == Os.WINDOWS
    // Nucleus derives the DWM immersive-dark flag, and the fallback tint, from this colour.
    WindowBackground(colors.background.mica.base)
    WindowAppearance(if (colors.darkMode) WindowAppearanceMode.Dark else WindowAppearanceMode.Light)
    if (onWindows) WindowsBackdrop(WindowsBackdropStyle.Mica)

    val palette = remember(colors) {
        WpPalette(
            isDark = colors.darkMode,
            background = Color.Transparent,
            surface = colors.background.card.default,
            surfaceAlt = colors.subtleFill.secondary,
            text = colors.text.text.primary,
            textSecondary = colors.text.text.secondary,
            accent = colors.fillAccent.default,
            onAccent = colors.text.onAccent.primary,
            outline = colors.stroke.card.default,
            danger = colors.system.critical,
            cardRadius = 8.dp,
            tileRadius = 6.dp,
            cardBorder = true,
        )
    }
    val snapshot = ui.rememberSnapshot()
    CompositionLocalProvider(
        LocalWpKit provides WindowsKit,
        LocalWpPalette provides palette,
        LocalWpUi provides ui,
        LocalWpSnapshot provides snapshot,
    ) {
        // Off Windows there is no native backdrop, so paint the Mica base colour ourselves.
        Box(Modifier.fillMaxSize().then(if (onWindows) Modifier else Modifier.background(colors.background.mica.base))) {
            WindowScaffold(
                titleBarPlacement = TitleBarPlacement.Overlay(autoHideInFullscreen = false),
                titleBar = { CaptionBar(ui, snapshot, icon) },
            ) { padding ->
                WindowsNavigation(ui, snapshot, topInset = padding.calculateTopPadding())
            }
            // Below the caption bar, so the native caption buttons stay reachable.
            WpViewerLayer(topReserve = CAPTION_HEIGHT)
        }
    }
}

private val CAPTION_HEIGHT = 48.dp

@Composable
private fun DecoratedWindowScope.CaptionBar(ui: WpUi, snap: WpSnapshot, icon: Painter?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(CAPTION_HEIGHT)
            .windowDragArea()
            .padding(LocalWindowChromeInsets.current.controlsInsets),
    ) {
        if (icon != null) {
            Image(painter = icon, contentDescription = null, modifier = Modifier.padding(start = 16.dp).size(16.dp))
        }
        FluentText(
            text = "WallPix",
            style = FluentTheme.typography.caption,
            modifier = Modifier.padding(start = 12.dp),
        )
        Spacer(Modifier.weight(1f))
        TooltipBox(tooltip = { FluentText("Check for new wallpapers") }) {
            SubtleButton(
                onClick = { ui.checkNow() },
                disabled = snap.busyNow,
                iconOnly = true,
                modifier = Modifier.noWindowDrag(),
            ) {
                Icon(WpIcons.Refresh, contentDescription = "Check now", modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        WindowControls(modifier = Modifier.noWindowDrag())
    }
}

@Composable
private fun WindowsNavigation(ui: WpUi, snap: WpSnapshot, topInset: androidx.compose.ui.unit.Dp) {
    val colors = FluentTheme.colors
    // The pane sits on Mica with Fluent's own translucent layer, like Settings and Photos.
    NavigationView(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset)
            .background(colors.background.layerOnMicaBaseAlt.secondary),
        displayMode = NavigationDisplayMode.Left,
        menuItems = {
            for (page in listOf(WpPage.TODAY, WpPage.LATEST, WpPage.DOWNLOADED, WpPage.FAVOURITES)) {
                item(key = page.id) {
                    MenuItem(
                        selected = ui.page == page,
                        onClick = { ui.page = page },
                        text = { FluentText(page.title) },
                        icon = { Icon(page.icon(), contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        },
        footerItems = {
            item(key = WpPage.SETTINGS.id) {
                MenuItem(
                    selected = ui.page == WpPage.SETTINGS,
                    onClick = { ui.page = WpPage.SETTINGS },
                    text = { FluentText(WpPage.SETTINGS.title) },
                    icon = { Icon(WpPage.SETTINGS.icon(), contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        },
        contentPadding = PaddingValues(),
        pane = { WpPageHost() },
    )
}

internal fun WpPage.icon(): ImageVector = when (this) {
    WpPage.TODAY -> WpIcons.Sun
    WpPage.LATEST -> WpIcons.Clock
    WpPage.DOWNLOADED -> WpIcons.Download
    WpPage.FAVOURITES -> WpIcons.Heart
    WpPage.SETTINGS -> WpIcons.Sliders
}
