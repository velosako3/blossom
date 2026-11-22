// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.backends

import io.github.velosako3.blossom.feature.sync.RemoteFile
import io.github.velosako3.blossom.feature.sync.SyncBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Instant

/**
 * A WebDAV folder (Nextcloud, ownCloud, a NAS) with basic auth; the password is read from the secure store each time.
 * Listing walks collections with `PROPFIND Depth: 1` (many servers refuse `infinity`); writes create missing parents
 * with `MKCOL` first.
 */
class WebDavBackend(
    baseUrl: String,
    private val username: String,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build(),
    private val password: suspend () -> String?,
) : SyncBackend {
    private val base: HttpUrl = baseUrl.let { if (it.endsWith("/")) it else "$it/" }.toHttpUrl()
    override val id = "webdav:$base"
    private val knownCollections = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private suspend fun auth() = Credentials.basic(username, password() ?: throw SignInRequired())

    private fun url(path: String): HttpUrl = base.newBuilder().addPathSegments(path.trim('/')).build()

    private suspend fun call(request: Request.Builder): okhttp3.Response {
        val r = request.header("Authorization", auth()).build()
        val response = runInterruptible { http.newCall(r).execute() }
        if (response.code == 401) { response.close(); throw SignInRequired() }
        return response
    }

    override suspend fun list(prefix: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        val dir = prefix.trim('/').substringBeforeLast('/', "").let { if (prefix.endsWith("/") || '.' !in prefix.substringAfterLast('/')) prefix.trim('/') else it }
        val out = mutableListOf<RemoteFile>()
        val queue = ArrayDeque(listOf(dir))
        while (queue.isNotEmpty()) {
            val d = queue.removeFirst()
            val collection = if (d.isEmpty()) base else base.newBuilder().addPathSegments(d).addPathSegment("").build()
            val body = call(Request.Builder().url(collection).method("PROPFIND", PROPS.toRequestBody(XML)).header("Depth", "1")).use { r ->
                if (r.code == 404) return@use null
                if (r.code != 207) throw IOException("PROPFIND HTTP ${r.code}")
                r.body.string()
            } ?: continue
            for (entry in parse(body)) {
                val rel = relative(entry.href) ?: continue
                if (rel.trimEnd('/') == d) continue
                if (entry.collection) queue += rel.trimEnd('/')
                else if (rel.startsWith(prefix.trimStart('/'))) out += RemoteFile(rel, entry.size, entry.modified, entry.etag)
            }
        }
        out
    }

    override suspend fun read(path: String): ByteArray = withContext(Dispatchers.IO) {
        call(Request.Builder().url(url(path))).use { r ->
            if (r.code == 404) throw FileNotFoundException(path)
            if (!r.isSuccessful) throw IOException("GET HTTP ${r.code}")
            r.body.bytes()
        }
    }

    override suspend fun write(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        // Parents first: MKCOL each missing collection (405 = it already exists).
        val parts = path.trim('/').split('/').dropLast(1)
        for (i in parts.indices) {
            val dir = parts.take(i + 1).joinToString("/")
            if (dir in knownCollections) continue
            call(Request.Builder().url(base.newBuilder().addPathSegments(dir).addPathSegment("").build()).method("MKCOL", null)).use { r ->
                if (r.code != 201 && r.code != 405 && r.code != 301) throw IOException("MKCOL $dir HTTP ${r.code}")
            }
            knownCollections += dir
        }
        call(Request.Builder().url(url(path)).put(bytes.toRequestBody(OCTETS))).use { r ->
            if (!r.isSuccessful) throw IOException("PUT HTTP ${r.code}")
        }
        Unit
    }

    /** A server href (absolute path or URL) as a path relative to the base folder; null when outside it. */
    private fun relative(href: String): String? {
        val path = URLDecoder.decode(if (href.startsWith("http")) href.toHttpUrl().encodedPath else href, Charsets.UTF_8)
        val root = URLDecoder.decode(base.encodedPath, Charsets.UTF_8)
        return if (path.startsWith(root)) path.removePrefix(root) else null
    }

    private class Entry(val href: String, val collection: Boolean, val size: Long, val etag: String?, val modified: Instant?)

    private fun parse(xml: String): List<Entry> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            .newDocumentBuilder().parse(xml.byteInputStream())
        val responses = doc.getElementsByTagNameNS("DAV:", "response")
        return (0 until responses.length).map { i ->
            val r = responses.item(i) as org.w3c.dom.Element
            fun text(name: String) = r.getElementsByTagNameNS("DAV:", name).item(0)?.textContent?.trim()
            Entry(
                href = text("href").orEmpty(),
                collection = r.getElementsByTagNameNS("DAV:", "collection").length > 0,
                size = text("getcontentlength")?.toLongOrNull() ?: 0,
                etag = text("getetag"),
                modified = null,
            )
        }
    }

    private companion object {
        val XML = "application/xml; charset=utf-8".toMediaType()
        val OCTETS = "application/octet-stream".toMediaType()
        const val PROPS = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getetag/><d:getlastmodified/></d:prop></d:propfind>"""
    }
}
