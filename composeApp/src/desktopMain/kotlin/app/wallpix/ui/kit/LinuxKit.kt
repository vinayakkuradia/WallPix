package app.wallpix.ui.kit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import app.wallpix.ui.WpViewerLayer
import app.wallpix.ui.rememberSnapshot
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode
import dev.nucleusframework.yarucompose.themes.LocalYaruColorScheme
import dev.nucleusframework.yarucompose.themes.LocalYaruContentColor
import dev.nucleusframework.yarucompose.themes.LocalYaruTypography
import dev.nucleusframework.yarucompose.themes.YaruConstants
import dev.nucleusframework.yarucompose.themes.YaruTheme
import dev.nucleusframework.yarucompose.themes.YaruVariant
import dev.nucleusframework.yarucompose.widgets.YaruChoiceChipBar
import dev.nucleusframework.yarucompose.widgets.YaruFilledButton
import dev.nucleusframework.yarucompose.widgets.YaruHorizontalDivider
import dev.nucleusframework.yarucompose.widgets.YaruIconButton
import dev.nucleusframework.yarucompose.widgets.YaruLinearProgressIndicator
import dev.nucleusframework.yarucompose.widgets.YaruOutlinedButton
import dev.nucleusframework.yarucompose.widgets.YaruPopupMenuButton
import dev.nucleusframework.yarucompose.widgets.YaruSlider
import dev.nucleusframework.yarucompose.widgets.YaruSwitch
import dev.nucleusframework.yarucompose.widgets.YaruText
import dev.nucleusframework.yarucompose.widgets.YaruTextField
import dev.nucleusframework.yarucompose.widgets.YaruTitleBar
import dev.nucleusframework.yarucompose.widgets.YaruVerticalDivider
import dev.nucleusframework.yarucompose.window.YaruDecoratedWindow
import kotlin.math.roundToInt

/** Picks the Yaru accent for the running desktop: Ubuntu orange, GNOME blue, KDE / others blue. */
private fun yaruVariantForDesktop(): YaruVariant {
    val desktop = (System.getenv("XDG_CURRENT_DESKTOP") ?: "").lowercase()
    return when {
        "ubuntu" in desktop -> YaruVariant.Orange
        "gnome" in desktop -> YaruVariant.AdwaitaBlue
        "kde" in desktop -> YaruVariant.KubuntuBlue
        "xfce" in desktop -> YaruVariant.XubuntuBlue
        else -> YaruVariant.Orange
    }
}

/**
 * Linux kit (Ubuntu / GNOME), built on Yaru Compose. Yaru is the Ubuntu theme: it follows GNOME's header-bar layout
 * and the window buttons are drawn by Nucleus on whichever side the desktop's `button-layout` asks for. It is a
 * faithful Yaru look, not libadwaita itself.
 */
object LinuxKit : WpKit {
    override val id: String = "Linux Yaru"
    override val pageTitleInContent: Boolean = false

    @Composable
    override fun Text(text: String, role: TextRole, modifier: Modifier, color: Color, maxLines: Int, bold: Boolean) {
        val t = LocalYaruTypography.current
        val style = when (role) {
            TextRole.Display -> t.headlineMedium
            TextRole.Title -> t.titleLarge
            TextRole.Heading -> t.titleSmall
            TextRole.Body -> t.bodyMedium
            TextRole.Caption -> t.bodySmall
        }
        YaruText(
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
        val content: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    YaruGlyph(icon, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                YaruText(text, maxLines = 1)
            }
        }
        if (primary) {
            YaruFilledButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        } else {
            YaruOutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        }
    }

    @Composable
    override fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
        YaruIconButton(
            onPressed = onClick,
            modifier = modifier,
            enabled = enabled,
            iconSize = 18.dp,
            semanticLabel = label,
            tooltip = label,
        ) {
            YaruGlyph(icon, Modifier.size(18.dp))
        }
    }

    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
        YaruSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
        // Yaru's slider has neither detents nor a "finished" callback, so snap here and commit on every new detent.
        YaruSlider(
            value = value,
            onValueChange = { raw ->
                val snapped = snap(raw, range, steps)
                if (snapped != value) {
                    onValueChange(snapped)
                    onValueChangeFinished()
                }
            },
            modifier = modifier,
            valueRange = range,
        )
    }

    private fun snap(raw: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
        if (steps <= 0) return raw
        val span = range.endInclusive - range.start
        val parts = steps + 1
        val index = (((raw - range.start) / span) * parts).roundToInt().coerceIn(0, parts)
        return range.start + span * index / parts
    }

    @Composable
    override fun Dropdown(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier, enabled: Boolean) {
        YaruPopupMenuButton(
            items = options,
            onSelected = { chosen ->
                val i = options.indexOf(chosen)
                if (i >= 0) onSelect(i)
            },
            modifier = modifier,
            enabled = enabled,
            selected = options.getOrNull(selectedIndex),
        )
    }

    @Composable
    override fun ChipBar(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
        YaruChoiceChipBar(
            isSelected = labels.indices.map { it == selectedIndex },
            onSelected = { onSelect(it) },
            label = { i -> YaruText(labels[i], maxLines = 1) },
            modifier = modifier,
            showCheckMarks = false,
            selectedFirst = false,
            clearOnSelect = false,
        )
    }

    @Composable
    override fun TextInput(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier) {
        YaruTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            placeholder = { YaruText(placeholder) },
        )
    }

    @Composable
    override fun ProgressBar(modifier: Modifier) {
        YaruLinearProgressIndicator(modifier = modifier)
    }

    @Composable
    override fun Divider(modifier: Modifier) {
        YaruHorizontalDivider(modifier)
    }
}

