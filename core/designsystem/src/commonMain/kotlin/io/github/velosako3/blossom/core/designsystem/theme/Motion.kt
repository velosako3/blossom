// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable

/** [spec] normally, an instant snap when the user turned on Reduce motion. Every blossom animation goes through this. */
@Composable
fun <T> motionSpec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = if (LocalReduceMotion.current) snap() else spec

/** The token spring (damping 0.8, stiffness 380), or a snap under Reduce motion. */
@Composable
fun <T> blossomSpring(): FiniteAnimationSpec<T> =
    motionSpec(spring(BlossomTokens.springDampingRatio, BlossomTokens.springStiffness))

/** The appearance setting. Dark is blossom's default. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

fun resolveDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
