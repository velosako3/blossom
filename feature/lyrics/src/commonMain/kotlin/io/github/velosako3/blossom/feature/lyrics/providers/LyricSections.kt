// SPDX-License-Identifier: GPL-3.0-only
// Ported from BitChord (https://github.com/kushagrasinghx/BitChord) v1.9.1,
// shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LyricSections.kt (GPL-3.0).
// Modified for blossom, 2026: package and HTTP client wiring.
package io.github.velosako3.blossom.feature.lyrics.providers

/**
 * Whether a plain lyric line is one of Genius's section markers — `[Chorus]`,
 * `[Verse 2: Artist]` — rather than words to sing.
 */
fun isGeniusSectionHeader(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length in 3..60
}
