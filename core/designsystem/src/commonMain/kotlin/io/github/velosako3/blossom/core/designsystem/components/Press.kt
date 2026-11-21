// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTokens
import io.github.velosako3.blossom.core.designsystem.theme.LocalReduceMotion

/** The shared press response: a small spring squeeze instead of a ripple, instant with reduce motion. */
@Composable
internal fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.95f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(
        if (isPressed && !reduce) pressed else 1f,
        if (reduce) snap() else spring(BlossomTokens.springDampingRatio, BlossomTokens.springStiffness),
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
