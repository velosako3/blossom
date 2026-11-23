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
import io.github.velosako3.blossom.feature.stats.resources.Res
import io.github.velosako3.blossom.feature.stats.resources.wr_png_image
import io.github.velosako3.blossom.feature.stats.resources.wr_save_title
import org.jetbrains.compose.resources.getString
import javax.swing.filechooser.FileNameExtensionFilter

actual object WrappedExporter {
    /** The palette the cards are tuned to (the app sets it from the top song's artwork). */
    @Volatile actual var palette: ArtworkPalette = ArtworkPalette.Default

    actual suspend fun exportPng(card: WrappedCard, widthPx: Int, heightPx: Int): ByteArray = withContext(Dispatchers.Default) {
        // Laid out at 360 dp wide, rendered at the requested pixels.
        // Artwork loads asynchronously: fetch it into the image cache first, then give the card a few frames to show it.
        artworkOf(card).forEach { url ->
            runCatching { coil3.SingletonImageLoader.get(coil3.PlatformContext.INSTANCE).execute(coil3.request.ImageRequest.Builder(coil3.PlatformContext.INSTANCE).data(url).build()) }
        }
        val scene = ImageComposeScene(widthPx, heightPx, Density(widthPx / 360f)) {
            BlossomTheme(true, palette, glassQuality = GlassQuality.FULL, reduceMotion = true) { WrappedCardView(card, Modifier.fillMaxSize()) }
        }
        try {
            repeat(4) { frame -> scene.render(frame * 100_000_000L).close(); kotlinx.coroutines.delay(60) }
            val image = scene.render(1_000_000_000L)
            image.encodeToData(EncodedImageFormat.PNG)!!.bytes.also { image.close() }
        } finally {
            scene.close()
        }
    }

    actual suspend fun share(png: ByteArray) {
        val title = getString(Res.string.wr_save_title)
        val filter = getString(Res.string.wr_png_image)
        // Swing dialogs belong on the event thread; Dispatchers.Main is that thread on desktop (no invokeAndWait from it).
        val file = withContext(Dispatchers.Main) {
            val chooser = JFileChooser().apply {
                dialogTitle = title
                selectedFile = File("blossom-wrapped.png")
                fileFilter = FileNameExtensionFilter(filter, "png")
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        } ?: return
        withContext(Dispatchers.IO) { (if (file.name.endsWith(".png")) file else File(file.path + ".png")).writeBytes(png) }
    }

    actual fun captureWith(capture: (suspend () -> androidx.compose.ui.graphics.ImageBitmap)?) = Unit

    private fun artworkOf(card: WrappedCard): List<String> = when (card) {
        is WrappedCard.Intro -> listOfNotNull(card.artworkUrl)
        is WrappedCard.TopSong -> listOfNotNull(card.song.item.artworkUrl)
        is WrappedCard.TopSongs -> card.songs.mapNotNull { it.item.artworkUrl }
        is WrappedCard.TopArtist -> listOfNotNull(card.artworkUrl)
        is WrappedCard.TopAlbum -> listOfNotNull(card.artworkUrl)
        is WrappedCard.Summary -> listOfNotNull(card.artworkUrl)
        else -> emptyList()
    }
}
