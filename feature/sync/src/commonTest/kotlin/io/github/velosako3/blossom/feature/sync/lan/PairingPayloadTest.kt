// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.lan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingPayloadTest {
    @Test fun roundTrip() {
        val p = PairingPayload(deviceId = "desk-1", deviceName = "Velo's PC ✦", hosts = listOf("192.168.1.20", "10.0.0.5"), port = 53683, secret = PairingPayload.newSecret())
        val qr = p.toQrString()
        assertTrue(qr.startsWith("blossom://pair?d="))
        assertEquals(p, PairingPayload.parse(qr))
    }

    @Test fun garbageIsNull() {
        assertNull(PairingPayload.parse("hello"))
        assertNull(PairingPayload.parse("blossom://pair?d=!!!"))
        assertNull(PairingPayload.parse("blossom://pair?d=e30")) // {}
        assertNull(PairingPayload.parse("https://example.com/?d=x"))
    }

    @Test fun sixWordCode() {
        val secret = PairCode.newSecret()
        val words = PairCode.encode("192.168.1.20", secret)
        assertEquals(6, words.size)
        val back = PairCode.decode(words.joinToString(" ").uppercase())!!
        assertEquals("192.168.1.20", back.host)
        assertEquals(secret.toList(), back.secret.toList())
        assertNull(PairCode.decode("only three words"))
        assertNull(PairCode.decode("zzzz zzzz zzzz zzzz zzzz zzzz"))
        assertEquals(4096, PairCode.words.toSet().size)
    }
}
