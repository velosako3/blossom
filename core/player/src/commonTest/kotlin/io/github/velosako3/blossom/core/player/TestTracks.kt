// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId

fun track(n: Int, kind: SourceKind = SourceKind.YOUTUBE_MUSIC, durationMs: Long = 200_000) = Track(
    id = TrackId.of(kind, "t$n"),
    title = "Track $n",
    artists = listOf(ArtistRef(null, "Artist")),
    album = null,
    durationMs = durationMs,
    artworkUrl = null,
)

fun tracks(count: Int) = (0 until count).map { track(it) }
