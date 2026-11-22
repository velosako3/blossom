// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.glass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
actual fun FrameTimes(onFrame: (durationMs: Double, nowMs: Long) -> Unit) {
    val callback = rememberUpdatedState(onFrame)
    val window = LocalView.current.context.activity()?.window ?: return
    DisposableEffect(window) {
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val gpu = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.GPU_DURATION) else 0L
            callback.value(maxOf(total, gpu) / 1_000_000.0, android.os.SystemClock.uptimeMillis())
        }
        // Its own thread: a main thread stuck waiting for a slow GPU must not stop the reports that would fix it.
        val thread = android.os.HandlerThread("blossom-frame-metrics").apply { start() }
        window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
        onDispose { runCatching { window.removeOnFrameMetricsAvailableListener(listener) }; thread.quitSafely() }
    }
}
