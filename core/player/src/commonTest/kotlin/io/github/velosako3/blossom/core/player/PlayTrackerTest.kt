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
        val position = MutableStateFlow(0L)
        val recorded = mutableListOf<PlayEvent>()
        val prefs = MapPreferenceStore()
        var trackerScope = child(scope)
        var tracker = start()

        var failNext = false
        fun start() = PlayTracker(state, position, { if (failNext) { failNext = false; error("database busy") }; recorded += it }, VirtualClock(scope), "dev", prefs, trackerScope)

        fun play() = state.update { it.copy(isPlaying = true, isBuffering = false) }
        fun pause() = state.update { it.copy(isPlaying = false) }
        fun seek(to: Long) { position.value = to }
        fun next() { position.value = 0; state.update { it.copy(currentIndex = it.currentIndex + 1) } }
        fun run(ms: Long) {
            var left = ms
            while (left > 0) {
                val step = minOf(250L, left)
                scope.advanceTimeBy(step)
                state.value.let { s -> if (s.isPlaying && !s.isBuffering) position.value += step }
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

    @Test fun aFailedRecordDoesNotStopTracking() = runTest {
        val r = Rig(this); runCurrent()
        r.failNext = true
        r.play(); r.run(31_000); r.next(); runCurrent() // this record fails
        r.run(31_000); r.next(); runCurrent()
        assertEquals(1, r.recorded.size)
        assertEquals(31_000, r.recorded.single().playedMs)
    }

    @Test fun nothingPlayedNothingRecorded() = runTest {
        val r = Rig(this); runCurrent()
        r.next(); runCurrent(); r.next(); runCurrent() // skipped while paused
        assertEquals(0, r.recorded.size)
    }

    @Test fun ignoredWhilePreviewing() = runTest {
        val r = Rig(this); runCurrent()
        r.play(); r.run(40_000)
        r.tracker.setIgnoring(true); runCurrent()
        assertEquals(1, r.recorded.size) // the play before the preview is kept
        r.next(); r.run(60_000); r.next(); runCurrent()
        assertEquals(1, r.recorded.size)
        r.tracker.setIgnoring(false); runCurrent()
        r.run(35_000); r.next(); runCurrent()
        assertEquals(2, r.recorded.size)
    }
}
