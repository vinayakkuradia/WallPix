package app.wallpix.core

import app.wallpix.model.FeedItem
import app.wallpix.model.Source
import java.nio.file.Path

/** Fetches the Peapix feed. Newest item first. */
interface FeedApi {
    suspend fun fetch(source: Source, country: String, n: Int): List<FeedItem>
}

/** Downloads one image to [dest]. Throws [java.io.IOException] on any failure; never leaves partial files. */
interface ImageFetcher {
    suspend fun download(url: String, dest: Path)
}

/** Sets the same wallpaper on every monitor. Blocking; called off the UI thread. */
interface WallpaperSetter {
    fun set(path: Path): Result<Unit>
}

/** Moves a file to the OS trash / Recycle Bin. Returns false (and keeps the file) if that is impossible. */
interface Trasher {
    fun trash(path: Path): Boolean
}

interface ScreenInfo {
    /** Longest edge, in physical pixels, across all screens. */
    fun longEdgePx(): Int
}

interface Autostart {
    fun isSupported(): Boolean
    fun isEnabled(): Boolean
    fun set(enabled: Boolean): Result<Unit>
}
