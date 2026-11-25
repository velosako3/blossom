// SPDX-License-Identifier: GPL-3.0-only
// Ported from BitChord (https://github.com/kushagrasinghx/BitChord) v1.9.1,
// shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/spotify/{SpotifyImporter,SpotifyLibrary}.kt (GPL-3.0).
// Modified for blossom, 2026: blossom's ImportedPlaylist/ImportedTrack with durations and albums; albums and single
// tracks; playlist pagination of any size; signed-in private playlists and Liked Songs; injectable hosts for tests.
package io.github.velosako3.blossom.source.spotify

import io.github.velosako3.blossom.core.model.PlaylistOrigin
import io.github.velosako3.blossom.source.api.ImportError
import io.github.velosako3.blossom.source.api.ImportedPlaylist
import io.github.velosako3.blossom.source.api.ImportedTrack
import io.github.velosako3.blossom.source.api.PlaylistImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Spotify playlists, albums and tracks. Public links need no sign-in: the embed page carries the item (first 100
 * tracks) and an anonymous token, and the web player's GraphQL pages through the rest. Private playlists and Liked
 * Songs use [auth]; without it they fail with [ImportError.NeedsLogin].
 */
class SpotifyImporter(
    private val auth: SpotifyAuth?,
    private val openBase: String = "https://open.spotify.com",
    private val apiBase: String = "https://api-partner.spotify.com",
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
) : PlaylistImporter {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun canHandle(url: String): Boolean = parse(url) != null || url == LIKED_SONGS

    override suspend fun read(url: String, onProgress: (loaded: Int, total: Int?) -> Unit): ImportedPlaylist = withContext(Dispatchers.IO) {
        if (url == LIKED_SONGS) return@withContext liked(onProgress)
        val (type, id) = parse(url) ?: throw ImportError.NotFound(url)
        val canonical = "https://open.spotify.com/$type/$id"
        val origin = PlaylistOrigin(null, SERVICE, canonical)
        val page = embed(type, id) ?: throw ImportError.NotFound(canonical)
        val entity = page.obj("props")?.obj("pageProps")?.obj("state")?.obj("data")?.obj("entity")
        if (entity == null) {
            // Private, or deleted. Signed in, a private playlist can still be read.
            if (type == "playlist" && auth?.tokens() != null) return@withContext signedInPlaylist(id, origin, onProgress)
            if (type == "playlist") throw ImportError.NeedsLogin(SERVICE)
            throw ImportError.NotFound(canonical)
        }
        val name = entity.str("name") ?: entity.str("title") ?: SERVICE
        when (type) {
            "track" -> ImportedPlaylist(name, null, null, origin, listOf(
                ImportedTrack(
                    title = name,
                    artists = entity.arr("artists").orEmpty().mapNotNull { (it as? JsonObject)?.str("name") },
                    durationMs = entity.long("duration"),
                    isrc = null,
                    nativeTrack = null,
                    sourceKey = entity.str("uri"),
                ),
            ))
            else -> {
                val album = name.takeIf { type == "album" }
                val raw = entity.arr("trackList").orEmpty()
                val tracks = raw.mapNotNull { (it as? JsonObject)?.embedTrack(album) }.toMutableList()
                onProgress(tracks.size, null)
                // The embed stops at 100 items; page on from the raw count (some items may have been unreadable).
                if (type == "playlist" && raw.size >= EMBED_LIMIT) {
                    val token = page.obj("props")?.obj("pageProps")?.obj("state")?.obj("settings")?.obj("session")?.str("accessToken")
                        ?: throw IOException("Spotify gave no token to read past the first 100 tracks")
                    pages(id, mapOf("Authorization" to "Bearer $token"), raw.size, tracks, onProgress)
                }
                ImportedPlaylist(name, entity.str("subtitle").takeIf { type == "playlist" }, null, origin, tracks)
            }
        }
    }

    /** `(type, id)` from `open.spotify.com/[intl-xx/]{type}/{id}` or `spotify:{type}:{id}`. */
    private fun parse(url: String): Pair<String, String>? {
        val u = url.trim()
        URI_FORM.matchEntire(u)?.let { return it.groupValues[1] to it.groupValues[2] }
        val m = WEB_FORM.find(u) ?: return null
        return m.groupValues[1].lowercase() to m.groupValues[2]
    }

    private suspend fun embed(type: String, id: String): JsonObject? {
        val request = Request.Builder().url("$openBase/embed/$type/$id").header("User-Agent", USER_AGENT).build()
        val html = runInterruptible {
            http.newCall(request).execute().use { r ->
                when {
                    r.code == 404 -> null // deleted or never existed
                    !r.isSuccessful -> throw IOException("Spotify embed HTTP ${r.code}") // 403/429/5xx: try again later
                    else -> r.body.string()
                }
            }
        } ?: return null
        // A page without data is how private items look; an empty object makes the caller ask for sign-in.
        val data = NEXT_DATA.find(html)?.groupValues?.get(1) ?: return JsonObject(emptyMap())
        return json.parseToJsonElement(data) as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun JsonObject.embedTrack(album: String?): ImportedTrack? {
        val title = str("title") ?: str("name") ?: return null
        // ponytail: the embed joins artists with ", ", so "Tyler, The Creator" splits in two; the matcher tolerates it.
        val artists = str("subtitle")?.split(", ")?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
        return ImportedTrack(title.trim(), artists, long("duration"), null, null, album = album, sourceKey = str("uri"))
    }

    /** Playlist tracks from [start] on, 100 per GraphQL page, until the total. */
    private suspend fun pages(id: String, headers: Map<String, String>, start: Int, into: MutableList<ImportedTrack>, onProgress: (Int, Int?) -> Unit) {
        var offset = start
        while (true) {
            val variables = """{"uri":"spotify:playlist:$id","offset":$offset,"limit":$PAGE,"enableWatchFeedEntrypoint":false}"""
            val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"$FETCH_PLAYLIST_V1"}}"""
            val url = "$apiBase/pathfinder/v1/query".toHttpUrl().newBuilder()
                .addQueryParameter("operationName", "fetchPlaylist")
                .addQueryParameter("variables", variables)
                .addQueryParameter("extensions", extensions)
                .build()
            val request = Request.Builder().url(url).apply {
                header("User-Agent", USER_AGENT); header("App-platform", "WebPlayer"); header("Accept", "application/json")
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            val root = getJson(request)
            val content = root.obj("data")?.obj("playlistV2")?.obj("content") ?: break
            val items = content.arr("items").orEmpty()
            items.forEach { item -> (item as? JsonObject)?.obj("itemV2")?.obj("data")?.gqlTrack()?.let(into::add) }
            val total = content.long("totalCount")?.toInt()
            onProgress(into.size, total)
            offset += items.size
            if (items.isEmpty() || total == null || offset >= total) break
        }
    }

    private fun JsonObject.gqlTrack(): ImportedTrack? {
        val title = str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val artists = obj("artists")?.arr("items").orEmpty().mapNotNull { (it as? JsonObject)?.obj("profile")?.str("name") }
        val duration = (obj("trackDuration") ?: obj("duration"))?.long("totalMilliseconds")
        return ImportedTrack(title, artists, duration, null, null, album = obj("albumOfTrack")?.str("name"), sourceKey = str("uri"))
    }

    private suspend fun signedIn(): Map<String, String> {
        val t = auth?.tokens() ?: throw ImportError.NeedsLogin(SERVICE)
        return buildMap {
            put("Authorization", "Bearer ${t.bearer}")
            put("Origin", "https://open.spotify.com")
            put("Referer", "https://open.spotify.com/")
            t.clientToken?.let { put("Client-Token", it) }
        }
    }

    private suspend fun signedInPlaylist(id: String, origin: PlaylistOrigin, onProgress: (Int, Int?) -> Unit): ImportedPlaylist {
        val tracks = mutableListOf<ImportedTrack>()
        pages(id, signedIn(), 0, tracks, onProgress)
        return ImportedPlaylist(SERVICE, null, null, origin, tracks)
    }

    /** The signed-in account's Liked Songs, 100 per page. */
    private suspend fun liked(onProgress: (Int, Int?) -> Unit): ImportedPlaylist {
        val headers = signedIn()
        val tracks = mutableListOf<ImportedTrack>()
        var offset = 0
        while (true) {
            val body = buildJsonObject {
                putJsonObject("variables") { put("offset", offset); put("limit", PAGE) }
                put("operationName", "fetchLibraryTracks")
                putJsonObject("extensions") { putJsonObject("persistedQuery") { put("version", 1); put("sha256Hash", FETCH_LIBRARY_TRACKS) } }
            }
            val request = Request.Builder().url("$apiBase/pathfinder/v2/query")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .apply {
                    header("User-Agent", USER_AGENT); header("App-platform", "WebPlayer"); header("Accept", "application/json")
                    headers.forEach { (k, v) -> header(k, v) }
                }.build()
            val page = getJson(request).obj("data")?.obj("me")?.obj("library")?.obj("tracks") ?: break
            val items = page.arr("items").orEmpty()
            items.forEach { (it as? JsonObject)?.obj("track")?.obj("data")?.gqlTrack()?.let(tracks::add) }
            val total = page.long("totalCount")?.toInt()
            onProgress(tracks.size, total)
            offset += items.size
            if (items.isEmpty() || total == null || offset >= total) break
        }
        return ImportedPlaylist("Spotify Liked Songs", null, null, PlaylistOrigin(null, SERVICE, LIKED_SONGS), tracks)
    }

    private suspend fun getJson(request: Request): JsonObject = runInterruptible {
        http.newCall(request).execute().use { r ->
            if (r.code == 401) throw ImportError.NeedsLogin(SERVICE)
            if (!r.isSuccessful) throw IOException("Spotify HTTP ${r.code}")
            json.parseToJsonElement(r.body.string()) as JsonObject
        }
    }

    companion object {
        const val SERVICE = "Spotify"
        /** Import source for the signed-in account's Liked Songs (Settings → Import). */
        const val LIKED_SONGS = "spotify:collection:tracks"
        private const val EMBED_LIMIT = 100
        private const val PAGE = 100
        private const val FETCH_PLAYLIST_V1 = "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf"
        private const val FETCH_LIBRARY_TRACKS = "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private val URI_FORM = Regex("""^spotify:(track|album|playlist):([A-Za-z0-9]+)$""")
        private val WEB_FORM = Regex("""^https?://(?:open|play)\.spotify\.com/(?:intl-[a-z]{2}(?:-[a-z]{2})?/)?(track|album|playlist)/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        private val NEXT_DATA = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
    }
}

private fun JsonObject.obj(key: String) = this[key] as? JsonObject
private fun JsonObject.arr(key: String) = this[key] as? JsonArray
private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
