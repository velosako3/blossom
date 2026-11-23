// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.lan

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * What a QR code (or link) carries to pair with this device: who it is, where to reach it, and a one-time secret
 * that authenticates the key exchange. `blossom://pair?d=<base64url JSON>`.
 */
@Serializable
data class PairingPayload(
    val v: Int = 1,
    val deviceId: String,
    val deviceName: String,
    /** Every LAN address this device has; the other side tries them in order. */
    val hosts: List<String>,
    val port: Int,
    /** 32 random bytes (QR) or 8 (typed code), base64url. */
    val secret: String,
) {
    fun toQrString(): String = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.encodeToString(serializer(), this).toByteArray())

    fun secretBytes(): ByteArray = Base64.getUrlDecoder().decode(secret)

    companion object {
        private const val PREFIX = "blossom://pair?d="
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun newSecret(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(PeerCrypto.randomBytes(32))

        fun parse(s: String): PairingPayload? {
            if (!s.trim().startsWith(PREFIX)) return null
            return runCatching {
                val p = json.decodeFromString(serializer(), String(Base64.getUrlDecoder().decode(s.trim().removePrefix(PREFIX))))
                p.takeIf { it.v == 1 && it.deviceId.isNotBlank() && it.hosts.isNotEmpty() && it.port in 1..65535 && it.secretBytes().size >= 8 }
            }.getOrNull()
        }
    }
}

/**
 * Desktop-to-desktop pairing without a camera: eight words that hold the IPv4 address and a 64-bit one-time secret
 * (the port is fixed: [PORT]). The words come from a generated list of 4,096 two-syllable words (12 bits each).
 * 64 bits keep the secret out of reach of offline guessing; the server also allows only 5 tries per pairing window.
 */
object PairCode {
    const val PORT = 53683
    private val consonants = "bdfghjklmnprstvz".toList()
    private val vowels = "aeio".toList()
    private val syllables = consonants.flatMap { c -> vowels.map { v -> "$c$v" } } // 64

    val words: List<String> = syllables.flatMap { a -> syllables.map { b -> a + b } } // 4,096

    class Decoded(val host: String, val secret: ByteArray)

    const val WORDS = 8

    fun newSecret(): ByteArray = PeerCrypto.randomBytes(8)

    fun encode(ipv4: String, secret: ByteArray): List<String> {
        require(secret.size == 8)
        val ip = ipv4.split('.').map { it.toInt() }
        require(ip.size == 4 && ip.all { it in 0..255 })
        var bits = java.math.BigInteger.ZERO
        (ip + secret.map { it.toInt() and 0xff }).forEach { bits = bits.shiftLeft(8).or(java.math.BigInteger.valueOf(it.toLong())) }
        return (WORDS - 1 downTo 0).map { i -> words[bits.shiftRight(i * 12).toInt() and 0xfff] }
    }

    fun decode(text: String): Decoded? {
        val parts = text.trim().lowercase().split(Regex("""[\s,.-]+""")).filter { it.isNotEmpty() }
        if (parts.size != WORDS) return null
        val index = parts.map { words.indexOf(it).takeIf { i -> i >= 0 } ?: return null }
        var bits = java.math.BigInteger.ZERO
        index.forEach { bits = bits.shiftLeft(12).or(java.math.BigInteger.valueOf(it.toLong())) }
        val bytes = (11 downTo 0).map { i -> bits.shiftRight(i * 8).toInt() and 0xff }
        return Decoded(bytes.take(4).joinToString("."), bytes.drop(4).map { it.toByte() }.toByteArray())
    }
}
