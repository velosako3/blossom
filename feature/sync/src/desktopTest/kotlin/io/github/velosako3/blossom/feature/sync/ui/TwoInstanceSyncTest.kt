// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.ui

import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.database.fileDatabase
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import io.github.velosako3.blossom.core.platform.FilePreferenceStore
import io.github.velosako3.blossom.core.platform.InMemorySecureStore
import io.github.velosako3.blossom.feature.sync.backends.LocalFolderBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

/**
 * Two blossom instances, each with its own data directory and database file, as two app processes would have:
 * first through a shared folder, then paired over loopback with no cloud backend at all.
 */
class TwoInstanceSyncTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val instances = mutableListOf<Instance>()

    private inner class Instance(val name: String) {
        val dir: File = createTempDirectory("blossom-$name").toFile()
        val db = fileDatabase(File(dir, "blossom.db"))
        val prefs = FilePreferenceStore(File(dir, "preferences.properties"))
        val id = "device-$name"
        val log = SqlOpLog(db, id, HybridLogicalClock(id, Clock.System))
        val projector = LibraryProjector(db)
        val commands = LibraryCommands(log, projector, db)
        val controller = SyncController(db, log, projector, prefs, InMemorySecureStore(), id, name, scope, connect = { c, _ -> LocalFolderBackend(File(c.location)) })
        val vm = SyncViewModel(controller, scope)
        val liked get() = db.blossomQueries.likedTracks().executeAsList().map { it.track_id }.toSet()
        init { instances += this }
    }

    @AfterTest fun stop() { instances.forEach { it.controller.stopLan(); it.dir.deleteRecursively() }; scope.cancel() }

    private fun track(n: Int) = Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "t$n"), "Song $n", listOf(ArtistRef(null, "A")), null, 180_000, null)

    @Test fun folderThenLan(): Unit = runBlocking {
        val shared = createTempDirectory("blossom-shared").toFile()
        try {
            val a = Instance("Desk"); val b = Instance("Laptop")
            // 1. A shared folder.
            a.vm.choose(BackendConfig(BackendKind.FOLDER, shared.path)); b.vm.choose(BackendConfig(BackendKind.FOLDER, shared.path))
            repeat(3) { a.commands.toggleLike(track(it)) }
            repeat(2) { b.commands.toggleLike(track(10 + it)) }
            a.vm.syncAll(); b.vm.syncAll(); a.vm.syncAll()
            assertEquals(5, a.liked.size); assertEquals(a.liked, b.liked)
            assertEquals(setOf("Desk", "Laptop"), b.vm.state.value.devices.map { it.name }.toSet())
            println("folder: both have ${a.liked.size} likes; files: ${shared.walkTopDown().filter { it.isFile }.map { it.relativeTo(shared).path }.sorted().toList()}")

            // 2. No cloud: pair over loopback and sync directly.
            a.vm.disconnect(); b.vm.disconnect()
            withTimeout(5_000) { a.vm.state.first { it.backend == null } }
            withTimeout(5_000) { b.vm.state.first { it.backend == null } }
            a.vm.startPairing() // what A's pairing screen does
            val code = a.vm.pair.value.payload!!.copy(hosts = listOf("127.0.0.1"))
            b.vm.pairWith(code.toQrString())
            assertEquals(PairStatus.Done("Desk"), withTimeout(10_000) { b.vm.pair.first { it.status !is PairStatus.Working && it.status != PairStatus.Waiting } }.status)
            withTimeout(10_000) { a.vm.pair.first { it.status is PairStatus.Done } }
            b.commands.toggleLike(track(20))
            b.vm.syncAll() // B → A
            assertEquals(6, a.liked.size)
            a.commands.toggleLike(track(21))
            a.vm.syncAll() // A reaches B at the address B announced while pairing
            assertEquals(7, b.liked.size); assertEquals(a.liked, b.liked)
            withTimeout(5_000) { a.vm.state.first { s -> s.devices.any { it.name == "Laptop" && it.paired } } }
            println("lan: paired ${a.vm.state.value.devices.filter { it.paired }.map { it.name }} / ${b.vm.state.value.devices.filter { it.paired }.map { it.name }}; both have ${a.liked.size} likes")

            // 3. Removing a device stops trusting it: syncing no longer reaches it.
            a.vm.removeDevice("device-Laptop")
            withTimeout(5_000) { a.vm.state.first { s -> s.devices.none { it.paired } } }
            b.commands.toggleLike(track(22))
            b.vm.syncAll()
            assertEquals(7, a.liked.size)
        } finally {
            shared.deleteRecursively()
        }
    }
}
