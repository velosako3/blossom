// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.feature.lyrics.providers.LyricAlignment
import io.github.velosako3.blossom.feature.lyrics.providers.LyricLine
import io.github.velosako3.blossom.feature.lyrics.providers.LyricSyllable
import io.github.velosako3.blossom.feature.lyrics.providers.LyricWord
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class StoredLyricsTest {
    @Test fun everythingTheViewUsesSurvivesTheCache() {
        val backing = LyricLine(1_200, "(oh)", listOf(LyricWord(1_200, 1_600, "(oh)")))
        val lines = listOf(
            LyricLine(
                1_000, "Hello there",
                listOf(LyricWord(1_000, 1_400, "Hello ", listOf(LyricSyllable(1_000, 1_200, 0, 3), LyricSyllable(1_200, 1_400, 3, 5))), LyricWord(1_400, 2_000, "there")),
                sungUntilMs = 2_100, background = backing, alignment = LyricAlignment.End,
            ),
            LyricLine(3_000, ""),
        )
        val lyrics = Lyrics.WordSynced(lines, "lrc.red")
        val json = Json.encodeToString(StoredLyrics.serializer(), StoredLyrics.of(lyrics))
        assertEquals(lyrics, Json.decodeFromString(StoredLyrics.serializer(), json).toLyrics())
        val plain = Lyrics.Plain("a\nb", "Genius")
        assertEquals(plain, StoredLyrics.of(plain).toLyrics())
    }
}
