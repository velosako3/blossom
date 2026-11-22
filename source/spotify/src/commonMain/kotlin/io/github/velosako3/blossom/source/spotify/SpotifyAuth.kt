// SPDX-License-Identifier: GPL-3.0-only
// Ported from BitChord (https://github.com/kushagrasinghx/BitChord) v1.9.1,
// shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/spotify/SpotifyLibrary.kt (GPL-3.0).
// Modified for blossom, 2026: token provider only; the library calls live in SpotifyImporter.
package io.github.velosako3.blossom.source.spotify

/** The web player's bearer token, and its client token when there is one. */
class SpotifyTokens(val bearer: String, val clientToken: String?)

/**
 * Where signed-in tokens come from: the platform's sign-in web view (Settings → Accounts). Null means signed out or
 * expired. Only private playlists and Liked Songs need it; public links import without signing in.
 */
fun interface SpotifyAuth {
    suspend fun tokens(): SpotifyTokens?
}
