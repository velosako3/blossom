// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.color

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtworkPaletteTest {
    private fun solid(color: Color, size: Int = 64) = ImageBitmap(size, size).also {
        Canvas(it).drawRect(Rect(Offset.Zero, androidx.compose.ui.geometry.Size(size.toFloat(), size.toFloat())), Paint().apply { this.color = color })
    }

    private fun twoTone(a: Color, b: Color) = ImageBitmap(64, 64).also {
        val c = Canvas(it)
        c.drawRect(Rect(0f, 0f, 64f, 40f), Paint().apply { color = a })
        c.drawRect(Rect(0f, 40f, 64f, 64f), Paint().apply { color = b })
    }

    @Test fun solidRedIsDominantRed() = runTest {
        val p = extractPalette(solid(Color(0xFFE01010)))
        assertTrue(p.dominant.red >= 0.8f, "red ${p.dominant.red}")
        assertTrue(p.dominant.green <= 0.2f && p.dominant.blue <= 0.2f, "${p.dominant}")
    }

    @Test fun onBackgroundAlwaysReadable() = runTest {
        val images = listOf(
            solid(Color(0xFFE01010)),
            solid(Color(0xFFF7F7F5)),
            solid(Color(0xFF080808)),
            twoTone(Color(0xFF1E88E5), Color(0xFFFFC107)),
            solid(Color(0xFF777777), size = 300),
        )
        for (img in images) {
            val p = extractPalette(img)
            assertTrue(contrastRatio(p.onBackground, p.dominant) >= 4.5, "$p")
        }
    }

    @Test fun twoToneDominantIsTheLargerArea() = runTest {
        val p = extractPalette(twoTone(Color(0xFF1E88E5), Color(0xFFFFC107)))
        assertTrue(p.dominant.blue > p.dominant.red, "${p.dominant}")
    }

    @Test fun transparentGivesDefault() = runTest {
        assertEquals(ArtworkPalette.Default, extractPalette(ImageBitmap(64, 64)))
    }
}
