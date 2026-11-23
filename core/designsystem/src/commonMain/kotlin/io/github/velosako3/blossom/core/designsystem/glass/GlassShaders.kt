// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import kotlin.random.Random

/**
 * Shared, allocation-once pieces of the glass look. Refraction and blur come from Haze's glass and blur
 * effects (AGSL on Android 13+, SkSL `RuntimeEffect` on desktop); this file holds what blossom draws on top.
 */
internal object GlassShaders {
    /** 128×128 monochrome noise tile, drawn at low alpha to stop banding in blurred gradients. */
    val grainTile: ImageBitmap by lazy {
        val size = 128
        val rnd = Random(0x5EED)
        val paint = Paint()
        ImageBitmap(size, size).also { bmp ->
            val canvas = Canvas(bmp)
            for (y in 0 until size) for (x in 0 until size) {
                val v = rnd.nextFloat()
                paint.color = Color(v, v, v)
                canvas.drawRect(Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f), paint)
            }
        }
    }

    val grainBrush: Brush by lazy { ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated)) }

    /** The 1 dp specular rim: white 45% at the top fading to 5% at the bottom. */
    val rimBrush: Brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f)))

    /** OFF quality: a clean 12% hairline instead of the lit rim. */
    val hairline: Color = Color.White.copy(alpha = 0.12f)
}

