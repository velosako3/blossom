// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.wrapped

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage

/** iOS: like Android, the card on screen is captured, scaled to size, and handed to the share sheet. */
actual object WrappedExporter {
    actual var palette: io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette = io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette.Default
    private var capture: (suspend () -> ImageBitmap)? = null

    actual suspend fun exportPng(card: WrappedCard, widthPx: Int, heightPx: Int): ByteArray {
        val shot = capture?.invoke() ?: error("the Wrapped screen is not showing")
        val surface = Surface.makeRasterN32Premul(widthPx, heightPx)
        surface.canvas.drawImageRect(Image.makeFromBitmap(shot.asSkiaBitmap()), Rect.makeWH(widthPx.toFloat(), heightPx.toFloat()))
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("PNG encoding failed")
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun share(png: ByteArray) {
        val data = png.usePinned { NSData.dataWithBytes(it.addressOf(0), png.size.toULong()) }
        val image = UIImage(data = data)
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        root.presentViewController(UIActivityViewController(listOf(image), null), animated = true, completion = null)
    }

    actual fun captureWith(capture: (suspend () -> ImageBitmap)?) { this.capture = capture }
}
