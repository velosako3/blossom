// SPDX-License-Identifier: GPL-3.0-only
// Ported from BitChord (https://github.com/kushagrasinghx/BitChord) at 21356206,
// sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/SyncedLyricsPanel.kt (GPL-3.0).
// Modified for blossom, 2026: package, blossom settings/strings/icons.
package io.github.velosako3.blossom.feature.lyrics.ui.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.velosako3.blossom.feature.lyrics.providers.LyricLine

/**
 * [LyricsPanel] for a screen in another module — the Android TV app's — that
 * only holds a [PlaybackPosition]: builds the lyric playhead from it, so the
 * internal playhead type never has to leave this module.
 */
@Composable
fun SyncedLyricsPanel(
    lines: List<LyricLine>,
    trackKey: String,
    position: PlaybackPosition,
    looking: Boolean,
    isPlaying: Boolean,
    onSeekToLine: (Long) -> Unit,
    /** See [LyricsPanel]'s own: the sung line held mid-panel, not at its top. */
    centerFocus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    LyricsPanel(
        lines = lines,
        trackKey = trackKey,
        playhead = rememberLyricPlayhead(position),
        looking = looking,
        isPlaying = isPlaying,
        onSeekToLine = onSeekToLine,
        controlsOpen = true,
        onRevealControls = {},
        onHideControls = {},
        centerFocus = centerFocus,
        modifier = modifier,
    )
}
