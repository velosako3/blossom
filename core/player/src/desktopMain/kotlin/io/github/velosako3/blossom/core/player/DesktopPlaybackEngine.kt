// SPDX-License-Identifier: AGPL-3.0-or-later
// Based on Spotube v6 (https://github.com/KRTirtho/spotube, AGPL-3.0-or-later),
// composeApp/src/jvmMain/kotlin/org/spotube/spotube/core/audioplayer/AudioPlayer.jvm.kt,
// Copyright (C) 2026 Kingkor Roy Tirtho and Spotube Contributors.
// Modified for blossom, 2026: one item per player (blossom owns the queue), events mapped to EngineEvent,
// remote streams fed through vlcj callback media from a ranged OkHttp reader.
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.offline.CacheCorruptedException
import io.github.velosako3.blossom.core.offline.DecryptingCursor
import io.github.velosako3.blossom.core.offline.DecryptingReader
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.callback.seekable.SeekableCallbackMedia
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

/**
 * libVLC through vlcj, bundled with the app (see composeApp's vlcj-bundler setup). [offlineUrl] maps a saved track
 * through a decrypting cursor over the encrypted cache (no plaintext file, no local server).
 */
class DesktopPlaybackEngine(
    /** Opens a saved track from the encrypted cache; it is decrypted chunk by chunk as libVLC reads. */
    private val offlineReader: (TrackId) -> DecryptingReader? = { null },
) : PlaybackEngine {
    @Volatile private var reader: ByteSource? = null
    /** The current HLS stream, which seeks by restarting at a segment; positions add [hlsOffsetMs]. */
    @Volatile private var hls: HlsReader? = null
    @Volatile private var hlsOffsetMs = 0L

    private fun hlsTime(vlcTime: Long): Long {
        if (hls == null || hlsOffsetMs == 0L) return vlcTime
        return if (hls?.absoluteTimes == true) vlcTime else vlcTime + hlsOffsetMs
    }
    @Volatile override var mediaId: Long = 0
        private set
    /** The id of the media libVLC is actually reporting on; switches in `mediaChanged`, in order with its events. */
    @Volatile private var eventsId: Long = 0
    /** libVLC calls into this through JNA; it must stay strongly reachable while it plays. */
    @Volatile private var media: ReaderMedia? = null
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<EngineEvent> = _events

    private val factory = MediaPlayerFactory(
        null as NativeDiscovery?,
        "--no-video", "--vout=dummy", "--aout=any", "--no-osd", "--no-snapshot-preview", "--quiet",
    )
    val mediaPlayer: MediaPlayer = factory.mediaPlayers().newMediaPlayer()

    private val nativeLog = if (System.getProperty("blossom.vlcLog") == "true") factory.application().newLog().apply {
        level = uk.co.caprica.vlcj.log.LogLevel.DEBUG
        addLogListener { level, module, _, _, _, _, _, message -> System.err.println("vlc[$level/$module] $message") }
    } else null

    /** From libVLC's lengthChanged event: event handlers must never call back into libVLC (see [init]). */
    @Volatile private var lengthMs: Long = 0

    init {
        // These run on libVLC's own event thread. Calling libVLC from here deadlocks against a track change: set_media
        // holds the player lock and waits for this thread (it froze the app on "next", and then on quit).
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) { lengthMs = newLength }
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                _events.tryEmit(EngineEvent.Position(hlsTime(newTime), hls?.durationMs ?: lengthMs.coerceAtLeast(0), eventsId))
            }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                _events.tryEmit(EngineEvent.Buffering(newCache < 100f, eventsId))
            }
            override fun playing(mediaPlayer: MediaPlayer) { _events.tryEmit(EngineEvent.Buffering(false, eventsId)) }
            override fun mediaChanged(mediaPlayer: MediaPlayer, media: uk.co.caprica.vlcj.media.MediaRef) { eventsId = mediaId }
            override fun finished(mediaPlayer: MediaPlayer) { _events.tryEmit(EngineEvent.Ended(eventsId)) }
            override fun error(mediaPlayer: MediaPlayer) {
                _events.tryEmit(EngineEvent.Error(reader?.lastStatus?.takeIf { it >= 400 }, null, eventsId))
            }
        })
    }

    override suspend fun load(input: MediaInput, startPositionMs: Long) {
        val options = if (startPositionMs > 0) arrayOf(":start-time=${startPositionMs / 1000.0}") else emptyArray()
        mediaId++
        lengthMs = 0
        reader?.close()
        when (input) {
            // libVLC's own HTTP client fails its second (seek) connection inside the JVM, so remote bytes come from
            // our ranged OkHttp reader through vlcj callback media; libVLC never opens a socket.
            is MediaInput.Remote -> if (input.stream.mimeType.contains("mpegurl", ignoreCase = true)) {
                // HLS (SoundCloud): the playlist and segment sizes are fetched up front, off the caller's thread.
                val stream = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { HlsReader(input.stream) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _events.tryEmit(EngineEvent.Error(null, e, mediaId)); return
                }
                hls = stream
                startHlsAt(stream, startPositionMs)
            } else { hls = null; hlsOffsetMs = 0; prepare(HttpRangeReader(input.stream).asSource(), options) }
            is MediaInput.LocalFile -> { hls = null; hlsOffsetMs = 0; reader = null; mediaPlayer.media().prepare(input.path, *options) }
            is MediaInput.Offline -> {
                val r = try { offlineReader(input.trackId) } catch (e: Exception) { null }
                if (r == null) {
                    reader = null
                    _events.tryEmit(EngineEvent.Error(null, CacheCorruptedException("saved file missing or unreadable"), mediaId))
                    return
                }
                hls = null; hlsOffsetMs = 0
                prepare(DecryptingCursor(r).asSource(), options)
            }
        }
    }

    private fun prepare(source: ByteSource, options: Array<String>) {
        reader = source
        val id = mediaId
        // A broken chunk can't throw into libVLC's native callback: report it and end the stream.
        val onError: (Exception) -> Unit = { e -> _events.tryEmit(EngineEvent.Error(null, e, id)) }
        mediaPlayer.media().prepare(ReaderMedia(source, onError).also { media = it }, *options)
    }

    override fun play() { mediaPlayer.submit { mediaPlayer.controls().play() } }
    override fun pause() { mediaPlayer.submit { mediaPlayer.controls().setPause(true) } }
    override fun seekTo(ms: Long) {
        val stream = hls
        if (stream == null) { mediaPlayer.submit { mediaPlayer.controls().setTime(ms) }; return }
        // libVLC can't seek concatenated HLS segments by bytes: restart at the segment that holds [ms].
        mediaPlayer.submit {
            if (hls !== stream) return@submit // the track changed before this ran
            val playing = mediaPlayer.status().isPlaying
            startHlsAt(stream, ms)
            if (playing) mediaPlayer.controls().play()
        }
    }

    /**
     * Starts [stream] at the segment boundary nearest [ms] (libVLC ignores `:start-time` on these slices; SoundCloud
     * segments are ~10 s, so a seek lands within ~5 s).
     */
    private fun startHlsAt(stream: HlsReader, ms: Long) {
        var segment = stream.segmentAt(ms)
        val next = stream.segmentStartMs(segment + 1)
        if (next - ms < ms - stream.segmentStartMs(segment) && next < stream.durationMs) segment++
        hlsOffsetMs = stream.segmentStartMs(segment)
        prepare(stream.from(segment).asSource(), emptyArray())
    }
    override fun setEqualizer(settings: io.github.velosako3.blossom.core.player.eq.EqSettings) {
        mediaPlayer.submit {
            if (!settings.enabled) { mediaPlayer.audio().setEqualizer(null); return@submit }
            val bands = factory.equalizer().bands().map { it.toInt() }.toIntArray()
            val gains = io.github.velosako3.blossom.core.player.eq.mapBands(settings.gains.toFloatArray(), bands)
            mediaPlayer.audio().setEqualizer(factory.equalizer().newEqualizer().apply { setPreamp(settings.preampDb); setAmps(gains) })
        }
    }

    override fun setVolume(v: Float) { mediaPlayer.submit { mediaPlayer.audio().setVolume((v * 100).toInt().coerceIn(0, 200)) } }

    override fun release() {
        mediaPlayer.release()
        factory.release()
        reader?.close()
    }
}

