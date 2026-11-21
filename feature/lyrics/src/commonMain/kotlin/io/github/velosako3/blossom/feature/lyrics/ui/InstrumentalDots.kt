// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Three dots for an instrumental gap. While the gap plays they breathe (0.8 ↔ 1.0, 1.2 s) and fill one by one
 * towards the next line. [progress] is read in the draw phase only.
 */
@Composable
internal fun InstrumentalDots(active: Boolean, progress: () -> Float, color: Color, breathe: Boolean, modifier: Modifier = Modifier) {
    val scale: State<Float> = if (active && breathe) {
        rememberInfiniteTransition().animateFloat(0.8f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse))
    } else remember { mutableFloatStateOf(1f) }
    Canvas(modifier.size(width = 72.dp, height = 24.dp)) {
        val p = if (active) progress() else 0f
        val r = 6.dp.toPx() * scale.value
        val step = size.width / 3
        for (i in 0 until 3) {
            val fill = (p * 3 - i).coerceIn(0f, 1f)
            drawCircle(color.copy(alpha = 0.35f + 0.65f * fill), r, Offset(step * i + step / 2, size.height / 2))
        }
    }
}
