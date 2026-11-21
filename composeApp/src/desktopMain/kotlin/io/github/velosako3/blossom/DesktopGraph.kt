// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.designsystem.components.installImageLoader
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.platform.DesktopNetworkMonitor
import io.github.velosako3.blossom.core.platform.DesktopSecureStore
import io.github.velosako3.blossom.core.platform.FilePreferenceStore
import io.github.velosako3.blossom.core.player.DefaultBlossomPlayer
import io.github.velosako3.blossom.core.player.DesktopPlaybackEngine
import io.github.velosako3.blossom.core.player.StreamResolver
import io.github.velosako3.blossom.core.player.media.SystemMediaControls
import io.github.velosako3.blossom.source.youtubemusic.YouTubeSource
import io.github.velosako3.blossom.source.youtubemusic.YouTubeStreams
import io.github.velosako3.blossom.source.youtubemusic.YtmSource
import io.github.velosako3.blossom.vlc.VlcLoader
import io.github.velosako3.blossom.window.desktopAppDataDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlin.time.Clock

/** Builds the desktop object graph. libVLC's bundled natives are registered before the engine starts. */
fun createDesktopGraph(): AppGraph {
    VlcLoader.getVerifiedPath()
    installImageLoader()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val dataDir = desktopAppDataDir()
    val cacheDir = File(dataDir, "cache").apply { mkdirs() }
    val secure = DesktopSecureStore(File(dataDir, "secrets.properties"))
    val prefs = FilePreferenceStore(File(dataDir, "preferences.properties"))
    val network = DesktopNetworkMonitor(scope)
    val streams = YouTubeStreams({ secure.get(YtmSource.COOKIE_KEY) }, cacheDir)
    val ytm = YtmSource(secure, cacheDir, streams = streams)
    val youtube = YouTubeSource(streams)
    val engine = DesktopPlaybackEngine()
    val player = DefaultBlossomPlayer(
        engine = engine,
        resolver = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to ytm, SourceKind.YOUTUBE to youtube), Clock.System),
        network = network,
        scope = scope,
        radio = { ytm.radio(it.id) },
    )
    SystemMediaControls(player, scope)
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { engine.release() } })
    return AppGraph(player, ytm, prefs, network, scope) { link ->
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null)
    }
}
