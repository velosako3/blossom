// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import io.github.velosako3.blossom.core.designsystem.color.requiredScrimAlpha
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTokens
import io.github.velosako3.blossom.core.designsystem.theme.LocalArtworkPalette
import io.github.velosako3.blossom.core.designsystem.theme.LocalBlossomColors
import io.github.velosako3.blossom.core.designsystem.theme.LocalGlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.LocalGlassTint
import androidx.compose.ui.graphics.shadow.Shadow as DropShadow
import com.kyant.backdrop.shadow.Shadow as GlassShadow

/** The animated background, recorded for glass inside a page (cards, chips, buttons). Provided by `BlossomTheme`. */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** The page under the floating bars, recorded so the bars bend and blur what scrolls beneath them. */
val LocalPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Marks the animated background as what in-page glass samples. */
fun Modifier.glassBackdropSource(): Modifier = composed { LocalGlassBackdrop.current?.let { layerBackdrop(it) } ?: Modifier }

/**
 * Marks the page (the screen under the floating bars) as what floating glass samples. Floating glass must not sit
 * inside this node: it would sample itself.
 */
fun Modifier.glassPageSource(): Modifier = composed { LocalPageBackdrop.current?.let { layerBackdrop(it) } ?: Modifier }

/** Tint alpha per quality, before the readability floor. Light enough that the backdrop shows. */
internal fun specTintAlpha(quality: GlassQuality, dark: Boolean): Float = when (quality) {
    GlassQuality.FULL -> if (dark) 0.28f else 0.42f
    GlassQuality.REDUCED -> if (dark) 0.45f else 0.55f
    GlassQuality.OFF -> 0.88f
}

/** The readability floor never takes the glass past this: past it, the three qualities look alike. */
private fun maxTintAlpha(quality: GlassQuality): Float = when (quality) {
    GlassQuality.FULL -> 0.5f
    GlassQuality.REDUCED -> 0.65f
    GlassQuality.OFF -> 1f
}

/**
 * Tint alpha that keeps [text] at 4.5:1 over the tint composited on the artwork's dominant colour (what the backdrop
 * mostly shows), capped per quality: the blur and vibrancy of real glass carry the rest of the contrast.
 */
internal fun glassTintAlpha(quality: GlassQuality, dark: Boolean, tint: Color, text: Color, backdrop: Color): Float {
    val spec = specTintAlpha(quality, dark)
    return maxOf(spec, requiredScrimAlpha(text, backdrop, tint.copy(alpha = 1f))).coerceAtMost(maxOf(spec, maxTintAlpha(quality)))
}

/**
 * A liquid-glass panel, drawn with Kyant0's `backdrop` (the library BitChord's glass uses).
 *
 * - FULL: the backdrop refracted by a lens at the edges (with depth and a slight chromatic fringe), blurred 6 dp, saturated,
 *   under a light tint, with a lit rim and a soft shadow.
 * - REDUCED: a 14 dp blur without the lens.
 * - OFF: a near-opaque tint with a hairline and a small shadow; nothing sampled.
 *
 * [floating] glass (bars, the mini player, the sidebar, sheets) samples the page and the background; other glass
 * samples the background only.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(BlossomTokens.radiusLarge),
    tint: Color = LocalGlassTint.current,
    quality: GlassQuality = LocalGlassQuality.current,
    floating: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalBlossomColors.current
    val palette = LocalArtworkPalette.current
    val background = LocalGlassBackdrop.current
    val page = LocalPageBackdrop.current
    val source: Backdrop? = when {
        background == null -> null
        floating && page != null -> rememberCombinedBackdrop(background, page)
        else -> background
    }
    val effective = if (source == null) GlassQuality.OFF else quality
    val alpha = remember(effective, colors, tint, palette.dominant) {
        glassTintAlpha(effective, colors.isDark, tint, colors.onSurface, palette.dominant)
    }
    val tinted = tint.copy(alpha = alpha)

    val material = if (effective == GlassQuality.OFF || source == null) {
        Modifier
            .dropShadow(shape, DropShadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.12f), offset = DpOffset(0.dp, 4.dp)))
            .clip(shape)
            .drawTint(tinted)
            .border(0.5.dp, GlassShaders.hairline, shape)
    } else {
        val lensed = effective == GlassQuality.FULL
        Modifier.drawBackdrop(
            backdrop = source,
            shape = { shape },
            effects = {
                vibrancy()
                // FULL blurs less: the lens bending the page is the point, and a heavy blur flattens it away.
                blur((if (lensed) BLUR_LENSED else BLUR).toPx())
                if (lensed) lens(LENS_HEIGHT.toPx(), LENS_AMOUNT.toPx(), depthEffect = true, chromaticAberration = true)
            },
            highlight = { Highlight.Default },
            shadow = { SHADOW },
            onDrawSurface = { drawRect(tinted) },
        )
    }

    Box(modifier.then(material), propagateMinConstraints = false) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) { content() }
    }
}

private val BLUR = 14.dp
private val BLUR_LENSED = 6.dp
private val LENS_HEIGHT = 24.dp
private val LENS_AMOUNT = 56.dp
private val SHADOW = GlassShadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.2f))

private fun Modifier.drawTint(color: Color) = drawWithContent {
    drawRect(color)
    drawContent()
}

/**
 * How much of the bottom of the page the floating bars cover. The page runs under them (so the glass has something
 * to bend); a screen's scroll container ends its content this far up so nothing is stuck beneath the bars.
 */
val LocalBarsPadding = androidx.compose.runtime.compositionLocalOf { 0.dp }
