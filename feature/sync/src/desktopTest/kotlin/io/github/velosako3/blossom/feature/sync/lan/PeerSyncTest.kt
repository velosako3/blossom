// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.lan

import io.github.velosako3.blossom.core.database.BlossomDatabase
import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.SqlLibraryRepository
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.database.inMemoryDatabase
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import io.github.velosako3.blossom.core.platform.InMemorySecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock

private fun track(n: Int) = Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "v$n"), "Song $n", listOf(ArtistRef(null, "A")), null, 180_000, null)

private class Node(val id: String, scope: CoroutineScope, preAuthMs: Long = 20_000) {
    val db: BlossomDatabase = inMemoryDatabase()
    val log = SqlOpLog(db, id, HybridLogicalClock(id, Clock.System))
    val projector = LibraryProjector(db)
    val commands = LibraryCommands(log, projector, db)
    val repo = SqlLibraryRepository(db)
    val secure = InMemorySecureStore()
    val sync = PeerSync(db, log, projector, secure, id, "Device $id")
    val server = PeerServer(sync, scope, port = 0, preAuthTimeoutMs = preAuthMs).also { it.start() }
    val liked get() = db.blossomQueries.likedTracks().executeAsList().map { it.track_id }.sorted()
}

class PeerSyncTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @AfterTest fun stop() = scope.cancel()

    private fun pairing(server: Node): PairingPayload {
        val secret = PairingPayload.newSecret()
        server.server.openPairing(secret)
        return PairingPayload(deviceId = server.id, deviceName = "Device ${server.id}", hosts = listOf("127.0.0.1"), port = server.server.port, secret = secret)
    }

    @Test fun pairsWithTheRightSecretOnly(): Unit = runBlocking {
        val a = Node("A", scope); val b = Node("B", scope)
        val good = pairing(a)
        assertFailsWith<PairingRejected> { PeerClient(b.sync).pair(good.copy(secret = PairingPayload.newSecret())) }
        assertEquals(0, b.db.blossomQueries.allPeers().executeAsList().size)
        PeerClient(b.sync).pair(good)
        assertEquals(listOf("A"), b.db.blossomQueries.allPeers().executeAsList().map { it.device_id })
        assertEquals(listOf("B"), a.db.blossomQueries.allPeers().executeAsList().map { it.device_id })
        // The pairing secret is single-use.
        assertFailsWith<PairingRejected> { PeerClient(Node("C", scope).sync).pair(good) }
    }

    @Test fun triesRunOut(): Unit = runBlocking {
        val a = Node("A", scope); val b = Node("B", scope)
        val good = pairing(a)
        repeat(3) { assertFailsWith<PairingRejected> { PeerClient(b.sync).pair(good.copy(secret = PairingPayload.newSecret())) } }
        // Attempts abandoned before proving anything count too (a stalled guesser can't keep the window open).
        repeat(2) {
            java.net.Socket("127.0.0.1", a.server.port).use { s ->
                val pub = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair().public.encoded
                val hello = "{\"type\":\"pair\",\"deviceId\":\"X\",\"deviceName\":\"X\",\"pub\":\"${java.util.Base64.getEncoder().encodeToString(pub)}\"}".toByteArray()
                java.io.DataOutputStream(s.getOutputStream()).apply { writeInt(hello.size); write(hello); flush() }
                java.io.DataInputStream(s.getInputStream()).let { i -> i.readFully(ByteArray(i.readInt())) }
            }
        }
        assertFailsWith<PairingRejected> { PeerClient(b.sync).pair(good) }
    }

    /** The server must not answer with anything keyed by the secret before the client proves it (no offline guessing). */
    @Test fun serverRevealsNothingKeyedFirst(): Unit = runBlocking {
        val a = Node("A", scope)
        pairing(a)
        java.net.Socket("127.0.0.1", a.server.port).use { s ->
            val out = java.io.DataOutputStream(s.getOutputStream()); val input = java.io.DataInputStream(s.getInputStream())
            val pub = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair().public.encoded
            val hello = "{\"type\":\"pair\",\"deviceId\":\"X\",\"deviceName\":\"X\",\"pub\":\"${java.util.Base64.getEncoder().encodeToString(pub)}\"}".toByteArray()
            out.writeInt(hello.size); out.write(hello); out.flush()
            val reply = String(ByteArray(input.readInt()).also(input::readFully))
            assertTrue("\"pub\"" in reply && "mac" !in reply, reply)
        }
    }

    @Test fun preAuthLimits(): Unit = runBlocking {
        val a = Node("A", scope, preAuthMs = 500)
        // A huge first frame is refused without allocating it.
        java.net.Socket("127.0.0.1", a.server.port).use { s ->
            java.io.DataOutputStream(s.getOutputStream()).apply { writeInt(50 * 1024 * 1024); flush() }
            s.soTimeout = 5_000
            assertEquals(-1, s.getInputStream().read())
        }
        // A silent connection is cut.
        java.net.Socket("127.0.0.1", a.server.port).use { s ->
            s.soTimeout = 5_000
            val t0 = System.currentTimeMillis()
            assertEquals(-1, s.getInputStream().read())
            assertTrue(System.currentTimeMillis() - t0 < 4_000)
        }
        // And the server still pairs afterwards.
        PeerClient(Node("B", scope).sync).pair(pairing(a))
    }

    @Test fun anImpostorIsRejected(): Unit = runBlocking {
        val a = Node("A", scope); val b = Node("B", scope)
        PeerClient(b.sync).pair(pairing(a))
        // Someone else answers at A's address claiming to be A, without A's key.
        val impostor = Node("A", scope)
        assertFailsWith<PairingRejected> { PeerClient(b.sync).sync("127.0.0.1", impostor.server.port, "A") }
    }

    @Test fun pairedPeersConvergeAndGossip(): Unit = runBlocking {
        val a = Node("A", scope); val b = Node("B", scope); val c = Node("C", scope)
        PeerClient(b.sync).pair(pairing(a))
        PeerClient(c.sync).pair(pairing(b))
        repeat(5) { a.commands.toggleLike(track(it)) }
        repeat(3) { b.commands.toggleLike(track(10 + it)) }
        PeerClient(b.sync).sync("127.0.0.1", a.server.port, "A") // A ↔ B
        assertEquals(a.liked, b.liked)
        assertEquals(8, a.liked.size)
        c.commands.toggleLike(track(20))
        PeerClient(c.sync).sync("127.0.0.1", b.server.port, "B") // B ↔ C: C gets A's ops through B
        assertEquals(9, c.liked.size)
        assertTrue(c.liked.containsAll(a.liked))
        PeerClient(b.sync).sync("127.0.0.1", a.server.port, "A")
        assertEquals(c.liked, a.liked)
    }
}
