// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.window

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import io.github.velosako3.blossom.BlossomBuildInfo
import io.github.velosako3.blossom.composeApp.resources.Res
import io.github.velosako3.blossom.composeApp.resources.window_close
import io.github.velosako3.blossom.composeApp.resources.window_maximize
import io.github.velosako3.blossom.composeApp.resources.window_minimize
import io.github.velosako3.blossom.composeApp.resources.window_restore
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The custom title bar for the undecorated window: drag anywhere on it, double-click to maximize/restore,
 * and glass window buttons on the right. It sits directly on the animated background.
 */
@Composable
fun FrameWindowScope.GlassTitleBar(state: WindowState, onClose: () -> Unit) {
    fun toggleMaximize() {
        state.placement = if (state.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
    }
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        WindowDraggableArea(
            Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) { detectTapGestures(onDoubleTap = { toggleMaximize() }) },
        ) {
            Box(Modifier.fillMaxHeight().padding(start = 20.dp), contentAlignment = Alignment.CenterStart) {
                Text(BlossomBuildInfo.APP_NAME, style = MaterialTheme.typography.labelLarge, color = BlossomTheme.colors.onSurfaceVariant)
            }
        }
        val maximized = state.placement == WindowPlacement.Maximized
        GlassIconButton({ state.isMinimized = true }, Icons.Rounded.Remove, stringResource(Res.string.window_minimize), size = 40.dp, iconSize = 18.dp, glass = false)
        GlassIconButton(
            ::toggleMaximize,
            if (maximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare,
            stringResource(if (maximized) Res.string.window_restore else Res.string.window_maximize),
            size = 40.dp, iconSize = 16.dp, glass = false,
        )
        GlassIconButton(onClose, Icons.Rounded.Close, stringResource(Res.string.window_close), size = 40.dp, iconSize = 18.dp, glass = false,
            modifier = Modifier.padding(end = 6.dp))
    }
}
