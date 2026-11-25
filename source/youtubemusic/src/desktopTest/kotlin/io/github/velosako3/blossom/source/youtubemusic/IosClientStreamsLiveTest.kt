// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.source.api.AudioQuality
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.test.Test
import kotlin.test.assertEquals

/** iOS's stream path, run here: a real song resolves and its audio downloads. Live only. */
class IosClientStreamsLiveTest {
    @Test fun aSongPlays(): Unit = runBlocking {
        val s = IosClientStreams().resolve("4NRXx6U8ABQ", AudioQuality.HIGH)
        val r = OkHttpClient().newCall(Request.Builder().url(s.url).header("Range", "bytes=0-65535").apply { s.headers.forEach { (k, v) -> header(k, v) } }.build()).execute()
        println("ios streams: ${s.mimeType} ${s.bitrateKbps} kbps, HTTP ${r.code}, ${r.body.bytes().size} bytes, expires ${s.expiresAt}")
        assertEquals(206, r.code)
    }
}
