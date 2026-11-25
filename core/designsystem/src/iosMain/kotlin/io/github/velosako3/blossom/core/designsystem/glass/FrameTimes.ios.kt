// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos

@Composable
actual fun FrameTimes(onFrame: (durationMs: Double, nowMs: Long) -> Unit) {
    val callback = rememberUpdatedState(onFrame)
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            callback.value((now - last) / 1_000_000.0, now / 1_000_000)
            last = now
        }
    }
}
