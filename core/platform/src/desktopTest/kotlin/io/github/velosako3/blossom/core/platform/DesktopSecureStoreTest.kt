// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSecureStoreTest {
    @Test fun fileFallbackRoundTripIsPrivate() = runTest {
        val dir = Files.createTempDirectory("ss").toFile()
        val file = dir.resolve("secrets.properties")
        val store = DesktopSecureStore(file, keyring = null)
        store.put("token", "s3cret")
        assertEquals("s3cret", store.get("token"))
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(file.toPath()))
        store.remove("token")
        assertNull(store.get("token"))
    }
}
