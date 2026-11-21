// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.color

import androidx.compose.ui.graphics.Color

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
