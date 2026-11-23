// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Design tokens shared by every blossom surface. */
object BlossomTokens {
    val radiusSmall = 12.dp
    val radiusMedium = 20.dp
    val radiusLarge = 28.dp
    /** Pill/circle: 50% of the shorter side. */
    val radiusFull = RoundedCornerShape(percent = 50)

    /** Spacing on a 4 dp grid: `space(1) == 4.dp`, `space(4) == 16.dp`. */
    fun space(steps: Int): Dp = (4 * steps).dp

    const val motionFast = 150
    const val motionStandard = 280
    const val motionEmphasized = 450

    const val springDampingRatio = 0.8f
    const val springStiffness = 380f
}
