// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

/**
 * Small secrets (tokens, cookies, passwords) for this device only — never synced, never logged.
 * Android: values encrypted with an Android Keystore key. Desktop: the OS keyring (Windows Credential
 * Manager, Linux Secret Service), falling back to a 0600 file when no keyring is reachable.
 */
interface SecureStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
}

/** For tests and previews. */
class InMemorySecureStore : SecureStore {
    private val map = mutableMapOf<String, String>()
    override suspend fun get(key: String): String? = map[key]
    override suspend fun put(key: String, value: String) { map[key] = value }
    override suspend fun remove(key: String) { map.remove(key) }
}
