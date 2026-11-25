// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import java.security.MessageDigest
import java.text.Normalizer

actual fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)

actual fun sha1Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
