// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.SourceKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class VolumeEngine : PlaybackEngine {
    val loads = mutableListOf<MediaInput>()
    var playing = false
    var level = 1f
    override val mediaId: Long get() = loads.size.toLong()
    override val events = kotlinx.coroutines.flow.MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    override suspend fun load(input: MediaInput, startPositionMs: Long) { loads += input }
    override fun play() { playing = true }
    override fun pause() { playing = false }
    override fun seekTo(ms: Long) {}
    override fun setVolume(v: Float) { level = v }
    override fun release() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class CrossfadeControllerTest {
    @Test fun equalPowerCurve() {
        assertEquals(1f to 0f, CrossfadeCurve.volumes(0, 6000))
        val (out, inc) = CrossfadeCurve.volumes(3000, 6000)
        assertEquals(0.707f, out, 0.01f)
        assertEquals(0.707f, inc, 0.01f)
        val (o2, i2) = CrossfadeCurve.volumes(6000, 6000)
        assertEquals(0f, o2, 1e-6f)
        assertEquals(1f, i2, 1e-6f)
    }

    @Test fun albumNeighboursPlayGapless() {
        val a1 = track(1).copy(album = AlbumRef("alb", "Album"))
        val a2 = track(2).copy(album = AlbumRef("alb", "Album"))
        val other = track(3).copy(album = AlbumRef("x", "Other"))
        assertFalse(shouldCrossfade(a1, a2, 6))
        assertTrue(shouldCrossfade(a1, other, 6))
        assertTrue(shouldCrossfade(track(1), track(2), 6)) // no album: not consecutive on one
        assertFalse(shouldCrossfade(a1, other, 0))
    }

    @Test fun playerFadesIntoTheNextTrackOnTheSecondEngine() = runTest {
        val first = VolumeEngine(); val second = VolumeEngine()
        val clock = FakeClock()
        val p = DefaultBlossomPlayer(
            engine = first, fadeEngine = second, crossfadeSeconds = { 6 },
            resolver = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to CountingSource(clock)), clock),
            network = FakeNetwork(), scope = backgroundScope,
        )
        p.setQueue(tracks(3), 0)
        p.play()
        runCurrent()
        first.events.emit(EngineEvent.Position(193_000, 200_000, first.mediaId)) // 7 s left: not yet
        runCurrent()
        assertEquals(0, second.loads.size)
        first.events.emit(EngineEvent.Position(194_000, 200_000, first.mediaId)) // 6 s left: fade
        runCurrent()
        assertEquals(1, second.loads.size)
        assertTrue(second.playing)
        assertEquals(1, p.state.value.currentIndex)
        advanceTimeBy(3_000); runCurrent()
        assertEquals(0.707f, first.level, 0.05f)
        assertEquals(0.707f, second.level, 0.05f)
        // The outgoing track's own end mid-fade doesn't skip the new one.
        first.events.emit(EngineEvent.Ended(first.mediaId))
        runCurrent()
        assertEquals(1, p.state.value.currentIndex)
        advanceTimeBy(3_100); runCurrent()
        assertEquals(1f, second.level, 1e-3f)
        assertFalse(first.playing)
        // The next fade goes back to the first engine.
        second.events.emit(EngineEvent.Position(196_000, 200_000, second.mediaId))
        runCurrent()
        assertEquals(2, first.loads.size)
        assertEquals(2, p.state.value.currentIndex)
    }

    @Test fun offMeansNoSecondEngine() = runTest {
        val first = VolumeEngine(); val second = VolumeEngine()
        val clock = FakeClock()
        val p = DefaultBlossomPlayer(engine = first, fadeEngine = second, crossfadeSeconds = { 0 },
            resolver = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to CountingSource(clock)), clock), network = FakeNetwork(), scope = backgroundScope)
        p.setQueue(tracks(3), 0); p.play(); runCurrent()
        first.events.emit(EngineEvent.Position(199_000, 200_000, first.mediaId)); runCurrent()
        assertEquals(0, second.loads.size)
        first.events.emit(EngineEvent.Ended(first.mediaId)); runCurrent()
        assertEquals(2, first.loads.size) // next track on the same engine
    }
}
