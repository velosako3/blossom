// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality

val LocalGlassQuality = staticCompositionLocalOf { GlassQuality.FULL }
val LocalReduceMotion = staticCompositionLocalOf { false }
/** Opaque tint base for glass; [GlassSurface] applies the quality-dependent alpha. */
val LocalGlassTint = staticCompositionLocalOf { BlossomColors.Dark.surface }
val LocalArtworkPalette = staticCompositionLocalOf { ArtworkPalette.Default }
val LocalBlossomColors = staticCompositionLocalOf { BlossomColors.Dark }

object BlossomTheme {
    val colors: BlossomColors @Composable @ReadOnlyComposable get() = LocalBlossomColors.current
    val palette: ArtworkPalette @Composable @ReadOnlyComposable get() = LocalArtworkPalette.current
    val glassQuality: GlassQuality @Composable @ReadOnlyComposable get() = LocalGlassQuality.current
    val reduceMotion: Boolean @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
}

@Composable
fun BlossomTheme(
    darkTheme: Boolean,
    palette: ArtworkPalette,
    glassQuality: GlassQuality = GlassQuality.FULL,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) BlossomColors.Dark else BlossomColors.Light
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent, background = colors.background,
            onBackground = colors.onSurface, surface = colors.surface, onSurface = colors.onSurface,
            onSurfaceVariant = colors.onSurfaceVariant, outline = colors.outline, error = colors.error,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent, background = colors.background,
            onBackground = colors.onSurface, surface = colors.surface, onSurface = colors.onSurface,
            onSurfaceVariant = colors.onSurfaceVariant, outline = colors.outline, error = colors.error,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = blossomTypography()) {
        CompositionLocalProvider(
            LocalBlossomColors provides colors,
            LocalGlassQuality provides glassQuality,
            LocalReduceMotion provides reduceMotion,
            LocalGlassTint provides colors.surface,
            LocalArtworkPalette provides palette,
            LocalContentColor provides colors.onSurface,
            content = content,
        )
    }
}

