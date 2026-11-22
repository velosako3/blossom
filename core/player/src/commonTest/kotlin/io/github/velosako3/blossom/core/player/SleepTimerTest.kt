// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.SourceKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private class LevelEngine : PlaybackEngine {
    var level = 1f
    var playing = false
    var loads = 0
    override val mediaId: Long get() = loads.toLong()
    override val events = kotlinx.coroutines.flow.MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    override suspend fun load(input: MediaInput, startPositionMs: Long) { loads++ }
    override fun play() { playing = true }
    override fun pause() { playing = false }
    override fun seekTo(ms: Long) {}
    override fun setVolume(v: Float) { level = v }
    override fun release() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    private fun TestScope.playing(engine: LevelEngine): DefaultBlossomPlayer {
        val clock = FakeClock()
        return DefaultBlossomPlayer(engine, StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to CountingSource(clock)), clock), FakeNetwork(), backgroundScope)
    }

    @Test fun fadesThenPausesAndRestoresVolume() = runTest {
        val engine = LevelEngine()
        val p = playing(engine)
        p.setQueue(tracks(3), 0); p.play(); runCurrent()
        val timer = SleepTimer(p, backgroundScope)
        timer.start(5.minutes)
        runCurrent()
        advanceTimeBy(4.minutes + 49.seconds); runCurrent()
        assertEquals(1f, engine.level, 0.01f)
        advanceTimeBy(6.seconds); runCurrent() // 4:55
        assertEquals(0.5f, engine.level, 0.05f)
        assertTrue(engine.playing)
        advanceTimeBy(5.seconds); runCurrent() // 5:00
        assertFalse(p.state.value.isPlaying)
        assertFalse(engine.playing)
        assertEquals(1f, engine.level, 0.01f)
        assertEquals(null, timer.state.value)
    }

    @Test fun endOfTrackPausesWhenTheTrackEnds() = runTest {
        val engine = LevelEngine()
        val p = playing(engine)
        p.setQueue(tracks(3), 0); p.play(); runCurrent()
        val timer = SleepTimer(p, backgroundScope)
        timer.endOfTrack()
        runCurrent()
        engine.events.emit(EngineEvent.Position(150_000, 200_000, engine.mediaId)); runCurrent()
        assertTrue(p.state.value.isPlaying)
        engine.events.emit(EngineEvent.Ended(engine.mediaId)); runCurrent()
        assertFalse(p.state.value.isPlaying)
        assertEquals(1, engine.loads, "the next track isn't started")
        assertEquals(null, timer.state.value)
        // Next time it plays on normally.
        p.play(); runCurrent()
        engine.events.emit(EngineEvent.Ended(engine.mediaId)); runCurrent()
        assertTrue(p.state.value.isPlaying)
    }

    @Test fun cancelMeansNoPause() = runTest {
        val engine = LevelEngine()
        val p = playing(engine)
        p.setQueue(tracks(3), 0); p.play(); runCurrent()
        val timer = SleepTimer(p, backgroundScope)
        timer.start(5.minutes)
        advanceTimeBy(4.minutes + 55.seconds); runCurrent()
        timer.cancel(); runCurrent()
        assertEquals(1f, engine.level, 0.01f)
        advanceTimeBy(1.minutes); runCurrent()
        assertTrue(p.state.value.isPlaying)
    }
}
