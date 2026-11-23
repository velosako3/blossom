// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.feature.lyrics.providers.Genius
import io.github.velosako3.blossom.feature.lyrics.providers.LyricsRepository
import io.github.velosako3.blossom.feature.lyrics.providers.LyricsSource
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.measureTimedValue

/** The owner's unreleased Ye songs, per source. Live: -PliveTests=true. */
class YeUnreleasedLiveTest {
    @Test fun asTheAppSeesThem() = runBlocking {
        for ((title, ms) in listOf("NITROUS" to 97_000L, "FREE MY KIDS" to 109_000L, "FREE DIDDY" to 178_000L)) {
            val (r, t) = measureTimedValue { bitChordLyrics().lyrics(LyricsQuery(title, listOf("YeBreak"), null, ms)) }
            println("app '$title' / YeBreak: ${r?.provider} ${r?.let { it::class.simpleName }} in ${t.inWholeMilliseconds} ms, first='${r?.lines?.firstOrNull { it.text.isNotBlank() }?.text}'")
            for (src in LyricsSource.offered) {
                val x = runCatching { LyricsRepository.lyrics("", title, "YeBreak", ms, sources = setOf(src), order = listOf(src)) }.getOrNull()
                if (x != null) println("app   $title ${src.label}: timed=${x.lines.any { it.timeMs > 0 }} first='${x.lines.firstOrNull { it.text.isNotBlank() }?.text}'")
            }
        }
    }

    @Test fun titleInLyrics() = runBlocking {
        val x = LyricsRepository.lyrics("", "NITROUS", "YeBreak", 97_000, sources = setOf(LyricsSource.LYRICS_PLUS), order = listOf(LyricsSource.LYRICS_PLUS))
        println("title LyricsPlus NITROUS mentions nitrous: ${x?.lines?.any { it.text.contains("nitrous", true) }}, last line at ${x?.lines?.maxOfOrNull { it.timeMs }} ms")
    }

    @Test fun identify() = runBlocking {
        for (artist in listOf("Ye", "Kanye West")) println("identify Nitrous / $artist: ${io.github.velosako3.blossom.feature.lyrics.providers.BiniLyrics.identify("Nitrous", artist, 180_000)}")
        for (artist in listOf("Ye", "Kanye West")) println("geniusq Nitrous / $artist: ${Genius.lyrics("Nitrous", artist)?.size}")
    }

    @Test fun fullLookup() = runBlocking {
        for (title in listOf("Nitrous", "Diddy Free", "Free My Kids")) for (artist in listOf("Ye", "Kanye West")) {
            val (r, t) = measureTimedValue { bitChordLyrics().lyrics(LyricsQuery(title, listOf(artist), null, 180_000)) }
            println("full '$title' / $artist: ${r?.provider} ${r?.let { it::class.simpleName }} in ${t.inWholeMilliseconds} ms, first='${r?.lines?.firstOrNull { it.text.isNotBlank() }?.text}'")
        }
    }

    @Test fun perSource() = runBlocking {
        for (title in listOf("Nitrous", "Diddy Free", "Free My Kids")) {
            for (artist in listOf("Ye", "Kanye West")) {
                for (src in LyricsSource.offered) {
                    val (r, t) = measureTimedValue { runCatching { LyricsRepository.lyrics("", title, artist, 0, sources = setOf(src), order = listOf(src)) }.getOrNull() }
                    if (r != null) println("ye '$title' / $artist ${src.label} (${t.inWholeMilliseconds} ms): ${r.lines.size} lines, timed=${r.lines.any { it.timeMs > 0 }} first='${r.lines.firstOrNull { it.text.isNotBlank() }?.text}'")
                }
                println("ye '$title' / $artist genius raw: ${Genius.lyrics(title, artist)?.take(3)?.map { it.text }}")
            }
        }
    }
}
