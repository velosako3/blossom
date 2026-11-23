// SPDX-License-Identifier: GPL-3.0-only
// Ported from BitChord (https://github.com/kushagrasinghx/BitChord) at 21356206,
// sharedUi/src/androidMain/kotlin/com/music/bitchord/ui/player/PlayerPlatformUi.android.kt (GPL-3.0).
// Modified for blossom, 2026: package; only the lyric panel's two platform pieces.
package io.github.velosako3.blossom.feature.lyrics.ui.panel

import android.os.Build
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate

// RenderEffect, API 31+; `Modifier.blur` is a no-op below it.
internal actual val renderEffectBlurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

internal actual fun DrawScope.clipShiftedDown(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    dy: Float,
    block: DrawScope.() -> Unit,
) {
    clipRect(left = left, top = top, right = right, bottom = bottom) {
        translate(top = dy) { block() }
    }
}
