// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.velosako3.blossom.core.designsystem.resources.Res
import io.github.velosako3.blossom.core.designsystem.resources.inter_bold
import io.github.velosako3.blossom.core.designsystem.resources.inter_medium
import io.github.velosako3.blossom.core.designsystem.resources.inter_regular
import io.github.velosako3.blossom.core.designsystem.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/** Inter (OFL), bundled: display 700, title 600, body 400, label 500. */
@Composable
fun interFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

@Composable
fun blossomTypography(family: FontFamily = interFontFamily()): Typography {
    fun style(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
    )
    val display = FontWeight.Bold
    val title = FontWeight.SemiBold
    val body = FontWeight.Normal
    val label = FontWeight.Medium
    return Typography(
        displayLarge = style(display, 48, 54, -0.02),
        displayMedium = style(display, 36, 42, -0.02),
        displaySmall = style(display, 30, 38, -0.015),
        headlineLarge = style(display, 28, 34, -0.01),
        headlineMedium = style(title, 24, 30, -0.01),
        headlineSmall = style(title, 20, 26),
        titleLarge = style(title, 20, 26, -0.005),
        titleMedium = style(title, 16, 22),
        titleSmall = style(title, 14, 20),
        bodyLarge = style(body, 16, 24),
        bodyMedium = style(body, 14, 20),
        bodySmall = style(body, 12, 16),
        labelLarge = style(label, 14, 18, 0.005),
        labelMedium = style(label, 12, 16, 0.01),
        labelSmall = style(label, 11, 14, 0.02),
    )
}
