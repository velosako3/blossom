// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import com.sun.net.httpserver.HttpServer
import io.github.velosako3.blossom.source.api.StreamInfo
import java.net.InetSocketAddress
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpRangeReaderTest {
    private val body = Random(1).nextBytes(300_000)
    private val ranges = mutableListOf<String?>()
    private val agents = mutableListOf<String?>()
    private val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/media") { ex ->
            ranges += ex.requestHeaders.getFirst("Range"); agents += ex.requestHeaders.getFirst("User-Agent")
            val (a, b) = Regex("""bytes=(\d+)-(\d*)""").find(ex.requestHeaders.getFirst("Range") ?: "bytes=0-")!!.destructured
            val start = a.toInt(); val end = (b.toIntOrNull() ?: body.lastIndex).coerceAtMost(body.lastIndex)
            ex.responseHeaders.add("Content-Range", "bytes $start-$end/${body.size}")
            ex.sendResponseHeaders(206, (end - start + 1).toLong())
            ex.responseBody.use { it.write(body, start, end - start + 1) }
        }
        createContext("/gone") { ex -> ex.sendResponseHeaders(403, -1); ex.close() }
        start()
    }
    private fun url(path: String) = "http://127.0.0.1:${upstream.address.port}$path"

    @AfterTest fun stop() = upstream.stop(0)

    private fun HttpRangeReader.readAll(): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(10_000)
        while (true) { val n = read(buf, buf.size); if (n <= 0) break; out.write(buf, 0, n) }
        return out.toByteArray()
    }

    @Test fun readsWholeStreamInChunksWithHeaders() {
        val r = HttpRangeReader(StreamInfo(url("/media"), mapOf("User-Agent" to "blossom-test"), "audio/webm", null, null), chunkBytes = 64 * 1024)
        assertEquals(300_000L, r.size())
        assertContentEquals(body, r.readAll())
        assertTrue(ranges.size >= 5, "$ranges")
        assertTrue(agents.all { it == "blossom-test" })
    }

    @Test fun seekThenRead() {
        val r = HttpRangeReader(StreamInfo(url("/media"), emptyMap(), "audio/webm", null, null), chunkBytes = 64 * 1024)
        r.read(ByteArray(1000), 1000)
        r.seek(250_000)
        assertContentEquals(body.copyOfRange(250_000, body.size), r.readAll())
    }

    @Test fun refusalIsReported() {
        val r = HttpRangeReader(StreamInfo(url("/gone"), emptyMap(), "audio/webm", null, null))
        assertEquals(-1, r.read(ByteArray(10), 10))
        assertEquals(403, r.lastStatus)
    }
}
