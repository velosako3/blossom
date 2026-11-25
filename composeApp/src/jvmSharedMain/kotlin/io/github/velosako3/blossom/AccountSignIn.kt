// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.velosako3.blossom.core.platform.SecureStore

/** Accounts blossom can sign in to (optional: everything public works without them). */
enum class Account { YOUTUBE_MUSIC, SPOTIFY }

/** Secure-store key of the Spotify `sp_dc` session cookie. */
const val SPOTIFY_COOKIE_KEY = "spotify.sp_dc"

fun Account.storeKey() = when (this) {
    Account.YOUTUBE_MUSIC -> io.github.velosako3.blossom.source.youtubemusic.YtmSource.COOKIE_KEY
    Account.SPOTIFY -> SPOTIFY_COOKIE_KEY
}

/** True when [cookie] can sign YouTube requests (it carries the SAPISID secret). */
fun isYtmCookie(cookie: String) = cookie.split(';').any { it.trim().substringBefore('=') in setOf("SAPISID", "__Secure-3PAPISID") }

/** The platform's sign-in: a web view on Android, a pasted cookie on desktop. Stores the session in [secure]. */
@Composable
expect fun AccountSignInScreen(account: Account, secure: SecureStore, onDone: (signedIn: Boolean) -> Unit, modifier: Modifier = Modifier, topBar: @Composable () -> Unit = {})

/** Spotify sign-in needs Android's web view (as in BitChord); playlists imported there sync to desktop. */
expect val spotifySignInSupported: Boolean

/** Forgets the platform's own web session too, so the next sign-in asks again. */
expect fun clearWebSession(account: Account)
