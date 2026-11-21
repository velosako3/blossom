// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import kotlinx.serialization.Serializable

@Serializable
data class LyricWord(val text: String, val startMs: Long, val endMs: Long)

/** [words] is empty for line-synced lyrics. */
@Serializable
data class LyricLine(val startMs: Long, val endMs: Long, val text: String, val words: List<LyricWord>)

/** Richest first: word-synced, line-synced, plain. [provider] names where they came from. */
@Serializable
sealed interface Lyrics {
    val provider: String

    @Serializable data class WordSynced(val lines: List<LyricLine>, override val provider: String) : Lyrics
    @Serializable data class LineSynced(val lines: List<LyricLine>, override val provider: String) : Lyrics
    @Serializable data class Plain(val text: String, override val provider: String) : Lyrics
}

/** 2 = word-synced, 1 = line-synced, 0 = plain. */
val Lyrics.richness: Int get() = when (this) { is Lyrics.WordSynced -> 2; is Lyrics.LineSynced -> 1; is Lyrics.Plain -> 0 }
