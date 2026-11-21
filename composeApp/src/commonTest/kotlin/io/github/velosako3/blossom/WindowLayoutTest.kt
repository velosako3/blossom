// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.navigation.WindowLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowLayoutTest {
    @Test fun compactBelow600() = assertEquals(WindowLayout.COMPACT, WindowLayout.from(599f))
    @Test fun medium600() = assertEquals(WindowLayout.MEDIUM, WindowLayout.from(600f))
    @Test fun medium839() = assertEquals(WindowLayout.MEDIUM, WindowLayout.from(839f))
    @Test fun expanded840() = assertEquals(WindowLayout.EXPANDED, WindowLayout.from(840f))
}
