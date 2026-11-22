// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.feature.lyrics.providers.LyricLine as BcLine
import io.github.velosako3.blossom.feature.lyrics.ui.LyricsItem
import io.github.velosako3.blossom.feature.lyrics.ui.lyricsItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OnlineProvidersTest {
    @Test fun blankProviderLinesBecomeGaps() {
        val l = assertIs<Lyrics.LineSynced>(listOf(BcLine(1_000, "a"), BcLine(4_000, ""), BcLine(20_000, "b")).toLyrics("p", 60_000))
        assertEquals(1, lyricsItems(l.lines).count { it is LyricsItem.Gap })
        assertEquals(listOf("a", "b"), lyricsItems(l.lines).filterIsInstance<LyricsItem.Line>().map { it.line.text })
    }
}
