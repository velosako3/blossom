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
