// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.backends

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.minutes

/**
 * Google Drive sign-in on Android, through Google Play services: Google recognizes blossom by its package name and
 * signing key (registered as an Android OAuth client), shows its own account and consent screen once, and then hands
 * out access tokens on request. There is no refresh token; [OAuthClient] asks [token] again when one runs out.
 */
object AndroidGoogleAuth {
    private val driveAppData = Scope("https://www.googleapis.com/auth/drive.appdata")

    /** A fresh access token. Only [interactive] calls may show Google's screens; otherwise a missing grant is [SignInRequired]. */
    suspend fun token(context: Context, interactive: Boolean): OAuthTokens {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(driveAppData)).build()
        val first = runCatching { Identity.getAuthorizationClient(context).authorize(request).await() }.getOrElse { throw SignInRequired() }
        val result = when {
            !first.hasResolution() -> first
            !interactive -> throw SignInRequired()
            else -> GoogleConsentActivity.ask(context, first.pendingIntent ?: throw SignInRequired())
        }
        val access = result.accessToken ?: throw SignInRequired()
        // Google's access tokens last an hour; renew a little early.
        return OAuthTokens(access, null, System.currentTimeMillis() + 50 * 60_000L)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
        addOnCanceledListener { c.cancel() }
    }
}

/** Invisible: shows Google's consent screen for one [AndroidGoogleAuth.token] call and hands back the result. */
class GoogleConsentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // recreated while Google's screen is up; its result still arrives here
        @Suppress("DEPRECATION")
        val intent = intent.getParcelableExtra<PendingIntent>(EXTRA) ?: return finishWith(null)
        @Suppress("DEPRECATION")
        runCatching { startIntentSenderForResult(intent.intentSender, REQUEST, null, 0, 0, 0) }.onFailure { finishWith(null) }
    }

    @Deprecated("Activity result API of the platform Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST) return
        finishWith(if (resultCode == RESULT_OK) runCatching { Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data) }.getOrNull() else null)
    }

    private fun finishWith(result: AuthorizationResult?) {
        pending?.complete(result)
        pending = null
        finish()
    }

    companion object {
        private const val EXTRA = "consent"
        private const val REQUEST = 1
        @Volatile private var pending: CompletableDeferred<AuthorizationResult?>? = null

        suspend fun ask(context: Context, consent: PendingIntent): AuthorizationResult {
            val p = CompletableDeferred<AuthorizationResult?>()
            pending?.complete(null)
            pending = p
            context.startActivity(Intent(context, GoogleConsentActivity::class.java).putExtra(EXTRA, consent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return withTimeoutOrNull(10.minutes) { p.await() } ?: throw SignInRequired()
        }
    }
}
