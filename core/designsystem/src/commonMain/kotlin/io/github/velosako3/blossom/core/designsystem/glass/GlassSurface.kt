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
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import io.github.velosako3.blossom.core.designsystem.color.requiredScrimAlpha
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTokens
import io.github.velosako3.blossom.core.designsystem.theme.LocalArtworkPalette
import io.github.velosako3.blossom.core.designsystem.theme.LocalBlossomColors
import io.github.velosako3.blossom.core.designsystem.theme.LocalGlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.LocalGlassTint

/** The backdrop every glass surface samples. Provided by `BlossomTheme`. */
val LocalGlassBackdrop = staticCompositionLocalOf { HazeState() }

/** Marks the layer that glass blurs and refracts — the animated background, and scrolling content under bars. */
fun Modifier.glassBackdropSource(zIndex: Float = 0f): Modifier = composed { hazeSource(LocalGlassBackdrop.current, zIndex) }

/** Tint alpha per the visual spec, before the readability floor. */
internal fun specTintAlpha(quality: GlassQuality, dark: Boolean): Float = when (quality) {
    GlassQuality.OFF -> 0.88f
    else -> if (dark) 0.55f else 0.65f
}

/**
 * Tint alpha that also keeps [text] at 4.5:1 over the tint composited on the artwork's dominant colour,
 * which is what the backdrop mostly shows. The tint itself is the scrim.
 */
internal fun glassTintAlpha(quality: GlassQuality, dark: Boolean, tint: Color, text: Color, backdrop: Color): Float {
    val spec = specTintAlpha(quality, dark)
    return maxOf(spec, requiredScrimAlpha(text, backdrop, tint.copy(alpha = 1f)))
}

/**
 * A liquid-glass panel.
 *
 * - FULL: 24 dp backdrop blur with a 6 dp refracting edge band and ≤1 px chromatic fringe (Haze glass),
 *   tinted surface, lit 1 dp rim, 2% grain and a soft 24 dp shadow.
 * - REDUCED: the same blur, tint, rim, grain and shadow without refraction.
 * - OFF: an 88% tinted surface with a hairline and a small shadow — no backdrop sampling at all.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(BlossomTokens.radiusLarge),
    tint: Color = LocalGlassTint.current,
    quality: GlassQuality = LocalGlassQuality.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalBlossomColors.current
    val palette = LocalArtworkPalette.current
    val alpha = remember(quality, colors, tint, palette.dominant) {
        glassTintAlpha(quality, colors.isDark, tint, colors.onSurface, palette.dominant)
    }
    val tinted = tint.copy(alpha = alpha)
    val backdrop = LocalGlassBackdrop.current
    val shadow = if (quality == GlassQuality.OFF) {
        Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.12f), offset = DpOffset(0.dp, 4.dp))
    } else {
        Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.20f), offset = DpOffset(0.dp, 8.dp))
    }

    val material = when (quality) {
        GlassQuality.FULL -> {
            val rounded = shape as? RoundedCornerShape
            val style = remember(tinted, rounded) {
                GlassStyle {
                    rounded?.let { shape(it) }
                    optics(
                        GlassOptics.regular.copy(
                            blurRadius = OpticalSizeValue.Fixed(24.dp),
                            refractionProfile = RefractionProfile.Edge(6.dp),
                            refractionDisplacement = 22.dp,
                        ),
                    )
                    chromaticAberrationStrength(0.12f)
                    tint(tinted)
                    specularIntensity(0.35f)
                }
            }
            Modifier.clip(shape).hazeGlass(HazeInput.Sources(backdrop), style).grain()
        }
        GlassQuality.REDUCED -> {
            val style = remember(tinted) {
                HazeBlurStyle {
                    blurRadius(24.dp)
                    backgroundColor(tinted.copy(alpha = 1f))
                    colorEffects(emptyList())
                    noiseFactor(0f)
                }
            }
            Modifier.clip(shape).hazeBlur(HazeInput.Sources(backdrop), style).drawTint(tinted).grain()
        }
        GlassQuality.OFF -> Modifier.clip(shape).drawTint(tinted)
    }
    val rim = if (quality == GlassQuality.OFF) {
        Modifier.border(0.5.dp, GlassShaders.hairline, shape)
    } else {
        Modifier.border(1.dp, GlassShaders.rimBrush, shape)
    }

    Box(modifier.dropShadow(shape, shadow).then(material).then(rim), propagateMinConstraints = false) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) { content() }
    }
}

private fun Modifier.drawTint(color: Color) = drawWithContent {
    drawRect(color)
    drawContent()
}

/** 2% monochrome grain drawn over the material, under the content. */
private fun Modifier.grain() = drawWithContent {
    drawRect(GlassShaders.grainBrush, alpha = 0.02f)
    drawContent()
}
