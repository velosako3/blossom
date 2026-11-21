// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import kotlinx.coroutines.flow.StateFlow

data class NetworkStatus(val online: Boolean, val metered: Boolean)

/** Whether blossom can reach the internet right now, and whether the connection is metered (mobile data). */
interface NetworkMonitor {
    val status: StateFlow<NetworkStatus>
}
