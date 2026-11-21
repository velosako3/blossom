// SPDX-License-Identifier: GPL-3.0-only
// Ported from BitChord (https://github.com/kushagrasinghx/BitChord) v1.9.1,
// shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/Innertube.kt (GPL-3.0).
// Modified for blossom, 2026: an injectable class instead of a global object; only search, browse and next
// (radio) are kept; the optional sign-in uses the cookie the caller supplies (SAPISID auth as upstream).
package io.github.velosako3.blossom.source.youtubemusic

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

/** YouTube Music's internal web API (WEB_REMIX), as its own web player calls it. */
class InnertubeClient(
    private val cookie: suspend () -> String? = { null },
    private val language: () -> String = { Locale.getDefault().language.ifBlank { "en" } },
    private val region: () -> String = { Locale.getDefault().country.ifBlank { "US" } },
    http: HttpClient? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = http ?: HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }
        expectSuccess = true
    }

    @Volatile var visitorData: String? = null
        private set

    suspend fun search(query: String, params: String? = null): JsonObject = post("search") {
        put("query", query)
        params?.let { put("params", it) }
    }

    suspend fun searchContinuation(token: String): JsonObject =
        post("search", mapOf("ctoken" to token, "continuation" to token, "type" to "next")) { put("continuation", token) }

    suspend fun browse(browseId: String, params: String? = null): JsonObject = post("browse") {
        put("browseId", browseId)
        params?.let { put("params", it) }
    }

    suspend fun browseContinuation(token: String): JsonObject =
        post("browse", mapOf("ctoken" to token, "continuation" to token, "type" to "next")) { put("continuation", token) }

    /** The "RDAMVM" radio mix YouTube Music would play after [videoId]. */
    suspend fun next(videoId: String): JsonObject = post("next") {
        put("videoId", videoId)
        put("playlistId", "RDAMVM$videoId")
        put("isAudioOnly", true)
    }

    suspend fun nextPlaylist(playlistId: String, videoId: String? = null): JsonObject = post("next") {
        videoId?.let { put("videoId", it) }
        put("playlistId", playlistId)
        put("isAudioOnly", true)
    }

    /** A guest visitor ID, minted once from `sw.js_data` like YouTube's own service worker does. */
    suspend fun ensureVisitorData(): String? {
        visitorData?.let { return it }
        return runCatching {
            val body = client.get("https://www.youtube.com/sw.js_data") { header("User-Agent", WEB_USER_AGENT) }.bodyAsText()
            findVisitorData(Json.parseToJsonElement(body.substringAfter("\n", body.drop(5))))
        }.getOrNull()?.also { visitorData = it }
    }

    private fun findVisitorData(element: JsonElement): String? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull { findVisitorData(it) }
        is JsonPrimitive -> element.contentOrNull?.takeIf { VISITOR_DATA.matches(it) }
        else -> null
    }

    private suspend fun post(
        endpoint: String,
        query: Map<String, String> = emptyMap(),
        body: JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val cookie = cookie()
        val hl = language()
        val gl = region()
        val response = withRetry {
            client.post("$MUSIC_BASE/$endpoint") {
                contentType(ContentType.Application.Json)
                parameter("prettyPrint", "false")
                parameter("hl", hl)
                query.forEach { (k, v) -> parameter(k, v) }
                header("User-Agent", WEB_USER_AGENT)
                header("Accept-Language", hl)
                header("X-Origin", MUSIC_ORIGIN)
                header("Origin", MUSIC_ORIGIN)
                header("Referer", "$MUSIC_ORIGIN/")
                header("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
                header("X-YouTube-Client-Version", WEB_REMIX_VERSION)
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
                cookie?.let { c ->
                    header("Cookie", c)
                    header("X-Goog-AuthUser", "0")
                    sapisidFrom(c)?.let { header("Authorization", sapisidHash(it)) }
                }
                setBody(
                    buildJsonObject {
                        putJsonObject("context") {
                            putJsonObject("client") {
                                put("clientName", "WEB_REMIX")
                                put("clientVersion", WEB_REMIX_VERSION)
                                put("hl", hl)
                                put("gl", gl)
                                visitorData?.let { put("visitorData", it) }
                            }
                            putJsonObject("user") { put("lockedSafetyMode", false) }
                            putJsonObject("request") { put("useSsl", true) }
                        }
                        body()
                    },
                )
            }.body<JsonObject>()
        }
        if (visitorData == null) {
            visitorData = response["responseContext"]?.jsonObject?.get("visitorData")?.jsonPrimitive?.contentOrNull
        }
        return response
    }

    /** Transport failures are retried with backoff; HTTP errors and timeouts are answers and are not. */
    private suspend fun <T> withRetry(attempts: Int = 3, block: suspend () -> T): T {
        var backoff = 500L
        repeat(attempts - 1) {
            try {
                return block()
            } catch (e: HttpRequestTimeoutException) {
                throw e
            } catch (_: IOException) {
            }
            delay(backoff)
            backoff *= 2
        }
        return block()
    }

    private fun sapisidFrom(cookieHeader: String): String? {
        val jar = cookieHeader.split(';').mapNotNull { e ->
            val name = e.substringBefore('=').trim()
            val value = e.substringAfter('=', "").trim()
            if (name.isEmpty() || value.isEmpty()) null else name to value
        }.toMap()
        return listOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID").firstNotNullOfOrNull { jar[it] }
    }

    private fun sapisidHash(sapisid: String): String {
        val ts = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1").digest("$ts $sapisid $MUSIC_ORIGIN".toByteArray())
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
        return "SAPISIDHASH ${ts}_$digest"
    }

    companion object {
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
        private const val MUSIC_BASE = "https://music.youtube.com/youtubei/v1"
        private const val MUSIC_ORIGIN = "https://music.youtube.com"
        private const val WEB_REMIX_VERSION = "1.20250101.01.00"
        private const val WEB_REMIX_CLIENT_ID = "67"
        private val VISITOR_DATA = Regex("""Cg[A-Za-z0-9_%-]{40,}""")
    }
}
