// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import com.sun.net.httpserver.HttpServer
import io.github.velosako3.blossom.source.api.StreamInfo
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteStreamProxyTest {
    private val body = Random(1).nextBytes(300_000)
    private val seenRanges = mutableListOf<String?>()
    private val seenUa = mutableListOf<String?>()
    private val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/media") { ex ->
            seenRanges += ex.requestHeaders.getFirst("Range")
            seenUa += ex.requestHeaders.getFirst("User-Agent")
            val (a, b) = Regex("""bytes=(\d+)-(\d*)""").find(ex.requestHeaders.getFirst("Range") ?: "bytes=0-")!!.destructured
            val start = a.toInt(); val end = (b.toIntOrNull() ?: body.lastIndex).coerceAtMost(body.lastIndex)
            ex.responseHeaders.add("Content-Type", "audio/webm")
            ex.responseHeaders.add("Content-Range", "bytes $start-$end/${body.size}")
            ex.sendResponseHeaders(206, (end - start + 1).toLong())
            ex.responseBody.use { it.write(body, start, end - start + 1) }
        }
        start()
    }
    private val proxy = RemoteStreamProxy(chunkBytes = 64 * 1024)

    @AfterTest fun stop() { upstream.stop(0); proxy.stop() }

    @Test fun forwardsHeadersAndChunksUpstream() {
        val url = proxy.register(StreamInfo("http://127.0.0.1:${upstream.address.port}/media", mapOf("User-Agent" to "blossom-test"), "audio/webm", null, null))
        assertTrue(url.startsWith("http://127.0.0.1:"))
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.setRequestProperty("Range", "bytes=1000-")
        assertEquals(206, conn.responseCode)
        assertEquals("bytes 1000-299999/300000", conn.getHeaderField("Content-Range"))
        val got = conn.inputStream.use { it.readBytes() }
        assertContentEquals(body.copyOfRange(1000, body.size), got)
        assertTrue(seenRanges.size > 1, "upstream fetched in chunks: $seenRanges")
        assertTrue(seenUa.all { it == "blossom-test" })
    }

    @Test fun upstreamRefusalIsPassedOn() {
        val url = proxy.register(StreamInfo("http://127.0.0.1:${upstream.address.port}/missing", emptyMap(), "audio/webm", null, null))
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        assertEquals(404, conn.responseCode)
        assertEquals(404, proxy.lastUpstreamStatus)
    }
}
