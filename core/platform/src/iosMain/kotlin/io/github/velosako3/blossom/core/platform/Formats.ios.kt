// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import platform.Foundation.NSDateFormatter
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle

actual fun monthName(month: Int, full: Boolean): String {
    val names = NSDateFormatter().run { if (full) standaloneMonthSymbols else shortStandaloneMonthSymbols }
    return names?.getOrNull(month - 1) as? String ?: month.toString()
}

// Apple's list starts on Sunday.
actual fun weekdayShortNames(): List<String> =
    (NSDateFormatter().shortWeekdaySymbols ?: emptyList<Any>()).map { it.toString() }.let { if (it.size == 7) it.drop(1) + it.first() else it }

actual fun groupedNumber(n: Long): String =
    NSNumberFormatter().apply { numberStyle = NSNumberFormatterDecimalStyle }.stringFromNumber(NSNumber(longLong = n)) ?: n.toString()
