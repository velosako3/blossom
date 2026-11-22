// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.offline.OfflineSettingsStore
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import io.github.velosako3.blossom.feature.library.local.MediaStoreLibrary

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
    val soundcloudClient = io.github.velosako3.blossom.source.soundcloud.SoundCloudClient()
    val soundcloud = io.github.velosako3.blossom.source.soundcloud.SoundCloudSource(soundcloudClient)
    val untitledClient = io.github.velosako3.blossom.source.untitled.UntitledClient()
    val untitled = io.github.velosako3.blossom.source.untitled.UntitledSource(untitledClient)
    val trees = prefs.folderList("local.trees")
    val permission = MutableStateFlow(!AndroidLocalBridge.hasAudioPermission(context))
    val localLibrary = MediaStoreLibrary(context, trees, scope) { AndroidLocalBridge.hasAudioPermission(context) }
    val local = LocalFilesHost(
        library = localLibrary,
        folders = trees,
        addFolder = { AndroidLocalBridge.pickFolder?.invoke() },
        removeFolder = { f -> prefs.setFolderList("local.trees", trees.value - f, trees) },
        needsPermission = permission,
        requestPermission = { AndroidLocalBridge.requestPermission?.invoke() },
    )
    AndroidLocalBridge.onFolderPicked = { uri -> prefs.setFolderList("local.trees", (trees.value + uri).distinct(), trees) }
    AndroidLocalBridge.onPermissionResult = { permission.value = !it; scope.launch { localLibrary.rescan() } }
    val db = androidDatabase(context)
    val resolver = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to ytm, SourceKind.YOUTUBE to youtube, SourceKind.SOUNDCLOUD to soundcloud, SourceKind.UNTITLED to untitled), Clock.System)
    // filesDir, not cacheDir: Android may purge the cache folder, and saved songs must stay.
    val offlineSettings = OfflineSettingsStore(prefs, defaultWifiOnly = true)
    val offline = offlineStore(db, java.io.File(context.filesDir, "offline"), secure, resolver, network, offlineSettings.settings, scope)
    io.github.velosako3.blossom.core.offline.DownloadWorker.install(context, offline.manager)
    val engine = Media3PlaybackEngine(context, offlineDataSource = io.github.velosako3.blossom.core.offline.EncryptedDataSource.Factory(offline))
    val player = DefaultBlossomPlayer(
        engine = engine,
        resolver = resolver,
        isSavedOffline = offline::isSaved,
        network = network,
        scope = scope,
        radio = { ytm.radio(it.id) },
        localPath = localLibrary::pathFor,
    )
    PlayerHolder.engine = engine
    PlayerHolder.player = player
    return AppGraph(player, db, prefs.deviceId(), ytm, prefs, network, scope, local, offline = offline, offlineSettings = offlineSettings,
        sources = mapOf(SourceKind.YOUTUBE to youtube, SourceKind.SOUNDCLOUD to soundcloud, SourceKind.UNTITLED to untitled), shareLink = { link ->
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    })
}

/** Connecting a controller starts the media session service, which then runs the notification and lock screen. */
fun connectPlaybackService(context: Context): () -> Unit {
    val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, BlossomPlaybackService::class.java))).buildAsync()
    return { MediaController.releaseFuture(future) }
}

/** Activity-owned launchers (permission, folder picker) that the app-scoped graph can trigger. */
object AndroidLocalBridge {
    var requestPermission: (() -> Unit)? = null
    var pickFolder: (() -> Unit)? = null
    var onPermissionResult: (Boolean) -> Unit = {}
    var onFolderPicked: (String) -> Unit = {}

    fun hasAudioPermission(context: Context): Boolean {
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
