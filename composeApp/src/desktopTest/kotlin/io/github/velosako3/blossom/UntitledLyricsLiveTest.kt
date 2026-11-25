// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.feature.lyrics.LocalLyricsSource
import io.github.velosako3.blossom.feature.lyrics.LyricsResolver
import io.github.velosako3.blossom.feature.lyrics.SqlLyricsCache
import io.github.velosako3.blossom.feature.lyrics.bitChordLyrics
import io.github.velosako3.blossom.source.untitled.UntitledClient
import io.github.velosako3.blossom.source.untitled.UntitledImporter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.Properties
import kotlin.test.Test

/**
 * Lyrics for the songs on the owner's untitled sample links (secrets.properties; the links are never printed),
 * through the app's own resolver. Prints what each song gets. Live: -PliveTests=true.
 */
class UntitledLyricsLiveTest {
    @Test fun sampleLinkLyrics(): Unit = runBlocking {
        val secrets = File("../secrets.properties").takeIf { it.isFile } ?: return@runBlocking println("lyrics live: no secrets.properties, skipped")
        val links = Properties().apply { secrets.reader().use(::load) }.getProperty("UNTITLED_SAMPLE_LINKS").orEmpty()
            .split(Regex("[ ,;]+")).filter { it.isNotBlank() }
        val tracks = links.flatMap { UntitledImporter(UntitledClient()).read(it).tracks.mapNotNull { t -> t.nativeTrack } }.take(40)
        val resolver = LyricsResolver(bitChordLyrics(), LocalLyricsSource { null }, SqlLyricsCache(io.github.velosako3.blossom.core.database.inMemoryDatabase(), "live"), kotlin.time.Clock.System)
        val gate = Semaphore(2)
        val results = tracks.map { t ->
            async { gate.withPermit { t to runCatching { resolver.resolve(t) }.getOrNull() } }
        }.awaitAll()
        for ((t, r) in results) {
            println("lyrics live: '${t.title}' / ${t.artistNames} -> ${r?.provider ?: "NONE"} ${r?.let { if (it is io.github.velosako3.blossom.feature.lyrics.Lyrics.Plain) "plain" else "synced" } ?: ""} '${r?.lines?.firstOrNull { it.text.isNotBlank() }?.text?.take(50) ?: ""}'")
        }
        println("lyrics live: ${results.count { it.second != null }}/${results.size} found, ${results.count { it.second.let { l -> l != null && l !is io.github.velosako3.blossom.feature.lyrics.Lyrics.Plain } }} synced")
    }
}
