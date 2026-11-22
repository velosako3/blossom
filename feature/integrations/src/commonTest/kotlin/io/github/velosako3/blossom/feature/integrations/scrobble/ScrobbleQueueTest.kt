// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.scrobble

import io.github.velosako3.blossom.core.platform.MapPreferenceStore
import io.github.velosako3.blossom.core.platform.NetworkMonitor
import io.github.velosako3.blossom.core.platform.NetworkStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private class Net(online: Boolean) : NetworkMonitor {
    override val status = MutableStateFlow(NetworkStatus(online = online, metered = false))
}

private class RecordingService(override val id: String, var failuresLeft: Int = 0) : ScrobbleService {
    val sent = mutableListOf<Scrobble>()
    override val enabled = true
    override suspend fun nowPlaying(track: ScrobbleTrack) = Unit
    override suspend fun scrobble(item: Scrobble) {
        if (failuresLeft > 0) { failuresLeft--; throw java.io.IOException("503") }
        sent += item
    }
}

private fun item(n: Int, at: Long) = Scrobble(ScrobbleTrack("Artist $n", "Song $n", null, 200_000), Instant.fromEpochSeconds(at))

@OptIn(ExperimentalCoroutinesApi::class)
class ScrobbleQueueTest {
    @Test fun offlineScrobblesGoOutInOrderOnceOnline() = runTest {
        val net = Net(online = false)
        val service = RecordingService("lastfm", failuresLeft = 1)
        val prefs = MapPreferenceStore()
        val queue = ScrobbleQueue(prefs, listOf(service), net, backgroundScope, retryDelaysMs = listOf(1_000, 5_000))
        queue.start()
        queue.enqueue(item(2, 2_000)); queue.enqueue(item(3, 3_000)); queue.enqueue(item(1, 1_000))
        runCurrent()
        assertEquals(0, service.sent.size)
        assertEquals(3, ScrobbleQueue(prefs, listOf(service), net, backgroundScope).pending().size, "persisted")
        net.status.value = NetworkStatus(online = true, metered = false)
        runCurrent()
        advanceTimeBy(1_500); runCurrent() // the first try failed; the retry after 1 s goes through
        assertEquals(listOf("Song 1", "Song 2", "Song 3"), service.sent.map { it.track.title })
        assertEquals(0, queue.pending().size)
    }

    @Test fun eachServiceGetsEachScrobbleOnce() = runTest {
        val net = Net(online = true)
        val good = RecordingService("listenbrainz"); val flaky = RecordingService("lastfm", failuresLeft = 2)
        val queue = ScrobbleQueue(MapPreferenceStore(), listOf(good, flaky), net, backgroundScope, retryDelaysMs = listOf(1_000, 1_000, 1_000))
        queue.start()
        queue.enqueue(item(1, 1_000))
        runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertEquals(1, good.sent.size)
        assertEquals(1, flaky.sent.size)
    }

    @Test fun givesUpAfterFiveTries() = runTest {
        val net = Net(online = true)
        val dead = RecordingService("lastfm", failuresLeft = 100)
        val queue = ScrobbleQueue(MapPreferenceStore(), listOf(dead), net, backgroundScope, retryDelaysMs = listOf(10, 10, 10, 10, 10))
        queue.start()
        queue.enqueue(item(1, 1_000)); queue.enqueue(item(2, 2_000))
        runCurrent(); advanceTimeBy(10_000); runCurrent()
        assertEquals(0, queue.pending().size)
        assertEquals(100 - 10, dead.failuresLeft) // 5 tries each
    }
}
