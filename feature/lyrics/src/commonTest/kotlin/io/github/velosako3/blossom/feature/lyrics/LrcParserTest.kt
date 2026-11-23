// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LrcParserTest {
    @Test fun lineSynced() {
        val l = assertIs<Lyrics.LineSynced>(LrcParser.parse("[00:12.50]Hello\n[00:15.00]World", "p", null))
        assertEquals(listOf(12_500L, 15_000L), l.lines.map { it.timeMs })
        assertEquals(15_000L, l.lines[0].sungUntilMs)
        assertEquals("Hello", l.lines[0].text)
    }

    @Test fun threeDigitFractions() {
        val l = assertIs<Lyrics.LineSynced>(LrcParser.parse("[01:02.345]x", "p", null))
        assertEquals(62_345, l.lines[0].timeMs)
    }

    @Test fun repeatedStamps() {
        val l = assertIs<Lyrics.LineSynced>(LrcParser.parse("[00:12.00][00:45.00]Chorus", "p", null))
        assertEquals(listOf(12_000L, 45_000L), l.lines.map { it.timeMs })
        assertEquals(listOf("Chorus", "Chorus"), l.lines.map { it.text })
    }

    @Test fun offsetShiftsEarlier() {
        val l = assertIs<Lyrics.LineSynced>(LrcParser.parse("[offset:+500]\n[00:10.00]A", "p", null))
        assertEquals(9_500, l.lines[0].timeMs)
    }

    @Test fun metadataIgnoredAndLastLineEnds() {
        val l = assertIs<Lyrics.LineSynced>(LrcParser.parse("[ar:Someone]\n[ti:Title]\n[00:01.00]Only", "p", null))
        assertEquals(1, l.lines.size)
        assertEquals(6_000L, l.lines[0].sungUntilMs)
        assertEquals(200_000L, assertIs<Lyrics.LineSynced>(LrcParser.parse("[00:01.00]Only", "p", 200_000)).lines[0].sungUntilMs)
    }

    @Test fun enhancedWords() {
        val l = assertIs<Lyrics.WordSynced>(LrcParser.parse("[00:01.00]<00:01.00>Hel<00:01.40>lo", "p", null))
        val words = l.lines[0].words
        assertEquals(2, words.size)
        assertEquals(1_000L to 1_400L, words[0].startMs to words[0].endMs)
        assertEquals("Hello", l.lines[0].text)
    }

    @Test fun plainAndBlank() {
        assertIs<Lyrics.Plain>(LrcParser.parse("Just words\nno stamps", "p", null))
        assertNull(LrcParser.parse("", "p", null))
        assertNull(LrcParser.parse("   \n ", "p", null))
    }

    @Test fun byteOrderMarkIgnored() {
        val l = assertIs<Lyrics.LineSynced>(LrcParser.parse("\uFEFF[offset:+200]\n[00:10.00]A", "p", null))
        assertEquals(9_800, l.lines[0].timeMs)
    }

    @Test fun textBeforeFirstWordStampIsAWord() {
        val l = assertIs<Lyrics.WordSynced>(LrcParser.parse("[00:01.00]Oh <00:01.50>yeah<00:02.00>", "p", null))
        assertEquals(listOf("Oh ", "yeah"), l.lines[0].words.map { it.text })
        assertEquals(1_000, l.lines[0].words[0].startMs)
    }

    @Test fun plainKeepsSectionLabels() {
        val l = assertIs<Lyrics.Plain>(LrcParser.parse("[ar:Someone]\n[Chorus: x2]\nla la", "p", null))
        assertEquals("[Chorus: x2]\nla la", l.text)
    }

    @Test fun negativeOffsetNeverEndsBeforeStart() {
        val l = assertIs<Lyrics.LineSynced>(LrcParser.parse("[offset:+5000]\n[00:01.00]A\n[00:03.00]B\n[00:09.00]C", "p", null))
        l.lines.forEach { assertTrue(it.sungUntilMs!! >= it.timeMs, "$it") }
    }

    @Test fun lastWordWithoutClosingStampIsCapped() {
        val l = assertIs<Lyrics.WordSynced>(LrcParser.parse("[00:01.00]<00:01.00>Hi <00:01.30>there\n[00:20.00]Next", "p", null))
        assertTrue(l.lines[0].words.last().endMs <= 1_300 + 2_000)
    }
}
