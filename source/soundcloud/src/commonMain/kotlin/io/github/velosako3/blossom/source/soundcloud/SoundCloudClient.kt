// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.soundcloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * SoundCloud's public web API (api-v2), as the website uses it: no account. The website's public `client_id` is read
 * from its script bundles and read again when the API stops accepting it.
 */
class SoundCloudClient(
    private val webBase: String = "https://soundcloud.com",
    private val apiBase: String = "https://api-v2.soundcloud.com",
    /** Where `on.soundcloud.com` short links live (tests point it at a fake). */
    private val shortBase: String = "https://on.soundcloud.com",
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val lock = Mutex()
    @Volatile private var clientId: String? = null

    /** The id to use. [rejected] is the one the API just refused, so concurrent callers refresh once, not each. */
    private suspend fun clientId(rejected: String?): String = lock.withLock {
        clientId?.let { if (it != rejected) return it }
        val home = text("$webBase/")
        val scripts = SCRIPT.findAll(home).map { it.groupValues[1] }.toList()
        // The id sits in one of the last bundles.
        val id = scripts.asReversed().firstNotNullOfOrNull { src -> CLIENT_ID.find(text(src))?.groupValues?.get(1) }
            ?: throw IOException("SoundCloud client id not found")
        clientId = id
        id
    }

    /** GET [path] on the API with the client id; retried once with a fresh id on 401/403. */
    suspend fun api(path: String, params: Map<String, String> = emptyMap()): JsonElement = withContext(Dispatchers.IO) {
        var rejected: String? = null
        while (true) {
            val id = clientId(rejected)
            val url = "$apiBase$path".toHttpUrl().newBuilder().apply {
                params.forEach { (k, v) -> addQueryParameter(k, v) }
                addQueryParameter("client_id", id)
            }.build()
            val (code, body) = get(url)
            if ((code == 401 || code == 403) && rejected == null) { rejected = id; continue }
            if (code == 404) throw NotFound(url.encodedPath)
            if (code !in 200..299) throw IOException("SoundCloud HTTP $code")
            return@withContext json.parseToJsonElement(body)
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    /** A transcoding URL as returned by the API (absolute), with the client id and the track's authorization. */
    suspend fun streamUrl(transcodingUrl: String, trackAuthorization: String?): String = withContext(Dispatchers.IO) {
        var rejected: String? = null
        while (true) {
            val id = clientId(rejected)
            val url = transcodingUrl.toHttpUrl().newBuilder().apply {
                addQueryParameter("client_id", id)
                trackAuthorization?.let { addQueryParameter("track_authorization", it) }
            }.build()
            val (code, body) = get(url)
            if ((code == 401 || code == 403) && rejected == null) { rejected = id; continue }
            if (code !in 200..299) throw IOException("SoundCloud stream HTTP $code")
            return@withContext json.parseToJsonElement(body).str("url") ?: throw IOException("no stream url")
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    /** Where an `on.soundcloud.com` short link points (one redirect, not followed further). */
    suspend fun followShortLink(code: String): String = withContext(Dispatchers.IO) {
        val noRedirects = http.newBuilder().followRedirects(false).build()
        val request = Request.Builder().url("$shortBase/$code").header("User-Agent", USER_AGENT).build()
        runInterruptible {
            noRedirects.newCall(request).execute().use { r -> r.header("Location") ?: throw IOException("short link did not redirect (${r.code})") }
        }
    }

    private suspend fun text(url: String): String = withContext(Dispatchers.IO) {
        val (code, body) = get(url.toHttpUrl())
        if (code !in 200..299) throw IOException("SoundCloud HTTP $code for $url")
        body
    }

    private suspend fun get(url: HttpUrl): Pair<Int, String> = runInterruptible {
        http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { it.code to it.body.string() }
    }

    class NotFound(path: String) : IOException("not found: $path")

    private companion object {
        val SCRIPT = Regex("""<script crossorigin src="([^"]+\.js)"""")
        val CLIENT_ID = Regex("""client_id\s*:\s*"([A-Za-z0-9]{32})"""")
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
