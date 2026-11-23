// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.scrobble

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest

class LastFmException(val code: Int, message: String) : IOException("Last.fm $code: $message")

/**
 * Last.fm API 2.0 (the protocol BitChord's client speaks, written for OkHttp): signed POSTs, `api_sig` = md5 of the
 * sorted parameters plus the shared secret. Sign-in is web auth: [token] → [authUrl] in the browser → [session].
 */
class LastFmClient(
    private val apiKey: String,
    private val secret: String,
    private val sessionKey: suspend () -> String?,
    private val http: OkHttpClient = OkHttpClient(),
    private val endpoint: String = "https://ws.audioscrobbler.com/2.0/",
) : ScrobbleService {
    override val id = "lastfm"
    @Volatile var hasSession = false
    override val enabled get() = apiKey.isNotEmpty() && hasSession

    private val json = Json { ignoreUnknownKeys = true }

    private fun sign(params: Map<String, String>): String {
        val text = params.toSortedMap().entries.joinToString("") { it.key + it.value } + secret
        return MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private suspend fun call(method: String, params: Map<String, String>, signed: Boolean = true): JsonObject {
        val all = params + mapOf("method" to method, "api_key" to apiKey)
        val form = FormBody.Builder().apply {
            all.forEach { (k, v) -> add(k, v) }
            if (signed) add("api_sig", sign(all))
            add("format", "json")
        }.build()
        val request = Request.Builder().url(endpoint).post(form).header("User-Agent", "blossom (https://github.com/velosako3/blossom)").build()
        val body = runInterruptible(Dispatchers.IO) { http.newCall(request).execute().use { it.body.string() } }
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse { throw IOException("Last.fm: unreadable answer") }
        obj["error"]?.let { throw LastFmException(it.jsonPrimitive.int, obj["message"]?.jsonPrimitive?.content.orEmpty()) }
        return obj
    }

    private suspend fun session() = sessionKey() ?: throw LastFmException(9, "not signed in")

    suspend fun token(): String = call("auth.getToken", emptyMap())["token"]!!.jsonPrimitive.content

    fun authUrl(token: String) = "https://www.last.fm/api/auth/?api_key=$apiKey&token=$token"

    /** After the user approved [token] in the browser: the session key and user name. */
    suspend fun session(token: String): Pair<String, String> {
        val s = call("auth.getSession", mapOf("token" to token))["session"]!!.jsonObject
        return s["key"]!!.jsonPrimitive.content to s["name"]!!.jsonPrimitive.content
    }

    private fun ScrobbleTrack.params(suffix: String = "") = buildMap {
        put("artist$suffix", artist); put("track$suffix", title)
        album?.let { put("album$suffix", it) }
        if (durationMs > 0) put("duration$suffix", (durationMs / 1000).toString())
    }

    override suspend fun nowPlaying(track: ScrobbleTrack) {
        call("track.updateNowPlaying", track.params() + ("sk" to session()))
    }

    override suspend fun scrobble(item: Scrobble) {
        call("track.scrobble", item.track.params("[0]") + mapOf("timestamp[0]" to item.startedAt.epochSeconds.toString(), "sk" to session()))
    }
}
