// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.core.platform.InMemorySecureStore
import io.github.velosako3.blossom.source.api.AudioQuality
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.time.measureTimedValue

/** How long a song takes to resolve, the first time and again. Live: -PliveTests=true. */
class StreamTimingLiveTest {
    @Test fun resolveTimes() = runBlocking {
        val source = YtmSource(InMemorySecureStore(), io.github.velosako3.blossom.source.youtubemusic.YouTubeStreams({ null }, Files.createTempDirectory("ytm").toFile()))
        val ids = listOf("Blinding Lights The Weeknd", "As It Was Harry Styles", "Around the World Daft Punk", "Runaway Kanye West", "Bohemian Rhapsody Queen")
            .map { q -> measureTimedValue { source.search(q).songs.first() }.also { println("timing search '$q': ${it.duration.inWholeMilliseconds} ms") }.value.id }
        for (id in ids) {
            val (s, t) = measureTimedValue { runCatching { source.resolveStream(id, AudioQuality.HIGH) } }
            println("timing resolve ${id.sourceId}: ${t.inWholeMilliseconds} ms ${if (s.isSuccess) "ok clen=${s.getOrNull()!!.url.contains("clen=")} c=${Regex("[?&]c=([A-Z_0-9]+)").find(s.getOrNull()!!.url)?.groupValues?.get(1)}" else "FAILED ${s.exceptionOrNull()?.message}"}")
        }
        // googlevideo pacing: the first MiB as one open-ended read vs one bounded range.
        val info = source.resolveStream(ids[1], AudioQuality.HIGH)
        val client = okhttp3.OkHttpClient()
        for (ranged in listOf(false, true)) {
            val req = okhttp3.Request.Builder().url(info.url).apply {
                info.headers.forEach { (k, v) -> header(k, v) }
                if (ranged) header("Range", "bytes=0-1048575")
            }.build()
            val t = measureTimedValue {
                client.newCall(req).execute().use { r -> val src = r.body.source(); var n = 0L; while (n < 1_048_576 && !src.exhausted()) { n += src.buffer.size; src.buffer.clear(); src.request(65_536) }; n }
            }
            println("timing first MiB ${if (ranged) "ranged" else "open-ended"}: ${t.duration.inWholeMilliseconds} ms (${t.value} bytes)")
        }
        val (_, again) = measureTimedValue { source.resolveStream(ids.first(), AudioQuality.HIGH) }
        println("timing resolve again ${ids.first().sourceId}: ${again.inWholeMilliseconds} ms")
    }
}
