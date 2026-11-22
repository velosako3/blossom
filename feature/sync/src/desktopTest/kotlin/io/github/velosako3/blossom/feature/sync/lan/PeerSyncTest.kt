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

private class Node(val id: String, scope: CoroutineScope) {
    val db: BlossomDatabase = inMemoryDatabase()
    val log = SqlOpLog(db, id, HybridLogicalClock(id, Clock.System))
    val projector = LibraryProjector(db)
    val commands = LibraryCommands(log, projector, db)
    val repo = SqlLibraryRepository(db)
    val secure = InMemorySecureStore()
    val sync = PeerSync(db, log, projector, secure, id, "Device $id")
    val server = PeerServer(sync, scope, port = 0).also { it.start() }
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
