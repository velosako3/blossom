// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertTrue

/** Asks every provider for "Kanye West – Runaway" and prints what each returned. Live: -PliveTests=true. */
class LyricsProvidersLiveTest {
    @Test fun eachProvider() = runBlocking {
        val q = LyricsQuery("Runaway", listOf("Kanye West"), "My Beautiful Dark Twisted Fantasy", 547_000, videoId = "Bm5iA4Zupek")
        var found = 0
        for (p in onlineLyricsProviders()) {
            val r = withTimeoutOrNull(15_000) { runCatching { p.fetch(q) }.getOrNull() }
            val kind = when (r) { is Lyrics.WordSynced -> "word-synced ${r.lines.size} lines"; is Lyrics.LineSynced -> "line-synced ${r.lines.size} lines"; is Lyrics.Plain -> "plain"; null -> "none" }
            println("provider ${p.name}: $kind")
            if (r != null) found++
        }
        assertTrue(found > 0)
    }
}
