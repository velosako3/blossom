// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.soundcloud

import io.github.velosako3.blossom.source.api.AudioQuality
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** Real SoundCloud, no account. Live: -PliveTests=true. */
class SoundCloudLiveTest {
    private val client = SoundCloudClient()

    @Test fun importSetAndResolveAStream(): Unit = runBlocking {
        val p = SoundCloudImporter(client).read("https://soundcloud.com/newpunjabisongs2027/sets/study-lofi?si=abc")
        val native = p.tracks.mapNotNull { it.nativeTrack }
        println("soundcloud live: '${p.name}' ${p.tracks.size} tracks, ${native.size} native, ${p.tracks.count { it.unplayableReason == "previewOnly" }} preview-only")
        val s = SoundCloudSource(client).resolveStream(native.first().id, AudioQuality.HIGH)
        println("soundcloud live stream: ${s.mimeType} ${s.bitrateKbps} kbps host=${java.net.URI(s.url).host}")
        val search = SoundCloudSource(client).search("lofi study beats")
        println("soundcloud live search: ${search.songs.size} songs, first '${search.songs.firstOrNull()?.title}'")
        assertTrue(p.tracks.size > 50 && native.isNotEmpty() && search.songs.isNotEmpty())
    }
}
