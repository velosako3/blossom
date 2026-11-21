// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TrackIdTest {
    @Test fun parseYtm() {
        val id = TrackId.parse("ytm:dQw4w9WgXcQ")
        assertEquals(SourceKind.YOUTUBE_MUSIC, id.kind)
        assertEquals("dQw4w9WgXcQ", id.sourceId)
    }

    @Test fun ofSoundCloud() = assertEquals("sc:123", TrackId.of(SourceKind.SOUNDCLOUD, "123").value)

    @Test fun sourceIdMayContainColons() = assertEquals("a:b", TrackId.parse("untitled:a:b").sourceId)

    @Test fun unknownPrefixThrows() { assertFailsWith<IllegalArgumentException> { TrackId.parse("foo:1") } }

    @Test fun missingSeparatorThrows() { assertFailsWith<IllegalArgumentException> { TrackId.parse("ytm") } }
}
