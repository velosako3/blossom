// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.background

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassShaders
import io.github.velosako3.blossom.core.designsystem.theme.LocalBlossomColors
import io.github.velosako3.blossom.core.designsystem.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val LOOP_MS = 20_000
private const val CROSSFADE_MS = 600

/**
 * The slow mesh gradient behind the whole app: four soft blobs in the artwork's dominant, vibrant and muted
 * colours drift on a 20 s loop. Colours crossfade over 600 ms when the track changes. Static with reduce motion.
 * Drawing reads the animated phase only in the draw phase, so it never recomposes per frame.
 */
@Composable
fun BlossomBackground(palette: ArtworkPalette, modifier: Modifier = Modifier) {
    val dark = LocalBlossomColors.current.isDark
    val reduceMotion = LocalReduceMotion.current
    val fade = tween<Color>(CROSSFADE_MS)
    val dominant by animateColorAsState(palette.dominant, fade)
    val vibrant by animateColorAsState(palette.vibrant, fade)
    val muted by animateColorAsState(palette.muted, fade)

    val phase: State<Float> = if (reduceMotion) {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.18f) }
    } else {
        rememberInfiniteTransition(label = "blossom-bg").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        )
    }

    Canvas(modifier) {
        val base = if (dark) lerp(dominant, Color.Black, 0.45f) else lerp(dominant, Color.White, 0.72f)
        drawRect(base)
        val t = phase.value * 2f * PI.toFloat()
        val strength = if (dark) 1f else 0.7f
        blob(vibrant, strength, 0.22f + 0.14f * cos(t), 0.20f + 0.12f * sin(t), 0.55f)
        blob(muted, strength, 0.82f + 0.10f * sin(t + 1.3f), 0.28f + 0.16f * cos(t + 0.7f), 0.50f)
        blob(lerp(dominant, vibrant, 0.35f), strength, 0.62f + 0.16f * cos(t + 2.6f), 0.88f + 0.08f * sin(t + 2.1f), 0.60f)
        blob(lerp(vibrant, muted, 0.5f), strength * 0.85f, 0.12f + 0.10f * sin(t + 4.0f), 0.82f + 0.10f * cos(t + 3.3f), 0.45f)
        // A gentle vignette for depth, then the anti-banding grain.
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.28f else 0.06f)),
                center = Offset(size.width / 2, size.height * 0.45f),
                radius = maxOf(size.width, size.height) * 0.8f,
            ),
        )
        drawRect(GlassShaders.grainBrush, alpha = 0.025f)
    }
}

/** A soft radial blob centred at fractions ([fx], [fy]) of the canvas, radius as a fraction of the larger side. */
private fun DrawScope.blob(color: Color, alpha: Float, fx: Float, fy: Float, radiusFraction: Float) {
    val r = maxOf(size.width, size.height) * radiusFraction
    val c = Offset(size.width * fx, size.height * fy)
    drawRect(
        Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            0.5f to color.copy(alpha = alpha * 0.5f),
            1f to color.copy(alpha = 0f),
            center = c,
            radius = r,
        ),
    )
}
