// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.designsystem.components.installImageLoader
import io.github.velosako3.blossom.core.database.fileDatabase
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import io.github.velosako3.blossom.feature.library.local.FolderLocalLibrary
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
    val folders = prefs.folderList("local.folders")
    val localLibrary = FolderLocalLibrary(folders.map { l -> l.map(::File) }.stateIn(scope, SharingStarted.Eagerly, folders.value.map(::File)), File(cacheDir, "artwork"), scope)
    val local = LocalFilesHost(
        library = localLibrary,
        folders = folders,
        addFolder = {
            javax.swing.SwingUtilities.invokeLater {
                val chooser = javax.swing.JFileChooser().apply { fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY }
                if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                    prefs.setFolderList("local.folders", (folders.value + chooser.selectedFile.absolutePath).distinct(), folders)
                }
            }
        },
        removeFolder = { f -> prefs.setFolderList("local.folders", folders.value - f, folders) },
    )
    val engine = DesktopPlaybackEngine()
    val player = DefaultBlossomPlayer(
        engine = engine,
        resolver = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to ytm, SourceKind.YOUTUBE to youtube), Clock.System),
        network = network,
        scope = scope,
        radio = { ytm.radio(it.id) },
        localPath = localLibrary::pathFor,
    )
    SystemMediaControls(player, scope)
    // Debug hook for hands-on checks: search and play the first song at startup.
    System.getProperty("blossom.debugPlay")?.let { query ->
        scope.launch {
            val songs = runCatching { ytm.search(query).songs }.getOrElse { System.err.println("debugPlay search failed: $it"); emptyList() }
            System.err.println("debugPlay: ${songs.size} songs for '$query'")
            if (songs.isNotEmpty()) { player.setQueue(songs, 0); player.play() }
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { engine.release() } })
    return AppGraph(player, fileDatabase(File(dataDir, "blossom.db")), prefs.deviceId(), ytm, prefs, network, scope, local) { link ->
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null)
    }
}
