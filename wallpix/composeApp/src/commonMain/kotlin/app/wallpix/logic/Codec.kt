package app.wallpix.logic

import app.wallpix.model.AppState
import app.wallpix.model.FeedItem
import app.wallpix.model.GroupMix
import app.wallpix.model.LibraryEntry
import app.wallpix.model.Naming
import app.wallpix.model.ResolutionMode
import app.wallpix.model.Settings
import app.wallpix.model.SlideshowOrder
import app.wallpix.model.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Hand-written JSON mapping (no compiler plugin needed) so the persisted formats stay explicit
 * and tolerant: unknown keys are ignored and missing keys fall back to defaults.
 */
object Codec {
    private val json = Json { prettyPrint = true }

    // ---- Peapix feed -------------------------------------------------------------------------

    /**
     * Parses a feed response. Items with a malformed key or an untrusted URL are dropped rather than
     * trusted, because keys become file names and URLs are fetched.
     */
    fun parseFeed(text: String, source: Source, country: String?): List<FeedItem> {
        val root = Json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
        return root.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val image = o.str("imageUrl") ?: return@mapNotNull null
            val full = o.str("fullUrl") ?: image
            val thumb = o.str("thumbUrl") ?: full
            if (!Naming.isValidKey(Naming.imageKey(image))) return@mapNotNull null
            if (!Naming.isTrustedUrl(image) || !Naming.isTrustedUrl(full) || !Naming.isTrustedUrl(thumb)) {
                return@mapNotNull null
            }
            FeedItem(
                source = source,
                country = country,
                title = o.str("title")?.trim().orEmpty().ifEmpty { "Untitled" },
                copyright = o.str("copyright")?.trim().orEmpty(),
                fullUrl = full,
                thumbUrl = thumb,
                imageUrl = image,
                pageUrl = o.str("pageUrl").orEmpty(),
                date = o.str("date"),
            )
        }
    }

    // ---- Settings ----------------------------------------------------------------------------

    fun encodeSettings(s: Settings): String = pretty(
        buildJsonObject {
            put("source", s.source.name)
            put("country", s.country)
            put("autoApplyDaily", s.autoApplyDaily)
            put("recentsCount", s.recentsCount)
            put("resolution", s.resolution.name)
            s.favouritesDir?.let { put("favouritesDir", it) }
            put("checkIntervalMin", s.checkIntervalMin)
            put("slideshowEnabled", s.slideshowEnabled)
            put("slideshowIntervalMin", s.slideshowIntervalMin)
            put("slideshowOrder", s.slideshowOrder.name)
            put("groupMix", s.groupMix.name)
            put("slideshowGroups", buildJsonArray { s.slideshowGroups.sorted().forEach { add(JsonPrimitive(it)) } })
            put("launchAtLogin", s.launchAtLogin)
        },
    )

    fun decodeSettings(text: String): Settings {
        val o = Json.parseToJsonElement(text) as JsonObject
        val d = Settings()
        return Settings(
            source = enumOr(o.str("source"), d.source),
            country = o.str("country") ?: d.country,
            autoApplyDaily = o.bool("autoApplyDaily") ?: d.autoApplyDaily,
            recentsCount = o.int("recentsCount") ?: d.recentsCount,
            resolution = enumOr(o.str("resolution"), d.resolution),
            favouritesDir = o.str("favouritesDir"),
            checkIntervalMin = o.int("checkIntervalMin") ?: d.checkIntervalMin,
            slideshowEnabled = o.bool("slideshowEnabled") ?: d.slideshowEnabled,
            slideshowIntervalMin = o.int("slideshowIntervalMin") ?: d.slideshowIntervalMin,
            slideshowOrder = enumOr(o.str("slideshowOrder"), d.slideshowOrder),
            groupMix = enumOr(o.str("groupMix"), d.groupMix),
            slideshowGroups = (o["slideshowGroups"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }?.toSet()
                ?: d.slideshowGroups,
            launchAtLogin = o.bool("launchAtLogin") ?: d.launchAtLogin,
        ).sanitized()
    }

    // ---- App state ---------------------------------------------------------------------------

    fun encodeState(s: AppState): String = pretty(
        buildJsonObject {
            put("version", 1)
            put("nextSeq", s.nextSeq)
            put("lastCheckMs", s.lastCheckMs)
            put("nextSlideshowMs", s.nextSlideshowMs)
            s.currentKey?.let { put("currentKey", it) }
            put("groups", buildJsonArray { s.groups.forEach { add(JsonPrimitive(it)) } })
            put("seen", buildJsonArray { s.seen.forEach { add(JsonPrimitive(it)) } })
            put(
                "entries",
                buildJsonArray {
                    s.entries.forEach { e ->
                        add(
                            buildJsonObject {
                                put("key", e.key)
                                put("seq", e.seq)
                                put("source", e.source.name)
                                e.country?.let { put("country", it) }
                                put("title", e.title)
                                put("copyright", e.copyright)
                                put("pageUrl", e.pageUrl)
                                e.date?.let { put("date", it) }
                                put("firstSeenMs", e.firstSeenMs)
                                put("fullUrl", e.fullUrl)
                                put("thumbUrl", e.thumbUrl)
                                put("imageUrl", e.imageUrl)
                                e.localPath?.let { put("localPath", it) }
                                put("favourite", e.favourite)
                                put("group", e.group)
                                put("pendingTrash", e.pendingTrash)
                            },
                        )
                    }
                },
            )
        },
    )

    fun decodeState(text: String): AppState {
        val o = Json.parseToJsonElement(text) as JsonObject
        val entries = (o["entries"] as? JsonArray).orEmpty().mapNotNull { el ->
            val e = el as? JsonObject ?: return@mapNotNull null
            val key = e.str("key") ?: return@mapNotNull null
            if (!Naming.isValidKey(key)) return@mapNotNull null
            LibraryEntry(
                key = key,
                seq = e.long("seq") ?: 0L,
                source = enumOr(e.str("source"), Source.BING),
                country = e.str("country"),
                title = e.str("title") ?: "Untitled",
                copyright = e.str("copyright").orEmpty(),
                pageUrl = e.str("pageUrl").orEmpty(),
                date = e.str("date"),
                firstSeenMs = e.long("firstSeenMs") ?: 0L,
                fullUrl = e.str("fullUrl").orEmpty(),
                thumbUrl = e.str("thumbUrl").orEmpty(),
                imageUrl = e.str("imageUrl").orEmpty(),
                localPath = e.str("localPath"),
                favourite = e.bool("favourite") ?: false,
                group = e.str("group").orEmpty(),
                pendingTrash = e.bool("pendingTrash") ?: false,
            )
        }
        return AppState(
            entries = entries,
            seen = (o["seen"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content },
            groups = (o["groups"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content },
            currentKey = o.str("currentKey"),
            nextSeq = o.long("nextSeq") ?: ((entries.maxOfOrNull { it.seq } ?: 0L) + 1),
            lastCheckMs = o.long("lastCheckMs") ?: 0L,
            nextSlideshowMs = o.long("nextSlideshowMs") ?: 0L,
        )
    }

    // ---- helpers -----------------------------------------------------------------------------

    private fun pretty(el: JsonElement): String = json.encodeToString(JsonElement.serializer(), el)

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
