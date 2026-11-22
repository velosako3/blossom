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

    init {
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                _events.tryEmit(EngineEvent.Position(newTime, mediaPlayer.status().length().coerceAtLeast(0), eventsId))
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
        reader?.close()
        when (input) {
            // libVLC's own HTTP client fails its second (seek) connection inside the JVM, so remote bytes come from
            // our ranged OkHttp reader through vlcj callback media; libVLC never opens a socket.
            is MediaInput.Remote -> prepare(HttpRangeReader(input.stream).asSource(), options)
            is MediaInput.LocalFile -> { reader = null; mediaPlayer.media().prepare(input.path, *options) }
            is MediaInput.Offline -> {
                val r = try { offlineReader(input.trackId) } catch (e: Exception) { null }
                if (r == null) {
                    reader = null
                    _events.tryEmit(EngineEvent.Error(null, CacheCorruptedException("saved file missing or unreadable"), mediaId))
                    return
                }
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
    override fun seekTo(ms: Long) { mediaPlayer.submit { mediaPlayer.controls().setTime(ms) } }
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
