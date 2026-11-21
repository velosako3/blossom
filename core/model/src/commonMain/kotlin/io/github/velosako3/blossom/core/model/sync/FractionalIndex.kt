// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.model.sync

/**
 * Order keys for playlist entries: base-62 strings compared lexicographically. A key never ends in the smallest
 * digit, so there is always room between two keys. Appends and prepends step one digit at a time, so repeated
 * inserts at either end grow keys by about one character per 60 inserts; inserts in the middle bisect.
 */
object FractionalIndex {
    private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val BASE = 62

    private fun d(c: Char): Int = DIGITS.indexOf(c).also { require(it >= 0) { "Invalid key character '$c'" } }
    private fun c(i: Int): Char = DIGITS[i]

    fun between(before: String?, after: String?): String {
        val a = before?.takeIf { it.isNotEmpty() }
        val b = after?.takeIf { it.isNotEmpty() }
        if (a != null && b != null) require(a < b) { "Keys out of order: $a !< $b" }
        return when {
            a == null && b == null -> c(BASE / 2).toString()
            a == null -> prepend(b!!)
            b == null -> append(a)
            else -> midpoint(a, b)
        }
    }

    /** Smallest step after [a]. */
    private fun append(a: String): String {
        val first = d(a[0])
        return if (first < BASE - 1) c(first + 1).toString()
        else a[0] + (if (a.length > 1) append(a.substring(1)) else c(BASE / 2).toString())
    }

    /** Smallest step before [b]. */
    private fun prepend(b: String): String {
        val first = d(b[0])
        return when {
            b.length > 1 && first >= 1 -> b[0].toString() // a strict prefix sorts first and doesn't end in '0'
            first > 1 -> c(first - 1).toString()
            first == 1 -> c(0) + c(BASE / 2).toString() // "1" → "0V"
            else -> c(0) + prepend(b.substring(1)) // b starts with '0'
        }
    }

    /** A key strictly between [a] and [b] (a < b, neither empty). */
    private fun midpoint(a: String, b: String): String {
        var n = 0
        while (n < b.length && (a.getOrNull(n) ?: c(0)) == b[n]) n++
        if (n > 0) return b.substring(0, n) + midpointTail(a.drop(n), b.substring(n))
        return midpointTail(a, b)
    }

    /** a < b and they differ at index 0 ("" counts as all zeros; b may be empty only when a is empty). */
    private fun midpointTail(a: String, b: String?): String {
        val da = if (a.isEmpty()) 0 else d(a[0])
        val db = if (b.isNullOrEmpty()) BASE else d(b[0])
        if (db - da > 1) return c((da + db) / 2).toString()
        // Adjacent digits. If b continues, its first digit alone is a strict prefix of b and above a; otherwise keep
        // a's digit and go one level deeper, above the rest of a.
        return if (b != null && b.length > 1) b[0].toString() else c(da) + midpointTail(a.drop(1), null)
    }
}
