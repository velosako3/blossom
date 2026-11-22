// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.PlayEvent
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.platform.MapPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** A scope that can be cancelled alone (a "process" that dies) without stopping the test. */
private fun child(scope: TestScope) = CoroutineScope(scope.backgroundScope.coroutineContext + kotlinx.coroutines.Job(scope.backgroundScope.coroutineContext[kotlinx.coroutines.Job]))

@OptIn(ExperimentalCoroutinesApi::class)
class PlayTrackerTest {
    private class VirtualClock(private val scope: TestScope) : Clock {
        override fun now() = Instant.fromEpochMilliseconds(1_800_000_000_000 + scope.testScheduler.currentTime)
    }

    private class Rig(val scope: TestScope, tracks: List<Track> = tracks(3)) {
        val state = MutableStateFlow(PlaybackState(queue = tracks, currentIndex = 0, durationMs = tracks[0].durationMs))
        val recorded = mutableListOf<PlayEvent>()
        val prefs = MapPreferenceStore()
        var trackerScope = child(scope)
        var tracker = start()

        fun start() = PlayTracker(state, { recorded += it }, VirtualClock(scope), "dev", prefs, trackerScope)

        fun play() = state.update { it.copy(isPlaying = true, isBuffering = false) }
        fun pause() = state.update { it.copy(isPlaying = false) }
        fun seek(to: Long) = state.update { it.copy(positionMs = to) }
        fun next() = state.update { it.copy(currentIndex = it.currentIndex + 1, positionMs = 0) }
        fun run(ms: Long) {
            var left = ms
            while (left > 0) {
                val step = minOf(250L, left)
                scope.advanceTimeBy(step)
                state.update { s -> if (s.isPlaying && !s.isBuffering) s.copy(positionMs = s.positionMs + step) else s }
                left -= step
            }
            scope.runCurrent()
        }
    }

    @Test fun twentyNineSecondsThenSkipIsNotCounted() = runTest {
        val r = Rig(this); runCurrent()
        r.play(); r.run(29_000); r.next(); runCurrent()
        assertEquals(1, r.recorded.size)
        assertEquals(29_000, r.recorded[0].playedMs)
        assertEquals(false, r.recorded[0].counted)
    }

    @Test fun thirtySecondsCounts() = runTest {
        val r = Rig(this); runCurrent()
        r.play(); r.run(30_000); r.next(); runCurrent()
        assertEquals(true, r.recorded.single().counted)
    }

    @Test fun shortTrackPlayedToTheEndCounts() = runTest {
        val short = tracks(2).map { it.copy(durationMs = 20_000) }
        val r = Rig(this, short); runCurrent()
        r.play(); r.run(20_000); r.next(); runCurrent()
        assertEquals(true, r.recorded.single().counted)
    }

    @Test fun pausesDoNotCount() = runTest {
        val r = Rig(this); runCurrent()
        r.play(); r.run(10_000); r.pause(); r.run(3_600_000); r.play(); r.run(10_000); r.next(); runCurrent()
        assertEquals(20_000, r.recorded.single().playedMs)
    }

    @Test fun seeksDoNotCount() = runTest {
        val r = Rig(this); runCurrent()
        r.play(); r.run(5_000); r.seek(65_000); r.run(5_000); r.next(); runCurrent()
        assertEquals(10_000, r.recorded.single().playedMs)
    }

    @Test fun crashLosesAtMostFifteenSeconds() = runTest {
        val r = Rig(this); runCurrent()
        r.play(); r.run(40_000)
        r.trackerScope.cancel() // the process dies: nothing is finalized
        assertEquals(0, r.recorded.size)
        r.trackerScope = child(this)
        r.state.value = PlaybackState() // a fresh start: nothing playing yet
        r.tracker = r.start(); runCurrent()
        val e = r.recorded.single()
        assertTrue(e.playedMs in 30_000..40_000, "${e.playedMs}")
        assertEquals(true, e.counted)
    }
}
