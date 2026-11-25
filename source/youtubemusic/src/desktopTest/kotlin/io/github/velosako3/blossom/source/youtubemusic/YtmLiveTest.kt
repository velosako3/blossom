// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.core.platform.InMemorySecureStore
import io.github.velosako3.blossom.source.api.AudioQuality
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/** Hits YouTube Music for real. Runs only with -PliveTests=true. */
class YtmLiveTest {
    @Test fun searchAndResolve() = runBlocking {
        val source = YtmSource(InMemorySecureStore(), io.github.velosako3.blossom.source.youtubemusic.YouTubeStreams({ null }, Files.createTempDirectory("ytm").toFile()))
        val songs = source.search("Daft Punk One More Time").songs
        println("live search: ${songs.size} songs, first = ${songs.firstOrNull()?.let { "${it.title} – ${it.artistNames}" }}")
        assertTrue(songs.isNotEmpty())
        val stream = source.resolveStream(songs.first().id, AudioQuality.HIGH)
        println("live stream: ${stream.mimeType} ${stream.bitrateKbps} kbps, expires ${stream.expiresAt}, headers ${stream.headers.keys}")
        assertTrue(stream.url.startsWith("https://"))
    }
}
