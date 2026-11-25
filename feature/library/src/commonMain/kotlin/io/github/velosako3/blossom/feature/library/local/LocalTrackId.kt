// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library.local

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.TrackId

private fun normalize(s: String) = s.replace(Regex("[\\s\\p{Z}]+"), " ").trim().lowercase()

/**
 * `local:` + SHA-1 of normalized title|artist|album. Duration is left out on purpose so the same song, tagged the
 * same on two devices, gets the same ID even when decoders report slightly different lengths.
 */
fun localTrackId(title: String, artist: String, album: String?): TrackId {
    val key = "${normalize(title)}|${normalize(artist)}|${normalize(album ?: "")}"
    val hex = io.github.velosako3.blossom.core.platform.sha1Hex(key.encodeToByteArray())
    return TrackId.of(SourceKind.LOCAL, hex)
}
