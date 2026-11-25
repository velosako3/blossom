// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import java.time.DayOfWeek
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

actual fun monthName(month: Int, full: Boolean): String =
    Month.of(month).getDisplayName(if (full) TextStyle.FULL_STANDALONE else TextStyle.SHORT, Locale.getDefault())

actual fun weekdayShortNames(): List<String> = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

actual fun groupedNumber(n: Long): String = "%,d".format(n)
