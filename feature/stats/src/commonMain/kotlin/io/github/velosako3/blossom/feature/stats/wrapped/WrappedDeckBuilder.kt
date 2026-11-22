// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.wrapped

import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.stats.Period
import io.github.velosako3.blossom.feature.stats.PeriodStats
import io.github.velosako3.blossom.feature.stats.Ranked

/** One story card. Each carries just what it shows. */
sealed interface WrappedCard {
    data class Intro(val period: Period, val artworkUrl: String?) : WrappedCard
    data class TotalMinutes(val minutes: Long, val plays: Int) : WrappedCard
    data class TopSong(val song: Ranked<Track>) : WrappedCard
    data class TopSongs(val songs: List<Ranked<Track>>) : WrappedCard
    data class TopArtist(val artist: Ranked<ArtistRef>, val artworkUrl: String?) : WrappedCard
    data class TopArtists(val artists: List<Ranked<ArtistRef>>) : WrappedCard
    data class TopAlbum(val album: Ranked<AlbumRef>, val artworkUrl: String?) : WrappedCard
    /** [peakHour] is the busiest local hour. */
    data class ListeningClock(val byHourMs: List<Long>, val peakHour: Int) : WrappedCard
    data class NewArtists(val artists: List<ArtistRef>) : WrappedCard
    data class Streak(val days: Int) : WrappedCard
    data class Comparison(val percent: Double) : WrappedCard
    data class Summary(val period: Period, val minutes: Long, val topSong: Track?, val topArtist: ArtistRef?, val artworkUrl: String?) : WrappedCard
    data object NotEnoughData : WrappedCard
}

/** Builds the story in a fixed order, leaving out cards without data. Fewer than 5 counted plays: just [WrappedCard.NotEnoughData]. */
object WrappedDeckBuilder {
    const val MIN_PLAYS = 5

    fun build(stats: PeriodStats): List<WrappedCard> {
        if (stats.countedPlays < MIN_PLAYS) return listOf(WrappedCard.NotEnoughData)
        val topSong = stats.topTracks.firstOrNull()
        val art = topSong?.item?.artworkUrl
        val minutes = stats.totalListeningMs / 60_000
        val artistArt = { a: ArtistRef -> stats.topTracks.firstOrNull { t -> t.item.artists.any { it.name == a.name } }?.item?.artworkUrl }
        val albumArt = { a: AlbumRef -> stats.topTracks.firstOrNull { it.item.album?.title == a.title }?.item?.artworkUrl }
        val peak = stats.byHourOfDayMs.indices.maxByOrNull { stats.byHourOfDayMs[it] } ?: 0
        return buildList {
            add(WrappedCard.Intro(stats.period, art))
            add(WrappedCard.TotalMinutes(minutes, stats.countedPlays))
            topSong?.let { add(WrappedCard.TopSong(it)) }
            if (stats.topTracks.size > 1) add(WrappedCard.TopSongs(stats.topTracks))
            stats.topArtists.firstOrNull()?.let { add(WrappedCard.TopArtist(it, artistArt(it.item))) }
            if (stats.topArtists.size > 1) add(WrappedCard.TopArtists(stats.topArtists))
            stats.topAlbums.firstOrNull()?.let { add(WrappedCard.TopAlbum(it, albumArt(it.item))) }
            if (stats.byHourOfDayMs.any { it > 0 }) add(WrappedCard.ListeningClock(stats.byHourOfDayMs.toList(), peak))
            if (stats.newArtists.isNotEmpty()) add(WrappedCard.NewArtists(stats.newArtists))
            if (stats.longestStreakDays > 1) add(WrappedCard.Streak(stats.longestStreakDays))
            stats.listeningChangePercent?.let { add(WrappedCard.Comparison(it)) }
            add(WrappedCard.Summary(stats.period, minutes, topSong?.item, stats.topArtists.firstOrNull()?.item, art))
        }
    }
}
