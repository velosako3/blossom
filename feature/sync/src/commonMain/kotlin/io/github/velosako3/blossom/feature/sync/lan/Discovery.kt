// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.lan

import kotlinx.coroutines.flow.Flow

/** A blossom device announcing itself on the LAN. */
data class FoundPeer(val deviceId: String, val host: String, val port: Int)

/**
 * mDNS (`_blossom._tcp`): this device announces its sync port, and paired devices are found again after an address
 * change. Only the device ID is announced, never names or keys.
 */
interface PeerDiscovery {
    fun advertise(deviceId: String, port: Int)
    fun found(): Flow<FoundPeer>
    fun close()

    companion object {
        const val SERVICE_TYPE = "_blossom._tcp"
    }
}
