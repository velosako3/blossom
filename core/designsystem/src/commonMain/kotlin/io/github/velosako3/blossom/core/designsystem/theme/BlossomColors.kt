// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Theme colours. Glass reads [surface] as its tint base; the alpha comes from the glass quality. */
@Immutable
data class BlossomColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val accent: Color,
    val onAccent: Color,
    val outline: Color,
    val error: Color,
) {
    companion object {
        val Dark = BlossomColors(
            isDark = true,
            background = Color(0xFF0B0A10),
            surface = Color(0xFF1A1822),
            onSurface = Color(0xFFF7F5FA),
            onSurfaceVariant = Color(0xFFC9C4D3),
            accent = Color(0xFFFF8AB5),
            onAccent = Color(0xFF2B0A18),
            outline = Color(0x33FFFFFF),
            error = Color(0xFFFF8A80),
        )
        val Light = BlossomColors(
            isDark = false,
            background = Color(0xFFF3F0F6),
            surface = Color(0xFFFBFAFD),
            onSurface = Color(0xFF15131B),
            onSurfaceVariant = Color(0xFF4A4556),
            accent = Color(0xFFB3265E),
            onAccent = Color.White,
            outline = Color(0x1F000000),
            error = Color(0xFFB3261E),
        )
    }
}
