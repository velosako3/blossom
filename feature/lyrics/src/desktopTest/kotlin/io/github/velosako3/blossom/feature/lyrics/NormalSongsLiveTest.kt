// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** Popular songs as YouTube Music names them, through the app's resolver. Live only. */
class NormalSongsLiveTest {
    @Test fun popularSongs() = runBlocking {
        val resolver = LyricsResolver(bitChordLyrics(), LocalLyricsSource { null }, SqlLyricsCache(io.github.velosako3.blossom.core.database.inMemoryDatabase(), "live"), kotlin.time.Clock.System)
        val songs = listOf(
            Triple("Blinding Lights", "The Weeknd", 200_000L), Triple("Runaway", "Kanye West", 547_000L), Triple("Bohemian Rhapsody", "Queen", 354_000L),
            Triple("Espresso", "Sabrina Carpenter", 175_000L), Triple("Self Control", "Frank Ocean", 249_000L), Triple("HUMBLE.", "Kendrick Lamar", 177_000L),
            Triple("Hey Jude - Remastered 2015", "The Beatles", 431_000L), Triple("Die With A Smile", "Lady Gaga, Bruno Mars", 251_000L),
        )
        var synced = 0
        for ((i, s) in songs.withIndex()) {
            val (title, artist, ms) = s
            val t = Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "n$i"), title, artist.split(", ").map { ArtistRef(null, it) }, null, ms, null)
            val r = resolver.resolve(t)
            if (r != null && r !is Lyrics.Plain) synced++
            println("normal '$title' / $artist -> ${r?.provider ?: "NONE"} ${r?.let { it::class.simpleName } ?: ""} '${r?.lines?.firstOrNull { it.text.isNotBlank() }?.text?.take(45) ?: ""}'")
        }
        println("normal: $synced/${songs.size} synced")
    }
}
