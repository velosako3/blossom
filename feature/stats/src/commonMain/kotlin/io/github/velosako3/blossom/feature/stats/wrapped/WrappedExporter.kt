// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.wrapped

/**
 * Wrapped cards as shareable images. Desktop renders a card off-screen at the exact size and saves it with a save
 * dialog; Android captures the card on screen, scales it to the size and opens the share sheet.
 */
expect object WrappedExporter {
    /** The palette cards are tuned to (the top song's artwork). */
    var palette: io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette

    suspend fun exportPng(card: WrappedCard, widthPx: Int = 1080, heightPx: Int = 1920): ByteArray
    suspend fun share(png: ByteArray)
    /** The Wrapped screen registers how to capture the card on screen (used on Android; desktop renders off-screen). */
    fun captureWith(capture: (suspend () -> androidx.compose.ui.graphics.ImageBitmap)?)
}
