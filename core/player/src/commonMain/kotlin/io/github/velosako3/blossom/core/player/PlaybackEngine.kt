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

sealed interface EngineEvent {
    data class Position(val positionMs: Long, val durationMs: Long) : EngineEvent
    data object Ended : EngineEvent
    data class Buffering(val buffering: Boolean) : EngineEvent
    /** [httpStatus] is set when the failure was an HTTP answer (403/410 mean an expired stream URL). */
    data class Error(val httpStatus: Int?, val cause: Throwable?) : EngineEvent
}

/** The platform audio engine: Media3 on Android, libVLC on desktop. */
interface PlaybackEngine {
    val events: Flow<EngineEvent>
    suspend fun load(input: MediaInput, startPositionMs: Long)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun setVolume(v: Float)
    fun release()
    /** The track about to be loaded, for system media controls (notification, lock screen, MPRIS, SMTC). */
    fun onTrackChanged(track: Track?) {}
}
