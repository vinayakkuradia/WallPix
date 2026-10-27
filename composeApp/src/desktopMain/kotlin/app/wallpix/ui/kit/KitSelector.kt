package app.wallpix.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.WindowState
import app.wallpix.platform.Os
import app.wallpix.ui.WpUi
import dev.nucleusframework.application.NucleusApplicationScope

/** Which native design system draws the window. */
enum class KitId(val key: String) {
    MACOS("macos"),
    WINDOWS("windows"),
    LINUX("linux"),
    ;

    companion object {
        /**
         * The kit that matches the operating system. For design work on one machine, `-Dwallpix.kit=macos|windows|linux`
         * (or the `WALLPIX_KIT` environment variable) forces another one; the window chrome then follows that kit
         * as far as the host OS allows.
         */
        fun select(
            os: Os = Os.current(),
            override: String? = System.getProperty("wallpix.kit") ?: System.getenv("WALLPIX_KIT"),
        ): KitId {
            when (override?.trim()?.lowercase()) {
                "macos", "mac" -> return MACOS
                "windows", "win", "fluent" -> return WINDOWS
                "linux", "yaru" -> return LINUX
            }
            return when (os) {
                Os.MAC -> MACOS
                Os.WINDOWS -> WINDOWS
                Os.LINUX -> LINUX
            }
        }
    }
}

/** Opens the application window in the chosen kit's native style. */
@Composable
fun NucleusApplicationScope.WallpixWindow(
    kit: KitId,
    ui: WpUi,
    visible: Boolean,
    state: WindowState,
    icon: Painter?,
    onCloseRequest: () -> Unit,
) {
    when (kit) {
        KitId.MACOS -> MacosWindow(ui, visible, state, icon, onCloseRequest)
        KitId.WINDOWS -> WindowsWindow(ui, visible, state, icon, onCloseRequest)
        KitId.LINUX -> LinuxWindow(ui, visible, state, icon, onCloseRequest)
    }
}
