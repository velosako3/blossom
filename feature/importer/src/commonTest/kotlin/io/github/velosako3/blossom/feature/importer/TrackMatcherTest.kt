// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.ImportedTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackMatcherTest {
    private fun cand(title: String, artists: List<String>, ms: Long, id: String = title) =
        Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, id), title, artists.map { ArtistRef(null, it) }, null, ms, null)
    private fun imp(title: String, artists: List<String>, ms: Long?, isrc: String? = null) = ImportedTrack(title, artists, ms, isrc, null)

    @Test fun identical() {
        val c = cand("Blinding Lights", listOf("The Weeknd"), 200_000)
        assertTrue(TrackMatcher.score(imp("Blinding Lights", listOf("The Weeknd"), 200_000), c) >= 0.95f)
        assertEquals(EntryStatus.MATCHED, TrackMatcher.match(imp("Blinding Lights", listOf("The Weeknd"), 200_000), listOf(c)).status)
    }

    @Test fun remasterSuffix() {
        val r = TrackMatcher.match(imp("Song - Remastered 2011", listOf("A"), 181_000), listOf(cand("Song", listOf("A"), 180_000)))
        assertEquals(EntryStatus.MATCHED, r.status)
    }

    @Test fun featuredArtistMoves() {
        val r = TrackMatcher.match(imp("Song (feat. B)", listOf("A"), 180_000), listOf(cand("Song", listOf("A", "B"), 180_500)))
        assertEquals(EntryStatus.MATCHED, r.status)
    }

    @Test fun differentArtistUnavailable() {
        val r = TrackMatcher.match(imp("Hello", listOf("Adele"), 295_000), listOf(cand("Hello", listOf("Lionel Richie"), 330_000)))
        assertEquals(EntryStatus.UNAVAILABLE, r.status)
    }

    @Test fun eightSecondsApartUncertain() {
        val r = TrackMatcher.match(imp("Song", listOf("A"), 188_000), listOf(cand("Song", listOf("A"), 180_000)))
        assertEquals(EntryStatus.UNCERTAIN, r.status)
    }

    @Test fun isrcWins() {
        val c = cand("Totally Different", listOf("Z"), 1_000)
        val r = TrackMatcher.match(imp("Song", listOf("A"), 180_000, isrc = "USRC17607839"), listOf(c), isrcOf = { "USRC17607839" })
        assertEquals(1f, r.confidence)
        assertEquals(EntryStatus.MATCHED, r.status)
    }

    @Test fun bestCandidateChosen() {
        val r = TrackMatcher.match(imp("Yellow", listOf("Coldplay"), 266_000), listOf(cand("Yellow (Live)", listOf("Coldplay"), 300_000, "x"), cand("Yellow", listOf("Coldplay"), 267_000, "y")))
        assertEquals("y", r.track?.id?.sourceId)
    }

    @Test fun dashTitlesKeepTheirWords() {
        val r = TrackMatcher.match(imp("Song - Stereo Hearts", listOf("A"), 180_000), listOf(cand("Song", listOf("A"), 180_000)))
        assertTrue(r.status != EntryStatus.MATCHED, "only a version suffix may be dropped, not words")
    }

    @Test fun withInsideATitleIsNotAnArtist() {
        val r = TrackMatcher.match(imp("(With or Without You)", listOf("U2"), 296_000), listOf(cand("With or Without You", listOf("U2"), 296_000)))
        assertEquals(EntryStatus.MATCHED, r.status)
    }

    @Test fun combiningMarksSurvive() {
        val r = TrackMatcher.match(imp("तुम ही हो", listOf("Arijit Singh"), 262_000), listOf(cand("तुम ही हो", listOf("Arijit Singh"), 262_000)))
        assertEquals(EntryStatus.MATCHED, r.status)
        assertTrue(TrackMatcher.score(imp("तुम ही हो", listOf("Arijit Singh"), 262_000), cand("तम ह ह", listOf("Arijit Singh"), 262_000)) < 0.95f)
    }

    @Test fun symbolOnlyTitlesDoNotMatchByArtistAlone() {
        val r = TrackMatcher.match(imp("!!!", listOf("A"), 180_000), listOf(cand("???", listOf("A"), 180_000)))
        assertTrue(r.status != EntryStatus.MATCHED)
    }
}
