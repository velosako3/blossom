// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.wrapped

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

actual object WrappedExporter {
    /** The palette the cards are tuned to (the app sets it from the top song's artwork). */
    @Volatile actual var palette: ArtworkPalette = ArtworkPalette.Default

    actual suspend fun exportPng(card: WrappedCard, widthPx: Int, heightPx: Int): ByteArray = withContext(Dispatchers.Default) {
        // Laid out at 360 dp wide, rendered at the requested pixels.
        val scene = ImageComposeScene(widthPx, heightPx, Density(widthPx / 360f)) {
            BlossomTheme(true, palette, glassQuality = GlassQuality.FULL, reduceMotion = true) { WrappedCardView(card, Modifier.fillMaxSize()) }
        }
        try {
            scene.render() // first frame lays out; the second has every resource in place
            val image = scene.render(1_000_000_000L)
            image.encodeToData(EncodedImageFormat.PNG)!!.bytes.also { image.close() }
        } finally {
            scene.close()
        }
    }

    actual suspend fun share(png: ByteArray) {
        val file = withContext(Dispatchers.Main) {
            var chosen: File? = null
            SwingUtilities.invokeAndWait {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Save Wrapped card"
                    selectedFile = File("blossom-wrapped.png")
                    fileFilter = FileNameExtensionFilter("PNG image", "png")
                }
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chosen = chooser.selectedFile
            }
            chosen
        } ?: return
        withContext(Dispatchers.IO) { (if (file.name.endsWith(".png")) file else File(file.path + ".png")).writeBytes(png) }
    }

    actual fun captureWith(capture: (suspend () -> androidx.compose.ui.graphics.ImageBitmap)?) = Unit
}
