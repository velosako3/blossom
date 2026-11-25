// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.SqlLibraryRepository
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.database.iosDatabase
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import io.github.velosako3.blossom.core.platform.AlwaysOnline
import io.github.velosako3.blossom.core.platform.IosSecureStore
import io.github.velosako3.blossom.core.platform.iosPreferenceStore
import io.github.velosako3.blossom.core.player.AvPlayerEngine
import io.github.velosako3.blossom.core.player.DefaultBlossomPlayer
import io.github.velosako3.blossom.core.player.StreamResolver
import io.github.velosako3.blossom.feature.home.HomeViewModel
import io.github.velosako3.blossom.feature.library.LibraryViewModel
import io.github.velosako3.blossom.feature.lyrics.LocalLyricsSource
import io.github.velosako3.blossom.feature.lyrics.LrcLibLyrics
import io.github.velosako3.blossom.feature.lyrics.LyricsResolver
import io.github.velosako3.blossom.feature.lyrics.LyricsViewModel
import io.github.velosako3.blossom.feature.lyrics.SqlLyricsCache
import io.github.velosako3.blossom.feature.search.RecentSearchStore
import io.github.velosako3.blossom.feature.search.SearchViewModel
import io.github.velosako3.blossom.source.youtubemusic.IosClientStreams
import io.github.velosako3.blossom.source.youtubemusic.YtmSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import platform.Foundation.NSUUID
import kotlin.time.Clock

/**
 * The iOS app's wiring: YouTube Music through the iPhone client's streams, AVPlayer, the shared library database,
 * LRCLIB lyrics. Smaller than Android/desktop's AppGraph: no import, sync, offline saving, stats or local files yet.
 */
class IosGraph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val prefs = iosPreferenceStore()
    val secure = IosSecureStore()
    val network = AlwaysOnline
    val db = iosDatabase()
    val deviceId: String = prefs.get("device.id") ?: NSUUID().UUIDString.also { prefs.put("device.id", it) }
    val ytm = YtmSource(secure, IosClientStreams())

    val engine = AvPlayerEngine()
    val player = DefaultBlossomPlayer(
        engine = engine,
        resolver = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to ytm, SourceKind.YOUTUBE to ytm), Clock.System),
        network = network,
        scope = scope,
        radio = { ytm.radio(it.id) },
    )

    private val hlc = HybridLogicalClock(deviceId, Clock.System)
    private val opLog = SqlOpLog(db, deviceId, hlc)
    private val projector = LibraryProjector(db)
    val commands = LibraryCommands(opLog, projector, db)
    val repo = SqlLibraryRepository(db)
    val library = LibraryViewModel(repo, commands, scope)
    val search = SearchViewModel(ytm, RecentSearchStore(prefs), scope, network)
    val home by lazy { HomeViewModel(ytm, scope, network) }
    val lyrics = LyricsViewModel(
        LyricsResolver(LrcLibLyrics(), LocalLyricsSource { null }, SqlLyricsCache(db, generation = BlossomBuildInfo.COMMIT.ifBlank { "dev" }.take(12)), Clock.System),
        player.state.map { it.current },
        scope,
    )

    init {
        // Lock screen and Control Center buttons drive the app's player.
        engine.remote = { command ->
            when (command) {
                "play" -> player.play()
                "pause" -> player.pause()
                "toggle" -> if (player.state.value.isPlaying) player.pause() else player.play()
                "next" -> player.next()
                "previous" -> player.previous()
            }
        }
    }
}
