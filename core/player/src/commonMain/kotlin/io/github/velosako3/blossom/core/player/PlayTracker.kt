// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.PlayEvent
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.sync.OperationJson
import io.github.velosako3.blossom.core.platform.PreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Records every listening session ([PlayEvent]): listening time grows only while audio plays (not paused, buffering
 * or seeking). A play ends on a track change, stop, queue clear or [finish]; it counts toward stats when it lasted 30 s
 * or reached the track's natural end. The play in progress is checkpointed every 15 s, and a checkpoint left by a crash
 * is recorded on the next start.
 */
class PlayTracker(
    state: StateFlow<PlaybackState>,
    /** The playhead ([BlossomPlayer.progress]); it ticks apart from [state]. */
    position: kotlinx.coroutines.flow.Flow<Long>,
    private val record: suspend (PlayEvent) -> Unit,
    private val clock: Clock,
    private val deviceId: String,
    private val prefs: PreferenceStore,
    scope: CoroutineScope,
) {
    private class Current(
        val id: String,
        val track: Track,
        val index: Int,
        val startedAt: Instant,
        var playedMs: Long = 0,
        var playingSince: Instant? = null,
        var lastPosition: Long = 0,
        var durationMs: Long = 0,
    )

    private val lock = Mutex()
    private var current: Current? = null
    /** While on, nothing is tracked (Wrapped's preview of your top song is not a play). */
    private var ignoring = false

    init {
        scope.launch {
            recoverCheckpoint()
            launch {
                while (true) {
                    delay(CHECKPOINT_MS)
                    lock.withLock { current?.let(::checkpoint) }
                }
            }
            kotlinx.coroutines.flow.combine(state, position) { s, p -> s to p }.collect { (s, p) -> lock.withLock { onState(s, p) } }
        }
    }

    /** Ends the play in progress (app shutdown). */
    suspend fun finish() = lock.withLock { current?.let { finalize(it) }; current = null }

    /** Stops (true) or resumes (false) tracking; the play in progress when it stops is recorded. */
    suspend fun setIgnoring(ignore: Boolean) = lock.withLock {
        if (ignore) { current?.let { finalize(it) }; current = null }
        ignoring = ignore
    }

    private suspend fun onState(s: PlaybackState, positionMs: Long) {
        if (ignoring) return
        val now = clock.now()
        val track = s.current
        current?.let { c ->
            val nearEnd = c.durationMs > 0 && c.lastPosition >= c.durationMs - END_SLACK_MS
            val restarted = track?.id == c.track.id && s.currentIndex == c.index && nearEnd && positionMs < END_SLACK_MS // repeat one
            if (track == null || track.id != c.track.id || s.currentIndex != c.index || restarted) {
                finalize(c, reachedEnd = nearEnd)
                current = null
            }
        }
        if (current == null && track != null) current = Current(Uuid.random().toString(), track, s.currentIndex, now)
        val c = current ?: return
        val playing = s.isPlaying && !s.isBuffering
        if (playing && c.playingSince == null) c.playingSince = now
        if (!playing) c.playingSince?.let { since -> c.playedMs += (now - since).inWholeMilliseconds; c.playingSince = null }
        c.lastPosition = positionMs
        c.durationMs = s.durationMs.takeIf { it > 0 } ?: c.track.durationMs
    }

    private fun listened(c: Current): Long = c.playedMs + (c.playingSince?.let { (clock.now() - it).inWholeMilliseconds } ?: 0)

    /**
     * Records the play, then clears its checkpoint (recording twice is harmless: plays are keyed by ID). A play with
     * no listening at all (skipped while paused) isn't recorded. A failed record never stops tracking.
     */
    private suspend fun finalize(c: Current, reachedEnd: Boolean = false) {
        val ms = listened(c)
        if (ms > 0) {
            try {
                record(PlayEvent(c.id, c.track, c.startedAt, ms, counted = ms >= COUNT_MS || reachedEnd, deviceId = deviceId))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Kept as the checkpoint, recorded on the next start.
                prefs.put(CHECKPOINT_KEY, OperationJson.encodeToString(PlayEvent.serializer(), PlayEvent(c.id, c.track, c.startedAt, ms, false, deviceId)))
                return
            }
        }
        prefs.put(CHECKPOINT_KEY, null)
    }

    private fun checkpoint(c: Current) {
        if (listened(c) == 0L) return
        val e = PlayEvent(c.id, c.track, c.startedAt, listened(c), counted = false, deviceId = deviceId)
        prefs.put(CHECKPOINT_KEY, OperationJson.encodeToString(PlayEvent.serializer(), e))
    }

    private suspend fun recoverCheckpoint() {
        val json = prefs.get(CHECKPOINT_KEY) ?: return
        prefs.put(CHECKPOINT_KEY, null)
        val e = runCatching { OperationJson.decodeFromString(PlayEvent.serializer(), json) }.getOrNull() ?: return
        if (e.playedMs > 0) runCatching { record(e.copy(counted = e.playedMs >= COUNT_MS)) }
    }

    companion object {
        const val COUNT_MS = 30_000L
        private const val CHECKPOINT_MS = 15_000L
        private const val END_SLACK_MS = 1_500L
        private const val CHECKPOINT_KEY = "stats.playCheckpoint"
    }
}
