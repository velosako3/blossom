// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSUserDefaults

private const val PREFS_KEY = "blossom.prefs"

/** Preferences in NSUserDefaults (one dictionary, written through on every change). */
fun iosPreferenceStore(): PreferenceStore {
    val defaults = NSUserDefaults.standardUserDefaults
    @Suppress("UNCHECKED_CAST")
    val initial = (defaults.dictionaryForKey(PREFS_KEY) as? Map<Any?, *>).orEmpty()
        .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.let { key to it } } }.toMap()
    return MapPreferenceStore(initial) { defaults.setObject(it, PREFS_KEY) }
}

/**
 * ponytail: tokens in NSUserDefaults inside the app's sandbox, not the Keychain. iOS blossom has no sign-in yet, so
 * nothing secret is stored; move to the Keychain (SecItemAdd) before adding one.
 */
class IosSecureStore : SecureStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override suspend fun get(key: String): String? = defaults.stringForKey("secure.$key")
    override suspend fun put(key: String, value: String) = defaults.setObject(value, "secure.$key")
    override suspend fun remove(key: String) = defaults.removeObjectForKey("secure.$key")
}

/** ponytail: always online; requests fail on their own when there's no network. NWPathMonitor when offline mode comes to iOS. */
object AlwaysOnline : NetworkMonitor {
    override val status: StateFlow<NetworkStatus> = MutableStateFlow(NetworkStatus(online = true, metered = false))
}
