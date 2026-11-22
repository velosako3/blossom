// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import kotlinx.coroutines.launch
import io.github.velosako3.blossom.core.offline.OfflineSettingsStore
import io.github.velosako3.blossom.feature.home.HomeState
import io.github.velosako3.blossom.feature.home.OfflinePlaylistCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.days
import io.github.velosako3.blossom.feature.lyrics.LocalLyricsSource
import io.github.velosako3.blossom.feature.lyrics.LyricsResolver
import io.github.velosako3.blossom.feature.lyrics.LyricsViewModel
import io.github.velosako3.blossom.feature.lyrics.SqlLyricsCache
import io.github.velosako3.blossom.feature.lyrics.onlineLyricsProviders
import io.github.velosako3.blossom.feature.lyrics.providers.YouTubeLyricsApi
import kotlinx.coroutines.flow.map
import io.github.velosako3.blossom.core.platform.NetworkMonitor
import io.github.velosako3.blossom.core.platform.PreferenceStore
import io.github.velosako3.blossom.core.player.BlossomPlayer
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.home.HomeViewModel
import io.github.velosako3.blossom.feature.search.RecentSearchStore
import io.github.velosako3.blossom.feature.search.SearchViewModel
import io.github.velosako3.blossom.source.youtubemusic.YtmSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import io.github.velosako3.blossom.feature.library.local.LocalLibrary
import io.github.velosako3.blossom.core.database.BlossomDatabase
import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.SqlLibraryRepository
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import io.github.velosako3.blossom.feature.library.LibraryViewModel
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Crossfade length in seconds, 0–12 (0 = off, gapless). A device preference. */
const val CROSSFADE_KEY = "player.crossfade"

/** The equalizer, encoded by [io.github.velosako3.blossom.core.player.eq.EqSettings.encode]. A device preference. */
const val EQ_KEY = "player.eq"

/** This device's stable sync identity, created once and kept in preferences. */
@OptIn(ExperimentalUuidApi::class)
fun PreferenceStore.deviceId(): String = get("device.id") ?: Uuid.random().toString().also { put("device.id", it) }

/**
 * The app's object graph, built once per process by the platform entry point (no DI framework: a handful of
 * long-lived objects wired by hand). View models live here so they survive navigation.
 */
