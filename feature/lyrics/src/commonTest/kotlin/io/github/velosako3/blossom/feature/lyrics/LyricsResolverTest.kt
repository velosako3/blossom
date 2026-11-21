// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class TestClock(var now: Instant = Instant.fromEpochSeconds(1_800_000_000)) : Clock {
    override fun now() = now
    fun advance(d: Duration) { now += d }
}

private class FakeProvider(override val name: String, override val priority: Int, private val delayMs: Long, private val result: Lyrics?) : LyricsProvider {
    var calls = 0
    override suspend fun fetch(q: LyricsQuery): Lyrics? { calls++; delay(delayMs); return result }
}

private class MemoryCache : LyricsCache {
    val map = HashMap<TrackId, CachedLyrics>()
    override suspend fun get(id: TrackId) = map[id]
    override suspend fun put(id: TrackId, lyrics: Lyrics?, at: Instant) { map[id] = CachedLyrics(lyrics, at) }
}

private class FakeLocal(private val result: Lyrics?) : LocalLyricsSource {
    override suspend fun lyrics(track: Track): Lyrics? = result
}

class LyricsResolverTest {
    private val track = Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "v1"), "Runaway", listOf(ArtistRef(null, "Kanye West")), null, 547_000, null)
    private val localTrack = track.copy(id = TrackId.of(SourceKind.LOCAL, "abc"))
    private val plain = Lyrics.Plain("words", "A")
    private val wordSynced = Lyrics.WordSynced(listOf(LyricLine(0, 1000, "w", listOf(LyricWord("w", 0, 1000)))), "B")

    @Test fun richestWinsWithinTimeout() = runTest {
        val r = LyricsResolver(listOf(FakeProvider("A", 1, 100, plain), FakeProvider("B", 2, 3_000, wordSynced)), FakeLocal(null), MemoryCache(), TestClock())
        assertIs<Lyrics.WordSynced>(r.resolve(track))
    }

    @Test fun slowProviderIsDropped() = runTest {
        val r = LyricsResolver(listOf(FakeProvider("A", 1, 100, plain), FakeProvider("B", 2, 6_000, wordSynced)), FakeLocal(null), MemoryCache(), TestClock())
        assertEquals(plain, r.resolve(track))
    }

    @Test fun negativeCacheForSevenDays() = runTest {
        val clock = TestClock()
        val a = FakeProvider("A", 1, 10, null); val b = FakeProvider("B", 2, 10, null)
        val cache = MemoryCache()
        val r = LyricsResolver(listOf(a, b), FakeLocal(null), cache, clock)
        assertNull(r.resolve(track))
        assertEquals(true, cache.map.containsKey(track.id))
        clock.advance(6.days)
        assertNull(r.resolve(track))
        assertEquals(1, a.calls)
        clock.advance(1.days + 1.seconds)
        r.resolve(track)
        assertEquals(2, a.calls)
    }

    @Test fun sidecarWinsWithoutAskingOnline() = runTest {
        val a = FakeProvider("A", 1, 10, wordSynced)
        val local = Lyrics.LineSynced(listOf(LyricLine(0, 1000, "from lrc", emptyList())), "local")
        val r = LyricsResolver(listOf(a), FakeLocal(local), MemoryCache(), TestClock())
        assertEquals(local, r.resolve(localTrack))
        assertEquals(0, a.calls)
    }

    @Test fun tieBrokenByPriority() = runTest {
        val first = Lyrics.Plain("first", "A"); val second = Lyrics.Plain("second", "B")
        val r = LyricsResolver(listOf(FakeProvider("B", 2, 10, second), FakeProvider("A", 1, 50, first)), FakeLocal(null), MemoryCache(), TestClock())
        assertEquals(first, r.resolve(track))
    }
}
