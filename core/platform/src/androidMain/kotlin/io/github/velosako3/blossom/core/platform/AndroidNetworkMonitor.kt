// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** ConnectivityManager's default network: validated internet means online; not "not metered" means metered. */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _status = MutableStateFlow(current())
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _status.value = current() }
            override fun onLost(network: Network) { _status.value = NetworkStatus(online = false, metered = false) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { _status.value = from(caps) }
        })
    }

    private fun current(): NetworkStatus =
        cm.getNetworkCapabilities(cm.activeNetwork)?.let(::from) ?: NetworkStatus(online = false, metered = false)

    private fun from(caps: NetworkCapabilities) = NetworkStatus(
        online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )
}
