// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.source.api.StreamInfo
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * An HLS stream (SoundCloud serves HLS) read as one seekable file for libVLC's callback media: the init segment
 * (`#EXT-X-MAP`) followed by every media segment. Fragmented MP4 and MPEG-TS segments both concatenate into a valid
 * stream. Segment sizes come from HEAD requests up front, so byte seeks map exactly onto segments. libVLC's own HTTP
 * client can't be used inside the JVM (see [HttpRangeReader]).
 */
class HlsReader private constructor(
    private val stream: StreamInfo,
    private val http: OkHttpClient,
    private val playlist: Playlist,
    /** First media segment this reader serves (after the init segment); see [from]. */
    private val firstSegment: Int,
) {
    constructor(stream: StreamInfo, http: OkHttpClient = HttpRangeReader.sharedClient) : this(stream, http, load(stream, http), 0)

    /** Init segment (if any) and media segments, with their sizes and durations. */
    private class Playlist(val init: String?, val initSize: Long, val segments: List<String>, val sizes: List<Long>, val durationsMs: List<Long>)

    private val parts: List<String> = listOfNotNull(playlist.init) + playlist.segments.drop(firstSegment)
    private val starts: LongArray
    private val total: Long
    private var position = 0L
    private var cachedIndex = -1
    private var cached = ByteArray(0)

    init {
        val sizes = listOfNotNull(playlist.init?.let { playlist.initSize }) + playlist.sizes.drop(firstSegment)
        starts = LongArray(parts.size + 1).also { s -> for (i in parts.indices) s[i + 1] = s[i] + sizes[i] }
        total = starts[parts.size]
    }

    /** The whole stream's length, from the playlist. */
    val durationMs: Long get() = playlist.durationsMs.sum()

    /** The media segment playing at [ms] of the whole stream. */
    fun segmentAt(ms: Long): Int {
        var t = 0L
        playlist.durationsMs.forEachIndexed { i, d -> if (ms < t + d) return i; t += d }
        return playlist.durationsMs.lastIndex.coerceAtLeast(0)
    }

    fun segmentStartMs(segment: Int): Long = playlist.durationsMs.take(segment).sum()

    /** The stream from media segment [segment] on (init segment first), for seeking: libVLC can't seek it by bytes. */
    fun from(segment: Int): HlsReader = HlsReader(stream, http, playlist, segment)

    fun size(): Long = total

    fun read(buffer: ByteArray, length: Int): Int {
        if (position >= total) return 0
        val index = partAt(position)
        val bytes = try { segment(index) } catch (e: IOException) { return -1 }
        val within = (position - starts[index]).toInt()
        val n = minOf(length, bytes.size - within)
        if (n <= 0) return -1
        bytes.copyInto(buffer, 0, within, within + n)
        position += n
        return n
    }

    fun seek(offset: Long): Boolean {
        if (offset < 0 || offset > total) return false
        position = offset
        return true
    }

    fun close() { cached = ByteArray(0); cachedIndex = -1 }

    private fun partAt(pos: Long): Int {
        var lo = 0
        var hi = parts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= pos) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun segment(index: Int): ByteArray {
        if (index == cachedIndex) return cached
        cached = get(parts[index]).also { cachedIndex = index }
        return cached
    }

    private fun get(url: String): ByteArray = fetch(http, stream, url)

    private companion object {
        val MAP_URI = Regex("""URI="([^"]+)"""")
        val EXTINF = Regex("""#EXTINF:([0-9.]+)""")

        fun load(stream: StreamInfo, http: OkHttpClient): Playlist {
            var current = stream.url
            repeat(3) {
                val lines = String(fetch(http, stream, current)).lines().map(String::trim).filter(String::isNotEmpty)
                val variant = lines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF") }
                if (variant >= 0) {
                    current = resolve(current, lines.drop(variant + 1).first { !it.startsWith("#") })
                    return@repeat
                }
                val init = lines.firstOrNull { it.startsWith("#EXT-X-MAP") }?.let { MAP_URI.find(it)?.groupValues?.get(1) }?.let { resolve(current, it) }
                val segments = mutableListOf<String>()
                val durations = mutableListOf<Long>()
                var pending = 0L
                for (line in lines) {
                    EXTINF.find(line)?.let { pending = (it.groupValues[1].toDouble() * 1000).toLong() }
                    if (!line.startsWith("#")) { segments += resolve(current, line); durations += pending; pending = 0 }
                }
                if (segments.isEmpty()) throw IOException("empty HLS playlist")
                val sizes = sizesOf(http, stream, listOfNotNull(init) + segments)
                return Playlist(init, if (init != null) sizes.first() else 0, segments, if (init != null) sizes.drop(1) else sizes, durations)
            }
            throw IOException("HLS playlist nesting too deep")
        }

        fun sizesOf(http: OkHttpClient, stream: StreamInfo, urls: List<String>): List<Long> {
            val pool = Executors.newFixedThreadPool(minOf(8, urls.size))
            try {
                return pool.invokeAll(urls.map { u -> Callable { head(http, stream, u) } }).map { it.get() }
            } finally {
                pool.shutdown()
            }
        }

        fun head(http: OkHttpClient, stream: StreamInfo, url: String): Long {
            val request = Request.Builder().url(url).head().apply { stream.headers.forEach { (k, v) -> header(k, v) } }.build()
            http.newCall(request).execute().use { r ->
                r.header("Content-Length")?.toLongOrNull()?.takeIf { r.isSuccessful && it >= 0 }?.let { return it }
            }
            // No usable HEAD: ask for one byte and read the total from Content-Range.
            val ranged = Request.Builder().url(url).header("Range", "bytes=0-0").apply { stream.headers.forEach { (k, v) -> header(k, v) } }.build()
            http.newCall(ranged).execute().use { r ->
                return r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: throw IOException("segment size unknown: $url")
            }
        }

        fun fetch(http: OkHttpClient, stream: StreamInfo, url: String): ByteArray {
            val request = Request.Builder().url(url).apply { stream.headers.forEach { (k, v) -> header(k, v) } }.build()
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HLS HTTP ${r.code}")
                return r.body.bytes()
            }
        }

        fun resolve(base: String, ref: String): String = base.toHttpUrl().resolve(ref)?.toString() ?: ref
    }
}
