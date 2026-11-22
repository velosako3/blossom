// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player.eq

import kotlin.math.ln

/**
 * Maps the 10 reference-band gains onto a device's own bands (Android devices expose 5, libVLC 10, others vary):
 * linear interpolation on a log-frequency scale between the neighbouring reference bands, clamped at the edges.
 */
fun mapBands(gains10: FloatArray, deviceCenterFreqsHz: IntArray): FloatArray = FloatArray(deviceCenterFreqsHz.size) { i ->
    val f = deviceCenterFreqsHz[i].toDouble()
    val upper = EQ_BANDS_HZ.indexOfFirst { it >= f }
    when {
        upper == 0 -> gains10.first()
        upper < 0 -> gains10.last()
        else -> {
            val lo = EQ_BANDS_HZ[upper - 1].toDouble(); val hi = EQ_BANDS_HZ[upper].toDouble()
            val t = ln(f / lo) / ln(hi / lo)
            (gains10[upper - 1] + (gains10[upper] - gains10[upper - 1]) * t).toFloat()
        }
    }
}
