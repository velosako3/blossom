// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.lan

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

/** Desktop mDNS through jmDNS. */
class JmDnsDiscovery(address: InetAddress? = null) : PeerDiscovery {
    private val dns = JmDNS.create(address)
    private val type = PeerDiscovery.SERVICE_TYPE + ".local."

    override fun advertise(deviceId: String, port: Int) {
        dns.registerService(ServiceInfo.create(type, "blossom-$deviceId", port, 0, 0, mapOf("d" to deviceId)))
    }

    override fun found(): Flow<FoundPeer> = callbackFlow {
        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) { dns.requestServiceInfo(event.type, event.name) }
            override fun serviceRemoved(event: ServiceEvent) = Unit
            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info
                val id = info.getPropertyString("d") ?: return
                val host = info.inet4Addresses.firstOrNull()?.hostAddress ?: info.hostAddresses.firstOrNull() ?: return
                trySend(FoundPeer(id, host, info.port))
            }
        }
        dns.addServiceListener(type, listener)
        awaitClose { dns.removeServiceListener(type, listener) }
    }

    override fun close() { runCatching { dns.unregisterAllServices(); dns.close() } }
}