class AppGraph(
    val player: BlossomPlayer,
    val db: BlossomDatabase,
    val deviceId: String,
    val ytm: YtmSource,
    val prefs: PreferenceStore,
    val network: NetworkMonitor,
    val scope: CoroutineScope,
    val local: LocalFilesHost,
    /** Shares a link: the share sheet on Android, the clipboard on desktop. */
    val shareLink: (String) -> Unit,
    /** Sidecar `.lrc` / embedded lyrics for local files (desktop); none on Android. */
    localLyrics: LocalLyricsSource = LocalLyricsSource { null },
    /** Desktop-only extras (full-screen lyrics). */
    val desktop: Boolean = false,
    /** The encrypted offline cache (null in UI tests that don't need it). */
    val offline: io.github.velosako3.blossom.core.offline.OfflineStore? = null,
    val offlineSettings: OfflineSettingsStore? = null,
    /** Every playable source by kind (YouTube Music is [ytm]). */
    val sources: Map<SourceKind, io.github.velosako3.blossom.source.api.MusicSource> = emptyMap(),
    /** Reads a pasted/shared link's tracks, per service. */
    importers: Map<io.github.velosako3.blossom.feature.importer.ImportService, io.github.velosako3.blossom.source.api.PlaylistImporter> = emptyMap(),
    /** Sync backends and LAN pairing (null in UI tests). */
    syncSetup: SyncSetup? = null,
) {
    val search = SearchViewModel(ytm, RecentSearchStore(prefs), scope, network, offlineSearch = ::searchOffline)
    val hlc = HybridLogicalClock(deviceId, Clock.System)
    val opLog = SqlOpLog(db, deviceId, hlc)
    val projector = LibraryProjector(db)
    val commands = LibraryCommands(opLog, projector, db)
    val repo = SqlLibraryRepository(db)
    val library = LibraryViewModel(repo, commands, scope)
    val home by lazy { HomeViewModel(ytm, scope, network, offlineHome()) }

    /** Local files and saved songs whose title, artist or album contains [query]. */
    private suspend fun searchOffline(query: String): List<Track> {
        val saved = offline?.savedTracks()?.first().orEmpty()
        val localTracks = local.library.tracks().first()
        return (localTracks + saved).distinctBy { it.id }.filter { t ->
            t.title.contains(query, true) || t.artistNames.contains(query, true) || t.album?.title?.contains(query, true) == true
        }
    }

    /** Saved playlists, then recently played saved songs (or the newest saved ones). */
    private fun offlineHome(): Flow<HomeState.OfflineHome> {
        val store = offline ?: return flowOf(HomeState.OfflineHome(emptyList(), emptyList()))
        val since = Clock.System.now() - 30.days
        return combine(repo.playlists(), store.savedPlaylists(), store.savedTracks(), repo.playEvents(since, Clock.System.now() + 1.days)) { lists, savedIds, saved, plays ->
            val savedSet = saved.mapTo(HashSet()) { it.id }
            val recent = plays.sortedByDescending { it.startedAt }.map { it.track }.filter { it.id in savedSet }.distinctBy { it.id }
            HomeState.OfflineHome(
                lists.filter { it.id in savedIds }.map { OfflinePlaylistCard(it.id.value, it.name, it.artworkUrls) },
                recent.ifEmpty { saved }.take(20),
            )
        }
    }

    /** Plain YouTube uploads and SoundCloud: where re-uploads of vanished songs usually are. */
    val findReplacement = io.github.velosako3.blossom.feature.importer.FindReplacement(listOfNotNull(sources[SourceKind.YOUTUBE], sources[SourceKind.SOUNDCLOUD]), commands)

    /** untitled and SoundCloud links can disappear; checked when a playlist opens, at most daily per entry. */
    val deadLinks = io.github.velosako3.blossom.feature.importer.DeadLinkRechecker(Clock.System, commands) { t ->
        if (!network.status.value.online) null
        else when (t.id.kind) {
            SourceKind.UNTITLED, SourceKind.SOUNDCLOUD -> sources[t.id.kind]?.track(t.id) != null
            else -> null
        }
    }

    /** Song matching for imports that don't play natively (Spotify): YouTube Music search for "title artists". */
    val matchFix = io.github.velosako3.blossom.feature.importer.FindReplacement(listOf(ytm), commands)

    /** untitled songs are saved offline as they're imported (setting on by default): links can vanish. */
    private val untitledAutoSave = io.github.velosako3.blossom.feature.importer.UntitledAutoSave(
        enabled = { prefs.get(io.github.velosako3.blossom.feature.importer.UntitledAutoSave.SETTING) != "false" },
    ) { t -> offline?.saveTrack(t, io.github.velosako3.blossom.core.offline.OfflineRef.UntitledAuto) }

    val importRunner = io.github.velosako3.blossom.feature.importer.ImportJobRunner(
        db, commands, repo, importers,
        search = { t -> ytm.search((listOf(t.title) + t.artists).joinToString(" "), io.github.velosako3.blossom.source.api.SearchFilter.SONGS).songs },
        scope = scope,
        onWritten = untitledAutoSave::onWritten,
    )
    val importVm = io.github.velosako3.blossom.feature.importer.ui.ImportViewModel(importRunner, repo, scope)

    /** Every listening session, for stats and Wrapped (counted after 30 s or a natural end). */
    val playTracker = io.github.velosako3.blossom.core.player.PlayTracker(player.state, { commands.recordPlay(it) }, Clock.System, deviceId, prefs, scope)

    val stats by lazy {
        io.github.velosako3.blossom.feature.stats.ui.StatsViewModel(repo, Clock.System, kotlinx.datetime.TimeZone.currentSystemDefault(), scope)
    }

    /** A link shared to blossom (Android share sheet, deep link); the app opens Import with it. */
    val pendingImport = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    init {
        // Imports interrupted by a quit or crash carry on.
        scope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { importRunner.resumePending() } }
    }

    /** Can [track] play right now? Local files need their file; offline, streamed songs need to be saved. */
    fun isAvailable(track: Track, online: Boolean): Boolean = when {
        track.id.kind == SourceKind.LOCAL -> local.isAvailable(track)
        online -> true
        else -> offline?.isSaved(track.id) == true
    }
    val lyrics = LyricsViewModel(
        LyricsResolver(onlineLyricsProviders(), localLyrics, SqlLyricsCache(db), Clock.System),
        player.state.map { it.current },
        scope,
    )

    init {
        YouTubeLyricsApi.next = ytm.client::next
        YouTubeLyricsApi.browse = { id, params -> ytm.client.browse(id, params) }
        YouTubeLyricsApi.transcript = ytm.client::transcript
    }
    val sleepTimer = io.github.velosako3.blossom.core.player.SleepTimer(player, scope)

    private val _eq = MutableStateFlow(io.github.velosako3.blossom.core.player.eq.EqSettings.decode(prefs.get(EQ_KEY)))
    /** The equalizer: saved per device and applied to the player at once. */
    val eq: StateFlow<io.github.velosako3.blossom.core.player.eq.EqSettings> = _eq
    fun setEq(settings: io.github.velosako3.blossom.core.player.eq.EqSettings) {
        _eq.value = settings
        prefs.put(EQ_KEY, settings.encode())
        player.setEqualizer(settings)
    }

    val syncController = syncSetup?.let { s ->
        io.github.velosako3.blossom.feature.sync.ui.SyncController(db, opLog, projector, prefs, s.secure, deviceId, s.defaultName, scope, s.connect, s.discovery)
    }
    val syncKinds = syncSetup?.kinds.orEmpty()
    val sync = syncController?.let { io.github.velosako3.blossom.feature.sync.ui.SyncViewModel(it, scope) }

    init {
        if (_eq.value.enabled) player.setEqualizer(_eq.value)
        if (syncController != null && sync != null) {
            scope.launch { runCatching { syncController.startLanIfPaired() } }
            // At start, 10 s after the last local change, and every 15 minutes.
            val changes = opLog.changes()
            io.github.velosako3.blossom.feature.sync.SyncScheduler({ sync.syncAll() }, changes, scope).start()
        }
    }
}

