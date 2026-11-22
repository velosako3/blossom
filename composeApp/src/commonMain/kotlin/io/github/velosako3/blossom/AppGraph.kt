// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

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
) {
    val search = SearchViewModel(ytm, RecentSearchStore(prefs), scope)
    val hlc = HybridLogicalClock(deviceId, Clock.System)
    val opLog = SqlOpLog(db, deviceId, hlc)
    val projector = LibraryProjector(db)
    val commands = LibraryCommands(opLog, projector, db)
    val repo = SqlLibraryRepository(db)
    val library = LibraryViewModel(repo, commands, scope)
    val home by lazy { HomeViewModel(ytm, scope) }
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
