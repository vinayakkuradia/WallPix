# WallPix

A tray-resident desktop client for [Peapix](https://peapix.com/api): daily Bing wallpapers for macOS, Windows and
Linux, written in Kotlin with Compose Desktop on the [Nucleus](https://nucleusframework.dev) framework, so the window, title bar,
controls and widgets are native to each OS. MIT licensed.

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

## Native look on every OS

| OS | Toolkit | What you get |
|---|---|---|
| macOS | `compose-macos-ui` (Liquid Glass) | Real AppKit traffic lights, full-height sidebar with the lights floating over it, glass toolbar, native-style push buttons, switches, sliders and pop-up buttons |
| Windows 11 | `composefluent` + Nucleus Fluent window | Mica backdrop, Fluent caption bar with native caption buttons and snap layouts, `NavigationView` sidebar, system accent colour |
| Ubuntu / GNOME | `yarucompose` + Nucleus Yaru window | Client-side Yaru header bars, window buttons drawn in the desktop's own style and on the side GNOME's `button-layout` asks for, Yaru variant per desktop |

The kit is chosen from the OS at start-up. To preview another one on your machine, run with `-Dwallpix.kit=macos|windows|linux`
(or set `WALLPIX_KIT`); the shared screens re-theme, and the window chrome follows as far as the host OS allows.

Note: Yaru is GNOME-flavoured, but it is not libadwaita itself, and it does not call GTK.

## Build and run

Requires JDK 17+ (21 recommended) and Gradle 9.x (the wrapper pins 9.4.0). Nucleus 2.5 needs Kotlin 2.4.10 and
Compose Multiplatform 1.12.0 exactly; both are pinned in `gradle/libs.versions.toml`.

```sh
./gradlew :composeApp:run                         # run from source
./gradlew :composeApp:desktopTest                 # tests
./gradlew :composeApp:createDistributable         # app image, no installer tooling needed
./gradlew :composeApp:packageDistributionForCurrentOS   # .deb + AppImage / .dmg / NSIS .exe for the OS you are on
```

Installers cannot be cross-compiled, so each one is built on its own OS; the GitHub Actions workflow does exactly that.
The macOS build links against the macOS 26 SDK (needed for Liquid Glass), so build it on a runner with Xcode 26.

Launch at login only works from an installed or packaged copy, not from `gradlew run`.

## Platform notes

| OS | Wallpaper | Trash | Notes |
|---|---|---|---|
| Windows | `SystemParametersInfo` via JNA, Fill style, all monitors | Recycle Bin | Unsigned: SmartScreen will warn |
| macOS | AppleScript through System Events (Finder as fallback) | Trash | First use asks for *Automation* permission. Sets each display's current Space. Unsigned: Gatekeeper will warn |
| Linux | GNOME, Cinnamon, MATE (`gsettings`), KDE Plasma (`plasma-apply-wallpaperimage`), XFCE (`xfconf-query`) | `gio trash`, `trash-put` or `kioclient5` | Other desktops fall back to the GNOME keys. Wayland compositors such as sway are not covered |

The system tray on GNOME needs an AppIndicator extension. The window and tray do not use AWT (the Tao backend has none);
folder pickers, trash and "open" go through the OS's own tools.

## Where things live

| | Windows | macOS | Linux |
|---|---|---|---|
| App data (settings, library, recents, thumbnails) | `%APPDATA%\WallPix` | `~/Library/Application Support/WallPix` | `$XDG_DATA_HOME/WallPix` (`~/.local/share/WallPix`) |
| Favourites (default) | `Downloads\WallPix` | `~/Downloads/WallPix` | `<XDG Downloads>/WallPix` |

## Design

See [`docs`](docs) and the project design notes. In short: a pure, tested engine (`app.wallpix.logic`, `model`) behind
small ports (`FeedApi`, `ImageFetcher`, `WallpaperSetter`, `Trasher`, `Autostart`), a controller that owns all state and
side effects (`core/AppController`), real OS integrations (`platform`), and a thin Compose UI (`ui`): kit-neutral pages (`WpPages`, `WpCards`) over a small `WpKit` widget interface that each
native kit (`ui/kit/{Macos,Windows,Linux}Kit.kt`) implements with its own toolkit.

## Peapix terms

Images are provided by Peapix "for wallpaper use only"; copyright stays with the photographers. WallPix identifies itself
with a descriptive User-Agent, respects Peapix's 30-minute cache by never polling faster than that, shows attribution
and links back to each image's Peapix page. Please keep it that way if you fork.
