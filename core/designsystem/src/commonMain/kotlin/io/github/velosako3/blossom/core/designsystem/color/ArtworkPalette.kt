// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.color

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Colours taken from the current album art; drives the animated background and accents. */
data class ArtworkPalette(val dominant: Color, val vibrant: Color, val muted: Color, val onBackground: Color) {
    companion object {
        /** blossom's own colours, used before any artwork is known: deep plum, blossom pink, dusk blue. */
        val Default = ArtworkPalette(
            dominant = Color(0xFF2A1B3D),
            vibrant = Color(0xFFE86A9A),
            muted = Color(0xFF3B4A6B),
            onBackground = Color.White,
        )
    }
}

/**
 * Extracts a palette from album art: draws a 64×64 copy, buckets opaque pixels into a 4-bit-per-channel
 * histogram and picks the most common colour (dominant), the most saturated well-populated one (vibrant)
 * and a calm mid-tone (muted). Runs on [Dispatchers.Default].
 */
suspend fun extractPalette(image: ImageBitmap): ArtworkPalette = withContext(Dispatchers.Default) {
    val size = 64
    val small = ImageBitmap(size, size)
    Canvas(small).drawImageRect(
        image,
        srcSize = IntSize(image.width, image.height),
        dstSize = IntSize(size, size),
        paint = Paint().apply { filterQuality = FilterQuality.Low },
    )
    val pixels = IntArray(size * size)
    small.readPixels(pixels)

    // bucket -> [count, sumR, sumG, sumB]
    val counts = IntArray(4096)
    val sums = LongArray(4096 * 3)
    var opaque = 0
    for (argb in pixels) {
        if ((argb ushr 24) < 128) continue
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
        counts[key]++
        sums[key * 3] += r.toLong(); sums[key * 3 + 1] += g.toLong(); sums[key * 3 + 2] += b.toLong()
        opaque++
    }
    if (opaque == 0) return@withContext ArtworkPalette.Default

    data class Bucket(val color: Color, val count: Int, val sat: Float, val value: Float)
    val buckets = (0 until 4096).filter { counts[it] > 0 }.map { k ->
        val n = counts[k]
        val c = Color((sums[k * 3] / n).toInt(), (sums[k * 3 + 1] / n).toInt(), (sums[k * 3 + 2] / n).toInt())
        val max = maxOf(c.red, c.green, c.blue)
        val min = minOf(c.red, c.green, c.blue)
        Bucket(c, n, if (max == 0f) 0f else (max - min) / max, max)
    }
    val dominant = buckets.maxBy { it.count }.color
    val minShare = opaque * 0.02
    val populated = buckets.filter { it.count >= minShare }.ifEmpty { buckets }
    val vibrant = populated.maxBy { it.sat * it.value * (0.5f + 0.5f * it.count / opaque) }.color
        .let { if (it == dominant) lerp(dominant, Color.White, 0.25f) else it }
    val muted = populated.minBy { kotlin.math.abs(it.sat - 0.25f) + kotlin.math.abs(it.value - 0.45f) }.color
        .let { if (it == dominant) lerp(dominant, Color.Black, 0.35f) else it }
    val onBackground = if (contrastRatio(Color.White, dominant) >= contrastRatio(Color.Black, dominant)) Color.White else Color.Black
    ArtworkPalette(dominant, vibrant, muted, onBackground)
}
