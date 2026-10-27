# WallPix

A tray-resident desktop client for [Peapix](https://peapix.com/api): daily Bing wallpapers for macOS, Windows and
Linux, written in Kotlin Multiplatform with Compose Desktop. MIT licensed.

## What it does

- **Updates your wallpaper daily.** Checks Peapix in the background (hourly by default), downloads every new image and
  applies the newest one. The tray menu has an **Apply new daily wallpaper** switch; with it off, images are still
  downloaded, just not applied.
- **Latest N** (default 10, maximum 30). Peapix only serves about a week at a time, so WallPix remembers what it has
  seen and tops the list up from its own history. The list grows with use.
- **Browse** the live feed and your downloads as thumbnails. Full-size images are only fetched when you use them.
- **Favourites** are saved to a folder you choose (default `Downloads/WallPix`) and are never cleaned up. Sub-folders
  are **groups**. Un-favouriting moves the file to the Trash / Recycle Bin. WallPix never deletes anything permanently.
- **Slideshow** of your favourites, with selectable groups, in order or shuffled, and an optional *even share per group*
  mode so a small group is not drowned out by a large one.
- **Sources:** Bing (12 regions) and, optionally, Windows Spotlight.
- **Resolution:** Auto (original image on screens wider than 1920 px, otherwise 1920 px), 1920 px, or Original.

Only the newest N non-favourite downloads are kept; older ones go to the Trash. The wallpaper currently on your desktop
is never removed from under you.

## Build and run

Requires JDK 17+ (21 recommended).

```sh
./gradlew :composeApp:run                         # run from source
./gradlew :composeApp:desktopTest                 # tests
./gradlew :composeApp:createDistributable         # app image, no installer tooling needed
./gradlew :composeApp:packageDistributionForCurrentOS   # .deb / .dmg / .msi for the OS you are on
```

`jpackage` cannot cross-compile, so each installer is built on its own OS; the GitHub Actions workflow does exactly
that. Windows installers need [WiX 3](https://wixtoolset.org/) on the machine.

Launch at login only works from an installed or packaged copy, not from `gradlew run`.

## Platform notes

| OS | Wallpaper | Trash | Notes |
|---|---|---|---|
| Windows | `SystemParametersInfo` via JNA, Fill style, all monitors | Recycle Bin | Unsigned: SmartScreen will warn |
| macOS | AppleScript through System Events (Finder as fallback) | Trash | First use asks for *Automation* permission. Sets each display's current Space. Unsigned: Gatekeeper will warn |
| Linux | GNOME, Cinnamon, MATE (`gsettings`), KDE Plasma (`plasma-apply-wallpaperimage`), XFCE (`xfconf-query`) | `gio trash`, `trash-put` or `kioclient5` | Other desktops fall back to the GNOME keys. Wayland compositors such as sway are not covered |

The system tray on GNOME needs an AppIndicator extension. Without a tray WallPix keeps its window open and closing it
quits the app.

## Where things live

| | Windows | macOS | Linux |
|---|---|---|---|
| App data (settings, library, recents, thumbnails) | `%APPDATA%\WallPix` | `~/Library/Application Support/WallPix` | `$XDG_DATA_HOME/WallPix` (`~/.local/share/WallPix`) |
| Favourites (default) | `Downloads\WallPix` | `~/Downloads/WallPix` | `<XDG Downloads>/WallPix` |

## Design

See [`docs`](docs) and the project design notes. In short: a pure, tested engine (`app.wallpix.logic`, `model`) behind
small ports (`FeedApi`, `ImageFetcher`, `WallpaperSetter`, `Trasher`, `Autostart`), a controller that owns all state and
side effects (`core/AppController`), real OS integrations (`platform`), and a thin Compose UI (`ui`).

## Peapix terms

Images are provided by Peapix "for wallpaper use only"; copyright stays with the photographers. WallPix identifies itself
with a descriptive User-Agent, respects Peapix's 30-minute cache by never polling faster than that, shows attribution
and links back to each image's Peapix page. Please keep it that way if you fork.
