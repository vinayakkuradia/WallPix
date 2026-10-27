package app.wallpix.core

import app.wallpix.logic.Codec
import app.wallpix.model.FeedItem
import app.wallpix.model.Naming
import app.wallpix.model.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Duration

object Http {
    /** Descriptive User-Agent so Peapix can identify the client. */
    const val USER_AGENT = "WallPix/1.0 (desktop wallpaper client; +https://peapix.com/api)"

    val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }
}

class PeapixApi(
    private val client: HttpClient = Http.client,
    private val base: String = "https://peapix.com",
) : FeedApi {

    override suspend fun fetch(source: Source, country: String, n: Int): List<FeedItem> = withContext(Dispatchers.IO) {
        val url = when (source) {
            Source.BING -> "$base/bing/feed?country=${enc(country)}&n=$n"
            Source.SPOTLIGHT -> "$base/spotlight/feed?n=$n"
        }
        val req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", Http.USER_AGENT)
            .header("Accept", "application/json")
            .GET().build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) throw IOException("Peapix returned HTTP ${resp.statusCode()}")
        try {
            Codec.parseFeed(resp.body(), source, if (source == Source.BING) country else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Unreadable response from Peapix", e)
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
}

class HttpImageFetcher(private val client: HttpClient = Http.client) : ImageFetcher {

    override suspend fun download(url: String, dest: Path): Unit = withContext(Dispatchers.IO) {
        if (!Naming.isTrustedUrl(url)) throw IOException("Refusing to fetch untrusted URL")
        Files.createDirectories(dest.parent)
        val tmp = dest.resolveSibling("${dest.fileName}.part")
        try {
            val req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .header("User-Agent", Http.USER_AGENT)
                .GET().build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofFile(tmp))
            if (resp.statusCode() != 200) throw IOException("HTTP ${resp.statusCode()} fetching image")
            val size = Files.size(tmp)
            if (size < MIN_BYTES || size > MAX_BYTES) throw IOException("Unexpected image size: $size bytes")
            if (!looksLikeImage(tmp)) throw IOException("Response is not an image")
            Files.move(tmp, dest, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun looksLikeImage(file: Path): Boolean {
        val h = ByteArray(12)
        val n = Files.newInputStream(file).use { it.read(h) }
        if (n < 12) return false
        val jpeg = h[0] == 0xFF.toByte() && h[1] == 0xD8.toByte() && h[2] == 0xFF.toByte()
        val png = h[0] == 0x89.toByte() && h[1] == 'P'.code.toByte() && h[2] == 'N'.code.toByte() && h[3] == 'G'.code.toByte()
        val webp = String(h, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(h, 8, 4, Charsets.ISO_8859_1) == "WEBP"
        return jpeg || png || webp
    }

    companion object {
        const val MIN_BYTES = 2_048L
        const val MAX_BYTES = 80L * 1024 * 1024
    }
}

/** On-disk cache of 640 px thumbnails, one file per image key. */
class ThumbStore(private val paths: AppPaths, private val fetcher: ImageFetcher) {

    fun fileFor(key: String): Path = paths.thumbs.resolve("$key.jpg")

    /** Larger (1920 px) copy used by the full-screen viewer for pictures that are not on disk yet. */
    fun previewFileFor(key: String): Path = paths.thumbs.resolve("$key.preview.jpg")

    suspend fun ensurePreview(key: String, url: String): Path? = fetchTo(previewFileFor(key), url)

    /** Returns the cached thumbnail, downloading it first if needed. Null when unavailable. */
    suspend fun ensure(key: String, url: String): Path? = fetchTo(fileFor(key), url)

    private suspend fun fetchTo(f: Path, url: String): Path? {
        if (Files.exists(f)) return f
        if (url.isBlank()) return null
        return try {
            fetcher.download(url, f)
            f
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    fun delete(key: String) {
        runCatching { Files.deleteIfExists(fileFor(key)) }
        runCatching { Files.deleteIfExists(previewFileFor(key)) }
    }

    /** Bounds the cache: thumbnails of keys not in [keep] are removed, oldest first, beyond [max] files. */
    fun prune(keep: Set<String>, max: Int = 300) {
        runCatching {
            if (!Files.isDirectory(paths.thumbs)) return
            val files = Files.list(paths.thumbs).use { s -> s.filter { it.fileName.toString().endsWith(".jpg") }.toList() }
            if (files.size <= max) return
            files.filter { it.fileName.toString().removeSuffix(".jpg").removeSuffix(".preview") !in keep }
                .sortedBy { Files.getLastModifiedTime(it).toMillis() }
                .take(files.size - max)
                .forEach { Files.deleteIfExists(it) }
        }
    }
}
