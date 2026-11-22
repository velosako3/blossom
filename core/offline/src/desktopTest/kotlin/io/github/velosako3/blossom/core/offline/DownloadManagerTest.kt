// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import io.github.velosako3.blossom.core.platform.NetworkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadManagerTest {
    private val h = OfflineHarness(mapOf("a" to 2_000_000, "b" to 300_000))
    private val store = SqlOfflineStore(h.db, h.manager, repo = null, scope = h.scope)

    @AfterTest fun close() = h.close()

    private fun decrypted(id: String): ByteArray {
        val r = store.openReader(track(id).id)!!
        val out = ByteArray(r.plainLength.toInt())
        var p = 0
        while (p < out.size) p += r.read(p.toLong(), out, p, out.size - p)
        r.close()
        return out
    }

    @Test fun savesAndDecrypts(): Unit = runBlocking {
        h.server.slowMs = 5
        val t = track("a")
        val seen = mutableListOf<OfflineState>()
        val watcher = launch(Dispatchers.IO) { store.state(t.id).collect { if (seen.lastOrNull() != it) seen += it } }
        store.saveTrack(t, OfflineRef.Single(t.id))
        withTimeout(30_000) { store.state(t.id).first { it == OfflineState.SAVED } }
        watcher.cancel()
        val tail = seen.dropWhile { it == OfflineState.NOT_SAVED }
        assertTrue(tail.first() in setOf(OfflineState.QUEUED, OfflineState.DOWNLOADING) && OfflineState.DOWNLOADING in tail && tail.last() == OfflineState.SAVED, "$seen")
        assertContentEquals(h.server.bytes("a"), decrypted("a"))
        assertEquals(1f, store.progress(t.id).first())
        assertTrue(store.usageBytes().first() >= 2_000_000)
    }

    @Test fun meteredWifiOnlyPausesThenCompletes(): Unit = runBlocking {
        h.settings.value = OfflineSettings(wifiOnly = true)
        h.network.status.value = NetworkStatus(online = true, metered = true)
        val t = track("b")
        store.saveTrack(t, OfflineRef.Single(t.id))
        withTimeout(10_000) { store.state(t.id).first { it == OfflineState.PAUSED_NETWORK } }
        h.network.status.value = NetworkStatus(online = true, metered = false)
        withTimeout(30_000) { store.state(t.id).first { it == OfflineState.SAVED } }
        assertContentEquals(h.server.bytes("b"), decrypted("b"))
    }

    @Test fun limitPausesForStorage(): Unit = runBlocking {
        h.settings.value = OfflineSettings(wifiOnly = false, limitBytes = 1_000_000)
        val t = track("a")
        store.saveTrack(t, OfflineRef.Single(t.id))
        withTimeout(10_000) { store.state(t.id).first { it == OfflineState.PAUSED_STORAGE } }
    }

    @Test fun dropAt40PercentResumesWithRange(): Unit = runBlocking {
        h.server.dropFirstAt = 0.4
        val t = track("a")
        store.saveTrack(t, OfflineRef.Single(t.id))
        withTimeout(30_000) { store.state(t.id).first { it == OfflineState.SAVED } }
        val resumed = h.server.ranges.filterNotNull()
        assertTrue(resumed.any { Regex("bytes=[1-9]\\d*-\\d*").matches(it) }, "${h.server.ranges}")
        assertContentEquals(h.server.bytes("a"), decrypted("a"))
    }

    @Test fun downloadsInBoundedRanges(): Unit = runBlocking {
        // YouTube throttles open-ended downloads to playback speed; bounded ranges come at full speed.
        val t = track("a")
        store.saveTrack(t, OfflineRef.Single(t.id))
        withTimeout(30_000) { store.state(t.id).first { it == OfflineState.SAVED } }
        val ranges = h.server.ranges.toList()
        assertTrue(ranges.size >= 1 && ranges.all { it != null && Regex("bytes=\\d+-\\d+").matches(it) }, "$ranges")
        assertContentEquals(h.server.bytes("a"), decrypted("a"))
    }

    @Test fun localFilesAreNeverSaved(): Unit = runBlocking {
        val local = track("x").copy(id = io.github.velosako3.blossom.core.model.TrackId.of(io.github.velosako3.blossom.core.model.SourceKind.LOCAL, "x"))
        store.saveTrack(local, OfflineRef.Single(local.id))
        assertEquals(OfflineState.NOT_SAVED, store.state(local.id).first())
    }
}
