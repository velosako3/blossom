// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class LyricsViewModelTest {
    @Test fun startsLoadingNotEmpty() = runTest {
        val resolver = LyricsResolver(OnlineLyrics { null }, LocalLyricsSource { null }, object : LyricsCache {
            override suspend fun get(id: io.github.velosako3.blossom.core.model.TrackId) = null
            override suspend fun put(id: io.github.velosako3.blossom.core.model.TrackId, lyrics: Lyrics?, at: kotlin.time.Instant) {}
        }, Clock.System)
        val vm = LyricsViewModel(resolver, MutableStateFlow(null), backgroundScope)
        assertEquals(LyricsUi.Loading, vm.state.value)
    }
}
