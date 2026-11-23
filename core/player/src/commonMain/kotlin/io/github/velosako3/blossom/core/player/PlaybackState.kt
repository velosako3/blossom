// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId

enum class RepeatMode { OFF, ALL, ONE }

sealed interface PlaybackError {
    /** The stream kept failing after re-resolving; the player moved on. */
    data class StreamFailed(val trackId: TrackId, val message: String?) : PlaybackError
    /** The track can't be played at all (removed, region-locked, not on this device). */
    data class Unavailable(val trackId: TrackId, val message: String?) : PlaybackError
}

data class PlaybackState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val error: PlaybackError? = null,
) {
    val current: Track? get() = queue.getOrNull(currentIndex)
}

/**
 * Where playback is, kept out of [PlaybackState] on purpose (as BitChord's `PlaybackPosition`): it ticks four times a
 * second, and everything that watches [PlaybackState] would redraw with it. Only the scrubbers and the lyrics read it.
 *
 * [sampledAtNanos] is when [positionMs] was read off the engine ([System.nanoTime]); [seeks] counts deliberate jumps
 * (a seek, a skip, a new track), so the lyrics can tell a jump from a late reading.
 */
data class PlaybackProgress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val sampledAtNanos: Long = 0,
    val seeks: Int = 0,
)
