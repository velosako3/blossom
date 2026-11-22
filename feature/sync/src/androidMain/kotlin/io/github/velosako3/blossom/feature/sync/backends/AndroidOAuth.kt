// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.backends

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/**
 * Android sign-in in the system browser. Dropbox redirects to `db-<app key>://1/connect`, which MainActivity hands to
 * [complete]; no registration in the Dropbox console is needed for that scheme.
 */
object AndroidOAuth {
    private class Pending(val state: String, val result: CompletableDeferred<Uri>)
    @Volatile private var pending: Pending? = null

    fun dropboxRedirect(appKey: String) = "db-$appKey://1/connect"

    suspend fun signIn(context: Context, client: OAuthClient, redirectUri: String): OAuthTokens {
        val pkce = Pkce.generate()
        val p = Pending(UUID.randomUUID().toString(), CompletableDeferred())
        pending = p
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(client.authorizeUrl(pkce, redirectUri, p.state))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val uri = withTimeoutOrNull(10.minutes) { p.result.await() } ?: throw SignInRequired()
        if (uri.getQueryParameter("state") != p.state) throw SignInRequired()
        val code = uri.getQueryParameter("code") ?: throw SignInRequired()
        return client.exchange(code, pkce, redirectUri)
    }

    /** Called with the redirect the browser opened blossom with. */
    fun complete(uri: Uri): Boolean {
        val p = pending ?: return false
        pending = null
        return p.result.complete(uri)
    }
}
