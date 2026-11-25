// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signed-in tokens from the account's `sp_dc` cookie, the way the web player gets them: `/api/token` with a TOTP made
 * from a secret the web player ships in its code. Spotify rotates that secret; the current ones are read from the web
 * player's scripts (once per run) with [FALLBACK] as the backstop. Without a valid cookie Spotify answers with an
 * anonymous token, which counts as signed out.
 */
class SpotifyCookieAuth(
    private val cookie: suspend () -> String?,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
    private val web: String = "https://open.spotify.com",
    private val clock: () -> Long = System::currentTimeMillis,
) : SpotifyAuth {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var cached: Triple<String, String, Long>? = null // cookie, token, expires at
    private var secrets: List<Secret>? = null

    /** A TOTP secret as the web player ships it (obfuscated characters) and its version number. */
    data class Secret(val raw: String, val version: Int)

    override suspend fun tokens(): SpotifyTokens? = lock.withLock {
        val c = cookie()?.trim()?.takeIf { it.isNotEmpty() } ?: return@withLock null.also { cached = null }
        cached?.let { (forCookie, token, until) -> if (forCookie == c && until > clock() + 60_000) return@withLock SpotifyTokens(token, null) }
        val known = secrets ?: (runCatching { scrapeSecrets() }.getOrDefault(emptyList()) + FALLBACK).distinctBy { it.version }
            .sortedByDescending { it.version }.also { secrets = it }
        val serverMs = runCatching { get("$web/api/server-time", null).long("serverTime")?.times(1000) }.getOrNull()
        for (s in known.take(3)) {
            val now = clock()
            val url = "$web/api/token?reason=init&productType=web-player&totp=${totp(s, now)}" +
                "&totpServer=${serverMs?.let { totp(s, it) } ?: "unavailable"}&totpVer=${s.version}"
            val o = runCatching { get(url, c) }.getOrNull() ?: continue
            val token = (o["accessToken"] as? JsonPrimitive)?.content ?: continue
            // An anonymous token means the cookie is missing, wrong or signed out.
            if ((o["isAnonymous"] as? JsonPrimitive)?.booleanOrNull == true) return@withLock null
            val until = o.long("accessTokenExpirationTimestampMs") ?: (now + 3_000_000)
            cached = Triple(c, token, until)
            return@withLock SpotifyTokens(token, null)
        }
        null
    }

    private suspend fun get(url: String, spDc: String?): JsonObject = runInterruptible(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", "application/json")
            .apply { if (spDc != null) header("Cookie", "sp_dc=$spDc") }.build()
        http.newCall(request).execute().use { r ->
            check(r.isSuccessful) { "HTTP ${r.code}" }
            json.parseToJsonElement(r.body.string()) as JsonObject
        }
    }

    /** The secrets in the web player's current scripts. */
    internal suspend fun scrapeSecrets(): List<Secret> = runInterruptible(Dispatchers.IO) {
        fun text(url: String) = http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { it.body.string() }
        val scripts = SCRIPT.findAll(text("$web/")).map { it.value }.distinct().toList()
        scripts.flatMap { runCatching { parseSecrets(text(it)) }.getOrDefault(emptyList()) }
    }

    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private val SCRIPT = Regex("""https://open[a-z.-]*spotifycdn\.com/cdn/build/web-player/[^"']+\.js""")
        private val SECRET = Regex("""\{secret:(['"])((?:\\.|(?!\1).)*)\1,version:(\d+)}""")

        /** The web player's secrets on 2026-11-25. */
        val FALLBACK = listOf(Secret(",7/*F(\"rLJ2oxaKL^f+E1xvP@N", 61))

        /** `{secret:'…',version:N}` entries in a web player script (JS string escapes undone). */
        fun parseSecrets(js: String): List<Secret> = SECRET.findAll(js).map { m ->
            Secret(m.groupValues[2].replace(Regex("""\\(.)""")) { it.groupValues[1] }, m.groupValues[3].toInt())
        }.toList()

        /** The web player's key: each character XOR (index mod 33 + 9), the numbers written out in decimal, as bytes. */
        fun key(s: Secret): ByteArray =
            s.raw.mapIndexed { i, c -> (c.code xor (i % 33 + 9)).toString() }.joinToString("").toByteArray(Charsets.UTF_8)

        /** RFC 6238 TOTP: HMAC-SHA1, 30 s steps, 6 digits. */
        fun totp(key: ByteArray, atMs: Long): String {
            val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }
            val h = mac.doFinal(ByteBuffer.allocate(8).putLong(atMs / 1000 / 30).array())
            val o = h.last().toInt() and 15
            val n = ((h[o].toInt() and 0x7f) shl 24) or ((h[o + 1].toInt() and 0xff) shl 16) or ((h[o + 2].toInt() and 0xff) shl 8) or (h[o + 3].toInt() and 0xff)
            return (n % 1_000_000).toString().padStart(6, '0')
        }

        private fun totp(s: Secret, atMs: Long) = totp(key(s), atMs)
    }
}
