// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.core.platform.InMemorySecureStore
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test

/** What blossom's own search gives for unreleased songs: the title/artist/length the lyrics lookup receives. Live only. */
class UnreleasedMetaLiveTest {
    @Test fun meta() = runBlocking {
        val source = YtmSource(InMemorySecureStore(), Files.createTempDirectory("ytm").toFile())
        for (q in listOf("Ye Nitrous", "Ye Diddy Free", "Ye Free My Kids")) {
            val r = source.search(q)
            r.songs.take(4).forEach { println("meta song '$q': id=${it.id.value} title='${it.title}' artists=${it.artists.map { a -> a.name }} album='${it.album?.title}' ms=${it.durationMs}") }
        }
    }
}
