// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.offline.OfflineRef
import io.github.velosako3.blossom.core.offline.OfflineState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Save a song while online, then start a second app with `blossom.forceOffline=true` (inside this JVM only; the
 * machine's network is untouched) and play it from the encrypted cache. Live: -PliveTests=true.
 */
class DesktopOfflineLiveTest {
    @Test fun playsSavedSongOffline(): Unit = runBlocking {
        resetTestDataDir()
        val online = createDesktopGraph()
        val song = online.ytm.search("Daft Punk One More Time").songs.first()
        online.offline!!.saveTrack(song, OfflineRef.Single(song.id))
        val watch = launch(kotlinx.coroutines.Dispatchers.IO) {
            while (true) { kotlinx.coroutines.delay(10_000); println("DIAG ${online.offline!!.state(song.id).first()} ${online.offline!!.progress(song.id).first()}") }
        }
        withTimeout(120_000) { online.offline!!.state(song.id).first { it == OfflineState.SAVED || it == OfflineState.FAILED } }
        watch.cancel()
        assertEquals(OfflineState.SAVED, online.offline!!.state(song.id).first())
        val bytes = online.offline!!.usageBytes().first()
        println("offline saved: ${song.title} – ${song.artistNames}, $bytes bytes")
        val dir = java.io.File(System.getProperty("blossom.dataDir"), "offline")
        assertTrue(dir.listFiles().orEmpty().all { it.name.endsWith(".blsm") })

        System.setProperty("blossom.forceOffline", "true")
        try {
            val offline = createDesktopGraph()
            assertFalse(offline.network.status.value.online)
            offline.player.setQueue(listOf(song), 0)
            offline.player.play()
            withTimeout(60_000) { offline.player.state.first { it.positionMs > 3_000 } }
            println("offline playing: ${offline.player.state.value.current?.title} at ${offline.player.state.value.positionMs} ms, online=${offline.network.status.value.online}")
            offline.player.pause()
        } finally {
            System.clearProperty("blossom.forceOffline")
        }
    }
}
