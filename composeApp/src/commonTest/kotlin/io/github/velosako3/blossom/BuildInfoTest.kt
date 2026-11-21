// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildInfoTest {
    @Test fun versionIsSemver() =
        assertTrue(Regex("""^\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?$""").matches(BlossomBuildInfo.VERSION))

    @Test fun appNameIsLowercaseBlossom() = assertEquals("blossom", BlossomBuildInfo.APP_NAME)
}
