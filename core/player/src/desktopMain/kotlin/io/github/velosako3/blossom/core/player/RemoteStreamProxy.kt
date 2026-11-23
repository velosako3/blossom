// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.velosako3.blossom.source.api.StreamInfo
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A 127.0.0.1-only HTTP proxy between libVLC and the stream host. libVLC can only set User-Agent and Referer,
 * so the proxy adds every [StreamInfo.headers] entry, and it fetches upstream in [chunkBytes] ranges because some
 * googlevideo clients refuse longer reads. URLs carry a random per-session token, so other local programs can't
 * guess them.
 */
class RemoteStreamProxy(private val chunkBytes: Long = 1024L * 1024) {
    private val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val streams = ConcurrentHashMap<String, StreamInfo>()
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 16).apply {
        executor = Executors.newCachedThreadPool { r -> Thread(r, "blossom-stream-proxy").apply { isDaemon = true } }
        createContext("/r/") { ex -> runCatching { serve(ex) }.onFailure { runCatching { ex.sendResponseHeaders(502, -1) } }; ex.close() }
        start()
    }

    /** The last HTTP status the upstream host answered with (403/410 mean the URL expired). */
    @Volatile var lastUpstreamStatus: Int? = null
        private set

    fun register(stream: StreamInfo): String {
        val id = streams.size.toString() + "-" + System.nanoTime()
        if (streams.size > 64) streams.clear()
        streams[id] = stream
        return "http://127.0.0.1:${server.address.port}/r/$token/$id"
    }

    fun stop() = server.stop(0)

    private fun serve(ex: HttpExchange) {
        val parts = ex.requestURI.path.removePrefix("/r/").split('/')
        val stream = if (parts.size == 2 && parts[0] == token) streams[parts[1]] else null
        if (stream == null) { ex.sendResponseHeaders(404, -1); return }

        val range = Regex("""bytes=(\d+)-(\d*)""").find(ex.requestHeaders.getFirst("Range").orEmpty())
        var pos = range?.groupValues?.get(1)?.toLong() ?: 0L
        val requestedEnd = range?.groupValues?.get(2)?.toLongOrNull()

        // First chunk tells us the total size and whether the URL still works.
        val first = fetch(stream, pos, chunkEnd(pos, requestedEnd))
        lastUpstreamStatus = first.code
        if (first.code !in 200..299) { first.close(); ex.sendResponseHeaders(first.code, -1); return }
        val total = first.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: first.body.contentLength().takeIf { it >= 0 }
        val end = requestedEnd ?: total?.minus(1)
        ex.responseHeaders.add("Content-Type", first.header("Content-Type") ?: stream.mimeType)
        ex.responseHeaders.add("Accept-Ranges", "bytes")
        if (range != null && total != null && end != null) {
            ex.responseHeaders.add("Content-Range", "bytes $pos-$end/$total")
            if (ex.requestMethod == "HEAD") { first.close(); ex.sendResponseHeaders(206, -1); return }
            ex.sendResponseHeaders(206, end - pos + 1)
        } else {
            if (ex.requestMethod == "HEAD") { first.close(); ex.sendResponseHeaders(200, -1); return }
            ex.sendResponseHeaders(200, total ?: 0)
        }
        ex.responseBody.use { out ->
            var response = first
            while (true) {
                val n = response.body.byteStream().use { it.copyTo(out) }
                response.close()
                pos += n
                if (n == 0L || end == null || pos > end) break
                response = fetch(stream, pos, chunkEnd(pos, end))
                lastUpstreamStatus = response.code
                if (response.code !in 200..299) { response.close(); break }
            }
        }
    }

    private fun chunkEnd(pos: Long, end: Long?): Long = minOf(pos + chunkBytes - 1, end ?: Long.MAX_VALUE)

    private fun fetch(stream: StreamInfo, from: Long, to: Long): okhttp3.Response {
        val builder = okhttp3.Request.Builder().url(stream.url).header("Range", "bytes=$from-$to")
        stream.headers.forEach { (k, v) -> builder.header(k, v) }
        return http.newCall(builder.build()).execute()
    }
}
