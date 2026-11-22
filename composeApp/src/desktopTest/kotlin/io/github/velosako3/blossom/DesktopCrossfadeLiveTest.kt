// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/** Crossfade with the real graph and two libVLC engines: two 8 s local files, crossfade set to 3 s. */
class DesktopCrossfadeLiveTest {
    private fun wav(file: File, hz: Double, seconds: Int) {
        val rate = 44_100; val samples = rate * seconds
        val data = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(samples) { data.putShort((sin(2 * PI * hz * it / rate) * 8_000).toInt().toShort()) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples * 2)
        }
        file.writeBytes(header.array() + data.array())
    }

    @Test fun fadesIntoTheNextTrack() = runBlocking {
        val data = File(System.getProperty("blossom.dataDir")).apply { deleteRecursively(); mkdirs() }
        val music = File(data, "music").apply { mkdirs() }
        wav(File(music, "a-first.wav"), 440.0, 8); wav(File(music, "b-second.wav"), 660.0, 8)
        File(data, "preferences.properties").writeText("local.folders=[\"${music.absolutePath}\"]\n$CROSSFADE_KEY=3\n")
        val graph = createDesktopGraph()
        val tracks = withTimeout(20_000) { graph.local.library.tracks().first { it.size == 2 } }.sortedBy { it.title }
        println("crossfade: tracks ${tracks.map { "${it.title} ${it.durationMs} ms" }}")
        graph.player.setQueue(tracks, 0); graph.player.play()
        val start = System.currentTimeMillis()
        var lastFirstPosition = 0L
        withTimeout(20_000) {
            graph.player.state.first { s ->
                if (s.currentIndex == 0) lastFirstPosition = s.positionMs
                s.currentIndex == 1
            }
        }
        val switchedAt = System.currentTimeMillis() - start
        println("crossfade: switched to track 2 after $switchedAt ms; track 1 was at $lastFirstPosition ms")
        // 8 s track, 3 s fade: the next one starts about 5 s in, well before the first one ends.
        assertTrue(lastFirstPosition in 4_000..6_500, "switched at $lastFirstPosition ms")
        withTimeout(15_000) { graph.player.state.first { it.currentIndex == 1 && it.positionMs > 3_500 } }
        println("crossfade: track 2 at ${graph.player.state.value.positionMs} ms, playing=${graph.player.state.value.isPlaying}")
        graph.player.pause()
    }
}
