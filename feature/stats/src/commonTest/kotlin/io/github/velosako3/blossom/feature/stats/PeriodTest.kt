// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class PeriodTest {
    private val london = TimeZone.of("Europe/London")
    private fun local(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) = LocalDateTime(y, mo, d, h, mi, s).toInstant(london)

    @Test fun weekBoundaryAtMidnightMonday() {
        assertEquals(Period.Week(2026, 47, london), Period.weekContaining(local(2026, 11, 22, 23, 59, 59), london))
        assertEquals(Period.Week(2026, 48, london), Period.weekContaining(local(2026, 11, 23, 0, 0, 0), london))
    }

    @Test fun springForwardWeekIs167Hours() {
        val w = Period.weekContaining(Instant.parse("2026-03-29T01:30:00Z"), london)
        assertEquals(167.hours, w.endExclusive - w.start)
        assertTrue(Instant.parse("2026-03-29T01:30:00Z") in w)
    }

    @Test fun week53Exists() {
        val w = Period.Week(2026, 53, london)
        assertTrue(local(2026, 12, 31, 12, 0) in w)
        assertEquals(Period.Week(2027, 1, london), w.next())
        assertEquals(w, Period.Week(2027, 1, london).previous())
    }

    @Test fun monthsAndYears() {
        val jan = Period.monthContaining(local(2026, 1, 31, 23, 55), london)
        assertEquals(Period.Month(2026, 1, london), jan)
        assertEquals(Period.Month(2026, 2, london), jan.next())
        assertEquals(Period.Month(2025, 12, london), jan.previous())
        assertEquals(Period.Year(2026, london), Period.yearContaining(local(2026, 6, 1, 0, 0), london))
    }
}