/** Bytes libVLC reads through callback media: a ranged HTTP stream or a decrypting cursor over the cache. */
private interface ByteSource {
    fun size(): Long
    fun read(buffer: ByteArray, length: Int): Int
    fun seek(offset: Long): Boolean
    fun close()
    /** The last HTTP status, for re-resolving expired streams (403/410); null for the cache. */
    val lastStatus: Int? get() = null
}

private fun HttpRangeReader.asSource() = object : ByteSource {
    override val lastStatus: Int? get() = this@asSource.lastStatus
    override fun size() = this@asSource.size()
    override fun read(buffer: ByteArray, length: Int) = this@asSource.read(buffer, length)
    override fun seek(offset: Long) = this@asSource.seek(offset)
    override fun close() = this@asSource.close()
}

private fun HlsReader.asSource() = object : ByteSource {
    override fun size() = this@asSource.size()
    override fun read(buffer: ByteArray, length: Int) = this@asSource.read(buffer, length)
    override fun seek(offset: Long) = this@asSource.seek(offset)
    override fun close() = this@asSource.close()
}

private fun DecryptingCursor.asSource() = object : ByteSource {
    override fun size() = this@asSource.size
    override fun read(buffer: ByteArray, length: Int) = this@asSource.read(buffer, length)
    override fun seek(offset: Long) = this@asSource.seek(offset)
    override fun close() = this@asSource.close()
}

/** Hands [ByteSource] bytes to libVLC. */
private class ReaderMedia(private val reader: ByteSource, private val onError: (Exception) -> Unit) : SeekableCallbackMedia() {
    override fun onGetSize(): Long = reader.size()
    override fun onOpen(): Boolean = true
    override fun onRead(buffer: ByteArray, bufferSize: Int): Int =
        try { reader.read(buffer, bufferSize) } catch (e: Exception) { onError(e); -1 }
    override fun onSeek(offset: Long): Boolean = reader.seek(offset)
    override fun onClose() = reader.close()
}
