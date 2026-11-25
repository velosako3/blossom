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
        kotlin.test.assertTrue(testScheduler.currentTime - start <= 2_600, "waited ${testScheduler.currentTime - start} ms")
    }

    @Test fun slowSourcesStillAnswerAfterTheDeadline() = runTest {
        val plain = Result(LyricsSource.GENIUS, listOf(LyricLine(0, "words")))
        val picked = LyricsRepository.pick(
            listOf(
                LyricsSource.LRC_RED to { kotlinx.coroutines.delay(4_000); null },
                LyricsSource.LRCLIB to { kotlinx.coroutines.delay(5_000); lineSynced },
                LyricsSource.GENIUS to { plain },
            ),
            prioritizeSyllableSync = false,
        )
        assertEquals(lineSynced, picked, "the deadline dropped sources that were still coming")
    }

    @Test fun onlyPlainAfterTheDeadline() = runTest {
        val plain = Result(LyricsSource.GENIUS, listOf(LyricLine(0, "words")))
        val picked = LyricsRepository.pick(
            listOf(LyricsSource.LRC_RED to { kotlinx.coroutines.delay(4_000); null }, LyricsSource.GENIUS to { plain }),
            prioritizeSyllableSync = false,
        )
        assertEquals(plain, picked)
    }

    @Test fun lyricsLongerThanTheSongAreAnotherSong() {
        val lines = listOf(LyricLine(1_000, "a"), LyricLine(250_000, "b"))
        kotlin.test.assertTrue(LyricsRepository.outlastsTrack(lines, 180_000))
        kotlin.test.assertFalse(LyricsRepository.outlastsTrack(lines, 255_000))
        kotlin.test.assertFalse(LyricsRepository.outlastsTrack(lines, 0))
    }
}

class KuGouTitleTest {
    @Test fun onlyTheSameTitleIsTheSong() {
        val same = io.github.velosako3.blossom.feature.lyrics.providers.KuGou::sameTitle
        kotlin.test.assertFalse(same("Flashing Lights", "Nitrous"))
        kotlin.test.assertFalse(same("Bound 2", "Free My Kids"))
        kotlin.test.assertTrue(same("Runaway (Explicit)", "Runaway"))
        kotlin.test.assertTrue(same("Diddy Free", "diddy free"))
        kotlin.test.assertFalse(same("", "Nitrous"))
    }
}

class SameWordsTest {
    private fun l(vararg t: String) = t.map { LyricLine(1_000, it) }

    @Test fun anotherSongsLyricsDontShareTheWords() {
        val genius = l("Nitrous really fucking with my brain", "Tryna see my kids, but they don't think I'm sane")
        kotlin.test.assertFalse(LyricsRepository.sameWords(l("Oh, understand, girl, I'm out of sight", "B-B-B-B-Bound to fall in love"), genius))
        kotlin.test.assertTrue(LyricsRepository.sameWords(l("Nitrous really fuckin' with my brain", "Tryna see my kids but they don't think I'm sane"), genius))
    }
}

class MentionsTitleTest {
    private fun l(vararg t: String) = t.map { LyricLine(1_000, it) }

    @Test fun theTitleIsSungSomewhere() {
        kotlin.test.assertTrue(LyricsRepository.mentionsTitle(l("Nitrous really fucking with my brain"), "NITROUS"))
        kotlin.test.assertTrue(LyricsRepository.mentionsTitle(l("We ain't goin' to sleep 'til we see Diddy free"), "FREE DIDDY"))
        kotlin.test.assertFalse(LyricsRepository.mentionsTitle(l("Oh, understand, girl, I'm out of sight"), "NITROUS"))
        kotlin.test.assertTrue(LyricsRepository.mentionsTitle(l("anything"), "Go"), "too short to say anything")
    }
}

class SourceMatchTest {
    @Test fun lrclibSearchHitsMustBeTheSameSong() {
        val same = io.github.velosako3.blossom.feature.lyrics.providers.LrcLib::sameSong
        kotlin.test.assertTrue(same("WW3", "Ye", "WW3", "Ye"))
        kotlin.test.assertTrue(same("Runaway (feat. Pusha T)", "Kanye West", "Runaway", "Kanye West, Pusha T"))
        kotlin.test.assertTrue(same("GAS CHAMBERS", "Ye", "Gas Chambers", "Ye"))
        kotlin.test.assertFalse(same("Nitrous", "Joji", "NITROUS", "Ye"))
        kotlin.test.assertFalse(same("So Much Money (WW3)", "Gucci Mane", "WW3", "Ye"))
    }

    @Test fun geniusAiPagesAreNotTheSong() {
        val ai = io.github.velosako3.blossom.feature.lyrics.providers.Genius::isAiPage
        kotlin.test.assertTrue(ai("gas chambers [ai]", "/Ye-gas-chambers-ai-lyrics"))
        kotlin.test.assertTrue(ai("Gas Chambers", "/Ye-gas-chambers-ai-lyrics"))
        kotlin.test.assertFalse(ai("nitrous", "/Ye-nitrous-lyrics"))
        kotlin.test.assertFalse(ai("Chained", "/Ye-chained-lyrics"))
    }
}
