// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
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

private val palette = ArtworkPalette.Default
private val london = TimeZone.of("Europe/London")
private val week = Period.Week(2026, 47, london)

@OptIn(ExperimentalTestApi::class)
private fun shot(name: String, content: @Composable () -> Unit) = runDesktopComposeUiTest(412, 1600) {
    setContent {
        BlossomTheme(true, palette, glassQuality = GlassQuality.FULL, reduceMotion = true) {
            Box(Modifier.fillMaxSize()) {
                BlossomBackground(palette, Modifier.fillMaxSize().glassBackdropSource())
                content()
            }
        }
    }
    waitForIdle()
    onRoot().captureRoboImage("src/desktopTest/snapshots/$name.png")
}

class StatsSnapshotTest {
    init { Locale.setDefault(Locale.UK) }

    private val artists = listOf("Frank Ocean", "SZA", "Kendrick Lamar", "Tyler, The Creator", "Solange", "Steve Lacy")
    private fun t(i: Int) = Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "v$i"), listOf("Pink + White", "Snooze", "Alright", "See You Again", "Cranes in the Sky", "Bad Habit", "Nights")[i % 7],
        listOf(ArtistRef(null, artists[i % artists.size])), AlbumRef(null, listOf("Blonde", "SOS", "TPAB", "Flower Boy", "A Seat at the Table", "Gemini Rights")[i % 6]), 210_000, null)

    private val events = (0 until 60).map { n ->
        val day = 16 + n % 7
        val hour = listOf(8, 9, 13, 18, 21, 22, 23)[n % 7]
        PlayEvent("p$n", t(n % 9), LocalDateTime(2026, 11, day, hour, (n * 7) % 60).toInstant(london), 180_000L + (n % 4) * 20_000, counted = n % 6 != 0, deviceId = "d")
    }

    @Test fun labels() {
        assertEquals("16–22 Nov 2026", label(week))
        assertEquals("November 2026", label(Period.Month(2026, 11, london)))
        assertEquals("2026", label(Period.Year(2026, london)))
    }

    @Test fun stats_week_full() = shot("stats_week_full") {
        val firsts = mapOf(StatsCalculator.artistKey(ArtistRef(null, "Frank Ocean")) to LocalDateTime(2025, 1, 1, 0, 0).toInstant(london))
        val stats = StatsCalculator.compute(week, events, firsts, previousTotalMs = 120 * 60_000L)
        StatsContent(week, StatsUi.Loaded(stats), {}, {}, {}, {})
    }

    @Test fun stats_empty() = shot("stats_empty") { StatsContent(week, StatsUi.Empty(week), {}, {}, {}, {}) }
}
