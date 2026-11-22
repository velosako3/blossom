// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.feature.lyrics.providers.LyricLine
import io.github.velosako3.blossom.feature.lyrics.providers.LyricWord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class TestClock(var now: Instant = Instant.fromEpochSeconds(1_800_000_000)) : Clock {
    override fun now() = now
    fun advance(d: Duration) { now += d }
}

/** Answers from [answers] in turn (the last one repeats); null answers mean "none found", [fail] throws. */
private class FakeOnline(vararg answers: Lyrics?, private val fail: Boolean = false) : OnlineLyrics {
    private val answers = answers.toList()
    val queries = mutableListOf<LyricsQuery>()
    override suspend fun lyrics(q: LyricsQuery): Lyrics? {
        queries += q
        if (fail) throw java.io.IOException("offline")
        return answers.getOrNull(queries.size - 1) ?: answers.lastOrNull()
    }
}

private class MemoryCache : LyricsCache {
    val map = HashMap<TrackId, CachedLyrics>()
    override suspend fun get(id: TrackId) = map[id]
    override suspend fun put(id: TrackId, lyrics: Lyrics?, at: Instant) { map[id] = CachedLyrics(lyrics, at) }
}

private class FakeLocal(private val result: Lyrics?, private val fail: Boolean = false) : LocalLyricsSource {
    override suspend fun lyrics(track: Track): Lyrics? = if (fail) throw java.io.IOException("unreadable") else result
}

class LyricsResolverTest {
    private val track = Track(
        TrackId.of(SourceKind.YOUTUBE_MUSIC, "v1"), "Runaway", listOf(ArtistRef(null, "Kanye West"), ArtistRef(null, "Pusha T")),
        null, 547_000, null,
    )
    private val localTrack = track.copy(id = TrackId.of(SourceKind.LOCAL, "abc"))
    private val plain = Lyrics.Plain("words", "Genius")
    private val wordSynced = Lyrics.WordSynced(listOf(LyricLine(0, "w", listOf(LyricWord(0, 1000, "w")))), "lrc.red")

    @Test fun asksWithEveryArtistAndTheVideoId() = runTest {
        val online = FakeOnline(wordSynced)
        LyricsResolver(online, FakeLocal(null), MemoryCache(), TestClock()).resolve(track)
        assertEquals(listOf("Kanye West", "Pusha T"), online.queries.single().artists)
        assertEquals("v1", online.queries.single().videoId)
    }

    @Test fun syncedLyricsAreCachedForGood() = runTest {
        val clock = TestClock()
        val online = FakeOnline(wordSynced)
        val r = LyricsResolver(online, FakeLocal(null), MemoryCache(), clock)
        assertEquals(wordSynced, r.resolve(track))
        clock.advance(30.days)
        assertEquals(wordSynced, r.resolve(track))
        assertEquals(1, online.queries.size)
    }

    @Test fun plainLyricsAreRetriedAfterADayAndUpgraded() = runTest {
        val clock = TestClock()
        val online = FakeOnline(plain, wordSynced)
        val r = LyricsResolver(online, FakeLocal(null), MemoryCache(), clock)
        assertEquals(plain, r.resolve(track))
        clock.advance(12.hours)
        assertEquals(plain, r.resolve(track))
        assertEquals(1, online.queries.size)
        clock.advance(13.hours)
        assertEquals(wordSynced, r.resolve(track))
        assertEquals(2, online.queries.size)
    }

    @Test fun aFailedRetryKeepsThePlainCopy() = runTest {
        val clock = TestClock()
        val cache = MemoryCache().apply { map[track.id] = CachedLyrics(plain, clock.now() - 2.days) }
        val r = LyricsResolver(FakeOnline(fail = true), FakeLocal(null), cache, clock)
        assertEquals(plain, r.resolve(track))
    }

    @Test fun noneFoundIsCachedForADay() = runTest {
        val clock = TestClock()
        val online = FakeOnline(null)
        val r = LyricsResolver(online, FakeLocal(null), MemoryCache(), clock)
        assertNull(r.resolve(track))
        clock.advance(23.hours)
        assertNull(r.resolve(track))
        assertEquals(1, online.queries.size)
        clock.advance(1.hours + 1.seconds)
        r.resolve(track)
        assertEquals(2, online.queries.size)
    }

    @Test fun failuresCacheNothing() = runTest {
        val cache = MemoryCache()
        val r = LyricsResolver(FakeOnline(fail = true), FakeLocal(null), cache, TestClock())
        assertNull(r.resolve(track))
        assertEquals(false, cache.map.containsKey(track.id))
    }

    @Test fun sidecarWinsWithoutAskingOnline() = runTest {
        val online = FakeOnline(wordSynced)
        val local = Lyrics.LineSynced(listOf(LyricLine(1000, "from lrc")), "local")
        assertEquals(local, LyricsResolver(online, FakeLocal(local), MemoryCache(), TestClock()).resolve(localTrack))
        assertEquals(0, online.queries.size)
    }

    @Test fun localSidecarBeatsAStaleNegativeEntry() = runTest {
        val clock = TestClock()
        val cache = MemoryCache().apply { map[localTrack.id] = CachedLyrics(null, clock.now()) }
        val local = Lyrics.Plain("new sidecar", "local")
        assertEquals(local, LyricsResolver(FakeOnline(null), FakeLocal(local), cache, clock).resolve(localTrack))
    }

    @Test fun unreadableLocalFallsBackToOnline() = runTest {
        val r = LyricsResolver(FakeOnline(plain), FakeLocal(null, fail = true), MemoryCache(), TestClock())
        assertEquals(plain, r.resolve(localTrack))
    }
}
