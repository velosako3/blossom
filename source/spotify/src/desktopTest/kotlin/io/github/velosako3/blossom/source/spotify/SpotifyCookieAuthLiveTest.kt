// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.spotify

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.test.Test
import kotlin.test.assertTrue

/** The web player still ships secrets blossom can read, and Spotify accepts codes made from them. Live only. */
class SpotifyCookieAuthLiveTest {
    @Test fun secretsAndTokenEndpoint(): Unit = runBlocking {
        val found = SpotifyCookieAuth(cookie = { null }).scrapeSecrets()
        println("spotify secrets: ${found.map { it.version }}")
        assertTrue(found.isNotEmpty(), "no secrets found in the web player's scripts")
        val s = found.maxBy { it.version }
        val code = SpotifyCookieAuth.totp(SpotifyCookieAuth.key(s), System.currentTimeMillis())
        val url = "https://open.spotify.com/api/token?reason=init&productType=web-player&totp=$code&totpServer=$code&totpVer=${s.version}"
        val body = OkHttpClient().newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0 Safari/537.36").build())
            .execute().use { assertTrue(it.isSuccessful, "HTTP ${it.code}"); it.body.string() }
        assertTrue("accessToken" in body, body.take(200))
    }
}
