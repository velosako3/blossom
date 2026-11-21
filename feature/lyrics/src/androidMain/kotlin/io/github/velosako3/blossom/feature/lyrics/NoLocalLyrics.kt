// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.core.model.Track

/** Android: scoped storage gives no access to sidecar `.lrc` files, so local songs use the online providers. */
object NoLocalLyrics : LocalLyricsSource {
    override suspend fun lyrics(track: Track): Lyrics? = null
}
