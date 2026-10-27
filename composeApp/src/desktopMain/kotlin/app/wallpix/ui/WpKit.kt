package app.wallpix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/** Semantic text sizes. Each kit maps them onto its own typography scale. */
enum class TextRole { Display, Title, Heading, Body, Caption }

/**
 * Colours and shape language the kit-neutral screens need. A kit builds this from its own toolkit's
 * theme, so the shared screens (hero, grid, settings cards) inherit macOS, Fluent or Yaru colours.
 */
@Immutable
class WpPalette(
    val isDark: Boolean,
    /** Page background behind cards. May be fully transparent when a native backdrop (Mica) shows through. */
    val background: Color,
    /** Card / panel fill. */
    val surface: Color,
    /** Placeholder behind images and hover fill. */
    val surfaceAlt: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val outline: Color,
    val danger: Color,
    /** Corner radius of settings cards and the hero. */
    val cardRadius: Dp,
    /** Corner radius of wallpaper thumbnails. */
    val tileRadius: Dp,
    /** Whether settings cards get a hairline border (Fluent, Yaru) or none (macOS grouped forms). */
    val cardBorder: Boolean,
)

val LocalWpPalette = staticCompositionLocalOf<WpPalette> { error("WpPalette not provided") }
val LocalWpKit = staticCompositionLocalOf<WpKit> { error("WpKit not provided") }
val LocalWpUi = staticCompositionLocalOf<WpUi> { error("WpUi not provided") }
val LocalWpSnapshot = staticCompositionLocalOf<WpSnapshot> { error("WpSnapshot not provided") }

/**
 * The widget set a kit must provide. Everything native-looking (buttons, switches, sliders, menus, text) comes from
 * the toolkit behind the kit; the kit-neutral screens only ever call these through the `Wp*` wrappers.
 *
 * Deliberately no default arguments here: defaults live on the wrappers, which keeps the compiler's handling of
 * composable interface methods trivial.
 */
interface WpKit {
    val id: String

    /** macOS shows the page name in the toolbar, so its pages skip the big heading; Fluent and Yaru show it in content. */
    val pageTitleInContent: Boolean

    @Composable
    fun Text(text: String, role: TextRole, modifier: Modifier, color: Color, maxLines: Int, bold: Boolean)

    @Composable
    fun Button(text: String, onClick: () -> Unit, modifier: Modifier, icon: ImageVector?, primary: Boolean, enabled: Boolean)

    @Composable
    fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean)

    @Composable
    fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean)

    @Composable
    fun Slider(
        value: Float,
        onValueChange: (Float) -> Unit,
        onValueChangeFinished: () -> Unit,
        range: ClosedFloatingPointRange<Float>,
        steps: Int,
        modifier: Modifier,
    )

    /** A pop-up selector showing the current choice. */
    @Composable
    fun Dropdown(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier, enabled: Boolean)

    /** A single-choice row of pills / segments. */
    @Composable
    fun ChipBar(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier)

    @Composable
    fun TextInput(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier)

    /** Indeterminate linear progress. */
    @Composable
    fun ProgressBar(modifier: Modifier)

    @Composable
    fun Divider(modifier: Modifier)
}
