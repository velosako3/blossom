// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.Track
import kotlinx.coroutines.flow.StateFlow

/** The one player the whole app talks to. */
interface BlossomPlayer {
    val state: StateFlow<PlaybackState>
    suspend fun setQueue(tracks: List<Track>, startIndex: Int, startPositionMs: Long = 0)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun next()
    fun previous()
    /** Jumps to [index] in the current queue (tapping a queue row). */
    fun skipTo(index: Int)
    fun setShuffle(enabled: Boolean)
    fun setRepeat(mode: RepeatMode)
    fun addNext(track: Track)
    fun addToQueue(tracks: List<Track>)
    fun moveInQueue(from: Int, to: Int)
    fun removeFromQueue(index: Int)
    fun setVolume(volume: Float)
    fun setEqualizer(settings: io.github.velosako3.blossom.core.player.eq.EqSettings) {}
    /** Pause when the current track ends instead of moving on (sleep timer "end of track"); clears itself then. */
    fun setStopAfterCurrent(enabled: Boolean) {}
    val stopAfterCurrent: kotlinx.coroutines.flow.StateFlow<Boolean> get() = kotlinx.coroutines.flow.MutableStateFlow(false)
}
