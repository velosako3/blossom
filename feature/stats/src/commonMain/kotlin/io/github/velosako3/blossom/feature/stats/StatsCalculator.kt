// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats

import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.PlayEvent
import java.time.temporal.ChronoUnit
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * Stats for one period from its play events. A play belongs to the period it started in. Listening time includes every
 * play; rankings count only counted plays (30 s or a natural end), then listening time, then the most recent play.
 */
object StatsCalculator {
    private const val TOP = 5
    private val SPACES = Regex("""\s+""")

    /** Artists without an ID are keyed by their normalized name ("  The  Band " = "the band"). */
    fun artistKey(a: ArtistRef): String = a.id?.let { "id:$it" } ?: "name:" + SPACES.replace(a.name.trim().lowercase(), " ")

    fun albumKey(a: AlbumRef): String = a.id?.let { "id:$it" } ?: "name:" + SPACES.replace(a.title.trim().lowercase(), " ")

    fun compute(period: Period, events: List<PlayEvent>, allTimeFirstPlays: Map<String, Instant>, previousTotalMs: Long): PeriodStats {
        val inPeriod = events.filter { it.startedAt in period }
        val counted = inPeriod.filter { it.counted }
        val zone = when (period) { is Period.Week -> period.zone; is Period.Month -> period.zone; is Period.Year -> period.zone }
        val total = inPeriod.sumOf { it.playedMs }

        val hours = LongArray(24)
        val weekdays = LongArray(7)
        for (e in inPeriod) {
            val local = e.startedAt.toJavaInstant().atZone(zoneOf(zone))
            hours[local.hour] += e.playedMs
            weekdays[local.dayOfWeek.value - 1] += e.playedMs
        }

        // New: the artist's first-ever counted play is in this period (a skipped stranger isn't new to you).
        val countedArtists = counted.flatMap { e -> e.track.artists }.distinctBy(::artistKey)
        val newArtists = countedArtists.filter { a -> allTimeFirstPlays[artistKey(a)]?.let { it in period } ?: true }

        val days = counted.map { localDate(it.startedAt, zone) }.toSortedSet()
        var longest = 0
        var run = 0
        var prev: java.time.LocalDate? = null
        for (d in days) {
            run = if (prev != null && ChronoUnit.DAYS.between(prev, d) == 1L) run + 1 else 1
            longest = maxOf(longest, run)
            prev = d
        }

        return PeriodStats(
            period = period,
            totalListeningMs = total,
            countedPlays = counted.size,
            topTracks = rank(inPeriod, { listOf(it.track.id.value) }) { e, _ -> e.track },
            // Each artist of a song is credited under its own name.
            topArtists = rank(inPeriod, { e -> e.track.artists.map(::artistKey) }) { e, k -> e.track.artists.first { artistKey(it) == k } },
            topAlbums = rank(inPeriod.filter { it.track.album != null }, { e -> listOf(albumKey(e.track.album!!)) }) { e, _ -> e.track.album!! },
            byHourOfDayMs = hours,
            byDayOfWeekMs = weekdays,
            newArtists = newArtists,
            longestStreakDays = longest,
            listeningChangePercent = if (previousTotalMs == 0L) null else (total - previousTotalMs) * 100.0 / previousTotalMs,
        )
    }

    /** Ranks items keyed by [keys]: counted plays, then listening time, then the latest play. */
    private fun <T> rank(events: List<PlayEvent>, keys: (PlayEvent) -> List<String>, item: (PlayEvent, key: String) -> T): List<Ranked<T>> {
        class Acc(var item: T, var plays: Int = 0, var ms: Long = 0, var last: Instant = Instant.DISTANT_PAST)
        val acc = LinkedHashMap<String, Acc>()
        for (e in events) for (k in keys(e).distinct()) {
            val a = acc.getOrPut(k) { Acc(item(e, k)) }
            if (e.counted) a.plays++
            a.ms += e.playedMs
            if (e.startedAt > a.last) a.last = e.startedAt
        }
        return acc.values.filter { it.plays > 0 }
            .sortedWith(compareByDescending<Acc> { it.plays }.thenByDescending { it.ms }.thenByDescending { it.last })
            .take(TOP)
            .map { Ranked(it.item, it.plays, it.ms) }
    }
}
