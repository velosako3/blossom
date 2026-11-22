// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.scrobble

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrobbleRuleTest {
    @Test fun lastFmRule() {
        assertTrue(ScrobbleRule.shouldScrobble(durationMs = 31_000, playedMs = 16_000))
        assertFalse(ScrobbleRule.shouldScrobble(durationMs = 30_000, playedMs = 30_000))
        assertTrue(ScrobbleRule.shouldScrobble(durationMs = 600_000, playedMs = 240_000))
        assertFalse(ScrobbleRule.shouldScrobble(durationMs = 600_000, playedMs = 239_000))
        assertFalse(ScrobbleRule.shouldScrobble(durationMs = 200_000, playedMs = 99_000))
    }
}
