// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.feature.lyrics.providers.LyricLine
import io.github.velosako3.blossom.feature.lyrics.providers.isGeniusSectionHeader

/**
 * Lyrics in BitChord's line model (word and syllable timing, background vocals, duet sides), sorted by time.
 * Richest first: word-synced, line-synced, plain. [provider] names where they came from.
 */
sealed interface Lyrics {
    val provider: String
    val lines: List<LyricLine>

    data class WordSynced(override val lines: List<LyricLine>, override val provider: String) : Lyrics
    data class LineSynced(override val lines: List<LyricLine>, override val provider: String) : Lyrics
    data class Plain(val text: String, override val provider: String) : Lyrics {
        override val lines: List<LyricLine> by lazy { text.lines().map { LyricLine(0L, it.trim()) } }
    }
}

/** True for word- and line-synced lyrics. */
val Lyrics.isSynced: Boolean get() = this !is Lyrics.Plain

/** Sorts [lines] into the right kind; null when there is no text at all. */
fun lyricsOf(lines: List<LyricLine>, provider: String): Lyrics? {
    if (lines.none { it.text.isNotBlank() }) return null
    return when {
        lines.any { it.isWordSynced } -> Lyrics.WordSynced(lines.sortedBy { it.timeMs }, provider)
        lines.any { it.timeMs > 0 } -> Lyrics.LineSynced(lines.sortedBy { it.timeMs }, provider)
        // Genius's [Verse 1] / [Chorus] labels are page structure, not words anyone sings.
        else -> lines.filterNot { isGeniusSectionHeader(it.text) }.joinToString("\n") { it.text }.trim()
            .replace(Regex("\n{3,}"), "\n\n").takeIf { it.isNotBlank() }?.let { Lyrics.Plain(it, provider) }
    }
}
