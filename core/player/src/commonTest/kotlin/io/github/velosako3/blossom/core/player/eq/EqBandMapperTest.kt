// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player.eq

import kotlin.test.Test
import kotlin.test.assertEquals

class EqBandMapperTest {
    private val device = intArrayOf(60, 230, 910, 3600, 14000)

    private fun assertBands(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { assertEquals(expected[it], actual[it], 0.01f, "band $it: ${actual.toList()}") }
    }

    @Test fun flatIsFlat() = assertBands(FloatArray(5), mapBands(EqPreset.FLAT.gains, device))

    @Test fun bassBoostInterpolatesOnALogScale() {
        // Bass boost: 31 Hz +6, 62 Hz +5, 125 Hz +4, 250 Hz +2, then 0.
        // 60 Hz sits 95.3 % of the way (in octaves) from 31 to 62 Hz: 6 − 0.953 = 5.047.
        // 230 Hz sits 88.0 % of the way from 125 to 250 Hz: 4 − 2 × 0.880 = 2.241.
        assertBands(floatArrayOf(5.047f, 2.241f, 0f, 0f, 0f), mapBands(EqPreset.BASS_BOOST.gains, device))
    }

    @Test fun outOfRangeClampsToTheEdgeBands() {
        val gains = floatArrayOf(3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -4f)
        assertBands(floatArrayOf(3f, -4f), mapBands(gains, intArrayOf(20, 20000)))
    }

    @Test fun settingsRoundTrip() {
        val s = EqSettings(enabled = true).choose(EqPreset.ROCK).setBand(3, 7.5f).copy(preampDb = -3f)
        assertEquals(EqPreset.CUSTOM, s.preset)
        assertEquals(s, EqSettings.decode(s.encode()))
        assertEquals(EqSettings(), EqSettings.decode("garbage"))
    }

    @Test fun presetsStayInRange() {
        EqPreset.entries.forEach { p -> assertEquals(10, p.gains.size); p.gains.forEach { g -> assert(g in -12f..12f) } }
    }
}
