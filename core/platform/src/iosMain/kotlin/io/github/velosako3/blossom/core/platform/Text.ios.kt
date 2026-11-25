// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun nfc(s: String): String = (s as NSString).precomposedStringWithCanonicalMapping

@OptIn(ExperimentalForeignApi::class)
actual fun sha1Hex(bytes: ByteArray): String = memScoped {
    val out = allocArray<UByteVar>(CC_SHA1_DIGEST_LENGTH)
    bytes.usePinned { CC_SHA1(if (bytes.isEmpty()) null else it.addressOf(0), bytes.size.toUInt(), out) }
    (0 until CC_SHA1_DIGEST_LENGTH).joinToString("") { out[it].toString(16).padStart(2, '0') }
}
