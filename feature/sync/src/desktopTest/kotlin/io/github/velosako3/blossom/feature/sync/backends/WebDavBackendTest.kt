// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.backends

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A minimal WebDAV server: collections must exist before PUT (409 otherwise), PROPFIND Depth 1, basic auth. */
class FakeDav : Dispatcher() {
    val files = ConcurrentHashMap<String, ByteArray>()
    val collections = ConcurrentHashMap.newKeySet<String>().apply { add("/dav/") }
    val methods = mutableListOf<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.getHeader("Authorization") != okhttp3.Credentials.basic("me", "s3cret")) return MockResponse().setResponseCode(401)
        val path = request.requestUrl!!.encodedPath.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
        synchronized(methods) { methods += "${request.method} $path" }
        fun parentOf(p: String) = p.trimEnd('/').substringBeforeLast('/') + "/"
        return when (request.method) {
            "MKCOL" -> if (path.trimEnd('/') + "/" in collections) MockResponse().setResponseCode(405)
                else if (parentOf(path) !in collections) MockResponse().setResponseCode(409)
                else { collections += path.trimEnd('/') + "/"; MockResponse().setResponseCode(201) }
            "PUT" -> if (parentOf(path) !in collections) MockResponse().setResponseCode(409) else { files[path] = request.body.readByteArray(); MockResponse().setResponseCode(201) }
            "GET" -> files[path]?.let { MockResponse().setBody(okio.Buffer().write(it)) } ?: MockResponse().setResponseCode(404)
            "PROPFIND" -> {
                val dir = path.trimEnd('/') + "/"
                if (dir !in collections) return MockResponse().setResponseCode(404)
                val children = (collections.filter { it != dir && parentOf(it) == dir }.map { it to null } +
                    files.filterKeys { parentOf(it) == dir }.map { (k, v) -> k to v.size })
                val body = buildString {
                    append("""<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">""")
                    append("""<d:response><d:href>$dir</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>""")
                    children.forEach { (href, size) ->
                        append("<d:response><d:href>${href.replace(" ", "%20")}</d:href><d:propstat><d:prop>")
                        if (size == null) append("<d:resourcetype><d:collection/></d:resourcetype>")
                        else append("<d:resourcetype/><d:getcontentlength>$size</d:getcontentlength><d:getetag>\"e$size\"</d:getetag>")
                        append("</d:prop></d:propstat></d:response>")
                    }
                    append("</d:multistatus>")
                }
                MockResponse().setResponseCode(207).setBody(body)
            }
            else -> MockResponse().setResponseCode(405)
        }
    }
}

class WebDavBackendTest {
    private val dav = FakeDav()
    private val server = MockWebServer().apply { dispatcher = dav; start() }
    private val backend = WebDavBackend(server.url("/dav/").toString(), "me") { "s3cret" }

    @AfterTest fun stop() = server.shutdown()

    @Test fun createsParentsThenPutsListsAndGets(): Unit = runBlocking {
        backend.write("blossom/v1/devices/A/manifest.json", "{}".toByteArray())
        backend.write("blossom/v1/devices/A/ops-0.jsonl", "x\n".toByteArray())
        backend.write("blossom/v1/devices/B/manifest.json", "{\"b\":1}".toByteArray())
        assertTrue(dav.methods.contains("MKCOL /dav/blossom/v1/devices/A/"), dav.methods.toString())
        assertEquals(1, dav.methods.count { it == "MKCOL /dav/blossom/" }, "existing collections aren't created again")
        val listed = backend.list("blossom/v1/devices").map { it.path }.sorted()
        assertEquals(listOf("blossom/v1/devices/A/manifest.json", "blossom/v1/devices/A/ops-0.jsonl", "blossom/v1/devices/B/manifest.json"), listed)
        assertEquals("{\"b\":1}", String(backend.read("blossom/v1/devices/B/manifest.json")))
        assertEquals(2L, backend.list("blossom/v1/devices/A").first { it.path.endsWith("ops-0.jsonl") }.sizeBytes)
    }
}
