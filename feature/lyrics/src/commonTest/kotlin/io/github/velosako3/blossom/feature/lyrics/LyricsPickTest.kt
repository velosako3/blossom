// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.feature.lyrics.providers.LyricLine
import io.github.velosako3.blossom.feature.lyrics.providers.LyricWord
import io.github.velosako3.blossom.feature.lyrics.providers.LyricsRepository
import io.github.velosako3.blossom.feature.lyrics.providers.LyricsRepository.Result
import io.github.velosako3.blossom.feature.lyrics.providers.LyricsSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** BitChord's ordered pick, as blossom relies on it: priority order, not arrival order, and Genius last. */
class LyricsPickTest {
    private val plain = Result(LyricsSource.GENIUS, listOf(LyricLine(0, "plain")))
    private val lineSynced = Result(LyricsSource.LRCLIB, listOf(LyricLine(1_000, "line")))
    private val wordSynced = Result(LyricsSource.LRC_RED, listOf(LyricLine(1_000, "word", listOf(LyricWord(1_000, 1_500, "word")))))

    @Test fun aSlowerHigherPrioritySourceBeatsAFasterLowerOne() = runTest {
        val slow = CompletableDeferred<Result?>()
        val picked = async {
            LyricsRepository.pick(listOf(LyricsSource.LRC_RED to { slow.await() }, LyricsSource.LRCLIB to { lineSynced }), false)
        }
        slow.complete(wordSynced)
        assertEquals(wordSynced, picked.await())
    }

    @Test fun theFirstTimedAnswerWinsWithoutWaitingForTheRest() = runTest {
        val picked = LyricsRepository.pick(
            listOf(LyricsSource.LRCLIB to { lineSynced }, LyricsSource.KUGOU to { awaitCancellation() }),
            false,
        )
        assertEquals(lineSynced, picked)
    }

    @Test fun geniusIsNotAskedOnceSomethingIsFound() = runTest {
        var geniusAsked = false
        val picked = LyricsRepository.pick(
            listOf(
                LyricsSource.LRC_RED to { null },
                LyricsSource.YOUTUBE_MUSIC to { Result(LyricsSource.YOUTUBE_MUSIC, listOf(LyricLine(0, "untimed"))) },
                LyricsSource.GENIUS to { geniusAsked = true; plain },
            ),
            prioritizeSyllableSync = false,
        )
        assertEquals(LyricsSource.YOUTUBE_MUSIC, picked?.source)
        assertFalse(geniusAsked)
    }

    @Test fun plainOnlyWhenNothingTimedExists() = runTest {
        val picked = LyricsRepository.pick(
            listOf(LyricsSource.LRC_RED to { null }, LyricsSource.LRCLIB to { null }, LyricsSource.GENIUS to { plain }),
            false,
        )
        assertEquals(plain, picked)
    }
}
