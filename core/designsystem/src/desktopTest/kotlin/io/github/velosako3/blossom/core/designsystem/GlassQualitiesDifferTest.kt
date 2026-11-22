// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** The owner's report: switching glass quality changed nothing visible. Each pair must differ clearly. */
class GlassQualitiesDifferTest {
    @OptIn(ExperimentalTestApi::class)
    private fun render(quality: GlassQuality): IntArray {
        var pixels = IntArray(0)
        runDesktopComposeUiTest(400, 300) {
            setContent {
                BlossomTheme(darkTheme = true, palette = ArtworkPalette.Default, glassQuality = quality, reduceMotion = true) {
                    Box(Modifier.fillMaxSize()) {
                        BusyBackground(Modifier.fillMaxSize().glassBackdropSource())
                        GlassSurface(Modifier.size(320.dp, 220.dp).align(Alignment.Center)) {}
                    }
                }
            }
            waitForIdle()
            val map = onRoot().captureToImage().toPixelMap()
            // The panel only: x 40..360, y 40..260.
            pixels = IntArray(320 * 220) { i -> val c = map[40 + i % 320, 40 + i / 320]; ((c.red * 255).toInt() shl 16) or ((c.green * 255).toInt() shl 8) or (c.blue * 255).toInt() }
        }
        return pixels
    }

    private fun meanDiff(a: IntArray, b: IntArray): Double = a.indices.sumOf { i ->
        (0..2).sumOf { ch -> abs(((a[i] shr (ch * 8)) and 0xFF) - ((b[i] shr (ch * 8)) and 0xFF)) }
    } / (a.size * 3.0)

    @Test fun eachQualityLooksDifferent() {
        val full = render(GlassQuality.FULL)
        val reduced = render(GlassQuality.REDUCED)
        val off = render(GlassQuality.OFF)
        val fr = meanDiff(full, reduced); val ro = meanDiff(reduced, off); val fo = meanDiff(full, off)
        println("glass mean difference per channel: full↔reduced $fr, reduced↔off $ro, full↔off $fo")
        assertTrue(fr > 4, "FULL and REDUCED look alike ($fr)")
        assertTrue(ro > 8, "REDUCED and OFF look alike ($ro)")
        assertTrue(fo > 8, "FULL and OFF look alike ($fo)")
    }
}
