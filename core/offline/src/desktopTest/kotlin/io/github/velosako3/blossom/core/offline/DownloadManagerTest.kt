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
import kotlin.test.assertFalse
import io.github.velosako3.blossom.source.api.StreamInfo
import kotlin.test.assertTrue

class DownloadManagerTest {
    private val h = OfflineHarness(mapOf("a" to 2_000_000, "b" to 300_000, "big" to 5_000_000))
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

    private suspend fun saveAndWait(id: String) {
        val t = track(id)
        store.saveTrack(t, OfflineRef.Single(t.id))
        withTimeout(30_000) { store.state(t.id).first { it == OfflineState.SAVED || it == OfflineState.FAILED } }
        assertEquals(OfflineState.SAVED, store.state(t.id).first())
    }

    @Test fun finishedFileIsNeverReencrypted(): Unit = runBlocking {
        saveAndWait("a")
        val f = h.manager.fileFor(track("a").id.value)
        val before = f.readBytes()
        // A crash after the last chunk was written but before SAVED was recorded.
        h.db.blossomQueries.offlineSetState(OfflineState.QUEUED.name, track("a").id.value)
        h.server.ranges.clear()
        h.manager.kick()
        withTimeout(30_000) { store.state(track("a").id).first { it == OfflineState.SAVED } }
        assertEquals(emptyList(), h.server.ranges.toList())
        assertContentEquals(before, f.readBytes())
    }

    @Test fun partialTrailingChunkRestartsWithNewNonces(): Unit = runBlocking {
        saveAndWait("a")
        val f = h.manager.fileFor(track("a").id.value)
        val oldHeader = f.readBytes().copyOf(ChunkedCipher.HEADER)
        // Cut inside a chunk: the bytes after the last whole chunk might be re-encrypted differently.
        java.io.RandomAccessFile(f, "rw").use { it.setLength(ChunkedCipher.HEADER + 3L * (ChunkedCipher.CHUNK + ChunkedCipher.TAG) + 100) }
        h.db.blossomQueries.offlineSetState(OfflineState.QUEUED.name, track("a").id.value)
        h.server.ranges.clear()
        h.manager.kick()
        withTimeout(30_000) { store.state(track("a").id).first { it == OfflineState.SAVED } }
        assertTrue(h.server.ranges.first()!!.startsWith("bytes=0-"), "${h.server.ranges}")
        assertFalse(oldHeader.contentEquals(f.readBytes().copyOf(ChunkedCipher.HEADER)))
        assertContentEquals(h.server.bytes("a"), decrypted("a"))
    }

    @Test fun expiredStreamIsResolvedAgainMidTransfer(): Unit = runBlocking {
        h.server.slowMs = 1
        val t = track("big")
        store.saveTrack(t, OfflineRef.Single(t.id))
        withTimeout(10_000) { while (h.server.ranges.size < 1) kotlinx.coroutines.delay(5) }
        h.server.minToken = 1 // the URL expires after the first range
        withTimeout(30_000) { store.state(t.id).first { it == OfflineState.SAVED || it == OfflineState.FAILED } }
        assertEquals(OfflineState.SAVED, store.state(t.id).first())
        assertContentEquals(h.server.bytes("big"), decrypted("big"))
    }

    @Test fun mismatchedContentRangeIsRejected(): Unit = runBlocking {
        h.server.wrongOffsetOnce = true
        saveAndWait("big")
        assertContentEquals(h.server.bytes("big"), decrypted("big"))
    }

    @Test fun sourceThatChangedSizeRestarts(): Unit = runBlocking {
        h.server.dropFirstAt = 0.4
        val t = track("a")
        store.saveTrack(t, OfflineRef.Single(t.id))
        withTimeout(10_000) { while (h.server.ranges.size < 1) kotlinx.coroutines.delay(5) }
        h.server.resizeTo = 1_500_000
        withTimeout(30_000) { store.state(t.id).first { it == OfflineState.SAVED || it == OfflineState.FAILED } }
        assertEquals(OfflineState.SAVED, store.state(t.id).first())
        assertContentEquals(h.server.bytes("a"), decrypted("a"))
    }

    @Test fun unsaveDuringDownloadLeavesNoFile(): Unit = runBlocking {
        h.server.slowMs = 20
        val t = track("a")
        store.saveTrack(t, OfflineRef.Single(t.id))
        withTimeout(10_000) { store.state(t.id).first { it == OfflineState.DOWNLOADING } }
        kotlinx.coroutines.delay(200)
        store.unsave(OfflineRef.Single(t.id))
        assertFalse(store.isSaved(t.id))
        kotlinx.coroutines.delay(500)
        assertEquals(emptyList(), h.dir.listFiles().orEmpty().map { it.name })
        assertEquals(OfflineState.NOT_SAVED, store.state(t.id).first())
    }

    @Test fun changedKeyHealsByDownloadingAgain(): Unit = runBlocking {
        saveAndWait("b")
        val other = SqlOfflineStore(h.db, DownloadManager(h.db, h.dir, KeyVault(io.github.velosako3.blossom.core.platform.InMemorySecureStore()),
            resolve = { t, _ -> StreamInfo(h.server.url(t.id.sourceId), emptyMap(), "audio/webm", 160, null) },
            http = okhttp3.OkHttpClient(), network = h.network, settings = h.settings, scope = h.scope), null, h.scope)
        kotlinx.coroutines.delay(300)
        assertEquals(null, other.openReader(track("b").id))
        withTimeout(30_000) { other.state(track("b").id).first { it == OfflineState.SAVED } }
        assertTrue(other.openReader(track("b").id) != null)
    }

    @Test fun localFilesAreNeverSaved(): Unit = runBlocking {
        val local = track("x").copy(id = io.github.velosako3.blossom.core.model.TrackId.of(io.github.velosako3.blossom.core.model.SourceKind.LOCAL, "x"))
        store.saveTrack(local, OfflineRef.Single(local.id))
        assertEquals(OfflineState.NOT_SAVED, store.state(local.id).first())
    }
}
