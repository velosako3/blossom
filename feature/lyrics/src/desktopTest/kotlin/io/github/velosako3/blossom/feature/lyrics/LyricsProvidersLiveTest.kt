// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/** Runs the app's lyrics lookup for a few well-known songs and prints source, kind and time. Live: -PliveTests=true. */
class LyricsProvidersLiveTest {
    private val songs = listOf(
        LyricsQuery("Runaway", listOf("Kanye West", "Pusha T"), "My Beautiful Dark Twisted Fantasy", 547_000),
        LyricsQuery("Blinding Lights", listOf("The Weeknd"), "After Hours", 200_000),
        LyricsQuery("Bohemian Rhapsody", listOf("Queen"), "A Night at the Opera", 354_000),
        LyricsQuery("As It Was", listOf("Harry Styles"), "Harry's House", 167_000),
        LyricsQuery("Around the World", listOf("Daft Punk"), "Homework", 429_000),
        LyricsQuery("Shape of You", listOf("Ed Sheeran"), "÷ (Deluxe)", 234_000),
    )

    @Test fun lookup() = runBlocking {
        val online = bitChordLyrics()
        var synced = 0
        for (q in songs) {
            val (r, took) = measureTimedValue { runCatching { online.lyrics(q) }.getOrNull() }
            val kind = when (r) {
                is Lyrics.WordSynced -> "word-synced ${r.lines.size} lines"
                is Lyrics.LineSynced -> "line-synced ${r.lines.size} lines"
                is Lyrics.Plain -> "plain"
                null -> "none"
            }
            val first = when (r) { is Lyrics.WordSynced -> r.lines.firstOrNull { it.text.isNotBlank() }?.text; is Lyrics.LineSynced -> r.lines.firstOrNull { it.text.isNotBlank() }?.text; else -> null }
            println("lyrics ${q.title} – ${q.artists.first()}: ${r?.provider} $kind in ${took.inWholeMilliseconds} ms; first line: $first")
            if (r is Lyrics.WordSynced || r is Lyrics.LineSynced) synced++
        }
        assertTrue(synced >= songs.size - 1, "synced for $synced of ${songs.size}")
    }
}
