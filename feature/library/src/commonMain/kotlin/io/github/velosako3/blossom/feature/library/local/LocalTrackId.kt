// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library.local

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.TrackId
import java.security.MessageDigest

private fun normalize(s: String) = s.trim().replace(Regex("\\s+"), " ").lowercase()

/**
 * `local:` + SHA-1 of normalized title|artist|album. Duration is left out on purpose so the same song, tagged the
 * same on two devices, gets the same ID even when decoders report slightly different lengths.
 */
fun localTrackId(title: String, artist: String, album: String?): TrackId {
    val key = "${normalize(title)}|${normalize(artist)}|${normalize(album ?: "")}"
    val hex = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    return TrackId.of(SourceKind.LOCAL, hex)
}