/** A stroked icon tinted with the surrounding Yaru content colour. */
@Composable
private fun YaruGlyph(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = LocalYaruContentColor.current) {
    Image(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}

/** The Linux window: a Yaru header-bar window with a sidebar, like GNOME Settings and Ubuntu's own apps. */
@Composable
fun NucleusApplicationScope.LinuxWindow(
    ui: WpUi,
    visible: Boolean,
    state: WindowState,
    icon: Painter?,
    onCloseRequest: () -> Unit,
) {
    YaruDecoratedWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        visible = visible,
        title = "WallPix",
        icon = icon,
        minimumSize = DpSize(820.dp, 560.dp),
    ) {
        val dark = isSystemInDarkMode()
        val variant = remember { yaruVariantForDesktop() }
        YaruTheme(isDark = dark, variant = variant) {
            val scheme = LocalYaruColorScheme.current
            val palette = remember(scheme) {
                WpPalette(
                    isDark = scheme.isDark,
                    background = scheme.surface,
                    surface = scheme.surfaceVariant,
                    surfaceAlt = scheme.surfaceVariant,
                    text = scheme.onSurface,
                    textSecondary = scheme.onSurfaceVariant,
                    accent = scheme.primary,
                    onAccent = scheme.onPrimary,
                    outline = scheme.outlineVariant,
                    danger = scheme.error,
                    cardRadius = YaruConstants.ContainerRadius,
                    tileRadius = 8.dp,
                    cardBorder = true,
                )
            }
            val snapshot = ui.rememberSnapshot()
            CompositionLocalProvider(
                LocalWpKit provides LinuxKit,
                LocalWpPalette provides palette,
                LocalWpUi provides ui,
                LocalWpSnapshot provides snapshot,
            ) {
                Box(Modifier.fillMaxSize()) {
                    LinuxShell(ui, snapshot, icon)
                    // Below the header-bar strip, so the window buttons stay reachable.
                    WpViewerLayer(topReserve = YaruConstants.TitleBarHeight)
                }
            }
        }
    }
}

private val SIDEBAR_WIDTH = 240.dp

@Composable
private fun LinuxShell(ui: WpUi, snap: WpSnapshot, icon: Painter?) {
    val scheme = LocalYaruColorScheme.current
    Row(Modifier.fillMaxSize().background(scheme.surface)) {
        // Master: header bar with the app name above the page list, as in GNOME Settings.
        Column(Modifier.width(SIDEBAR_WIDTH).fillMaxHeight().background(scheme.surfaceVariant.copy(alpha = 0.5f))) {
            YaruTitleBar(
                title = { YaruText("WallPix") },
                leading = {
                    if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                centerTitle = false,
                backgroundColor = Color.Transparent,
            )
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (page in listOf(WpPage.TODAY, WpPage.LATEST, WpPage.DOWNLOADED, WpPage.FAVOURITES)) {
                    SidebarRow(page, ui.page == page, badge = badgeFor(page, snap)) { ui.page = page }
                }
            }
            Spacer(Modifier.weight(1f))
            YaruHorizontalDivider(Modifier.padding(horizontal = 8.dp))
            Column(Modifier.padding(8.dp)) {
                SidebarRow(WpPage.SETTINGS, ui.page == WpPage.SETTINGS, badge = null) { ui.page = WpPage.SETTINGS }
            }
        }
        YaruVerticalDivider()
        // Detail: header bar with the page title and the refresh action.
        Column(Modifier.weight(1f).fillMaxHeight()) {
            YaruTitleBar(
                title = { YaruText(ui.page.title) },
                actions = {
                    LinuxKit.IconButton(WpIcons.Refresh, "Check for new wallpapers", { ui.checkNow() }, Modifier, !snap.busyNow)
                },
                centerTitle = true,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) { WpPageHost() }
        }
    }
}

private fun badgeFor(page: WpPage, snap: WpSnapshot): String? = when (page) {
    WpPage.DOWNLOADED -> snap.downloadedCount.takeIf { it > 0 }?.toString()
    WpPage.FAVOURITES -> snap.favouriteCount.takeIf { it > 0 }?.toString()
    else -> null
}

@Composable
private fun SidebarRow(page: WpPage, selected: Boolean, badge: String?, onClick: () -> Unit) {
    val scheme = LocalYaruColorScheme.current
    val shape = RoundedCornerShape(YaruConstants.ButtonRadius)
    val fill = if (selected) scheme.primary.copy(alpha = 0.16f) else Color.Transparent
    val tint = if (selected) scheme.primary else scheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        YaruGlyph(page.icon(), Modifier.size(18.dp), tint)
        YaruText(page.title, Modifier.weight(1f), color = tint, maxLines = 1, fontWeight = if (selected) FontWeight.SemiBold else null)
        if (badge != null) YaruText(badge, color = scheme.onSurfaceVariant)
    }
}
