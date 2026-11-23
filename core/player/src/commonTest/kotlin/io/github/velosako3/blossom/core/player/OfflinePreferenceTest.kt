// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.SourceKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class OfflinePreferenceTest {
    @Test fun savedTrackPlaysFromCacheEvenOnline() = runTest {
        val clock = FakeClock()
        val source = CountingSource(clock)
        val engine = FakeEngine()
        val p = DefaultBlossomPlayer(
            engine = engine,
            resolver = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to source), clock),
            network = FakeNetwork(online = true),
            scope = backgroundScope,
            isSavedOffline = { it.sourceId == "t0" },
            radio = { emptyList() },
        )
        p.setQueue(tracks(2), 0)
        runCurrent()
        assertIs<MediaInput.Offline>(engine.loads.single().first)
        assertEquals(0, source.calls)
    }
}
