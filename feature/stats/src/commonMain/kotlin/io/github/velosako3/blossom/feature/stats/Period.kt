// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaZoneId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * A calendar period in the listener's time zone: an ISO week (Monday first; weeks belong to the year of their
 * Thursday, so some years have a week 53), a month or a year. Boundaries are local midnights, so DST weeks are 167 or
 * 169 hours long.
 */
sealed interface Period {
    val start: Instant
    val endExclusive: Instant
    fun previous(): Period
    fun next(): Period
    operator fun contains(i: Instant): Boolean = i >= start && i < endExclusive

    data class Week(val isoYear: Int, val isoWeek: Int, val zone: TimeZone) : Period {
        private val monday: LocalDate = LocalDate.of(isoYear, 1, 4)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, isoWeek.toLong())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        init { require(monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == isoWeek && monday.get(IsoFields.WEEK_BASED_YEAR) == isoYear) { "no week $isoWeek in $isoYear" } }
        override val start get() = midnight(monday, zone)
        override val endExclusive get() = midnight(monday.plusWeeks(1), zone)
        override fun previous() = week(monday.minusWeeks(1), zone)
        override fun next() = week(monday.plusWeeks(1), zone)
    }

    data class Month(val year: Int, val month: Int, val zone: TimeZone) : Period {
        private val first: LocalDate = LocalDate.of(year, month, 1)
        override val start get() = midnight(first, zone)
        override val endExclusive get() = midnight(first.plusMonths(1), zone)
        override fun previous() = first.minusMonths(1).let { Month(it.year, it.monthValue, zone) }
        override fun next() = first.plusMonths(1).let { Month(it.year, it.monthValue, zone) }
    }

    data class Year(val year: Int, val zone: TimeZone) : Period {
        override val start get() = midnight(LocalDate.of(year, 1, 1), zone)
        override val endExclusive get() = midnight(LocalDate.of(year + 1, 1, 1), zone)
        override fun previous() = Year(year - 1, zone)
        override fun next() = Year(year + 1, zone)
    }

    /** A compact form for navigation: `week:2026-47`, `month:2026-11`, `year:2026`. */
    val key: String
        get() = when (this) {
            is Week -> "week:$isoYear-$isoWeek"
            is Month -> "month:$year-$month"
            is Year -> "year:$year"
        }

    companion object {
        fun fromKey(key: String, z: TimeZone): Period? = runCatching {
            val (kind, value) = key.split(':', limit = 2)
            val parts = value.split('-').map(String::toInt)
            when (kind) {
                "week" -> Week(parts[0], parts[1], z)
                "month" -> Month(parts[0], parts[1], z)
                "year" -> Year(parts[0], z)
                else -> null
            }
        }.getOrNull()

        fun weekContaining(i: Instant, z: TimeZone): Week = week(localDate(i, z), z)
        fun monthContaining(i: Instant, z: TimeZone): Month = localDate(i, z).let { Month(it.year, it.monthValue, z) }
        fun yearContaining(i: Instant, z: TimeZone): Year = Year(localDate(i, z).year, z)
    }
}

private fun week(d: LocalDate, z: TimeZone) = Period.Week(d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), z)

internal fun zoneOf(z: TimeZone): ZoneId = z.toJavaZoneId()

internal fun localDate(i: Instant, z: TimeZone): LocalDate = i.toJavaInstant().atZone(zoneOf(z)).toLocalDate()

private fun midnight(d: LocalDate, z: TimeZone): Instant = d.atStartOfDay(zoneOf(z)).toInstant().toKotlinInstant()
