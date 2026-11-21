// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.pow

/** WCAG 2.x relative luminance of an opaque sRGB colour. */
fun relativeLuminance(c: Color): Double {
    fun lin(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
}

/** WCAG contrast ratio between two colours, 1.0..21.0. Translucent colours are judged as given (alpha ignored). */
fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/**
 * The smallest alpha in 0..1 for [scrim] composited over [background] that gives [text] at least [minRatio].
 * Returns 1 when even an opaque scrim can't reach it. Evaluated with Compose's own colour packing, so the
 * returned alpha is the one that actually renders.
 */
fun requiredScrimAlpha(text: Color, background: Color, scrim: Color, minRatio: Double = 4.5): Float {
    fun ok(alpha: Float) = contrastRatio(text, scrim.copy(alpha = alpha).compositeOver(background)) >= minRatio
    if (ok(0f)) return 0f
    if (!ok(1f)) return 1f
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val mid = (lo + hi) / 2
        if (ok(mid)) hi = mid else lo = mid
    }
    return hi
}
