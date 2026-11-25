// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** iOS's lyrics source, run here: synced lyrics for a popular song and a Ye upload. Live only. */
class LrcLibLyricsLiveTest {
    @Test fun findsSyncedLyrics(): Unit = runBlocking {
        val source = LrcLibLyrics(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp))
        for ((title, artist, ms) in listOf(Triple("Blinding Lights", "The Weeknd", 200_000L), Triple("NITROUS", "YeBreak", 97_000L))) {
            val r = source.lyrics(LyricsQuery(title, listOf(artist), null, ms))
            println("lrclib ktor '$title': ${r?.let { it::class.simpleName }} '${r?.lines?.firstOrNull { it.text.isNotBlank() }?.text}'")
            assertTrue(r is Lyrics.LineSynced || r is Lyrics.WordSynced, "$title: $r")
        }
    }
}
