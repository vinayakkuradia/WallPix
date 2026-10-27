package app.wallpix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One icon set for all three kits, drawn from SVG path data (Lucide, ISC licence) so no toolkit's icon artifact is
 * needed and every glyph exists. They are plain stroked outlines, which sit comfortably next to SF Symbols,
 * Segoe Fluent Icons and Yaru's line icons, and take the colour of the surrounding text.
 */
object WpIcons {
    private fun icon(name: String, vararg paths: String, filled: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) {
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = if (filled) SolidColor(Color.Black) else null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private const val HEART =
        "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"

    /** "Today": a sun. */
    val Sun = icon(
        "sun",
        "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8",
        "M12 2v2", "M12 20v2", "M4.93 4.93l1.41 1.41", "M17.66 17.66l1.41 1.41",
        "M2 12h2", "M20 12h2", "M6.34 17.66l-1.41 1.41", "M19.07 4.93l-1.41 1.41",
    )

    /** "Latest": a clock face. */
    val Clock = icon("clock", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20", "M12 6v6l4 2")

    val Download = icon(
        "download",
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3",
    )

    val Heart = icon("heart", HEART)
    val HeartFilled = icon("heart-filled", HEART, filled = true)

    /** "Settings": three sliders. */
    val Sliders = icon(
        "sliders",
        "M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3", "M14 2v4", "M8 10v4", "M16 18v4",
    )

    val Refresh = icon(
        "refresh",
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5",
    )

    val Folder = icon(
        "folder",
        "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
    )

    val ExternalLink = icon(
        "external-link",
        "M15 3h6v6", "M10 14 21 3", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
    )

    /** "Full screen": two arrows pointing outwards. */
    val Expand = icon("maximize", "M15 3h6v6", "M9 21H3v-6", "M21 3l-7 7", "M3 21l7-7")

    val ChevronLeft = icon("chevron-left", "M15 18l-6-6 6-6")
    val ChevronRight = icon("chevron-right", "M9 18l6-6-6-6")
    val Plus = icon("plus", "M5 12h14", "M12 5v14")
    val Close = icon("x", "M18 6L6 18", "M6 6l12 12")

    /** "Set as wallpaper": a monitor. */
    val Monitor = icon("monitor", "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z", "M8 21h8", "M12 17v4")

    /** Empty state glyph. */
    val Photo = icon(
        "photo",
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z",
        "M9 7.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3",
        "M21 15l-3.09-3.09a2 2 0 0 0-2.82 0L6 21",
    )
}

/** Tinted icon. Works the same inside every toolkit, because it only depends on Compose foundation. */
@Composable
fun WpIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Image(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
