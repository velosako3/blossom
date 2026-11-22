// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.wrapped

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
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
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

private val london = TimeZone.of("Europe/London")
private val week = Period.Week(2026, 47, london)
private val names = listOf("Pink + White" to "Frank Ocean", "Snooze" to "SZA", "Alright" to "Kendrick Lamar", "See You Again" to "Tyler, The Creator", "Bad Habit" to "Steve Lacy", "Cranes in the Sky" to "Solange")
private fun t(i: Int) = Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "v$i"), names[i].first, listOf(ArtistRef(null, names[i].second)),
    AlbumRef(null, listOf("Blonde", "SOS", "To Pimp a Butterfly", "Flower Boy", "Gemini Rights", "A Seat at the Table")[i]), 210_000, null)
private val events = (0 until 80).map { n ->
    PlayEvent("p$n", t((n * n) % 6), LocalDateTime(2026, 11, 16 + n % 7, listOf(8, 13, 18, 21, 22, 23, 23)[n % 7], n % 60).toInstant(london), 200_000, counted = n % 7 != 0, deviceId = "d")
}
private val stats = StatsCalculator.compute(week, events, mapOf(StatsCalculator.artistKey(ArtistRef(null, "Frank Ocean")) to LocalDateTime(2025, 3, 1, 0, 0).toInstant(london)), previousTotalMs = 140 * 60_000L)
private val deck = WrappedDeckBuilder.build(stats)

@OptIn(ExperimentalTestApi::class)
private fun shot(name: String, card: WrappedCard) = runDesktopComposeUiTest(360, 640) {
    setContent { BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL, reduceMotion = true) { WrappedCardView(card, Modifier.fillMaxSize()) } }
    waitForIdle()
    onRoot().captureRoboImage("src/desktopTest/snapshots/wrapped_$name.png")
}

private inline fun <reified T : WrappedCard> card(): T = deck.filterIsInstance<T>().single()

class WrappedSnapshotTest {
    init { Locale.setDefault(Locale.UK) }

    @Test fun deckHasEveryCard() = assertEquals(12, deck.size)
    @Test fun intro() = shot("intro", card<WrappedCard.Intro>())
    @Test fun totalMinutes() = shot("totalMinutes", card<WrappedCard.TotalMinutes>())
    @Test fun topSong() = shot("topSong", card<WrappedCard.TopSong>())
    @Test fun topSongs() = shot("topSongs", card<WrappedCard.TopSongs>())
    @Test fun topArtist() = shot("topArtist", card<WrappedCard.TopArtist>())
    @Test fun topArtists() = shot("topArtists", card<WrappedCard.TopArtists>())
    @Test fun topAlbum() = shot("topAlbum", card<WrappedCard.TopAlbum>())
    @Test fun listeningClock() = shot("listeningClock", card<WrappedCard.ListeningClock>())
    @Test fun newArtists() = shot("newArtists", card<WrappedCard.NewArtists>())
    @Test fun streak() = shot("streak", card<WrappedCard.Streak>())
    @Test fun comparison() = shot("comparison", card<WrappedCard.Comparison>())
    @Test fun summary() = shot("summary", card<WrappedCard.Summary>())
    @Test fun notEnoughData() = shot("notEnoughData", WrappedCard.NotEnoughData)
}

class WrappedExporterTest {
    @Test fun exportsFullSizePng() = kotlinx.coroutines.runBlocking {
        val png = WrappedExporter.exportPng(WrappedCard.Streak(6))
        val image = javax.imageio.ImageIO.read(png.inputStream())
        assertEquals(1080, image.width)
        assertEquals(1920, image.height)
    }
}
