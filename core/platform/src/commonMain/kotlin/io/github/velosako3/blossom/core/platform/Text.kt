// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

/** [s] in Unicode normalization form C (composed accents), so the same words compare equal. */
expect fun nfc(s: String): String

/** SHA-1 of [bytes] as lowercase hex. */
expect fun sha1Hex(bytes: ByteArray): String
