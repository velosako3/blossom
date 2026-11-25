// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

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
        private val monday: LocalDate = mondayOf(isoYear, isoWeek)
        init { require(isoWeekOf(monday) == isoYear to isoWeek) { "no week $isoWeek in $isoYear" } }
        override val start get() = midnight(monday, zone)
        override val endExclusive get() = midnight(monday.plus(1, DateTimeUnit.WEEK), zone)
        override fun previous() = week(monday.minus(1, DateTimeUnit.WEEK), zone)
        override fun next() = week(monday.plus(1, DateTimeUnit.WEEK), zone)
    }

    data class Month(val year: Int, val month: Int, val zone: TimeZone) : Period {
        private val first: LocalDate = LocalDate(year, month, 1)
        override val start get() = midnight(first, zone)
        override val endExclusive get() = midnight(first.plus(1, DateTimeUnit.MONTH), zone)
        override fun previous() = first.minus(1, DateTimeUnit.MONTH).let { Month(it.year, it.month.number, zone) }
        override fun next() = first.plus(1, DateTimeUnit.MONTH).let { Month(it.year, it.month.number, zone) }
    }

    data class Year(val year: Int, val zone: TimeZone) : Period {
        override val start get() = midnight(LocalDate(year, 1, 1), zone)
        override val endExclusive get() = midnight(LocalDate(year + 1, 1, 1), zone)
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
        fun monthContaining(i: Instant, z: TimeZone): Month = localDate(i, z).let { Month(it.year, it.month.number, z) }
        fun yearContaining(i: Instant, z: TimeZone): Year = Year(localDate(i, z).year, z)
    }
}

/** ISO week-numbering year and week of [d]: the week belongs to the year of its Thursday. */
internal fun isoWeekOf(d: LocalDate): Pair<Int, Int> {
    val thursday = d.plus(4 - d.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)
    return thursday.year to (thursday.dayOfYear - 1) / 7 + 1
}

/** The Monday of ISO week [week] of [isoYear] (week 1 holds 4 January). */
private fun mondayOf(isoYear: Int, week: Int): LocalDate {
    val jan4 = LocalDate(isoYear, 1, 4)
    return jan4.minus(jan4.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY).plus((week - 1) * 7, DateTimeUnit.DAY)
}

private fun week(d: LocalDate, z: TimeZone) = isoWeekOf(d).let { (y, w) -> Period.Week(y, w, z) }

internal fun localDate(i: Instant, z: TimeZone): LocalDate = i.toLocalDateTime(z).date

private fun midnight(d: LocalDate, z: TimeZone): Instant = d.atStartOfDayIn(z)
