// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.ui

import io.github.velosako3.blossom.feature.sync.DeviceManifest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/** A device in the sync group: seen through the cloud backend, paired on the LAN, or both. */
data class DeviceRow(val id: String, val name: String, val lastSeen: Instant?, val paired: Boolean, val isThis: Boolean)

data class SyncUi(
    val backend: BackendConfig? = null,
    val deviceName: String = "",
    val lastSync: Instant? = null,
    val syncing: Boolean = false,
    /** The last sync's problem, shown with a retry. */
    val error: String? = null,
    val devices: List<DeviceRow> = emptyList(),
)

/** Settings → Sync. No conflict screens: merges are automatic. */
class SyncViewModel(private val controller: SyncController, private val scope: CoroutineScope, private val clock: Clock = Clock.System) {
    private val _state = MutableStateFlow(SyncUi(controller.config(), controller.deviceName()))
    val state: StateFlow<SyncUi> = _state
    private var cloudDevices: List<DeviceManifest> = emptyList()
    private val _pair = MutableStateFlow(PairUi())
    val pair: StateFlow<PairUi> = _pair
    private var pairWatch: Job? = null

    init { refreshDevices() }

    fun choose(config: BackendConfig, password: String? = null) {
        scope.launch {
            controller.choose(config, password)
            _state.update { it.copy(backend = config) }
            syncNowInternal()
        }
    }

    fun disconnect() {
        scope.launch { controller.clear(); _state.update { it.copy(backend = null, error = null) } }
    }

    fun syncNow() { scope.launch { syncNowInternal() } }

    /** Cloud and LAN sync, as the scheduler runs it. */
    suspend fun syncAll() = syncNowInternal()

    private suspend fun syncNowInternal() {
        _state.update { it.copy(syncing = true) }
        runCatching { controller.syncPeers() }
        val error = try {
            val report = controller.syncNow()
            if (report != null) cloudDevices = report.devicesSeen
            report?.errors?.firstOrNull()
                ?: if (report == null && controller.config() != null) "Couldn't connect to the sync service" else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: e::class.simpleName
        }
        _state.update { it.copy(syncing = false, error = error, lastSync = if (error == null) clock.now() else it.lastSync) }
        refreshDevices()
    }

    fun rename(name: String) {
        controller.rename(name)
        _state.update { it.copy(deviceName = controller.deviceName()) }
    }

    fun removeDevice(id: String) {
        scope.launch { controller.peers.forget(id); refreshDevices() }
    }

    /** Opens the pairing window; the screen shows the code and the result when another device pairs. */
    fun startPairing() {
        _pair.value = runCatching { controller.startPairing() }.getOrElse { PairUi(status = PairStatus.Failed(it.message ?: "network unavailable")) }
        pairWatch?.cancel()
        pairWatch = scope.launch {
            controller.peers.paired.collect { name -> _pair.update { it.copy(status = PairStatus.Done(name)) }; refreshDevices() }
        }
    }

    fun stopPairing() { pairWatch?.cancel(); controller.stopPairing() }

    /** Pairs using a scanned code, a pasted link or six words. */
    fun pairWith(code: String) {
        _pair.update { it.copy(status = PairStatus.Working) }
        scope.launch {
            val result = try {
                PairStatus.Done(controller.pair(code))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PairStatus.Failed(e.message ?: e::class.simpleName ?: "error")
            }
            _pair.update { it.copy(status = result) }
            refreshDevices()
        }
    }

    fun refreshDevices() {
        scope.launch {
            val peers = withContext(Dispatchers.IO) { controller.peers.peerRows() }
            val rows = LinkedHashMap<String, DeviceRow>()
            rows[controller.deviceId] = DeviceRow(controller.deviceId, controller.deviceName(), null, paired = false, isThis = true)
            for (m in cloudDevices) if (m.deviceId != controller.deviceId) rows[m.deviceId] = DeviceRow(m.deviceId, m.deviceName, m.updatedAt, paired = false, isThis = false)
            for ((id, name, seen) in peers) rows[id] = DeviceRow(id, rows[id]?.name ?: name, listOfNotNull(seen, rows[id]?.lastSeen).maxOrNull(), paired = true, isThis = false)
            _state.update { it.copy(devices = rows.values.toList()) }
        }
    }
}
