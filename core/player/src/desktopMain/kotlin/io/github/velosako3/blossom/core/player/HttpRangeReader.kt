// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.source.api.StreamInfo
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Reads a remote stream as a seekable byte source for libVLC (which then does no networking of its own). Every
 * [StreamInfo.headers] entry is sent, and upstream is read in [chunkBytes] ranges because some googlevideo clients
 * refuse longer reads. Not thread-safe; libVLC calls it from one input thread.
 */
class HttpRangeReader(
    private val stream: StreamInfo,
    private val chunkBytes: Long = 1024L * 1024,
    private val http: OkHttpClient = sharedClient,
) {
    private var position = 0L
    private var total: Long? = null
    private var response: Response? = null
    private var body: InputStream? = null
    private var chunkEnd = -1L

    /** The last upstream HTTP status (403/410 mean the URL expired). */
    @Volatile var lastStatus: Int? = null
        private set

    fun size(): Long {
        total?.let { return it }
        if (!openChunk()) return 0
        return total ?: 0
    }

    /** Reads up to [length] bytes into [buffer]; returns 0 at the end, -1 on an upstream error. */
    fun read(buffer: ByteArray, length: Int): Int {
        while (true) {
            total?.let { if (position >= it) return 0 }
            if (body == null || position > chunkEnd) {
                closeChunk()
                if (!openChunk()) return if (lastStatus in 200..299 || lastStatus == 416) 0 else -1
            }
            val n = body!!.read(buffer, 0, minOf(length.toLong(), chunkEnd - position + 1).toInt())
            if (n > 0) { position += n; return n }
            closeChunk()
            if (total != null && position >= total!!) return 0
            if (n < 0 && position <= chunkEnd) return -1
        }
    }

    fun seek(offset: Long): Boolean {
        if (offset == position && body != null) return true
        closeChunk()
        position = offset
        return true
    }

    fun close() = closeChunk()

    private fun openChunk(): Boolean {
        val end = (position + chunkBytes - 1).let { e -> total?.let { minOf(e, it - 1) } ?: e }
        val request = okhttp3.Request.Builder().url(stream.url).header("Range", "bytes=$position-$end")
        stream.headers.forEach { (k, v) -> request.header(k, v) }
        val r = runCatching { http.newCall(request.build()).execute() }.getOrElse { lastStatus = null; return false }
        lastStatus = r.code
        if (r.code !in 200..299) { r.close(); return false }
        if (r.code == 200) {
            // Server ignored the range: the whole file follows.
            total = r.body.contentLength().takeIf { it >= 0 }
            if (position > 0) r.body.byteStream().skip(position)
            chunkEnd = (total ?: Long.MAX_VALUE) - 1
        } else {
            val cr = r.header("Content-Range")
            total = cr?.substringAfter('/')?.toLongOrNull() ?: total
            chunkEnd = cr?.substringAfter("bytes ")?.substringBefore('/')?.substringAfter('-')?.toLongOrNull() ?: end
        }
        response = r
        body = r.body.byteStream()
        return true
    }

    private fun closeChunk() {
        runCatching { response?.close() }
        response = null
        body = null
        chunkEnd = -1
    }

    private companion object {
        val sharedClient: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }
}
