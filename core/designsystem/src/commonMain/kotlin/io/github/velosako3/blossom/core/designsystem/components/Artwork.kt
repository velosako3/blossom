// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.color.extractPalette
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import androidx.compose.ui.graphics.ImageBitmap

/** Installs the app-wide Coil loader (Ktor/OkHttp networking, memory + disk cache defaults). Call once at startup. */
fun installImageLoader() {
    SingletonImageLoader.setSafe { context: PlatformContext ->
        ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory()) }.build()
    }
}

/** Album art with a soft gradient placeholder (and the same as fallback when there's no URL or it fails). */
@Composable
fun Artwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    iconSize: Dp = 24.dp,
) {
    val colors = BlossomTheme.colors
    val palette = BlossomTheme.palette
    Box(
        modifier.clip(shape).background(Brush.linearGradient(listOf(palette.vibrant.copy(alpha = 0.55f), palette.muted.copy(alpha = 0.75f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MusicNote, null, Modifier.size(iconSize), tint = Color.White.copy(alpha = 0.7f))
        if (url != null) {
            AsyncImage(model = url, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Loads [url] and extracts its palette off the main thread; [ArtworkPalette.Default] until then or on failure. */
@Composable
fun rememberArtworkPalette(url: String?): ArtworkPalette {
    val context = LocalPlatformContext.current
    var palette by remember { mutableStateOf(ArtworkPalette.Default) }
    LaunchedEffect(url) {
        if (url == null) { palette = ArtworkPalette.Default; return@LaunchedEffect }
        val request = ImageRequest.Builder(context).data(url).size(128).softwareBitmap().build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
        val bitmap = (result as? SuccessResult)?.image?.let(::toImageBitmap) ?: return@LaunchedEffect
        palette = runCatching { extractPalette(bitmap) }.getOrDefault(ArtworkPalette.Default)
    }
    return palette
}

/** Readable (non-hardware) bitmaps, so the palette can sample pixels. */
internal expect fun ImageRequest.Builder.softwareBitmap(): ImageRequest.Builder

internal expect fun toImageBitmap(image: coil3.Image): ImageBitmap
