// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

/** The month's name in the device language ([month] 1-12): "November", or "Nov" when not [full]. Standalone form. */
expect fun monthName(month: Int, full: Boolean): String

/** Short weekday names in the device language, Monday first. */
expect fun weekdayShortNames(): List<String>

/** [n] with the device's thousands separators: "12,345". */
expect fun groupedNumber(n: Long): String
