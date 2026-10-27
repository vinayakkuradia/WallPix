package app.wallpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// ---- thin wrappers: defaults live here, the kit supplies the native look ------------------------------------

@Composable
fun WpText(
    text: String,
    role: TextRole = TextRole.Body,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    bold: Boolean = false,
) = LocalWpKit.current.Text(text, role, modifier, color, maxLines, bold)

@Composable
fun WpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) = LocalWpKit.current.Button(text, onClick, modifier, icon, primary, enabled)

@Composable
fun WpIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = LocalWpKit.current.IconButton(icon, label, onClick, modifier, enabled)

@Composable
fun WpSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) =
    LocalWpKit.current.Switch(checked, onCheckedChange, enabled)

@Composable
fun WpSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
) = LocalWpKit.current.Slider(value, onValueChange, onValueChangeFinished, range, steps, modifier)

@Composable
fun WpDropdown(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = LocalWpKit.current.Dropdown(options, selectedIndex, onSelect, modifier, enabled)

@Composable
fun WpChipBar(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) =
    LocalWpKit.current.ChipBar(labels, selectedIndex, onSelect, modifier)

@Composable
fun WpTextInput(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) =
    LocalWpKit.current.TextInput(value, onValueChange, placeholder, modifier)

@Composable
fun WpProgressBar(modifier: Modifier = Modifier) = LocalWpKit.current.ProgressBar(modifier)

@Composable
fun WpDivider(modifier: Modifier = Modifier) = LocalWpKit.current.Divider(modifier)

// ---- composed pieces --------------------------------------------------------------------------------------

/** A rounded group, the macOS grouped form / Fluent settings card / GNOME boxed list. */
@Composable
fun WpCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalWpPalette.current
    val shape = RoundedCornerShape(p.cardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface)
            .then(if (p.cardBorder) Modifier.border(1.dp, p.outline, shape) else Modifier),
        content = content,
    )
}

/** A titled settings group. */
@Composable
fun WpSection(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            WpText(
                title,
                TextRole.Heading,
                Modifier.padding(start = 4.dp, bottom = 8.dp),
                color = LocalWpPalette.current.textSecondary,
            )
        }
        WpCard(content = content)
    }
}

/** One row in a [WpSection]: label and optional explanation on the left, the control on the right. */
@Composable
fun WpSettingRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    divider: Boolean = true,
    control: @Composable () -> Unit,
) {
    val p = LocalWpPalette.current
    if (divider) WpDivider(Modifier.padding(horizontal = 16.dp))
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f).widthIn(min = 140.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            WpText(label, TextRole.Body)
            if (description != null) WpText(description, TextRole.Caption, color = p.textSecondary)
        }
        // Some toolkits' pop-up buttons and sliders fill whatever width they are offered. Capping the control keeps
        // the label column wide enough to read, instead of squeezing it into a one-letter-wide strip.
        Box(Modifier.widthIn(max = 340.dp), contentAlignment = Alignment.CenterEnd) { control() }
    }
}

/** A small filled capsule over images ("Now showing"). */
@Composable
fun WpPill(text: String, modifier: Modifier = Modifier, background: Color, content: Color) {
    Box(
        modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        WpText(text, TextRole.Caption, color = content, maxLines = 1, bold = true)
    }
}

/** A round dark badge on top of an image holding an icon. */
@Composable
fun WpBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        WpIcon(icon, tint, size = 16.dp)
    }
}

@Composable
fun WpEmptyState(title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val p = LocalWpPalette.current
    Column(
        modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WpIcon(WpIcons.Photo, p.textSecondary, size = 44.dp)
        WpText(title, TextRole.Title)
        WpText(message, TextRole.Body, color = p.textSecondary)
        if (action != null) {
            Box(Modifier.padding(top = 6.dp)) { action() }
        }
    }
}

/**
 * Floating status line at the bottom of the content: progress while the engine works, otherwise the latest message,
 * which dismisses itself after a few seconds.
 */
@Composable
fun WpStatusBanner(modifier: Modifier = Modifier) {
    val ui = LocalWpUi.current
    val snap = LocalWpSnapshot.current
    val p = LocalWpPalette.current
    val message = ui.message
    LaunchedEffect(message) {
        if (message != null) {
            delay(9_000)
            if (ui.message == message) ui.message = null
        }
    }
    val text = snap.busy ?: message
    if (text == null) return
    val shape = RoundedCornerShape(p.cardRadius)
    Column(
        modifier
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.outline, shape),
    ) {
        if (snap.busyNow) WpProgressBar(Modifier.fillMaxWidth())
        Row(
            Modifier.padding(start = 16.dp, end = 6.dp).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WpText(text, TextRole.Body, Modifier.weight(1f, fill = false), maxLines = 3)
            if (!snap.busyNow) {
                WpIconButton(WpIcons.Close, "Dismiss", { ui.message = null })
            }
        }
    }
}
