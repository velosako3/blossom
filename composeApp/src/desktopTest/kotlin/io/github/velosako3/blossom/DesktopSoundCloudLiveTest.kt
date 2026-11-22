// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.source.soundcloud.SoundCloudClient
import io.github.velosako3.blossom.source.soundcloud.SoundCloudSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test

/** A SoundCloud (HLS) song plays and seeks in the real desktop player. Live: -PliveTests=true. */
class DesktopSoundCloudLiveTest {
    @Test fun playsAndSeeksSoundCloudHls(): Unit = runBlocking {
        resetTestDataDir()
        val graph = createDesktopGraph()
        val song = SoundCloudSource(SoundCloudClient()).search("lofi study beats").songs.first { it.durationMs > 120_000 }
        graph.player.setQueue(listOf(song), 0)
        graph.player.play()
        withTimeout(60_000) { graph.player.progress.first { it.positionMs > 3_000 } }
        println("soundcloud playing: ${song.title} – ${song.artistNames} at ${graph.player.progress.value.positionMs} ms")
        graph.player.seekTo(95_000)
        withTimeout(30_000) { graph.player.progress.first { it.positionMs in 90_000..115_000 } }
        delay(2_000)
        val after = graph.player.progress.value.positionMs
        check(after in 90_000..105_000) { "seek landed at $after" }
        delay(2_000)
        println("soundcloud after seek: ${graph.player.progress.value.positionMs} ms, error=${graph.player.state.value.error}")
        graph.player.pause()
    }
}
