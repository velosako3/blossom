// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContrastTest {
    @Test fun whiteOnBlackIs21() = assertEquals(21.0, contrastRatio(Color.White, Color.Black), 0.01)

    @Test fun sameColourIs1() = assertEquals(1.0, contrastRatio(Color.White, Color.White), 0.001)

    @Test fun noScrimNeededWhenAlreadyReadable() =
        assertEquals(0f, requiredScrimAlpha(Color.White, Color.Black, Color.Black))

    @Test fun scrimIsMinimalAndSufficient() {
        val a = requiredScrimAlpha(Color.White, Color(0xFFDDDDDD), Color.Black)
        assertTrue(contrastRatio(Color.White, Color.Black.copy(alpha = a).compositeOver(Color(0xFFDDDDDD))) >= 4.5)
        assertTrue(contrastRatio(Color.White, Color.Black.copy(alpha = a - 0.02f).compositeOver(Color(0xFFDDDDDD))) < 4.5)
    }
}
