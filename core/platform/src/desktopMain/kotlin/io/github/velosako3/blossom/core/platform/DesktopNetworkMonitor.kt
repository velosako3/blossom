// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Checks every 15 s for an up, non-loopback interface and a DNS answer for YouTube Music — no traffic to any
 * blossom server. `-Dblossom.forceOffline=true` pins it offline, to test offline mode without touching the
 * machine's network.
 */
class DesktopNetworkMonitor(scope: CoroutineScope, private val intervalMs: Long = 15_000) : NetworkMonitor {
    private val forced = System.getProperty("blossom.forceOffline") == "true"
    private val _status = MutableStateFlow(NetworkStatus(online = !forced, metered = false))
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    init {
        if (!forced) scope.launch(Dispatchers.IO) {
            while (true) {
                _status.value = NetworkStatus(online = check(), metered = false)
                delay(intervalMs)
            }
        }
    }

    private fun check(): Boolean = runCatching {
        val up = NetworkInterface.getNetworkInterfaces().asSequence().any { it.isUp && !it.isLoopback && it.inetAddresses.hasMoreElements() }
        up && InetAddress.getAllByName("music.youtube.com").isNotEmpty()
    }.getOrDefault(false)
}
