// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import com.github.javakeyring.Keyring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties

/**
 * OS keyring first; if it can't be reached (no Secret Service on a bare Linux session), a properties file that
 * only the user can read (0600). The fallback is announced once on stderr, without any value.
 */
class DesktopSecureStore(
    private val fallbackFile: File,
    private val service: String = "blossom",
    private val keyring: Keyring? = runCatching { Keyring.create() }.getOrNull(),
) : SecureStore {
    @Volatile private var keyringBroken = keyring == null
    @Volatile private var warned = false

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        keyringOp { it.getPassword(service, key) } ?: fileRead().getProperty(key)
    }

    override suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        if (keyringOp { it.setPassword(service, key, value); true } == true) fileWrite { remove(key) }
        else fileWrite { setProperty(key, value) }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        keyringOp { it.deletePassword(service, key); true }
        fileWrite { remove(key) }
    }

    private fun <T> keyringOp(block: (Keyring) -> T): T? {
        if (keyringBroken) return null
        return try {
            block(keyring!!)
        } catch (e: com.github.javakeyring.PasswordAccessException) {
            null // missing entry, keyring itself works
        } catch (e: Throwable) {
            keyringBroken = true
            if (!warned) {
                warned = true
                System.err.println("blossom: OS keyring unavailable, using a private file for secrets")
            }
            null
        }
    }

    @Synchronized
    private fun fileRead(): Properties = Properties().apply {
        if (fallbackFile.isFile) fallbackFile.inputStream().use(::load)
    }

    @Synchronized
    private fun fileWrite(change: Properties.() -> Unit) {
        val props = fileRead().apply(change)
        if (props.isEmpty && !fallbackFile.exists()) return
        fallbackFile.parentFile?.mkdirs()
        val tmp = File(fallbackFile.parentFile, fallbackFile.name + ".tmp")
        tmp.delete()
        tmp.createNewFile()
        runCatching { Files.setPosixFilePermissions(tmp.toPath(), PosixFilePermissions.fromString("rw-------")) }
            .onFailure { tmp.setReadable(false, false); tmp.setReadable(true, true); tmp.setWritable(true, true) }
        tmp.outputStream().use { props.store(it, null) }
        if (!tmp.renameTo(fallbackFile)) { fallbackFile.delete(); tmp.renameTo(fallbackFile) }
    }
}
