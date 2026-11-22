// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed interface SleepState {
    data class Until(val endsAt: Instant) : SleepState
    data object EndOfTrack : SleepState
}

/**
 * Stops playback after a while: the volume fades out over the last [FADE], then playback pauses and the volume goes
 * back to full for next time. "End of track" pauses when the current song ends instead of starting the next.
 */
class SleepTimer(private val player: BlossomPlayer, private val scope: CoroutineScope, private val clock: Clock = Clock.System) {
    private val _state = MutableStateFlow<SleepState?>(null)
    /** The running timer, or null. */
    val state: StateFlow<SleepState?> = _state.asStateFlow()
    private var job: Job? = null

    fun start(duration: Duration) {
        cancel()
        _state.value = SleepState.Until(clock.now() + duration)
        job = scope.launch {
            delay((duration - FADE).coerceAtLeast(Duration.ZERO))
            val fade = minOf(FADE, duration)
            var left = fade
            while (left > Duration.ZERO) {
                player.setVolume((left / fade).toFloat())
                delay(STEP)
                left -= STEP
            }
            player.pause()
            player.setVolume(1f)
            _state.value = null
        }
    }

    fun endOfTrack() {
        cancel()
        _state.value = SleepState.EndOfTrack
        player.setStopAfterCurrent(true)
        job = scope.launch {
            player.stopAfterCurrent.first { !it } // the player cleared it: the track ended and it paused
            _state.value = null
        }
    }

    fun cancel() {
        val wasFading = job?.isActive == true && _state.value is SleepState.Until
        job?.cancel(); job = null
        if (_state.value == SleepState.EndOfTrack) player.setStopAfterCurrent(false)
        if (wasFading) player.setVolume(1f)
        _state.value = null
    }

    companion object {
        val FADE = 10.seconds
        private val STEP = 250.milliseconds
        /** The choices offered, plus "end of track". */
        val OPTIONS = listOf(5, 10, 15, 30, 45, 60).map { it.minutes }
    }
}