/** Platform glue for local files: the library, the folders it watches, a folder picker and (Android) permission. */
class LocalFilesHost(
    val library: LocalLibrary,
    val folders: StateFlow<List<String>>,
    val addFolder: () -> Unit,
    val removeFolder: (String) -> Unit,
    val needsPermission: StateFlow<Boolean> = MutableStateFlow(false),
    val requestPermission: () -> Unit = {},
) {
    /** Streamed tracks are always "here"; local ones only when the file exists on this device. */
    fun isAvailable(track: Track): Boolean = track.id.kind != SourceKind.LOCAL || library.pathFor(track.id) != null

}

/** Folders kept as a JSON list in preferences (device-specific, never synced). */
fun PreferenceStore.folderList(key: String): StateFlow<List<String>> {
    val decode = { v: String? -> v?.let { runCatching { kotlinx.serialization.json.Json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty() }
    val out = MutableStateFlow(decode(get(key)))
    return out
}

fun PreferenceStore.setFolderList(key: String, folders: List<String>, flow: StateFlow<List<String>>) {
    put(key, kotlinx.serialization.json.Json.encodeToString(folders))
    (flow as MutableStateFlow).value = folders
}

/** The public web link for a track, for "Share link". */
fun Track.shareUrl(): String? = when (id.kind) {
    SourceKind.YOUTUBE_MUSIC -> "https://music.youtube.com/watch?v=${id.sourceId}"
    SourceKind.YOUTUBE -> "https://www.youtube.com/watch?v=${id.sourceId}"
    SourceKind.SOUNDCLOUD, SourceKind.UNTITLED, SourceKind.LOCAL -> null
}
