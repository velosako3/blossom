// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats

import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.PlayEvent
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatsCalculatorTest {
    private val london = TimeZone.of("Europe/London")
    private fun at(mo: Int, d: Int, h: Int = 12, mi: Int = 0) = LocalDateTime(2026, mo, d, h, mi).toInstant(london)
    private fun track(id: String, artist: String = "Artist $id", artistId: String? = null, album: String? = null) =
        Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, id), "Song $id", listOf(ArtistRef(artistId, artist)), album?.let { AlbumRef(null, it) }, 200_000, null)
    private var n = 0
    private fun play(t: Track, at: kotlin.time.Instant, ms: Long = 60_000, counted: Boolean = true) = PlayEvent("p${n++}", t, at, ms, counted, "dev")
    private val nov = Period.Month(2026, 11, london)

    @Test fun playOverMonthEndCountsInTheMonthItStarted() {
        val jan = Period.Month(2026, 1, london)
        val s = StatsCalculator.compute(jan, listOf(play(track("a"), LocalDateTime(2026, 1, 31, 23, 55).toInstant(london), ms = 600_000)), emptyMap(), 0)
        assertEquals(600_000, s.totalListeningMs)
        assertEquals(1, s.countedPlays)
    }

    @Test fun tiesBreakByListeningTime() {
        val a = track("a"); val b = track("b")
        val events = List(3) { play(a, at(11, 3 + it), ms = 60_000) } + List(3) { play(b, at(11, 3 + it), ms = 120_000) }
        val s = StatsCalculator.compute(nov, events, emptyMap(), 0)
        assertEquals(listOf("b", "a"), s.topTracks.map { it.item.id.sourceId })
        assertEquals(3, s.topTracks[0].plays)
    }

    @Test fun newArtistsOnlyWhenFirstPlayedInPeriod() {
        val old = track("o", artist = "Old Band"); val fresh = track("f", artist = "Fresh Face")
        val first = mapOf(StatsCalculator.artistKey(old.artists[0]) to at(10, 15), StatsCalculator.artistKey(fresh.artists[0]) to at(11, 2))
        val s = StatsCalculator.compute(nov, listOf(play(old, at(11, 2)), play(fresh, at(11, 2))), first, 0)
        assertEquals(listOf("Fresh Face"), s.newArtists.map { it.name })
    }

    @Test fun longestStreak() {
        val t = track("s")
        val s = StatsCalculator.compute(nov, listOf(1, 2, 3, 5, 6).map { play(t, at(11, it)) } + play(t, at(11, 9), counted = false), emptyMap(), 0)
        assertEquals(3, s.longestStreakDays)
    }

    @Test fun listeningChange() {
        val t = track("c")
        assertNull(StatsCalculator.compute(nov, listOf(play(t, at(11, 1))), emptyMap(), 0).listeningChangePercent)
        val s = StatsCalculator.compute(nov, listOf(play(t, at(11, 1), ms = 150 * 60_000L)), emptyMap(), 100 * 60_000L)
        assertEquals(50.0, s.listeningChangePercent)
    }

    @Test fun bucketsUseLocalTimeAndArtistsKeyByName() {
        val a1 = track("x", artist = "  The  Band "); val a2 = track("y", artist = "the band")
        val s = StatsCalculator.compute(nov, listOf(play(a1, at(11, 2, h = 23, mi = 30)), play(a2, at(11, 2, h = 23, mi = 40))), emptyMap(), 0)
        assertEquals(120_000, s.byHourOfDayMs[23])
        assertEquals(120_000, s.byDayOfWeekMs[0]) // Monday Nov 2
        assertEquals(1, s.topArtists.size)
        assertEquals(2, s.topArtists[0].plays)
    }

    @Test fun topListsHoldFive() {
        val events = (0 until 8).flatMap { i -> List(8 - i) { play(track("t$i", album = "Album $i"), at(11, 1 + it)) } }
        val s = StatsCalculator.compute(nov, events, emptyMap(), 0)
        assertEquals(5, s.topTracks.size); assertEquals(5, s.topArtists.size); assertEquals(5, s.topAlbums.size)
        assertEquals("t0", s.topTracks[0].item.id.sourceId)
    }
}
