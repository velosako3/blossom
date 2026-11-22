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

/** HLS read as one seekable file: init segment + media segments, sizes from HEAD requests. */
class HlsReaderTest {
    private val init = Random(1).nextBytes(700)
    private val segments = (0 until 5).map { Random(10 + it).nextBytes(40_000 + it * 1_000) }
    private val all = init + segments.reduce { a, b -> a + b }
    private val heads = java.util.concurrent.atomic.AtomicInteger()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            val path = ex.requestURI.path
            val body = when {
                path == "/media/master.m3u8" -> "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=160000\nplaylist.m3u8?sig=abc\n".toByteArray()
                path == "/media/playlist.m3u8" -> buildString {
                    append("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:10\n#EXT-X-MAP:URI=\"init.mp4?sig=abc\"\n")
                    segments.indices.forEach { append("#EXTINF:10.0,\nseg$it.m4s?sig=abc\n") }
                    append("#EXT-X-ENDLIST\n")
                }.toByteArray()
                path == "/media/init.mp4" -> init
                path.startsWith("/media/seg") -> segments[path.removePrefix("/media/seg").removeSuffix(".m4s").toInt()]
                else -> null
            }
            if (body == null) { ex.sendResponseHeaders(404, -1); ex.close(); return@createContext }
            if (ex.requestMethod == "HEAD") {
                heads.incrementAndGet()
                ex.responseHeaders.add("Content-Length", body.size.toString())
                ex.sendResponseHeaders(200, -1); ex.close(); return@createContext
            }
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        start()
    }
    private val url = "http://127.0.0.1:${server.address.port}/media/master.m3u8"

    @AfterTest fun stop() = server.stop(0)

    private fun reader() = HlsReader(StreamInfo(url, emptyMap(), "application/vnd.apple.mpegurl", 160, null))

    @Test fun readsTheWholeStreamInOrder() {
        val r = reader()
        assertEquals(all.size.toLong(), r.size())
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(9_000)
        while (true) { val n = r.read(buf, buf.size); if (n <= 0) break; out.write(buf, 0, n) }
        assertContentEquals(all, out.toByteArray())
        assertTrue(heads.get() >= segments.size)
        r.close()
    }

    @Test fun seeksIntoTheMiddleOfASegment() {
        val r = reader()
        val at = init.size + segments[0].size + segments[1].size + 1234L
        assertTrue(r.seek(at))
        val buf = ByteArray(500)
        var got = 0
        while (got < 500) { val tmp = ByteArray(500 - got); val n = r.read(tmp, tmp.size); tmp.copyInto(buf, got, 0, n); got += n }
        assertContentEquals(all.copyOfRange(at.toInt(), at.toInt() + 500), buf)
        r.close()
    }

    @Test fun segmentDurationsAndSlices() {
        val r = reader()
        assertEquals(50_000, r.durationMs)
        assertEquals(2, r.segmentAt(25_000))
        assertEquals(20_000, r.segmentStartMs(2))
        val tail = r.from(2)
        assertEquals((init.size + segments[2].size + segments[3].size + segments[4].size).toLong(), tail.size())
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(7_000)
        while (true) { val n = tail.read(buf, buf.size); if (n <= 0) break; out.write(buf, 0, n) }
        assertContentEquals(init + segments[2] + segments[3] + segments[4], out.toByteArray())
        r.close()
    }
}
