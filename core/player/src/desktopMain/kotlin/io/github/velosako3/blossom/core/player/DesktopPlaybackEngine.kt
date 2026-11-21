// SPDX-License-Identifier: AGPL-3.0-or-later
// Based on Spotube v6 (https://github.com/KRTirtho/spotube, AGPL-3.0-or-later),
// composeApp/src/jvmMain/kotlin/org/spotube/spotube/core/audioplayer/AudioPlayer.jvm.kt,
// Copyright (C) 2026 Kingkor Roy Tirtho and Spotube Contributors.
// Modified for blossom, 2026: one item per player (blossom owns the queue), events mapped to EngineEvent,
// remote streams fed through vlcj callback media from a ranged OkHttp reader.
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.callback.seekable.SeekableCallbackMedia
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

/**
 * libVLC through vlcj, bundled with the app (see composeApp's vlcj-bundler setup). [offlineUrl] maps a saved track
 * to its loopback URL on the encrypted-cache server (Task 5.3).
 */
class DesktopPlaybackEngine(
    private val offlineUrl: (TrackId) -> String? = { null },
) : PlaybackEngine {
    @Volatile private var reader: HttpRangeReader? = null
    @Volatile override var mediaId: Long = 0
        private set
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
                _events.tryEmit(EngineEvent.Position(newTime, mediaPlayer.status().length().coerceAtLeast(0), mediaId))
            }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                _events.tryEmit(EngineEvent.Buffering(newCache < 100f, mediaId))
            }
            override fun playing(mediaPlayer: MediaPlayer) { _events.tryEmit(EngineEvent.Buffering(false, mediaId)) }
            override fun finished(mediaPlayer: MediaPlayer) { _events.tryEmit(EngineEvent.Ended(mediaId)) }
            override fun error(mediaPlayer: MediaPlayer) {
                _events.tryEmit(EngineEvent.Error(reader?.lastStatus?.takeIf { it >= 400 }, null, mediaId))
            }
        })
    }

    override suspend fun load(input: MediaInput, startPositionMs: Long) {
        val options = if (startPositionMs > 0) arrayOf(":start-time=${startPositionMs / 1000.0}") else emptyArray()
        mediaId++
        when (input) {
            // libVLC's own HTTP client fails its second (seek) connection inside the JVM, so remote bytes come from
            // our ranged OkHttp reader through vlcj callback media; libVLC never opens a socket.
            is MediaInput.Remote -> {
                val r = HttpRangeReader(input.stream).also { reader = it }
                mediaPlayer.media().prepare(ReaderMedia(r).also { media = it }, *options)
            }
            is MediaInput.LocalFile -> { reader = null; mediaPlayer.media().prepare(input.path, *options) }
            is MediaInput.Offline -> {
                reader = null
                val url = offlineUrl(input.trackId) ?: run { _events.tryEmit(EngineEvent.Error(null, IllegalStateException("not saved"))); return }
                mediaPlayer.media().prepare(url, *options)
            }
        }
    }

    override fun play() { mediaPlayer.submit { mediaPlayer.controls().play() } }
    override fun pause() { mediaPlayer.submit { mediaPlayer.controls().setPause(true) } }
    override fun seekTo(ms: Long) { mediaPlayer.submit { mediaPlayer.controls().setTime(ms) } }
    override fun setVolume(v: Float) { mediaPlayer.submit { mediaPlayer.audio().setVolume((v * 100).toInt().coerceIn(0, 200)) } }

    override fun release() {
        mediaPlayer.release()
        factory.release()
    }
}

/** Hands [HttpRangeReader] bytes to libVLC. */
private class ReaderMedia(private val reader: HttpRangeReader) : SeekableCallbackMedia() {
    override fun onGetSize(): Long = reader.size()
    override fun onOpen(): Boolean = true
    override fun onRead(buffer: ByteArray, bufferSize: Int): Int = reader.read(buffer, bufferSize)
    override fun onSeek(offset: Long): Boolean = reader.seek(offset)
    override fun onClose() = reader.close()
}
