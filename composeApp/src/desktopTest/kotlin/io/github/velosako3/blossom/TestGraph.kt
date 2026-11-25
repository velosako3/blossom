// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.database.NewEntry
import io.github.velosako3.blossom.core.database.inMemoryDatabase
import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlayEvent
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.platform.InMemorySecureStore
import io.github.velosako3.blossom.core.platform.MapPreferenceStore
import io.github.velosako3.blossom.core.platform.NetworkMonitor
import io.github.velosako3.blossom.core.platform.NetworkStatus
import io.github.velosako3.blossom.core.player.DefaultBlossomPlayer
import io.github.velosako3.blossom.core.player.EngineEvent
import io.github.velosako3.blossom.core.player.MediaInput
import io.github.velosako3.blossom.core.player.PlaybackEngine
import io.github.velosako3.blossom.core.player.StreamResolver
import io.github.velosako3.blossom.feature.library.local.LocalLibrary
import io.github.velosako3.blossom.source.youtubemusic.YtmSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

private class SilentEngine : PlaybackEngine {
    override val events = MutableSharedFlow<EngineEvent>()
    override suspend fun load(input: MediaInput, startPositionMs: Long) {}
    override fun play() {}
    override fun pause() {}
    override fun seekTo(ms: Long) {}
    override fun setVolume(v: Float) {}
    override fun release() {}
}

private object OfflineNet : NetworkMonitor {
    override val status = MutableStateFlow(NetworkStatus(online = false, metered = false))
}

fun testTrack(n: Int) = Track(
    TrackId.of(SourceKind.YOUTUBE_MUSIC, "t$n"), listOf("Midnight City", "Around the World", "Daylight", "Harder Better", "Instant Crush", "Nightcall")[n % 6] + if (n >= 6) " ($n)" else "",
    listOf(ArtistRef(null, listOf("M83", "Daft Punk", "Kavinsky")[n % 3])), AlbumRef("al${n % 3}", listOf("Hurry Up", "Homework", "OutRun")[n % 3]), 200_000 + n * 7_000L, null,
)

/**
 * The real app graph with no network: an in-memory library (a playlist, likes, a week of plays) and a silent engine
 * with a queue loaded. Everything a screen shows comes from here.
 */
class TestGraph(val withSync: Boolean = false) {
    val prefs = MapPreferenceStore()
    // Not Main: the test body blocks the Swing thread while it waits.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dir = kotlin.io.path.createTempDirectory("blossom-test").toFile()
    private val ytm = YtmSource(InMemorySecureStore(), io.github.velosako3.blossom.source.youtubemusic.YouTubeStreams({ null }, dir))
    private val player = DefaultBlossomPlayer(SilentEngine(), StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to ytm), Clock.System), OfflineNet, scope)
    private val local = LocalFilesHost(
        library = object : LocalLibrary {
            override fun tracks() = flowOf(emptyList<Track>())
            override suspend fun rescan() {}
            override fun pathFor(id: TrackId): String? = null
        },
        folders = MutableStateFlow(emptyList()), addFolder = {}, removeFolder = {},
    )
    val graph = AppGraph(player, inMemoryDatabase(), "test-device", ytm, prefs, OfflineNet, scope, local, shareLink = {}, desktop = true,
        syncSetup = if (withSync) SyncSetup(InMemorySecureStore(), "Test PC", io.github.velosako3.blossom.feature.sync.ui.BackendKind.entries, connect = { _, _ -> null }) else null)
    var playlist = PlaylistId("")

    init {
        runBlocking {
            val tracks = (0 until 12).map(::testTrack)
            playlist = graph.commands.createPlaylist("Road trip", "Songs for the drive")
            graph.commands.addEntries(playlist, tracks.take(8).map { NewEntry(it, EntryStatus.MATCHED, it.title, it.artistNames, null) })
            tracks.take(4).forEach { graph.commands.like(it) }
            val now = Clock.System.now()
            tracks.forEachIndexed { i, t ->
                repeat(12 - i) { k -> graph.commands.recordPlay(PlayEvent("p$i-$k", t, now - (i * 5 + k).hours, 180_000, true, "test-device")) }
            }
            player.setQueue(tracks.take(6), 1)
        }
    }
}
