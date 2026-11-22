// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.untitled

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * untitled.stream's public page data and storage signing, as its web player uses them. No account: links are public;
 * a private or removed link answers 204 (page data) and is reported as [Gone]. Cloudflare in front of the site
 * challenges unknown user agents, so every request carries a desktop browser's.
 */
class UntitledClient(
    private val base: String = "https://untitled.stream",
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    class Gone(what: String) : IOException("untitled link is private or was removed: $what")

    private val projects = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, JsonObject>>()

    /** A project's page data, remembered for 10 minutes (each of its tracks would otherwise fetch it again). */
    suspend fun project(slug: String): JsonObject {
        projects[slug]?.let { (at, data) -> if (System.currentTimeMillis() - at < 600_000) return data }
        val data = page("/library/project/$slug", "routes/library.project.\$projectSlug").obj("project") ?: throw Gone(slug)
        projects[slug] = System.currentTimeMillis() to data
        return data
    }

    suspend fun track(slug: String): JsonObject =
        page("/library/track/$slug", "routes/library.track.\$trackSlug").obj("track") ?: throw Gone(slug)

    /** A short-lived signed URL for a storage object (`https://…/storage/v1/object/<bucket>/<path>`), and its lifetime. */
    suspend fun signedUrl(storageUrl: String): Pair<String, Long> {
        val rest = storageUrl.substringAfter("/storage/v1/object/", "").substringBefore('?')
        val bucket = rest.substringBefore('/')
        val path = rest.substringAfter('/', "")
        if (bucket.isEmpty() || path.isEmpty()) throw IOException("not a storage URL")
        // The path goes in as ONE encoded segment (slashes as %2F), as untitled's own player sends it.
        val url = "$base/api/storage/buckets".toHttpUrl().newBuilder()
            .addPathSegment(bucket).addPathSegment("objects").addPathSegment(path).addPathSegment("signedUrl")
            .addQueryParameter("durationInSeconds", "3600")
            .addQueryParameter("cacheBufferInSeconds", "300")
            .build()
        val body = get(url.toString(), json = true) ?: throw Gone(path)
        val o = json.parseToJsonElement(body) as JsonObject
        val signed = o.str("url") ?: throw IOException("no signed url")
        return signed to (o.long("expiresInMs") ?: 3_600_000)
    }

    /** Where an invite link leads (redirects are followed). */
    suspend fun followInvite(code: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$base/invite/$code").header("User-Agent", USER_AGENT).build()
        runInterruptible { http.newCall(request).execute().use { it.request.url.toString() } }
    }

    private suspend fun page(path: String, route: String): JsonObject {
        val url = "$base$path".toHttpUrl().newBuilder().addQueryParameter("_data", route).build()
        val body = get(url.toString(), json = false)?.takeIf { it.isNotBlank() } ?: throw Gone(path)
        return json.parseToJsonElement(body) as JsonObject
    }

    /** Null for "private or removed": 204, 404, or a redirect to the login page. */
    private suspend fun get(url: String, json: Boolean): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).apply { if (json) header("Content-Type", "application/json") }.build()
        runInterruptible {
            http.newCall(request).execute().use { r ->
                when {
                    // 401/403 here is a private or revoked link (the page data needs no account): gone, not an outage.
                    r.code == 204 || r.code == 401 || r.code == 403 || r.code == 404 || r.request.url.encodedPath.startsWith("/login") -> null
                    !r.isSuccessful -> throw IOException("untitled HTTP ${r.code}")
                    else -> r.body.string()
                }
            }
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}

internal fun JsonElement.obj(key: String) = (this as? JsonObject)?.get(key) as? JsonObject
internal fun JsonElement.str(key: String) = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonElement.long(key: String) = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.longOrNull
internal fun JsonElement.double(key: String) = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.content?.toDoubleOrNull()
