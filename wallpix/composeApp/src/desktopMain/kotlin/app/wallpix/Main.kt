package app.wallpix

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import app.wallpix.core.AppPaths
import app.wallpix.core.WallPixRuntime
import app.wallpix.platform.Os
import app.wallpix.platform.SingleInstance
import app.wallpix.ui.WallpixApp
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

private const val APP_NAME = "WallPix"

fun main(args: Array<String>) {
    // Started at login: stay in the tray and do not pop a window up.
    val startHidden = "--background" in args

    val os = Os.current()
    // A second copy would mean a second tray icon and two updaters fighting over the desktop.
    if (!SingleInstance.acquire(AppPaths.default(os).lockFile)) {
        System.err.println("$APP_NAME is already running (look for its tray icon).")
        return
    }

    val runtime = WallPixRuntime.create(os)
    val controller = runtime.controller
    val scope = runtime.scope
    runtime.start()

    application {
        val settings by controller.settings.collectAsState()
        val trayState = rememberTrayState()
        var message by remember { mutableStateOf<String?>(null) }
        // Without a tray there is no way back to a hidden window, so then we always show it.
        var windowOpen by remember { mutableStateOf(!startHidden || !isTraySupported) }
        val appIcon = remember { useResource("icons/app.png") { BitmapPainter(loadImageBitmap(it)) } }
        val trayIcon = remember { useResource("icons/tray.png") { BitmapPainter(loadImageBitmap(it)) } }

        LaunchedEffect(Unit) {
            controller.events.collect { e ->
                message = e.message
                if (e.notify && isTraySupported) {
                    trayState.sendNotification(Notification(APP_NAME, e.message, Notification.Type.Info))
                }
            }
        }

        if (isTraySupported) {
            Tray(
                icon = trayIcon,
                state = trayState,
                tooltip = APP_NAME,
                onAction = { windowOpen = true },
                menu = {
                    Item("Open $APP_NAME", onClick = { windowOpen = true })
                    Separator()
                    CheckboxItem(
                        "Apply new daily wallpaper",
                        checked = settings.autoApplyDaily,
                        onCheckedChange = { on -> scope.launch { controller.updateSettings { it.copy(autoApplyDaily = on) } } },
                    )
                    CheckboxItem(
                        "Slideshow of favourites",
                        checked = settings.slideshowEnabled,
                        onCheckedChange = { on -> scope.launch { controller.updateSettings { it.copy(slideshowEnabled = on) } } },
                    )
                    Separator()
                    Item("Check for new wallpapers now", onClick = { scope.launch { controller.checkNow() } })
                    Item("Next favourite", onClick = { scope.launch { controller.advanceSlideshow(1) } })
                    Item("Previous favourite", onClick = { scope.launch { controller.advanceSlideshow(-1) } })
                    Separator()
                    Item("Quit", onClick = { exitApplication() })
                },
            )
        }

        // Only composed while open: a hidden tray app then holds no window or thumbnails in memory.
        if (windowOpen) {
            Window(
                onCloseRequest = { if (isTraySupported) windowOpen = false else exitApplication() },
                title = APP_NAME,
                icon = appIcon,
                state = rememberWindowState(width = 1120.dp, height = 780.dp),
            ) {
                WallpixApp(controller, scope, runtime.defaultFavouritesDir, message)
            }
        }
    }

    runtime.stop()
    exitProcess(0)
}
