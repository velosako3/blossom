// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.velosako3.blossom.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * ExoPlayer playing one item at a time (the queue lives in [DefaultBlossomPlayer]). Stream headers from
 * [io.github.velosako3.blossom.source.api.StreamInfo] are attached per URL through a resolving data source.
 * [offlineDataSource] plays [MediaInput.Offline] from the encrypted cache (Task 5.3).
 */
@OptIn(UnstableApi::class)
class Media3PlaybackEngine(
    context: Context,
    private val offlineDataSource: DataSource.Factory? = null,
) : PlaybackEngine {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val headers = ConcurrentHashMap<String, Map<String, String>>()
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<EngineEvent> = _events

    private val http = OkHttpDataSource.Factory(OkHttpClient())
    private val upstream = DefaultDataSource.Factory(context, ResolvingDataSource.Factory(http) { spec ->
        headers[spec.uri.toString()]?.let { spec.withAdditionalHeaders(it) } ?: spec
    })
    private val factory = DataSource.Factory {
        val offline = offlineDataSource
        if (offline == null) upstream.createDataSource() else RoutingDataSource(upstream.createDataSource(), offline.createDataSource())
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
        .setHandleAudioBecomingNoisy(false) // handled by BlossomPlaybackService through AudioFocusPolicy
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private var metadata: MediaMetadata = MediaMetadata.EMPTY
    @Volatile override var mediaId: Long = 0
        private set
    /** Switches on the item transition, so callbacks still queued for the old item keep the old id. */
    @Volatile private var eventsId: Long = 0

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> _events.tryEmit(EngineEvent.Buffering(true, eventsId))
                    Player.STATE_READY -> _events.tryEmit(EngineEvent.Buffering(false, eventsId))
                    Player.STATE_ENDED -> _events.tryEmit(EngineEvent.Ended(eventsId))
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { eventsId = mediaId }

            override fun onPlayerError(error: PlaybackException) {
                val status = generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
                _events.tryEmit(EngineEvent.Error(status, error, eventsId))
            }
        })
        scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    _events.tryEmit(EngineEvent.Position(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0), mediaId))
                }
                delay(250)
            }
        }
    }

    override fun onTrackChanged(track: Track?) {
        metadata = track?.let {
            MediaMetadata.Builder()
                .setTitle(it.title)
                .setArtist(it.artistNames)
                .setAlbumTitle(it.album?.title)
                .setArtworkUri(it.artworkUrl?.let(Uri::parse))
                .build()
        } ?: MediaMetadata.EMPTY
    }

    override suspend fun load(input: MediaInput, startPositionMs: Long) = withContext(Dispatchers.Main) {
        val uri = when (input) {
            is MediaInput.Remote -> input.stream.url.also { headers.clear(); headers[it] = input.stream.headers }
            is MediaInput.LocalFile -> if (input.path.startsWith("content://")) input.path else Uri.fromFile(java.io.File(input.path)).toString()
            is MediaInput.Offline -> "$OFFLINE_SCHEME://${input.trackId.value}"
        }
        mediaId++
        exoPlayer.setMediaItem(MediaItem.Builder().setUri(uri).setMediaMetadata(metadata).build(), startPositionMs)
        exoPlayer.prepare()
    }

    override fun play() = onMain { exoPlayer.play() }
    override fun pause() = onMain { exoPlayer.pause() }
    override fun seekTo(ms: Long) = onMain { exoPlayer.seekTo(ms) }
    override fun setVolume(v: Float) = onMain { exoPlayer.volume = v }
    override fun release() = onMain { scope.cancel(); exoPlayer.release() }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        const val OFFLINE_SCHEME = "blossom-offline"
    }
}

/** Sends `blossom-offline://` URIs to the encrypted cache and everything else upstream. */
@OptIn(UnstableApi::class)
private class RoutingDataSource(private val upstream: DataSource, private val offline: DataSource) : DataSource {
    private var active: DataSource? = null
    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        upstream.addTransferListener(transferListener); offline.addTransferListener(transferListener)
    }
    override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
        val source = if (dataSpec.uri.scheme == Media3PlaybackEngine.OFFLINE_SCHEME) offline else upstream
        active = source
        return source.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active!!.read(buffer, offset, length)
    override fun getUri(): Uri? = active?.uri
    override fun close() { active?.close(); active = null }
}
