// SPDX-License-Identifier: GPL-3.0-only
// Ported from BitChord (https://github.com/kushagrasinghx/BitChord) at 21356206,
// sharedUi/src/jvmMain/kotlin/com/music/bitchord/ui/player/PlayerPlatformUi.jvm.kt (GPL-3.0).
// Modified for blossom, 2026: package; only the lyric panel's two platform pieces; the same for iOS (Skia, as on desktop).
package io.github.velosako3.blossom.feature.lyrics.ui.panel

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.floor
import kotlin.math.round
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect as SkRect

internal actual val renderEffectBlurSupported: Boolean = true

private val subPixelSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)

internal actual fun DrawScope.clipShiftedDown(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    dy: Float,
    block: DrawScope.() -> Unit,
) {
    val whole = floor(dy)
    val fraction = dy - whole
    if (fraction < 0.02f || fraction > 0.98f) {
        clipRect(left = left, top = top, right = right, bottom = bottom) {
            translate(top = round(dy)) { block() }
        }
        return
    }
    val canvas = drawContext.canvas.nativeCanvas
    val paint = Paint().apply {
        imageFilter = ImageFilter.makeMatrixTransform(
            Matrix33.makeTranslate(0f, fraction),
            subPixelSampling,
            null,
        )
    }
    // Room for the pixel the fraction reaches into below the clip.
    canvas.saveLayer(SkRect.makeLTRB(left, top - 1f, right, bottom + 1f), paint)
    // Clipped where it will be *before* the layer moves it, so it lands
    // exactly on the rectangle asked for.
    clipRect(left = left, top = top - fraction, right = right, bottom = bottom - fraction) {
        translate(top = whole) { block() }
    }
    canvas.restore()
    paint.close()
}
