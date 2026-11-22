// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.ui

import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.database.inMemoryDatabase
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import io.github.velosako3.blossom.core.platform.InMemorySecureStore
import io.github.velosako3.blossom.core.platform.MapPreferenceStore
import io.github.velosako3.blossom.feature.sync.InMemoryBackend
import io.github.velosako3.blossom.feature.sync.RemoteFile
import io.github.velosako3.blossom.feature.sync.SyncBackend
import io.github.velosako3.blossom.feature.sync.lan.PeerSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class SyncViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = inMemoryDatabase()
    private val log = SqlOpLog(db, "A", HybridLogicalClock("A", Clock.System))
    private val projector = LibraryProjector(db)
    private val prefs = MapPreferenceStore()
    private val secure = InMemorySecureStore()
    private val memory = InMemoryBackend()
    private var failing = false
    private val broken = object : SyncBackend {
        override val id = "broken"
        override suspend fun list(prefix: String): List<RemoteFile> = if (failing) throw java.io.IOException("server unreachable") else memory.list(prefix)
        override suspend fun read(path: String) = memory.read(path)
        override suspend fun write(path: String, bytes: ByteArray) = if (failing) throw java.io.IOException("server unreachable") else memory.write(path, bytes)
    }
    private var interactiveSignIns = 0
    private var declineSignIn = false
    private val controller = SyncController(db, log, projector, prefs, secure, "A", defaultName = "Test PC", scope = scope,
        connect = { config, interactive ->
            when (config.kind) {
                BackendKind.FOLDER, BackendKind.WEBDAV -> broken
                BackendKind.DROPBOX -> if (!interactive) null else { interactiveSignIns++; if (declineSignIn) throw io.github.velosako3.blossom.feature.sync.backends.SignInRequired() else broken }
                else -> null
            }
        })
    private val vm = SyncViewModel(controller, scope)

    @AfterTest fun stop() = scope.cancel()

    @Test fun choosingABackendSyncs(): Unit = runBlocking {
        vm.choose(BackendConfig(BackendKind.FOLDER, "/tmp/sync"))
        val s = withTimeout(10_000) { vm.state.first { it.lastSync != null } }
        assertEquals(BackendKind.FOLDER, s.backend?.kind)
        assertTrue(memory.files.keys.any { it.startsWith("blossom/v1/devices/A/") }, memory.files.keys.toString())
        assertEquals(BackendKind.FOLDER, SyncController(db, log, projector, prefs, secure, "A", "Test PC", scope, connect = { _, _ -> null }).config()?.kind, "choice is remembered")
    }

    @Test fun errorsShowWithRetry(): Unit = runBlocking {
        vm.choose(BackendConfig(BackendKind.FOLDER, "/tmp/sync"))
        withTimeout(10_000) { vm.state.first { it.lastSync != null } }
        failing = true
        vm.syncNow()
        val s = withTimeout(10_000) { vm.state.first { it.error != null } }
        assertTrue("server unreachable" in s.error!!, s.error)
        failing = false
        vm.syncNow()
        withTimeout(10_000) { vm.state.first { it.error == null && !it.syncing } }
    }

    @Test fun removingADeviceForgetsThePeer(): Unit = runBlocking {
        PeerSync(db, log, projector, secure, "A", "Test PC").trust("B", "Phone", ByteArray(32) { 1 })
        vm.refreshDevices()
        withTimeout(5_000) { vm.state.first { s -> s.devices.any { it.id == "B" } } }
        vm.removeDevice("B")
        withTimeout(5_000) { vm.state.first { s -> s.devices.none { it.id == "B" } } }
        assertEquals(0, db.blossomQueries.allPeers().executeAsList().size)
        assertEquals(null, secure.get("peer.B"))
    }

    @Test fun signInOnlyWhenChosen(): Unit = runBlocking {
        vm.choose(BackendConfig(BackendKind.DROPBOX))
        withTimeout(10_000) { vm.state.first { it.lastSync != null } }
        // Later syncs (scheduler, "Sync now") never open a browser.
        controller.rename("x") // drops the cached engine
        vm.syncNow(); vm.syncNow()
        withTimeout(10_000) { vm.state.first { !it.syncing } }
        assertEquals(1, interactiveSignIns)
    }

    @Test fun declinedSignInKeepsThePreviousChoice(): Unit = runBlocking {
        declineSignIn = true
        vm.choose(BackendConfig(BackendKind.DROPBOX))
        val s = withTimeout(10_000) { vm.state.first { it.error != null && !it.syncing } }
        assertEquals(null, s.backend)
        assertEquals(null, controller.config())
    }

    @Test fun blankPasswordKeepsTheStoredOne(): Unit = runBlocking {
        controller.choose(BackendConfig(BackendKind.WEBDAV, "https://dav.example.com"), "pw")
        controller.choose(BackendConfig(BackendKind.WEBDAV, "https://dav.example.com/other"), "")
        assertEquals("pw", controller.password())
    }

    @Test fun syncWorkLeavesTheCallersThread(): Unit = runBlocking {
        val threads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val watching = object : io.github.velosako3.blossom.feature.sync.SyncBackend by memory {
            override suspend fun list(prefix: String) = memory.list(prefix).also { threads += Thread.currentThread().name }
        }
        val engine = io.github.velosako3.blossom.feature.sync.SyncEngine(watching, log, projector, db, "A", "A")
        val single = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "ui-thread") }.asCoroutineDispatcher()
        kotlinx.coroutines.withContext(single) { engine.syncOnce() }
        single.close()
        assertTrue(threads.isNotEmpty() && "ui-thread" !in threads, threads.toString())
    }

    @Test fun deviceNameDefaultsAndRenames(): Unit = runBlocking {
        assertEquals("Test PC", vm.state.value.deviceName)
        vm.rename("Living room")
        assertEquals("Living room", vm.state.value.deviceName)
        assertEquals("Living room", prefs.get("sync.deviceName"))
    }
}
