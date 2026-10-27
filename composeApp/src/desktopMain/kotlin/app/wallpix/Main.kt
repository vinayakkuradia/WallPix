package app.wallpix

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import app.wallpix.core.WallPixRuntime
import app.wallpix.platform.Os
import app.wallpix.ui.WpUi
import app.wallpix.ui.kit.KitId
import app.wallpix.ui.kit.WallpixWindow
import dev.nucleusframework.application.SingleInstanceRestoreEffect
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.composenativetray.tray.api.Tray
import dev.nucleusframework.notification.common.NotificationManager
import dev.nucleusframework.notification.common.notification
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess

private const val APP_NAME = "WallPix"

fun main(args: Array<String>) {
    // Started at login: stay in the tray and do not pop a window up.
    val startHidden = "--background" in args

    // nucleusApplication takes the single-instance lock itself: a second launch exits at once and asks this
    // process to show its window (see SingleInstanceRestoreEffect below).
    nucleusApplication(args = args, dockIconFollowsWindows = true) {
        val os = remember { Os.current() }
        val kit = remember { KitId.select(os) }
        val runtime = remember { WallPixRuntime.create(os) }
        val controller = runtime.controller
        val scope = runtime.scope
        val ui = remember { WpUi(controller, scope, runtime.defaultFavouritesDir, os) }
        val settings by controller.settings.collectAsState()

        var windowOpen by remember { mutableStateOf(!startHidden) }
        val windowState = rememberWindowState(
            size = DpSize(1180.dp, 780.dp),
            position = WindowPosition(Alignment.Center),
        )
        val appIcon = remember { useResource("icons/app.png") { BitmapPainter(loadImageBitmap(it)) } }
        val trayPng = remember { resourceAsFileUri("/icons/${trayFor(os, ico = false)}", ".png") }
        val trayIco = remember { resourceAsFileUri("/icons/${trayFor(os, ico = true)}", ".ico") }

        DisposableEffect(runtime) {
            runtime.start()
            onDispose { runtime.stop() }
        }

        LaunchedEffect(controller) {
            runCatching { NotificationManager.initialize() }
            controller.events.collect { e ->
                ui.message = e.message
                if (e.notify) {
                    runCatching { NotificationManager.send(notification(title = APP_NAME, message = e.message) { }) }
                }
            }
        }

        // A second launch (from the launcher, Start menu or Dock) brings the window back.
        SingleInstanceRestoreEffect { windowOpen = true }

        Tray(
            iconPath = trayPng,
            windowsIconPath = trayIco,
            tooltip = APP_NAME,
            primaryAction = { windowOpen = true },
        ) {
            Item(label = "Open $APP_NAME", onClick = { windowOpen = true })
            Divider()
            CheckableItem(
                label = "Apply new daily wallpaper",
                checked = settings.autoApplyDaily,
                onCheckedChange = { on -> scope.launch { controller.updateSettings { it.copy(autoApplyDaily = on) } } },
            )
            CheckableItem(
                label = "Slideshow of favourites",
                checked = settings.slideshowEnabled,
                onCheckedChange = { on -> scope.launch { controller.updateSettings { it.copy(slideshowEnabled = on) } } },
            )
            Divider()
            Item(label = "Check for new wallpapers now", onClick = { scope.launch { controller.checkNow() } })
            Item(label = "Next favourite", onClick = { scope.launch { controller.advanceSlideshow(1) } })
            Item(label = "Previous favourite", onClick = { scope.launch { controller.advanceSlideshow(-1) } })
            Divider()
            Item(label = "Quit $APP_NAME", onClick = { exitApplication() })
        }

        // Only composed while open: a hidden tray app then holds no window or thumbnails in memory, and on macOS the
        // Dock icon goes away with the last window (dockIconFollowsWindows).
        if (windowOpen) {
            WallpixWindow(
                kit = kit,
                ui = ui,
                visible = true,
                state = windowState,
                icon = appIcon,
                onCloseRequest = { windowOpen = false },
            )
        }
    }
    exitProcess(0)
}

/** macOS menu bars and GNOME panels want a neutral glyph; Windows takes the full-colour .ico. */
private fun trayFor(os: Os, ico: Boolean): String = when {
    ico -> "tray.ico"
    os == Os.MAC -> "tray-glyph.png"
    else -> "tray.png"
}

/**
 * Copies a classpath resource to a temp file and returns it as a `file:` URI string. The tray library parses the icon
 * path as a URI, which rejects raw Windows paths (`C:\...` reads as a URI scheme).
 */
private fun resourceAsFileUri(resource: String, suffix: String): String {
    val bytes = checkNotNull(object {}.javaClass.getResourceAsStream(resource)) { "Missing resource $resource" }
        .use { it.readBytes() }
    val file = File.createTempFile("wallpix-tray", suffix)
    file.writeBytes(bytes)
    file.deleteOnExit()
    return file.toURI().toString()
}
