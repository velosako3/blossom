// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.window

import io.github.velosako3.blossom.composeApp.resources.blossom_icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.github.velosako3.blossom.BlossomBuildInfo
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import java.awt.Dimension

/**
 * The main window: restores size, position and maximized state, enforces 960×600 minimum, and is undecorated
 * with blossom's glass title bar unless the user chose "Use system title bar".
 */
@OptIn(FlowPreview::class)
@Composable
fun ApplicationScope.BlossomWindow(
    store: WindowStateStore = remember { WindowStateStore() },
    onPreviewKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { false },
    onKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { false },
    content: @Composable FrameWindowScope.(titleBar: (@Composable () -> Unit)?) -> Unit,
) {
    val saved = remember { store.load() }
    val state = rememberWindowState(
        placement = if (saved.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = if (saved.x != null && saved.y != null) WindowPosition(saved.x.dp, saved.y.dp) else WindowPosition.PlatformDefault,
        size = DpSize(saved.widthDp.dp, saved.heightDp.dp),
    )
    fun persist() {
        val floating = state.placement == WindowPlacement.Floating
        val prev = store.load()
        store.save(
            prev.copy(
                widthDp = if (floating) state.size.width.value.toInt() else prev.widthDp,
                heightDp = if (floating) state.size.height.value.toInt() else prev.heightDp,
                x = if (floating && state.position.isSpecified) state.position.x.value.toInt() else prev.x,
                y = if (floating && state.position.isSpecified) state.position.y.value.toInt() else prev.y,
                maximized = state.placement == WindowPlacement.Maximized,
            ),
        )
    }
    val close = { persist(); exitApplication() }
    Window(
        onCloseRequest = close,
        state = state,
        title = BlossomBuildInfo.APP_NAME,
        icon = org.jetbrains.compose.resources.painterResource(io.github.velosako3.blossom.composeApp.resources.Res.drawable.blossom_icon),
        undecorated = !saved.systemTitleBar,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(WindowStateStore.MIN_WIDTH, WindowStateStore.MIN_HEIGHT)
            snapshotFlow { Triple(state.size, state.position, state.placement) }.debounce(500).collect { persist() }
        }
        content(if (saved.systemTitleBar) null else { { GlassTitleBar(state, onClose = close) } })
    }
}
