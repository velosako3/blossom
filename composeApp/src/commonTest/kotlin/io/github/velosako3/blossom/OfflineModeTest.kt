// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.model.ArtistRef
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
import io.github.velosako3.blossom.feature.home.HomeState
import io.github.velosako3.blossom.feature.home.HomeViewModel
import io.github.velosako3.blossom.feature.home.OfflinePlaylistCard
import io.github.velosako3.blossom.feature.search.RecentSearchStore
import io.github.velosako3.blossom.feature.search.SearchViewModel
import io.github.velosako3.blossom.source.youtubemusic.YtmSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

private class Offline : NetworkMonitor {
    override val status = MutableStateFlow(NetworkStatus(online = false, metered = false))
}

private class NullEngine : PlaybackEngine {
    val loads = mutableListOf<MediaInput>()
    override val events = MutableSharedFlow<EngineEvent>()
    override suspend fun load(input: MediaInput, startPositionMs: Long) { loads += input }
    override fun play() {}
    override fun pause() {}
    override fun seekTo(ms: Long) {}
    override fun setVolume(v: Float) {}
    override fun release() {}
}

private fun song(id: String, title: String) = Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, id), title, listOf(ArtistRef(null, "Band")), null, 180_000, null)

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineModeTest {
    // Never reached while offline: any network call would throw.
    private val ytm = YtmSource(InMemorySecureStore(), kotlin.io.path.createTempDirectory("ytm").toFile())

    @Test fun homeListsSavedPlaylistsOffline() = runTest {
        val offlineHome = MutableStateFlow(HomeState.OfflineHome(listOf(OfflinePlaylistCard("p1", "Road trip", emptyList())), emptyList()))
        val vm = HomeViewModel(ytm, backgroundScope, network = Offline(), offline = offlineHome)
        runCurrent()
        val s = assertIs<HomeState.OfflineHome>(vm.state.value)
        assertEquals(listOf("Road trip"), s.playlists.map { it.name })
    }

    @Test fun queueSkipsUnsavedTracksOffline() = runTest {
        val engine = NullEngine()
        val saved = setOf("a", "c")
        val p = DefaultBlossomPlayer(
            engine = engine,
            resolver = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to ytm), Clock.System),
            network = Offline(),
            scope = backgroundScope,
            isSavedOffline = { it.sourceId in saved },
            radio = { emptyList() },
        )
        p.setQueue(listOf(song("a", "A"), song("b", "B"), song("c", "C")), 0)
        runCurrent()
        assertEquals(0, p.state.value.currentIndex)
        p.next()
        runCurrent()
        assertEquals(2, p.state.value.currentIndex)
        assertTrue(engine.loads.all { it is MediaInput.Offline })
    }

    @Test fun searchUsesLocalAndSavedOffline() = runTest {
        val library = listOf(song("a", "Midnight City"), song("b", "Daylight"), song("c", "City Lights"))
        val vm = SearchViewModel(ytm, RecentSearchStore(MapPreferenceStore()), backgroundScope, network = Offline(),
            offlineSearch = { q -> library.filter { it.title.contains(q, ignoreCase = true) } })
        vm.onQueryChange("city")
        advanceTimeBy(400); runCurrent()
        val s = vm.state.value
        assertTrue(s.offline)
        assertEquals(listOf("Midnight City", "City Lights"), s.results?.songs?.map { it.title })
    }
}
