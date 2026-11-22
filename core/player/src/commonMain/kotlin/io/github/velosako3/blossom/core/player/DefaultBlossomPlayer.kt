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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Queue + stream resolution + engine. Every command runs serialised under one mutex on [scope].
 *
 * Expired stream URLs (HTTP 403/410 from the engine) are re-resolved and reloaded at the last position, up to
 * [MAX_RERESOLVES] times; after that the track is reported as [PlaybackError.StreamFailed] and skipped.
 * Tracks with no playable input — unsaved while offline, a local file missing here — are skipped.
 *
 * Crossfade: with [fadeEngine] and a [crossfadeSeconds] setting above 0, the next track starts on the idle engine that
 * many seconds before the current one ends, the two volumes follow [CrossfadeCurve], and the engines swap roles.
 * At 0 (and between neighbours on one album) the next track's stream is resolved ahead so it starts at once.
 */
class DefaultBlossomPlayer(
    engine: PlaybackEngine,
    private val resolver: StreamResolver,
    private val network: NetworkMonitor,
    private val scope: CoroutineScope,
    private val isSavedOffline: (TrackId) -> Boolean = { false },
    private val localPath: (TrackId) -> String? = { null },
    private val radio: suspend (Track) -> List<Track> = { emptyList() },
    private val quality: () -> AudioQuality = { AudioQuality.HIGH },
    private val autoplaySimilar: () -> Boolean = { true },
    private val fadeEngine: PlaybackEngine? = null,
    private val crossfadeSeconds: () -> Int = { 0 },
) : BlossomPlayer {
    private val primary = engine
    /** The engine playing the current track; swaps with [fadeEngine] at each crossfade. */
    private var engine: PlaybackEngine = engine
    private val _activeEngine = MutableStateFlow(engine)
    /** The engine playing the current track (Android points its media session at it). */
    val activeEngine: StateFlow<PlaybackEngine> = _activeEngine.asStateFlow()
    private var volume = 1f
    private var fadeJob: Job? = null
    private var fadingOut: PlaybackEngine? = null
    private var preloadedFor: Int = -1

    private val queue = QueueManager()
    private val mutex = Mutex()
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var wantPlaying = false
    private var reResolves = 0
    private var lastPositionMs = 0L

    init {
        scope.launch {
            merge(*listOfNotNull(primary, fadeEngine).map { e -> e.events.map { e to it } }.toTypedArray()).collect { (from, event) ->
                mutex.withLock {
                    // Only the current track's engine speaks; the other one is idle or fading out.
                    if (from !== this@DefaultBlossomPlayer.engine) return@withLock
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
        endFade()
        wantPlaying = false
        _state.update { it.copy(isPlaying = false) }
        engine.pause()
    }

    override fun seekTo(positionMs: Long) = command {
        endFade()
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
    override fun setVolume(volume: Float) = command {
        this.volume = volume.coerceIn(0f, 1f)
        if (fadeJob?.isActive != true) engine.setVolume(this.volume)
    }

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
                approachingEnd(event.durationMs - event.positionMs)
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

    /** The track that plays after the current one ends on its own, without moving the queue. */
    private fun upNext(): Track? = when {
        queue.repeat == RepeatMode.ONE -> null
        queue.currentIndex < queue.queue.lastIndex -> queue.queue[queue.currentIndex + 1]
        queue.repeat == RepeatMode.ALL -> queue.queue.firstOrNull()
        else -> null
    }

    /** Near the end: start the crossfade, or resolve the next stream ahead so it starts at once. */
    private suspend fun approachingEnd(remainingMs: Long) {
        if (remainingMs <= 0 || fadeJob?.isActive == true || !wantPlaying) return
        val current = queue.current ?: return
        val next = upNext() ?: return
        val seconds = crossfadeSeconds()
        // The idle engine: after each crossfade the two swap roles.
        val fade = fadeEngine?.let { if (engine === primary) it else primary }
        if (fade != null && shouldCrossfade(current, next, seconds) && remainingMs <= seconds * 1000L) {
            val input = inputFor(next) ?: return // can't play it: the usual end-of-track skip handles it
            queue.next()
            reResolves = 0
            crossfadeTo(next, input, fade, minOf(seconds * 1000L, remainingMs))
            return
        }
        if (remainingMs <= PRELOAD_MS && preloadedFor != queue.currentIndex && next.id.kind != SourceKind.LOCAL && !isSavedOffline(next.id) && network.status.value.online) {
            preloadedFor = queue.currentIndex
            scope.launch { runCatching { resolver.resolve(next.id, quality()) } }
        }
    }

    private suspend fun crossfadeTo(next: Track, input: MediaInput, incoming: PlaybackEngine, fadeMs: Long) {
        val outgoing = engine
        engine = incoming
        _activeEngine.value = incoming
        fadingOut = outgoing
        lastPositionMs = 0
        _state.update { it.copy(positionMs = 0, durationMs = next.durationMs, error = null) }
        publish()
        incoming.setVolume(0f)
        incoming.onTrackChanged(next)
        incoming.load(input, 0)
        incoming.play()
        val level = volume
        fadeJob = scope.launch {
            var elapsed = 0L
            while (elapsed < fadeMs) {
                val (out, inc) = CrossfadeCurve.volumes(elapsed, fadeMs)
                outgoing.setVolume(out * level); incoming.setVolume(inc * level)
                delay(FADE_STEP_MS)
                elapsed += FADE_STEP_MS
            }
            mutex.withLock { if (fadingOut === outgoing) endFade() }
        }
    }

    /** Finishes a crossfade at once: the outgoing track stops, the current one plays at full volume. */
    private fun endFade() {
        val out = fadingOut ?: return
        fadingOut = null
        fadeJob?.cancel() // from inside the fade job too: only its own (finished) loop is cancelled
        fadeJob = null
        out.pause()
        out.setVolume(volume)
        engine.setVolume(volume)
    }

    /** Loads the current track, skipping forward past anything with no playable input. */
    private suspend fun loadCurrent(startMs: Long) {
        endFade()
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
        const val PRELOAD_MS = 20_000L
        const val FADE_STEP_MS = 50L
    }
}
