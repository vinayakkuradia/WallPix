package app.wallpix.core

import app.wallpix.logic.Codec
import app.wallpix.model.AppState
import app.wallpix.model.Settings
import app.wallpix.platform.Os
import app.wallpix.platform.Proc
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class AppPaths(val dataDir: Path) {
    val recents: Path = dataDir.resolve("recents")
    val thumbs: Path = dataDir.resolve("thumbs")
    val stateFile: Path = dataDir.resolve("library.json")
    val settingsFile: Path = dataDir.resolve("settings.json")
    val lockFile: Path = dataDir.resolve("wallpix.lock")

    companion object {
        fun default(os: Os = Os.current()): AppPaths {
            val home = System.getProperty("user.home")
            val dir = when (os) {
                Os.WINDOWS -> Paths.get(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "WallPix")
                Os.MAC -> Paths.get(home, "Library", "Application Support", "WallPix")
                Os.LINUX -> Paths.get(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "WallPix")
            }
            return AppPaths(dir)
        }

        /** `<Downloads>/WallPix`, using the user's localised Downloads folder on Linux when known. */
        fun defaultFavourites(os: Os = Os.current()): Path {
            val home = System.getProperty("user.home")
            val downloads = if (os == Os.LINUX) {
                val r = Proc.run(listOf("xdg-user-dir", "DOWNLOAD"), 5)
                val p = r.output.takeIf { r.ok && it.isNotBlank() }?.let { Paths.get(it) }
                if (p != null && p.toString() != home) p else Paths.get(home, "Downloads")
            } else {
                Paths.get(home, "Downloads")
            }
            return downloads.resolve("WallPix")
        }
    }
}

/** JSON persistence with atomic writes. A corrupt file is set aside rather than overwritten. */
class Storage(private val paths: AppPaths, private val clock: () -> Long = { System.currentTimeMillis() }) {

    fun loadSettings(): Settings = load(paths.settingsFile, Codec::decodeSettings) ?: Settings()

    fun loadState(): AppState = load(paths.stateFile, Codec::decodeState) ?: AppState()

    fun saveSettings(s: Settings) = write(paths.settingsFile, Codec.encodeSettings(s))

    fun saveState(s: AppState) = write(paths.stateFile, Codec.encodeState(s))

    private fun <T> load(file: Path, decode: (String) -> T): T? {
        if (!Files.exists(file)) return null
        return try {
            decode(Files.readString(file))
        } catch (e: Exception) {
            runCatching { Files.move(file, file.resolveSibling("${file.fileName}.corrupt-${clock()}")) }
            null
        }
    }

    private fun write(file: Path, text: String) {
        Files.createDirectories(file.parent)
        val tmp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(tmp, text)
        try {
            Files.move(tmp, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp, file, REPLACE_EXISTING)
        }
    }
}
