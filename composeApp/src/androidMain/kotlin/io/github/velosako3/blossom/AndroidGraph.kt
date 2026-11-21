// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.github.velosako3.blossom.core.designsystem.components.installImageLoader
import io.github.velosako3.blossom.core.database.androidDatabase
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.platform.AndroidNetworkMonitor
import io.github.velosako3.blossom.core.platform.AndroidSecureStore
import io.github.velosako3.blossom.core.platform.SharedPreferenceStore
import io.github.velosako3.blossom.core.player.BlossomPlaybackService
import io.github.velosako3.blossom.core.player.DefaultBlossomPlayer
import io.github.velosako3.blossom.core.player.Media3PlaybackEngine
import io.github.velosako3.blossom.core.player.PlayerHolder
import io.github.velosako3.blossom.core.player.StreamResolver
import io.github.velosako3.blossom.source.youtubemusic.YouTubeSource
import io.github.velosako3.blossom.source.youtubemusic.YouTubeStreams
import io.github.velosako3.blossom.source.youtubemusic.YtmSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Clock

/** Builds the Android object graph once per process and hands the player to the media session service. */
fun createAndroidGraph(context: Context): AppGraph {
    installImageLoader()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val secure = AndroidSecureStore(context)
    val prefs = SharedPreferenceStore(context)
    val network = AndroidNetworkMonitor(context)
    val streams = YouTubeStreams({ secure.get(YtmSource.COOKIE_KEY) }, context.cacheDir)
    val ytm = YtmSource(secure, context.cacheDir, streams = streams)
    val youtube = YouTubeSource(streams)
    val engine = Media3PlaybackEngine(context)
    val player = DefaultBlossomPlayer(
        engine = engine,
        resolver = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to ytm, SourceKind.YOUTUBE to youtube), Clock.System),
        network = network,
        scope = scope,
        radio = { ytm.radio(it.id) },
    )
    PlayerHolder.engine = engine
    PlayerHolder.player = player
    return AppGraph(player, androidDatabase(context), prefs.deviceId(), ytm, prefs, network, scope) { link ->
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Connecting a controller starts the media session service, which then runs the notification and lock screen. */
fun connectPlaybackService(context: Context): () -> Unit {
    val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, BlossomPlaybackService::class.java))).buildAsync()
    return { MediaController.releaseFuture(future) }
}
