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

    @Test fun anUntitledUploadNobodyKnowsIsTriedAsYe() = runTest {
        val upload = Track(TrackId.of(SourceKind.UNTITLED, "u1"), "MOON ROCK (prod. X) [CDQ]", listOf(ArtistRef(null, "some_uploader")), null, 180_000, null)
        val asked = mutableListOf<LyricsQuery>()
        val online = OnlineLyrics { q -> asked += q; if (q.artists == listOf("Ye")) wordSynced else null }
        assertEquals(wordSynced, LyricsResolver(online, FakeLocal(null), MemoryCache(), TestClock()).resolve(upload))
        assertEquals(listOf("MOON ROCK" to listOf("some_uploader"), "MOON ROCK" to listOf("Ye")), asked.map { it.title to it.artists })
    }

    @Test fun otherSourcesAreNotRetriedAsYe() = runTest {
        val online = FakeOnline(null)
        LyricsResolver(online, FakeLocal(null), MemoryCache(), TestClock()).resolve(track)
        assertEquals(1, online.queries.size)
    }

    @Test fun unreadableLocalFallsBackToOnline() = runTest {
        val r = LyricsResolver(FakeOnline(plain), FakeLocal(null, fail = true), MemoryCache(), TestClock())
        assertEquals(plain, r.resolve(localTrack))
    }
}

class PlainLyricsTest {
    @Test fun sectionLabelsAreNotLyrics() {
        val l = lyricsOf(listOf("[Verse 1]", "Hello", "", "[Chorus]", "World").map { io.github.velosako3.blossom.feature.lyrics.providers.LyricLine(0, it) }, "Genius")
        assertEquals("Hello\n\nWorld", (l as Lyrics.Plain).text)
    }
}

class LyricsArtistTest {
    @Test fun yeUploadersAreYe() {
        for (a in listOf("YeBreak", "YEBREAK", "Ye Leaks", "yeleaks", "Ye Archive", "KanyeLeaks", "Kanye West", "kanye", "Ye", "YE"))
            assertEquals("Ye", lyricsArtist(a), a)
    }

    @Test fun othersAreLeftAlone() {
        for (a in listOf("Yeat", "Yeah Yeah Yeahs", "Yelawolf", "Yes", "King Combs", "Joji", "Yeezus Fan"))
            assertEquals(a, lyricsArtist(a), a)
    }
}

class TidyLyricsQueryTest {
    private fun q(title: String, vararg artists: String) = LyricsQuery(title, artists.toList(), null, 180_000)
    private fun tidy(title: String, artist: String, uploaded: Boolean = true) = tidyLyricsQuery(q(title, artist), uploaded).let { it.title to it.artists }

    @Test fun uploadsWithTheArtistInTheTitle() {
        assertEquals("Never Abandon Your Family" to listOf("Kanye West"), tidy("Kanye West – Never Abandon Your Family (Yedit)", "fake_uploader"))
        assertEquals("NITROUS" to listOf("Ye"), tidy("Ye - NITROUS (prod. Ojivolta) [v2]", "donda.wav"))
        assertEquals("Hurricane" to listOf("Kanye West", "The Weeknd"), tidy("Kanye West & The Weeknd - Hurricane [CDQ] ✦", "leaks"))
    }

    @Test fun labelsTheSitesNeverUse() {
        assertEquals("FREE DIDDY" to listOf("YeBreak"), tidy("FREE DIDDY (Snippet) prod. Kanye", "YeBreak"))
        assertEquals("Blinding Lights" to listOf("The Weeknd"), tidy("The Weeknd - Blinding Lights (Official Video)", "The Weeknd", uploaded = false))
        assertEquals("Hey Jude" to listOf("The Beatles"), tidy("Hey Jude - Remastered 2015", "The Beatles", uploaded = false))
        assertEquals("Runaway" to listOf("Kanye West"), tidy("Runaway (feat. Pusha T)", "Kanye West", uploaded = false))
    }

    @Test fun ordinaryTitlesStayAsTheyAre() {
        assertEquals("Self Control" to listOf("Frank Ocean"), tidy("Self Control", "Frank Ocean", uploaded = false))
        // A dash inside a song title is not "artist – title" unless the left side is an artist.
        assertEquals("Ain't No Mountain High Enough - Live" to listOf("Marvin Gaye"), tidy("Ain't No Mountain High Enough - Live", "Marvin Gaye", uploaded = false))
        assertEquals("Love Lockdown (Remix)" to listOf("Kanye West"), tidy("Love Lockdown (Remix)", "Kanye West", uploaded = false))
    }
}
