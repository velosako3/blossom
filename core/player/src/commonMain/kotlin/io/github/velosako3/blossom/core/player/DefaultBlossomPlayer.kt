// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.platform.NetworkMonitor
import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.TrackUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Queue + stream resolution + engine. Every command runs serialised under one mutex on [scope].
 *
 * Expired stream URLs (HTTP 403/410 from the engine) are re-resolved and reloaded at the last position, up to
 * [MAX_RERESOLVES] times; after that the track is reported as [PlaybackError.StreamFailed] and skipped.
 * Tracks with no playable input — unsaved while offline, a local file missing here — are skipped.
 */
class DefaultBlossomPlayer(
    private val engine: PlaybackEngine,
    private val resolver: StreamResolver,
    private val network: NetworkMonitor,
    private val scope: CoroutineScope,
    private val isSavedOffline: (TrackId) -> Boolean = { false },
    private val localPath: (TrackId) -> String? = { null },
    private val radio: suspend (Track) -> List<Track> = { emptyList() },
    private val quality: () -> AudioQuality = { AudioQuality.HIGH },
    private val autoplaySimilar: () -> Boolean = { true },
) : BlossomPlayer {
    private val queue = QueueManager()
    private val mutex = Mutex()
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var wantPlaying = false
    private var reResolves = 0
    private var lastPositionMs = 0L

    init {
        scope.launch {
            engine.events.collect { event ->
                mutex.withLock {
                    // One bad event (an engine call that throws) must not stop the player from hearing the rest.
                    try { onEvent(event) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                }
            }
        }
    }

    override suspend fun setQueue(tracks: List<Track>, startIndex: Int, startPositionMs: Long) = mutex.withLock {
        queue.setQueue(tracks, startIndex)
        _state.update { it.copy(error = null) }
        reResolves = 0
        loadCurrent(startPositionMs)
    }

    override fun play() = command {
        if (queue.current == null) return@command
        wantPlaying = true
        _state.update { it.copy(isPlaying = true, error = null) }
        engine.play()
    }

    override fun pause() = command {
        wantPlaying = false
        _state.update { it.copy(isPlaying = false) }
        engine.pause()
    }

    override fun seekTo(positionMs: Long) = command {
        lastPositionMs = positionMs
        _state.update { it.copy(positionMs = positionMs) }
        engine.seekTo(positionMs)
    }

    override fun next() = command { advance(userInitiated = true) }

    override fun previous() = command {
        when (val move = queue.previous(_state.value.positionMs)) {
            QueueMove.Restart -> { lastPositionMs = 0; _state.update { it.copy(positionMs = 0) }; engine.seekTo(0) }
            is QueueMove.Moved -> { reResolves = 0; loadCurrent(0) }
            QueueMove.End -> Unit
        }
    }

    override fun skipTo(index: Int) = command {
        if (index in queue.queue.indices) { queue.jumpTo(index); reResolves = 0; _state.update { it.copy(error = null) }; loadCurrent(0) }
    }

    override fun setShuffle(enabled: Boolean) = command { queue.setShuffle(enabled); publish() }
    override fun setRepeat(mode: RepeatMode) = command { queue.repeat = mode; publish() }
    override fun addNext(track: Track) = command { queue.addNext(track); publish() }
    override fun addToQueue(tracks: List<Track>) = command { queue.addToQueue(tracks); publish() }
    override fun moveInQueue(from: Int, to: Int) = command { queue.move(from, to); publish() }
    override fun removeFromQueue(index: Int) = command {
        val wasCurrent = index == queue.currentIndex
        queue.remove(index)
        if (wasCurrent) loadCurrent(0) else publish()
    }
    override fun setVolume(volume: Float) = engine.setVolume(volume.coerceIn(0f, 1f))

    private fun command(block: suspend () -> Unit) {
        scope.launch { mutex.withLock { block() } }
    }

    private suspend fun onEvent(event: EngineEvent) {
        // Still queued from the media before the last load: ignore (a stale Ended would skip a song).
        if (event.mediaId != 0L && event.mediaId != engine.mediaId) return
        when (event) {
            is EngineEvent.Position -> {
                // Playing again after a recovery: the next expiry starts a fresh count ("2 consecutive failures").
                if (event.positionMs > 0) reResolves = 0
                lastPositionMs = event.positionMs
                _state.update { it.copy(positionMs = event.positionMs, durationMs = event.durationMs.takeIf { d -> d > 0 } ?: it.durationMs) }
            }
            is EngineEvent.Buffering -> _state.update { it.copy(isBuffering = event.buffering) }
            is EngineEvent.Ended -> advance(userInitiated = false)
            is EngineEvent.Error -> onError(event)
        }
    }

    private suspend fun onError(event: EngineEvent.Error) {
        val track = queue.current ?: return
        if (reResolves < MAX_RERESOLVES && track.id.kind != SourceKind.LOCAL && !isSavedOffline(track.id)) {
            reResolves++
            resolver.invalidate(track.id)
            loadCurrent(lastPositionMs)
            return
        }
        _state.update { it.copy(error = PlaybackError.StreamFailed(track.id, event.cause?.message ?: event.httpStatus?.let { s -> "HTTP $s" })) }
        reResolves = 0
        // userInitiated: even with repeat ONE a dead stream moves on instead of retrying forever.
        advance(userInitiated = true, keepError = true)
    }

    private suspend fun advance(userInitiated: Boolean, keepError: Boolean = false) {
        if (!keepError) _state.update { it.copy(error = null) }
        when (queue.next(userInitiated)) {
            is QueueMove.Moved -> { reResolves = 0; loadCurrent(0) }
            QueueMove.Restart -> Unit
            QueueMove.End -> {
                val last = queue.current
                val more = if (last != null && queue.repeat == RepeatMode.OFF && autoplaySimilar()) {
                    runCatching { radio(last) }.onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyList())
                        .filter { candidate -> queue.queue.none { it.id == candidate.id } }
                } else emptyList()
                if (more.isNotEmpty()) {
                    queue.append(more)
                    queue.next()
                    reResolves = 0
                    loadCurrent(0)
                } else {
                    wantPlaying = false
                    engine.pause()
                    _state.update { it.copy(isPlaying = false, positionMs = 0) }
                    publish()
                }
            }
        }
    }

    /** Loads the current track, skipping forward past anything with no playable input. */
    private suspend fun loadCurrent(startMs: Long) {
        var attempts = queue.queue.size
        while (attempts-- > 0) {
            val track = queue.current ?: break
            val input = inputFor(track)
            if (input != null) {
                lastPositionMs = startMs
                _state.update { it.copy(positionMs = startMs, durationMs = track.durationMs) }
                publish()
                engine.onTrackChanged(track)
                engine.load(input, startMs)
                if (wantPlaying) engine.play()
                return
            }
            if (queue.next(userInitiated = true) !is QueueMove.Moved || queue.currentIndex == 0 && queue.repeat == RepeatMode.OFF) break
        }
        wantPlaying = false
        engine.pause()
        _state.update { it.copy(isPlaying = false) }
        publish()
    }

    private suspend fun inputFor(track: Track): MediaInput? {
        if (track.id.kind == SourceKind.LOCAL) return localPath(track.id)?.let { MediaInput.LocalFile(it) }
        if (isSavedOffline(track.id)) return MediaInput.Offline(track.id)
        if (!network.status.value.online) return null
        return try {
            MediaInput.Remote(resolver.resolve(track.id, quality()))
        } catch (e: TrackUnavailableException) {
            _state.update { it.copy(error = PlaybackError.Unavailable(track.id, e.message)) }
            null
        }
    }

    private fun publish() {
        _state.update {
            it.copy(queue = queue.queue, currentIndex = queue.currentIndex, shuffle = queue.shuffled, repeat = queue.repeat, isPlaying = wantPlaying)
        }
    }

    private companion object {
        const val MAX_RERESOLVES = 2
    }
}
