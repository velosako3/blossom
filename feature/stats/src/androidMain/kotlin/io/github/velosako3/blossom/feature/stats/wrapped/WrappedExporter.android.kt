// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.wrapped

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

actual object WrappedExporter {
    @Volatile actual var palette: io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette = io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette.Default

    /** Set by the app: needed for the share sheet and the cache folder. */
    @Volatile var context: Context? = null
    /** Set by the Wrapped screen: captures the card on screen. */
    @Volatile var capture: (suspend () -> ImageBitmap)? = null

    actual suspend fun exportPng(card: WrappedCard, widthPx: Int, heightPx: Int): ByteArray = withContext(Dispatchers.Default) {
        val shot = capture?.invoke() ?: error("the Wrapped screen is not showing")
        val scaled = Bitmap.createScaledBitmap(shot.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false), widthPx, heightPx, true)
        ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
    }

    actual suspend fun share(png: ByteArray) {
        val ctx = context ?: return
        val file = withContext(Dispatchers.IO) {
            File(File(ctx.cacheDir, "wrapped").apply { mkdirs() }, "blossom-wrapped.png").apply { writeBytes(png) }
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the read grant through the chooser to whichever app is picked.
        send.clipData = android.content.ClipData.newRawUri(null, uri)
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    actual fun captureWith(capture: (suspend () -> ImageBitmap)?) { this.capture = capture }
}
