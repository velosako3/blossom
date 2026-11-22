// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.wrapped

import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.PlayEvent
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.feature.stats.Period
import io.github.velosako3.blossom.feature.stats.StatsCalculator
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WrappedDeckBuilderTest {
    private val london = TimeZone.of("Europe/London")
    private val week = Period.Week(2026, 47, london)
    private fun t(i: Int, album: Boolean = true) = Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "v$i"), "Song $i", listOf(ArtistRef(null, "Artist ${i % 3}")),
        if (album) AlbumRef(null, "Album ${i % 2}") else null, 200_000, null)
    private fun events(n: Int, album: Boolean = true) = (0 until n).map { i ->
        PlayEvent("p$i", t(i % 4, album), LocalDateTime(2026, 11, 16 + i % 7, 10 + i % 10, 0).toInstant(london), 180_000, true, "d")
    }

    @Test fun tooFewPlays() {
        val s = StatsCalculator.compute(week, events(3), emptyMap(), 0)
        assertEquals(listOf<WrappedCard>(WrappedCard.NotEnoughData), WrappedDeckBuilder.build(s))
    }

    @Test fun fullDeckStartsWithIntroAndEndsWithSummary() {
        val s = StatsCalculator.compute(week, events(30), emptyMap(), previousTotalMs = 60 * 60_000L)
        val deck = WrappedDeckBuilder.build(s)
        assertIs<WrappedCard.Intro>(deck.first())
        assertIs<WrappedCard.Summary>(deck.last())
        assertTrue(deck.any { it is WrappedCard.Comparison } && deck.any { it is WrappedCard.TopAlbum } && deck.any { it is WrappedCard.NewArtists })
        // The documented order, with nothing out of place.
        val order = listOf(WrappedCard.Intro::class, WrappedCard.TotalMinutes::class, WrappedCard.TopSong::class, WrappedCard.TopSongs::class, WrappedCard.TopArtist::class,
            WrappedCard.TopArtists::class, WrappedCard.TopAlbum::class, WrappedCard.ListeningClock::class, WrappedCard.NewArtists::class, WrappedCard.Streak::class,
            WrappedCard.Comparison::class, WrappedCard.Summary::class)
        assertEquals(deck.map { it::class }, order.filter { k -> deck.any { it::class == k } })
    }

    @Test fun noAlbumsNoAlbumCard() {
        val s = StatsCalculator.compute(week, events(30, album = false), emptyMap(), 0)
        assertTrue(WrappedDeckBuilder.build(s).none { it is WrappedCard.TopAlbum })
    }

    @Test fun noPreviousPeriodNoComparison() {
        val s = StatsCalculator.compute(week, events(30), emptyMap(), 0)
        assertTrue(WrappedDeckBuilder.build(s).none { it is WrappedCard.Comparison })
    }
}
