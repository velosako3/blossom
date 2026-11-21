// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.components

import io.github.velosako3.blossom.core.designsystem.theme.blossomSpring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme

/**
 * A glass seek/volume slider: a frosted track with a bright fill and a white thumb that grows while dragged.
 * Tap or drag anywhere; arrow keys step by 5%. Reports values in [valueRange].
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val colors = BlossomTheme.colors
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span == 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
    var dragging by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val thumb by animateDpAsState(if (dragging || focused) 18.dp else 12.dp, blossomSpring())
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    fun emit(f: Float) = change(valueRange.start + f.coerceIn(0f, 1f) * span)

    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics {
                contentDescription?.let { this.contentDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                setProgress { target -> change(target.coerceIn(valueRange)); true }
            }
            .focusable(enabled, interaction)
            .onKeyEvent { e ->
                if (!enabled || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight, Key.DirectionUp -> { emit(fraction + 0.05f); finished?.invoke(); true }
                    Key.DirectionLeft, Key.DirectionDown -> { emit(fraction - 0.05f); finished?.invoke(); true }
                    else -> false
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { emit(it.x / size.width); finished?.invoke() }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; emit(it.x / size.width) },
                    onDragEnd = { dragging = false; finished?.invoke() },
                    onDragCancel = { dragging = false; finished?.invoke() },
                ) { change, _ -> emit(change.position.x / size.width) }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            val trackH = 6.dp.toPx()
            val y = (size.height - trackH) / 2
            val r = CornerRadius(trackH / 2)
            val track = if (colors.isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f)
            drawRoundRect(track, Offset(0f, y), Size(size.width, trackH), r)
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(0f, y), Size(size.width, trackH / 2), r)
            val fill = if (colors.isDark) Color.White.copy(alpha = 0.92f) else colors.accent
            drawRoundRect(fill, Offset(0f, y), Size(size.width * fraction, trackH), r)
            val tr = thumb.toPx() / 2
            val cx = (size.width * fraction).coerceIn(tr, size.width - tr)
            drawCircle(Color.Black.copy(alpha = 0.25f), tr + 2.dp.toPx(), Offset(cx, size.height / 2 + 1.5.dp.toPx()))
            drawCircle(Color.White, tr, Offset(cx, size.height / 2))
        }
    }
}
