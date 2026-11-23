// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.glass

import androidx.compose.runtime.Composable

/**
 * Reports how long each frame really took, for [FrameBudgetMonitor]: on Android the platform's frame metrics
 * (GPU time included: a weak GPU can take seconds while the UI thread looks fine), elsewhere the frame clock.
 */
@Composable
expect fun FrameTimes(onFrame: (durationMs: Double, nowMs: Long) -> Unit)
