// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.discord

import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class PresenceBuilderTest {
    private val track = Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "x"), "Around the World", listOf(ArtistRef(null, "Daft Punk"), ArtistRef(null, "Someone")),
        AlbumRef("a", "Homework"), 430_000, "https://example.com/art.jpg")
    private val now = Instant.fromEpochMilliseconds(1_800_000_000_000)

    @Test fun playing() {
        val a = PresenceBuilder.activity(track, positionMs = 60_000, playing = true, now = now)
        assertEquals("Around the World", a["details"]!!.jsonPrimitive.content)
        assertEquals("by Daft Punk, Someone", a["state"]!!.jsonPrimitive.content)
        assertEquals(2, a["type"]!!.jsonPrimitive.content.toInt()) // "Listening to"
        val ts = a["timestamps"]!!.jsonObject
        assertEquals(1_800_000_000_000 - 60_000, ts["start"]!!.jsonPrimitive.long)
        assertEquals(1_800_000_000_000 - 60_000 + 430_000, ts["end"]!!.jsonPrimitive.long)
        assertEquals("https://example.com/art.jpg", a["assets"]!!.jsonObject["large_image"]!!.jsonPrimitive.content)
    }

    @Test fun paused() {
        val a = PresenceBuilder.activity(track, positionMs = 60_000, playing = false, now = now)
        assertNull(a["timestamps"])
        assertEquals("by Daft Punk, Someone · paused", a["state"]!!.jsonPrimitive.content)
    }

    @Test fun handshakeAndActivityOverAUnixSocket() {
        val dir = kotlin.io.path.createTempDirectory("dipc").toFile()
        val path = java.io.File(dir, "discord-ipc-0").toPath()
        val server = java.nio.channels.ServerSocketChannel.open(java.net.StandardProtocolFamily.UNIX).apply { bind(java.net.UnixDomainSocketAddress.of(path)) }
        val got = mutableListOf<Pair<Int, String>>()
        val t = Thread {
            server.accept().use { ch ->
                val i = java.nio.channels.Channels.newInputStream(ch); val o = java.nio.channels.Channels.newOutputStream(ch)
                got += DiscordIpc.readFrame(i); DiscordIpc.writeFrame(o, 1, "{\"evt\":\"READY\"}")
                got += DiscordIpc.readFrame(i); DiscordIpc.writeFrame(o, 1, "{\"cmd\":\"SET_ACTIVITY\"}")
            }
        }.apply { start() }
        val ipc = DiscordIpc("123", listOf(dir))
        kotlin.test.assertTrue(ipc.connect())
        kotlin.test.assertTrue(ipc.setActivity(PresenceBuilder.activity(track, 0, true, now)))
        t.join(5_000); ipc.close(); server.close(); dir.deleteRecursively()
        assertEquals(0, got[0].first); kotlin.test.assertTrue("\"client_id\":\"123\"" in got[0].second)
        assertEquals(1, got[1].first); kotlin.test.assertTrue("SET_ACTIVITY" in got[1].second && "Around the World" in got[1].second)
    }

    @Test fun noDiscordMeansNoConnection() = kotlin.test.assertFalse(DiscordIpc("123", listOf(kotlin.io.path.createTempDirectory("none").toFile())).connect())

    @Test fun ipcFrame() {
        val out = ByteArrayOutputStream()
        DiscordIpc.writeFrame(out, 1, """{"a":1}""")
        val b = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, b.int); assertEquals(7, b.int)
        assertEquals("""{"a":1}""", String(out.toByteArray(), 8, 7))
    }
}
