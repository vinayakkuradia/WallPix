package app.wallpix.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private suspend fun decode(path: Path): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching { Files.newInputStream(path).use { loadImageBitmap(it) } }.getOrNull()
}

/** The 640 px preview of an image. Browsing never loads full-size pictures. Fades in when ready. */
@Composable
fun WpThumbnail(key: String, url: String, title: String, modifier: Modifier = Modifier) {
    val ui = LocalWpUi.current
    val palette = LocalWpPalette.current
    val bitmap by produceState<ImageBitmap?>(null, key, url) {
        val path = ui.controller.thumbFile(key, url)
        value = if (path == null) null else decode(path)
    }
    Picture(bitmap, title, modifier.background(palette.surfaceAlt))
}

/**
 * The picture you actually have on disk, for the large hero. Falls back to the thumbnail while the file is
 * decoding or when it is missing, so the hero never flashes empty.
 */
@Composable
fun WpFullImage(localPath: String?, key: String, thumbUrl: String, title: String, modifier: Modifier = Modifier) {
    val ui = LocalWpUi.current
    val palette = LocalWpPalette.current
    val full by produceState<ImageBitmap?>(null, localPath) {
        value = if (localPath == null) null else decode(Paths.get(localPath))
    }
    val thumb by produceState<ImageBitmap?>(null, key, thumbUrl) {
        val path = ui.controller.thumbFile(key, thumbUrl)
        value = if (path == null) null else decode(path)
    }
    Picture(full ?: thumb, title, modifier.background(palette.surfaceAlt))
}

@Composable
private fun Picture(bitmap: ImageBitmap?, description: String, modifier: Modifier) {
    val alpha by animateFloatAsState(if (bitmap != null) 1f else 0f, tween(260))
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
            )
        }
    }
}
