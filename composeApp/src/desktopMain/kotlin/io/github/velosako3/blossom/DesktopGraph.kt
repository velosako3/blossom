// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.offline.OfflineSettingsStore
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
    val soundcloudClient = io.github.velosako3.blossom.source.soundcloud.SoundCloudClient()
    val soundcloud = io.github.velosako3.blossom.source.soundcloud.SoundCloudSource(soundcloudClient)
    val untitledClient = io.github.velosako3.blossom.source.untitled.UntitledClient()
    val untitled = io.github.velosako3.blossom.source.untitled.UntitledSource(untitledClient)
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
    val db = fileDatabase(File(dataDir, "blossom.db"))
    val resolver = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to ytm, SourceKind.YOUTUBE to youtube, SourceKind.SOUNDCLOUD to soundcloud, SourceKind.UNTITLED to untitled), Clock.System)
    val offlineSettings = OfflineSettingsStore(prefs, defaultWifiOnly = false)
    val offline = offlineStore(db, File(dataDir, "offline"), secure, resolver, network, offlineSettings.settings, scope)
    val engine = DesktopPlaybackEngine(offlineReader = offline::openReader)
    // The second engine plays the incoming track during a crossfade.
    val fadeEngine = DesktopPlaybackEngine(offlineReader = offline::openReader)
    val player = DefaultBlossomPlayer(
        engine = engine,
        resolver = resolver,
        isSavedOffline = offline::isSaved,
        network = network,
        scope = scope,
        radio = { ytm.radio(it.id) },
        localPath = localLibrary::pathFor,
        fadeEngine = fadeEngine,
        crossfadeSeconds = { prefs.get(CROSSFADE_KEY)?.toIntOrNull() ?: 0 },
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
    val graph = AppGraph(
        player, db, prefs.deviceId(), ytm, prefs, network, scope, local,
        shareLink = { link -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null) },
        localLyrics = io.github.velosako3.blossom.feature.lyrics.FileLocalLyricsSource(localLibrary::pathFor),
        desktop = true,
        offline = offline,
        offlineSettings = offlineSettings,
        sources = mapOf(SourceKind.YOUTUBE to youtube, SourceKind.SOUNDCLOUD to soundcloud, SourceKind.UNTITLED to untitled),
        importers = mapOf(
            io.github.velosako3.blossom.feature.importer.ImportService.SPOTIFY to io.github.velosako3.blossom.source.spotify.SpotifyImporter(auth = null),
            io.github.velosako3.blossom.feature.importer.ImportService.YOUTUBE_MUSIC to io.github.velosako3.blossom.source.youtubemusic.YtmImporter(ytm),
            io.github.velosako3.blossom.feature.importer.ImportService.SOUNDCLOUD to io.github.velosako3.blossom.source.soundcloud.SoundCloudImporter(soundcloudClient),
            io.github.velosako3.blossom.feature.importer.ImportService.UNTITLED to io.github.velosako3.blossom.source.untitled.UntitledImporter(untitledClient),
        ),
        syncSetup = desktopSyncSetup(secure),
        secure = secure,
    )
    Runtime.getRuntime().addShutdownHook(Thread {
        // The play in progress is recorded before the process ends.
        runCatching { kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeoutOrNull(2_000) { graph.playTracker.finish() } } }
        runCatching { engine.release() }
        runCatching { fadeEngine.release() }
    })
    return graph
}

private fun desktopSyncSetup(secure: io.github.velosako3.blossom.core.platform.SecureStore): SyncSetup {
    val googleId = BlossomSecrets.GOOGLE_OAUTH_DESKTOP_CLIENT_ID
    val dropboxKey = BlossomSecrets.DROPBOX_APP_KEY
    val kinds = io.github.velosako3.blossom.feature.sync.ui.BackendKind.entries.filter {
        when (it) {
            io.github.velosako3.blossom.feature.sync.ui.BackendKind.GOOGLE_DRIVE -> googleId.isNotEmpty()
            io.github.velosako3.blossom.feature.sync.ui.BackendKind.DROPBOX -> dropboxKey.isNotEmpty()
            else -> true
        }
    }
    return SyncSetup(
        secure = secure,
        defaultName = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() && it != "localhost" } ?: System.getProperty("os.name"),
        kinds = kinds,
        connect = { c, interactive ->
            when (c.kind) {
                io.github.velosako3.blossom.feature.sync.ui.BackendKind.FOLDER -> io.github.velosako3.blossom.feature.sync.backends.LocalFolderBackend(File(c.location))
                io.github.velosako3.blossom.feature.sync.ui.BackendKind.WEBDAV ->
                    io.github.velosako3.blossom.feature.sync.backends.WebDavBackend(c.location, c.username) { secure.get(io.github.velosako3.blossom.feature.sync.ui.SyncController.PASSWORD) }
                io.github.velosako3.blossom.feature.sync.ui.BackendKind.GOOGLE_DRIVE -> {
                    val o = io.github.velosako3.blossom.feature.sync.backends.OAuthClient(
                        io.github.velosako3.blossom.feature.sync.backends.OAuthConfig.googleDrive(googleId, BlossomSecrets.GOOGLE_OAUTH_DESKTOP_CLIENT_SECRET), secure, "gdrive")
                    // Signed out: only a fresh choice (interactive) opens the browser.
                    if (o.tokens() == null && !interactive) null else {
                        if (o.tokens() == null) io.github.velosako3.blossom.feature.sync.backends.DesktopOAuth.signIn(o)
                        io.github.velosako3.blossom.feature.sync.backends.GoogleDriveBackend(o)
                    }
                }
                io.github.velosako3.blossom.feature.sync.ui.BackendKind.DROPBOX -> {
                    val o = io.github.velosako3.blossom.feature.sync.backends.OAuthClient(
                        io.github.velosako3.blossom.feature.sync.backends.OAuthConfig.dropbox(dropboxKey), secure, "dropbox")
                    if (o.tokens() == null && !interactive) null else {
                        if (o.tokens() == null) {
                            io.github.velosako3.blossom.feature.sync.backends.DesktopOAuth.signIn(o,
                                io.github.velosako3.blossom.feature.sync.backends.DesktopOAuth.DROPBOX_PORT, io.github.velosako3.blossom.feature.sync.backends.DesktopOAuth.DROPBOX_PATH)
                        }
                        io.github.velosako3.blossom.feature.sync.backends.DropboxBackend(o)
                    }
                }
            }
        },
        discovery = { io.github.velosako3.blossom.feature.sync.lan.JmDnsDiscovery() },
    )
}
