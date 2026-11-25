// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.search

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.platform.MapPreferenceStore
import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.MusicSource
import io.github.velosako3.blossom.source.api.SearchFilter
import io.github.velosako3.blossom.source.api.SearchResults
import io.github.velosako3.blossom.source.api.StreamInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingSource : MusicSource {
    val queries = mutableListOf<String>()
    override val kind = SourceKind.YOUTUBE_MUSIC
    override suspend fun search(query: String, filter: SearchFilter): SearchResults { queries += query; return SearchResults.Empty }
    override suspend fun track(id: TrackId): Track? = null
    override suspend fun resolveStream(id: TrackId, quality: AudioQuality): StreamInfo = error("unused")
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Test fun debouncesTyping() = runTest {
        val source = RecordingSource()
        val vm = SearchViewModel(source, RecentSearchStore(MapPreferenceStore()), backgroundScope)
        runCurrent()
        vm.onQueryChange("a"); advanceTimeBy(100)
        vm.onQueryChange("ab"); advanceTimeBy(100)
        vm.onQueryChange("abc"); advanceTimeBy(400); runCurrent()
        assertEquals(listOf("abc"), source.queries)
    }

    @Test fun blankQueryShowsRecentsWithoutSearching() = runTest {
        val source = RecordingSource()
        val recents = RecentSearchStore(MapPreferenceStore()).apply { add("Frank Ocean") }
        val vm = SearchViewModel(source, recents, backgroundScope)
        vm.onQueryChange("   "); advanceTimeBy(400); runCurrent()
        assertEquals(emptyList(), source.queries)
        assertEquals(listOf("Frank Ocean"), vm.state.value.recents)
    }

    @Test fun recentsAreCaseInsensitiveUnique() = runTest {
        val source = RecordingSource()
        val recents = RecentSearchStore(MapPreferenceStore())
        val vm = SearchViewModel(source, recents, backgroundScope)
        vm.onQueryChange("Kanye"); advanceTimeBy(400); runCurrent(); vm.commit()
        vm.onQueryChange("kanye"); advanceTimeBy(400); runCurrent(); vm.commit()
        assertEquals(1, recents.recents().size)
    }

    @Test fun onlyWhatWasSearchedForIsRemembered() = runTest {
        val recents = RecentSearchStore(MapPreferenceStore())
        val vm = SearchViewModel(RecordingSource(), recents, backgroundScope)
        for (typed in listOf("frank", "frank ", "frank oc", "frank ocean")) { vm.onQueryChange(typed); advanceTimeBy(400); runCurrent() }
        assertEquals(emptyList(), recents.recents(), "pauses while typing are not searches to remember")
        vm.commit()
        assertEquals(listOf("frank ocean"), recents.recents())
    }

    @Test fun recentsCappedAt20MostRecentFirst() {
        val recents = RecentSearchStore(MapPreferenceStore())
        (1..25).forEach { recents.add("q$it") }
        assertEquals(20, recents.recents().size)
        assertEquals("q25", recents.recents().first())
    }
}
