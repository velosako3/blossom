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

/** blossom's changes to the pick: a deadline on priority, and lyrics that outlast the song rejected. */
class LyricsPickDeadlineTest {
    private val lineSynced = Result(LyricsSource.LRCLIB, listOf(LyricLine(1_000, "line")))

    @Test fun aStuckTopSourceDoesNotHoldUpAnAnswerThatIsAlreadyIn() = runTest {
        val start = testScheduler.currentTime
        val picked = LyricsRepository.pick(
            listOf(LyricsSource.LRC_RED to { awaitCancellation() }, LyricsSource.LRCLIB to { lineSynced }),
            prioritizeSyllableSync = false,
        )
        assertEquals(lineSynced, picked)
        kotlin.test.assertTrue(testScheduler.currentTime - start <= 2_500, "waited ${testScheduler.currentTime - start} ms")
    }

    @Test fun lyricsLongerThanTheSongAreAnotherSong() {
        val lines = listOf(LyricLine(1_000, "a"), LyricLine(250_000, "b"))
        kotlin.test.assertTrue(LyricsRepository.outlastsTrack(lines, 180_000))
        kotlin.test.assertFalse(LyricsRepository.outlastsTrack(lines, 255_000))
        kotlin.test.assertFalse(LyricsRepository.outlastsTrack(lines, 0))
    }
}
