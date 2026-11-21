// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.platform.NetworkMonitor
import io.github.velosako3.blossom.core.platform.NetworkStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import io.github.velosako3.blossom.core.player.RepeatMode

class FakeEngine : PlaybackEngine {
    val loads = mutableListOf<Pair<MediaInput, Long>>()
    var tagEvents = false
    override val mediaId: Long get() = if (tagEvents) loads.size.toLong() else 0
    var playing = false
    override val events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    override suspend fun load(input: MediaInput, startPositionMs: Long) { loads += input to startPositionMs }
    override fun play() { playing = true }
    override fun pause() { playing = false }
    override fun seekTo(ms: Long) {}
    override fun setVolume(v: Float) {}
    override fun release() {}
}

class FakeNetwork(online: Boolean = true) : NetworkMonitor {
    override val status = MutableStateFlow(NetworkStatus(online = online, metered = false))
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBlossomPlayerTest {
    private fun TestScope.player(engine: FakeEngine, network: FakeNetwork = FakeNetwork(), saved: Set<String> = emptySet()): DefaultBlossomPlayer {
        val clock = FakeClock()
        val resolver = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to CountingSource(clock)), clock)
        return DefaultBlossomPlayer(
            engine = engine,
            resolver = resolver,
            network = network,
            scope = backgroundScope,
            isSavedOffline = { it.sourceId in saved },
            radio = { emptyList() },
        )
    }

    @Test fun expiredStreamReResolvesAtSamePosition() = runTest {
        val engine = FakeEngine()
        val p = player(engine)
        p.setQueue(tracks(3), 0)
        p.play()
        runCurrent()
        engine.events.emit(EngineEvent.Position(95_000, 200_000))
        engine.events.emit(EngineEvent.Error(403, null))
        runCurrent()
        val (input, start) = engine.loads.last()
        assertIs<MediaInput.Remote>(input)
        assertTrue(input.stream.url.endsWith("n=2"), input.stream.url)
        assertEquals(95_000, start)
        assertTrue(p.state.value.isPlaying)
        assertEquals(null, p.state.value.error)
    }

    @Test fun threeFailuresSurfaceErrorAndSkip() = runTest {
        val engine = FakeEngine()
        val p = player(engine)
        p.setQueue(tracks(3), 0)
        p.play()
        runCurrent()
        repeat(3) {
            engine.events.emit(EngineEvent.Error(403, null))
            runCurrent()
        }
        assertIs<PlaybackError.StreamFailed>(p.state.value.error)
        assertEquals(1, p.state.value.currentIndex)
    }

    @Test fun staleEventsFromPreviousMediaAreIgnored() = runTest {
        val engine = FakeEngine().apply { tagEvents = true }
        val p = player(engine)
        p.setQueue(tracks(3), 0)
        p.play()
        runCurrent()
        val oldId = engine.mediaId
        p.next()
        runCurrent()
        engine.events.emit(EngineEvent.Position(120_000, 200_000, mediaId = oldId))
        engine.events.emit(EngineEvent.Ended(mediaId = oldId))
        runCurrent()
        assertEquals(1, p.state.value.currentIndex)
        assertEquals(0, p.state.value.positionMs)
    }

    @Test fun repeatOneDeadStreamStillSkips() = runTest {
        val engine = FakeEngine()
        val p = player(engine)
        p.setQueue(tracks(3), 0)
        p.setRepeat(RepeatMode.ONE)
        p.play()
        runCurrent()
        repeat(3) { engine.events.emit(EngineEvent.Error(403, null)); runCurrent() }
        assertEquals(1, p.state.value.currentIndex)
    }

    @Test fun removingLastCurrentTrackStopsEngine() = runTest {
        val engine = FakeEngine()
        val p = player(engine)
        p.setQueue(tracks(1), 0)
        p.play()
        runCurrent()
        p.removeFromQueue(0)
        runCurrent()
        assertEquals(false, engine.playing)
        assertEquals(false, p.state.value.isPlaying)
    }

    @Test fun recoveredExpiriesDoNotAccumulate() = runTest {
        val engine = FakeEngine()
        val p = player(engine)
        p.setQueue(tracks(3), 0)
        p.play()
        runCurrent()
        repeat(4) {
            engine.events.emit(EngineEvent.Error(403, null)); runCurrent()
            engine.events.emit(EngineEvent.Position(10_000L * (it + 1), 200_000)); runCurrent()
        }
        assertEquals(0, p.state.value.currentIndex)
        assertEquals(null, p.state.value.error)
    }

    @Test fun offlineSkipsUnsavedTracks() = runTest {
        val engine = FakeEngine()
        val p = player(engine, FakeNetwork(online = false), saved = setOf("t1"))
        p.setQueue(tracks(3), 0)
        runCurrent()
        assertEquals(1, p.state.value.currentIndex)
        assertIs<MediaInput.Offline>(engine.loads.single().first)
    }
}
