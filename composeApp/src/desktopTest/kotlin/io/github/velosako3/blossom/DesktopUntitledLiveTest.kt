// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.source.untitled.UntitledClient
import io.github.velosako3.blossom.source.untitled.UntitledImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Imports the owner's untitled sample links (from the git-ignored secrets.properties; never printed) and plays a track
 * from each in the real desktop player. Live: -PliveTests=true.
 */
class DesktopUntitledLiveTest {
    @Test fun importAndPlaySampleLinks(): Unit = runBlocking {
        val secrets = File("../secrets.properties").takeIf { it.isFile } ?: return@runBlocking println("untitled live: no secrets.properties, skipped")
        val links = Properties().apply { secrets.reader().use(::load) }.getProperty("UNTITLED_SAMPLE_LINKS").orEmpty()
            .split(Regex("[ ,;]+")).filter { it.isNotBlank() }
        resetTestDataDir()
        val graph = createDesktopGraph()
        val importer = UntitledImporter(UntitledClient())
        links.forEachIndexed { i, link ->
            val p = importer.read(link)
            val track = p.tracks.first().nativeTrack!!
            val probe = runCatching { io.github.velosako3.blossom.source.untitled.UntitledSource(UntitledClient()).resolveStream(track.id, io.github.velosako3.blossom.source.api.AudioQuality.HIGH) }
            println("untitled live: probe ${i + 1}: ${probe.exceptionOrNull()?.let { it::class.simpleName } ?: ("ok " + probe.getOrNull()!!.mimeType)}")
            probe.getOrNull()?.let { s ->
                val r = okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(s.url).header("Range", "bytes=0-1023").apply { s.headers.forEach { (k, v) -> header(k, v) } }.build()).execute()
                println("untitled live: media ${i + 1}: HTTP ${r.code} type=${r.header("Content-Type")} range=${r.header("Content-Range")?.substringAfter('/')} bytes=${r.body.bytes().size}")
                r.close()
            }
            graph.player.setQueue(listOf(track), 0)
            graph.player.play()
            kotlinx.coroutines.withTimeoutOrNull(60_000) { graph.player.progress.first { it.positionMs > 3_000 } }
                ?: println("untitled live: state ${i + 1}: index=${graph.player.state.value.currentIndex} error=${graph.player.state.value.error?.let { it::class.simpleName }}")
            println("untitled live: sample link ${i + 1}: ${p.tracks.size} tracks, all native=${p.tracks.all { it.nativeTrack != null }}, first track playing at ${graph.player.progress.value.positionMs} ms")
            graph.player.pause()
        }
        assertTrue(links.isNotEmpty())
    }
}
