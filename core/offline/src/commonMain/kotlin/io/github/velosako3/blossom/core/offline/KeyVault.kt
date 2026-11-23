// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import io.github.velosako3.blossom.core.platform.SecureStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import kotlin.io.encoding.Base64

/**
 * The offline cache key: 32 random bytes made once per device. [SecureStore] keeps it in the Android Keystore-wrapped
 * store, or the OS keyring on desktop (0600 file fallback with a one-time warning).
 */
class KeyVault(private val store: SecureStore) {
    private val lock = Mutex()
    private var key: ByteArray? = null

    /** The key if it was already loaded (no IO), else null. */
    fun cachedKey(): ByteArray? = key

    suspend fun offlineKey(): ByteArray = lock.withLock {
        // An unreadable stored key counts as missing: saved files then fail to decrypt and download again.
        key ?: (store.get(KEY)?.let { runCatching { Base64.decode(it) }.getOrNull()?.takeIf { k -> k.size == 32 } } ?: ByteArray(32).also {
            SecureRandom().nextBytes(it)
            store.put(KEY, Base64.encode(it))
        }).also { key = it }
    }

    private companion object { const val KEY = "offline.key" }
}
