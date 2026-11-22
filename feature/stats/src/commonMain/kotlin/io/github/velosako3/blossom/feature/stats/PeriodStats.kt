// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats

import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.Track

data class Ranked<T>(val item: T, val plays: Int, val listeningMs: Long)

/** One period's listening. Hours and weekdays are local; [byDayOfWeekMs] starts on Monday. */
class PeriodStats(
    val period: Period,
    val totalListeningMs: Long,
    val countedPlays: Int,
    val topTracks: List<Ranked<Track>>,
    val topArtists: List<Ranked<ArtistRef>>,
    val topAlbums: List<Ranked<AlbumRef>>,
    val byHourOfDayMs: LongArray,
    val byDayOfWeekMs: LongArray,
    val newArtists: List<ArtistRef>,
    val longestStreakDays: Int,
    /** Null when there was no listening in the previous period. */
    val listeningChangePercent: Double?,
)
