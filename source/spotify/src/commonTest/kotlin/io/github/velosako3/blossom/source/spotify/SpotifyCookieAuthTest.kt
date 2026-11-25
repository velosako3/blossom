// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.spotify

import kotlin.test.Test
import kotlin.test.assertEquals

class SpotifyCookieAuthTest {
    @Test fun rfc6238Vector() {
        // RFC 6238 appendix B, SHA1, T = 59 s: 94287082, last 6 digits.
        assertEquals("287082", SpotifyCookieAuth.totp("12345678901234567890".toByteArray(), 59_000))
    }

    @Test fun theWebPlayersKeyAndCode() {
        val s = SpotifyCookieAuth.FALLBACK.first()
        assertEquals("37613638753845989388", String(SpotifyCookieAuth.key(s)).take(20))
        // Computed by a prototype whose codes Spotify's /api/token accepted on 2026-11-25.
        assertEquals("592788", SpotifyCookieAuth.totp(SpotifyCookieAuth.key(s), 1_764_000_000_000))
    }

    @Test fun secretsAreReadFromTheScript() {
        val js = """let ej=[{secret:',7/*F("rLJ2oxaKL^f+E1xvP@N',version:61},{secret:'OmE{ZA.J^":0FG\\Uz?[@WW',version:60},{secret:"{iOFn;4}<1PFYKPV?5{%u14]M>/V0hDH",version:59}].map(e=>e)"""
        val found = SpotifyCookieAuth.parseSecrets(js)
        assertEquals(listOf(61, 60, 59), found.map { it.version })
        assertEquals(SpotifyCookieAuth.FALLBACK.first().raw, found[0].raw)
        assertEquals("OmE{ZA.J^\":0FG\\Uz?[@WW", found[1].raw)
    }
}
