// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.ui

import io.github.velosako3.blossom.feature.stats.Period
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodLabelTest {
    /** The spring-forward week is 167 hours; its label still ends on Sunday. */
    @Test fun springForwardWeekEndsOnSunday() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        try {
            assertEquals("23–29 Mar 2026", label(Period.Week(2026, 13, TimeZone.of("Europe/London"))))
        } finally { Locale.setDefault(before) }
    }
}
