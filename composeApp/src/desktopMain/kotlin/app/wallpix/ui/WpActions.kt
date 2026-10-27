package app.wallpix.ui

import app.wallpix.core.AppController
import app.wallpix.logic.Wallpaper

/** Apply from the live feed (downloading if needed) or from the local copy. */
suspend fun AppController.applyWallpaper(w: Wallpaper) {
    val item = w.item
    if (item != null) applyItem(item) else applyEntry(w.key)
}

/** Favourite from the local recents when there is a copy on disk, otherwise straight from the feed. */
suspend fun AppController.favouriteWallpaper(w: Wallpaper, group: String) {
    val item = w.item
    if (w.onDisk) addFavourite(w.key, group) else if (item != null) addFavourite(item, group)
}
