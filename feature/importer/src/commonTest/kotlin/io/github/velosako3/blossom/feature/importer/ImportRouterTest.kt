// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportRouterTest {
    private val table = listOf(
        "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc" to Triple(ImportService.SPOTIFY, LinkKind.PLAYLIST, "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"),
        "https://open.spotify.com/intl-de/album/xyz" to Triple(ImportService.SPOTIFY, LinkKind.ALBUM, "https://open.spotify.com/album/xyz"),
        "spotify:track:abc" to Triple(ImportService.SPOTIFY, LinkKind.TRACK, "https://open.spotify.com/track/abc"),
        "https://music.youtube.com/playlist?list=PLx" to Triple(ImportService.YOUTUBE_MUSIC, LinkKind.PLAYLIST, "https://music.youtube.com/playlist?list=PLx"),
        "https://www.youtube.com/watch?v=abc&list=PLx" to Triple(ImportService.YOUTUBE_MUSIC, LinkKind.PLAYLIST, "https://music.youtube.com/playlist?list=PLx"),
        "https://youtu.be/abc" to Triple(ImportService.YOUTUBE_MUSIC, LinkKind.TRACK, "https://music.youtube.com/watch?v=abc"),
        "https://music.youtube.com/browse/MPREb_x" to Triple(ImportService.YOUTUBE_MUSIC, LinkKind.ALBUM, "https://music.youtube.com/browse/MPREb_x"),
        "https://soundcloud.com/artist/track" to Triple(ImportService.SOUNDCLOUD, LinkKind.TRACK, "https://soundcloud.com/artist/track"),
        "https://soundcloud.com/artist/sets/name" to Triple(ImportService.SOUNDCLOUD, LinkKind.PLAYLIST, "https://soundcloud.com/artist/sets/name"),
        "https://on.soundcloud.com/AbC12" to Triple(ImportService.SOUNDCLOUD, LinkKind.PLAYLIST, "https://on.soundcloud.com/AbC12"),
        "https://untitled.stream/library/project/AbCdEfGhIjKlMnOpQrStU" to Triple(ImportService.UNTITLED, LinkKind.ALBUM, "https://untitled.stream/library/project/AbCdEfGhIjKlMnOpQrStU"),
        "https://untitled.stream/library/project/AbCdEfGhIjKlMnOpQrStU/track/WxYzWxYzWxYzWxYz" to Triple(ImportService.UNTITLED, LinkKind.TRACK, "https://untitled.stream/library/project/AbCdEfGhIjKlMnOpQrStU/track/WxYzWxYzWxYzWxYz"),
        "https://untitled.stream/library/track/QrStUvWxYzAbCdEfGhIjK" to Triple(ImportService.UNTITLED, LinkKind.TRACK, "https://untitled.stream/library/track/QrStUvWxYzAbCdEfGhIjK"),
        "https://untitled.stream/library/track/QrStUvWxYzAbCdEfGhIjK/embed" to Triple(ImportService.UNTITLED, LinkKind.TRACK, "https://untitled.stream/library/track/QrStUvWxYzAbCdEfGhIjK"),
        "https://untitled.stream/invite/abc123" to Triple(ImportService.UNTITLED, LinkKind.PLAYLIST, "https://untitled.stream/invite/abc123"),
        "  https://open.spotify.com/track/t1?utm_source=x&si=y  " to Triple(ImportService.SPOTIFY, LinkKind.TRACK, "https://open.spotify.com/track/t1"),
        "https://music.youtube.com/watch?v=v1&feature=share" to Triple(ImportService.YOUTUBE_MUSIC, LinkKind.TRACK, "https://music.youtube.com/watch?v=v1"),
    )

    @Test fun detectsEveryFormat() {
        for ((raw, expected) in table) {
            val d = ImportRouter.detect(raw) ?: error("not detected: $raw")
            assertEquals(expected, Triple(d.service, d.kind, d.canonicalUrl), raw)
        }
    }

    @Test fun rejectsNonLinks() {
        assertNull(ImportRouter.detect("not a link"))
        assertNull(ImportRouter.detect("https://example.com/playlist/1"))
        assertNull(ImportRouter.detect(""))
    }
}
