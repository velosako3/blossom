// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui.panel

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The few player settings BitChord's lyric panel reads, under the name it reads them by. The app keeps them in step
 * with blossom's own preferences (reduce motion, glass quality).
 */
object PlayerSettings {
    /** blossom's "reduce motion": no glow, rise or growth, only the colour sweep. */
    val reduceAnimation = MutableStateFlow(false)
    /** True when glass is off: the lines lose their depth blur, which is the expensive part. */
    val reduceDynamicBlur = MutableStateFlow(false)
    val lyricsBlur = MutableStateFlow(true)
    val lyricsOffsetMs = MutableStateFlow(0)
}
