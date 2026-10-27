package app.wallpix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import app.wallpix.core.AppController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files

/** A button that opens a menu of options. */
@Composable
fun <T> Dropdown(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(label(selected) + "  ▾") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

private sealed interface ThumbState {
    data object Loading : ThumbState
    data object Missing : ThumbState
    class Ready(val bitmap: ImageBitmap) : ThumbState
}

/** The 640 px preview of an image. Browsing never loads full-size pictures. */
@Composable
fun Thumbnail(
    controller: AppController,
    key: String,
    url: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    val thumb by produceState<ThumbState>(ThumbState.Loading, key, url) {
        val path = controller.thumbFile(key, url)
        val bitmap: ImageBitmap? = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { Files.newInputStream(path).use { loadImageBitmap(it) } }.getOrNull()
            }
        }
        value = if (bitmap == null) ThumbState.Missing else ThumbState.Ready(bitmap)
    }
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when (val t = thumb) {
            is ThumbState.Ready -> Image(
                bitmap = t.bitmap,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            ThumbState.Loading -> Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            ThumbState.Missing -> Text("No preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
