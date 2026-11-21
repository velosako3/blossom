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
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val error: PlaybackError? = null,
) {
    val current: Track? get() = queue.getOrNull(currentIndex)
}
