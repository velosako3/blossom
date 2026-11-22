// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.ui

import io.github.velosako3.blossom.core.database.BlossomDatabase
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.platform.PreferenceStore
import io.github.velosako3.blossom.core.platform.SecureStore
import io.github.velosako3.blossom.feature.sync.SyncEngine
import io.github.velosako3.blossom.feature.sync.SyncReport
import io.github.velosako3.blossom.feature.sync.lan.PairCode
import io.github.velosako3.blossom.feature.sync.lan.PairingPayload
import io.github.velosako3.blossom.feature.sync.lan.PeerClient
import io.github.velosako3.blossom.feature.sync.lan.PeerDiscovery
import io.github.velosako3.blossom.feature.sync.lan.PeerServer
import io.github.velosako3.blossom.feature.sync.lan.PeerSync
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class BackendKind { FOLDER, WEBDAV, GOOGLE_DRIVE, DROPBOX }

/** The chosen backend. [location] is a folder path or tree URI, or a WebDAV URL; [username] is for WebDAV. */
@Serializable
data class BackendConfig(val kind: BackendKind, val location: String = "", val username: String = "")

/**
 * This device's sync setup (device preferences, never synced): the backend, the device name and the engine built
 * from them. [connect] turns a config into a backend; it runs the platform's sign-in when one is needed and returns
 * null when it can't (signed out, option unavailable).
 */
class SyncController(
    private val db: BlossomDatabase,
    private val opLog: SqlOpLog,
    private val projector: LibraryProjector,
    private val prefs: PreferenceStore,
    private val secure: SecureStore,
    val deviceId: String,
    private val defaultName: String,
    val scope: CoroutineScope,
    private val connect: suspend (BackendConfig) -> io.github.velosako3.blossom.feature.sync.SyncBackend?,
    /** mDNS for the LAN, started with the server; none in tests. */
    private val discovery: (() -> PeerDiscovery)? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    @Volatile private var engine: SyncEngine? = null

    /** LAN pairing and peers share the device name and the op log. */
    val peers = PeerSync(db, opLog, projector, secure, deviceId, defaultName).also { it.deviceName = deviceName() }

    /** The LAN server, once [startLan] ran. */
    @Volatile var server: PeerServer? = null
        private set

    fun config(): BackendConfig? = prefs.get(BACKEND)?.let { runCatching { json.decodeFromString(BackendConfig.serializer(), it) }.getOrNull() }

    fun deviceName(): String = prefs.get(NAME)?.takeIf { it.isNotBlank() } ?: defaultName

    fun rename(name: String) {
        prefs.put(NAME, name.trim().ifEmpty { null })
        peers.deviceName = deviceName()
        engine = null // rebuilt with the new name on the next sync
    }

    /** Remembers [config] (and a WebDAV [password], in the secure store); the next sync connects to it. */
    suspend fun choose(config: BackendConfig, password: String? = null) = lock.withLock {
        if (password != null) secure.put(PASSWORD, password)
        prefs.put(BACKEND, json.encodeToString(BackendConfig.serializer(), config))
        engine = null
    }

    /** The WebDAV password, for [connect]. */
    suspend fun password(): String? = secure.get(PASSWORD)

    suspend fun clear() = lock.withLock { prefs.put(BACKEND, null); engine = null }

    /** The engine for the chosen backend; null when there's none or it can't connect. */
    suspend fun engine(): SyncEngine? = lock.withLock {
        engine ?: config()?.let { c -> connect(c)?.let { SyncEngine(it, opLog, projector, db, deviceId, deviceName()) } }?.also { engine = it }
    }

    suspend fun syncNow(): SyncReport? = engine()?.syncOnce()

    /** Starts listening for LAN peers: on the fixed port if it's free (so six-word codes work), otherwise any. */
    fun startLan(): PeerServer = server ?: synchronized(this) {
        server ?: (runCatching { PeerServer(peers, scope, PairCode.PORT) }.getOrElse { PeerServer(peers, scope, 0) }).also {
            it.start(); server = it
            // Starting mDNS can block for seconds: never on the caller's (possibly UI) thread.
            discovery?.let { d -> scope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { startDiscovery(d()) } } }
        }
    }

    fun stopLan() { server?.stop(); server = null }

    /** At app start: listens on the LAN only once this device has paired with another. */
    suspend fun startLanIfPaired() {
        if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { peers.peerRows() }.isNotEmpty()) startLan()
    }

    /** Opens a pairing window: this device's QR payload, and six words when it listens on the fixed port. */
    fun startPairing(): PairUi {
        val server = startLan()
        val secret = PairCode.newSecret()
        server.openPairing(secret)
        val hosts = lanAddresses()
        val payload = PairingPayload(deviceId = deviceId, deviceName = deviceName(), hosts = hosts.ifEmpty { listOf("127.0.0.1") }, port = server.port,
            secret = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secret))
        val ipv4 = hosts.firstOrNull { it.count { c -> c == '.' } == 3 }
        return PairUi(payload, if (server.port == PairCode.PORT && ipv4 != null) PairCode.encode(ipv4, secret) else null)
    }

    fun stopPairing() { server?.closePairing() }

    /** Pairs with the device behind a scanned or pasted `blossom://pair` link or six words; returns its name. */
    suspend fun pair(code: String): String {
        val payload = PairingPayload.parse(code) ?: PairCode.decode(code)?.let {
            PairingPayload(deviceId = "?", deviceName = it.host, hosts = listOf(it.host), port = PairCode.PORT,
                secret = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it.secret))
        } ?: throw IllegalArgumentException("that isn't a blossom pairing code")
        startLan() // so the other side can reach this one back
        val paired = PeerClient(peers).pair(payload)
        runCatching { PeerClient(peers).sync(paired.host, paired.port, paired.deviceId) }
        return paired.name
    }

    /** One session with every paired device whose address is known; unreachable ones are skipped (they may be off). */
    suspend fun syncPeers(): Int {
        var pulled = 0
        for ((id, address) in peers.addresses.toMap()) {
            if (peers.keyOf(id) == null) continue
            pulled += runCatching { PeerClient(peers).sync(address.first, address.second, id) }.getOrDefault(0)
        }
        return pulled
    }

    /** Announces this device on the LAN and learns where paired devices are. */
    private fun startDiscovery(discovery: PeerDiscovery) {
        discovery.advertise(deviceId, server?.port ?: return)
        scope.launch {
            discovery.found().collect { found ->
                if (found.deviceId != deviceId && peers.keyOf(found.deviceId) != null) peers.addresses[found.deviceId] = found.host to found.port
            }
        }
    }

    companion object {
        private const val BACKEND = "sync.backend"
        private const val NAME = "sync.deviceName"
        /** Secure-store key of the WebDAV password. */
        const val PASSWORD = "sync.webdav.password"
    }
}

/** This device's LAN addresses, IPv4 first; no loopback, no link-local. */
fun lanAddresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.inetAddresses.toList() }
        .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress && !it.isMulticastAddress }
        .sortedBy { if (it is Inet4Address) 0 else 1 }
        .map { it.hostAddress.substringBefore('%') }
        .distinct()
}.getOrDefault(emptyList())
