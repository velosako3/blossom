// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs

/**
 * LRCLIB alone, over Ktor: the lyrics source on iOS (BitChord's race of sources is JVM-only for now). Exact match by
 * title, artist and length first, then a search whose hits must be the same song by the same artist, closest length.
 */
class LrcLibLyrics(private val http: HttpClient = HttpClient()) : OnlineLyrics {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun lyrics(q: LyricsQuery): Lyrics? {
        val artist = q.artists.map(::lyricsArtist).distinct().joinToString(", ")
        val seconds = q.durationMs / 1000
        val exact = get("$BASE/get") { parameter("track_name", q.title); parameter("artist_name", artist); parameter("duration", seconds) }
            ?.let { json.parseToJsonElement(it) as? JsonObject }?.text("syncedLyrics")
        val synced = exact ?: get("$BASE/search") { parameter("track_name", q.title); parameter("artist_name", artist) }
            ?.let { json.parseToJsonElement(it) as? JsonArray }.orEmpty().mapNotNull { it as? JsonObject }
            .filter { it.text("syncedLyrics") != null && sameSong(it.text("trackName").orEmpty(), it.text("artistName").orEmpty(), q.title, artist) }
            .minByOrNull { abs((it["duration"] as? JsonPrimitive)?.doubleOrNull?.minus(seconds) ?: 1e9) }?.text("syncedLyrics")
        return synced?.let { LrcParser.parse(it, PROVIDER, q.durationMs) }
    }

    private suspend fun get(url: String, block: io.ktor.client.request.HttpRequestBuilder.() -> Unit): String? = runCatching {
        http.get(url) { header("User-Agent", AGENT); block() }.takeIf { it.status.isSuccess() }?.bodyAsText()
    }.getOrNull()

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        private const val BASE = "https://lrclib.net/api"
        private const val AGENT = "blossom (https://github.com/velosako3/blossom)"
        private const val PROVIDER = "LRCLIB"

        /** The hit's name is the song's (brackets, case and punctuation aside) and its artist is among the artists. */
        fun sameSong(hitTitle: String, hitArtist: String, title: String, artist: String): Boolean {
            fun norm(s: String) = s.lowercase().replace(Regex("""[(\[][^)\]]*[)\]]"""), " ").replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
            val a = norm(hitArtist)
            return norm(hitTitle) == norm(title) && artist.split(',', '&').map(::norm).any { it.isNotEmpty() && (a == it || a.contains(it) || it.contains(a)) }
        }
    }
}
