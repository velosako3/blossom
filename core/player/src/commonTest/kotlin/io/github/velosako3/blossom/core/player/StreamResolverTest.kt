// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.MusicSource
import io.github.velosako3.blossom.source.api.SearchFilter
import io.github.velosako3.blossom.source.api.SearchResults
import io.github.velosako3.blossom.source.api.StreamInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FakeClock(var now: Instant = Instant.fromEpochSeconds(1_800_000_000)) : Clock {
    override fun now(): Instant = now
    fun advance(d: Duration) { now += d }
}

class CountingSource(private val clock: Clock, private val lifetime: Duration = 6.hours) : MusicSource {
    var calls = 0
    override val kind = SourceKind.YOUTUBE_MUSIC
    override suspend fun search(query: String, filter: SearchFilter) = SearchResults.Empty
    override suspend fun track(id: TrackId): Track? = null
    override suspend fun resolveStream(id: TrackId, quality: AudioQuality): StreamInfo {
        calls++
        return StreamInfo("https://cdn/${id.sourceId}?n=$calls", emptyMap(), "audio/webm", 160, clock.now() + lifetime)
    }
}

class StreamResolverTest {
    private val id = TrackId("ytm:abc")

    @Test fun cachedWithinLifetime() = runTest {
        val clock = FakeClock(); val src = CountingSource(clock)
        val r = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to src), clock)
        r.resolve(id, AudioQuality.HIGH)
        clock.advance(1.minutes)
        r.resolve(id, AudioQuality.HIGH)
        assertEquals(1, src.calls)
    }

    @Test fun refreshedFiveMinutesBeforeExpiry() = runTest {
        val clock = FakeClock(); val src = CountingSource(clock, lifetime = 6.hours)
        val r = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to src), clock)
        r.resolve(id, AudioQuality.HIGH)
        clock.advance(6.hours - 5.minutes)
        r.resolve(id, AudioQuality.HIGH)
        assertEquals(2, src.calls)
    }

    @Test fun invalidateForcesNewResolve() = runTest {
        val clock = FakeClock(); val src = CountingSource(clock)
        val r = StreamResolver(mapOf(SourceKind.YOUTUBE_MUSIC to src), clock)
        r.resolve(id, AudioQuality.HIGH)
        r.invalidate(id)
        r.resolve(id, AudioQuality.HIGH)
        assertEquals(2, src.calls)
    }
}
