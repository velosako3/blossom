// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.StreamInfo
import kotlinx.coroutines.flow.Flow

/** What the engine is asked to play. */
sealed interface MediaInput {
    data class Remote(val stream: StreamInfo) : MediaInput
    data class LocalFile(val path: String) : MediaInput
    /** A track from blossom's encrypted offline cache. */
    data class Offline(val trackId: TrackId) : MediaInput
}

/**
 * Engine callbacks. [mediaId] says which [PlaybackEngine.load] produced the event (0 = untagged), so events still
 * queued from the previous track can be dropped after a track change.
 */
sealed interface EngineEvent {
    val mediaId: Long

    data class Position(val positionMs: Long, val durationMs: Long, override val mediaId: Long = 0) : EngineEvent
    data class Ended(override val mediaId: Long = 0) : EngineEvent
    data class Buffering(val buffering: Boolean, override val mediaId: Long = 0) : EngineEvent
    /** [httpStatus] is set when the failure was an HTTP answer (403/410 mean an expired stream URL). */
    data class Error(val httpStatus: Int?, val cause: Throwable?, override val mediaId: Long = 0) : EngineEvent
}

/** The platform audio engine: Media3 on Android, libVLC on desktop. */
interface PlaybackEngine {
    val events: Flow<EngineEvent>
    /** The id tagged onto events from the most recent [load]; 0 when the engine doesn't tag. */
    val mediaId: Long get() = 0
    suspend fun load(input: MediaInput, startPositionMs: Long)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun setVolume(v: Float)
    fun release()
    /** The track about to be loaded, for system media controls (notification, lock screen, MPRIS, SMTC). */
    fun onTrackChanged(track: Track?) {}
}
