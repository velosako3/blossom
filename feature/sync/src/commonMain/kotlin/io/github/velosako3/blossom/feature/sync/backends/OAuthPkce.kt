// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.backends

import io.github.velosako3.blossom.core.platform.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

/** An OAuth PKCE pair: the verifier stays here, the S256 challenge goes to the provider. */
class Pkce(val verifier: String, val challenge: String) {
    companion object {
        fun generate(): Pkce {
            val bytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
            val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
            return Pkce(verifier, challenge)
        }
    }
}

/** One provider's endpoints and this app's client. [clientSecret] is only for Google's "desktop app" clients. */
data class OAuthConfig(
    val clientId: String,
    val clientSecret: String?,
    val authorizeUrl: String,
    val tokenUrl: String,
    val scopes: List<String>,
) {
    companion object {
        fun googleDrive(clientId: String, clientSecret: String?) = OAuthConfig(
            clientId, clientSecret, "https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token",
            listOf("https://www.googleapis.com/auth/drive.appdata"),
        )
        fun dropbox(appKey: String) = OAuthConfig(appKey, null, "https://www.dropbox.com/oauth2/authorize", "https://api.dropboxapi.com/oauth2/token", emptyList())
    }
}

@Serializable
data class OAuthTokens(val access: String, val refresh: String?, val expiresAtMs: Long)

/**
 * Tokens for one provider, kept in [SecureStore] under `oauth.<key>`. Requests go through [authorized]: an expired
 * access token is refreshed and the request retried once. Without a refresh token (Android's Google sign-in hands out
 * access tokens only) [renew] fetches a new one instead.
 */
class OAuthClient(
    val config: OAuthConfig,
    private val store: SecureStore,
    private val key: String,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build(),
    private val renew: (suspend () -> OAuthTokens)? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    suspend fun tokens(): OAuthTokens? = store.get("oauth.$key")?.let { runCatching { json.decodeFromString(OAuthTokens.serializer(), it) }.getOrNull() }
    suspend fun save(t: OAuthTokens) = store.put("oauth.$key", json.encodeToString(OAuthTokens.serializer(), t))
    suspend fun signOut() = store.remove("oauth.$key")

    /** The browser URL that starts sign-in. Google and Dropbox each ignore the other's "offline" parameter. */
    fun authorizeUrl(pkce: Pkce, redirectUri: String, state: String): String = config.authorizeUrl.toHttpUrl().newBuilder()
        .addQueryParameter("client_id", config.clientId)
        .addQueryParameter("redirect_uri", redirectUri)
        .addQueryParameter("response_type", "code")
        .addQueryParameter("code_challenge", pkce.challenge)
        .addQueryParameter("code_challenge_method", "S256")
        .addQueryParameter("state", state)
        .apply { if (config.scopes.isNotEmpty()) addQueryParameter("scope", config.scopes.joinToString(" ")) }
        .addQueryParameter("access_type", "offline") // Google: hand out a refresh token
        .addQueryParameter("token_access_type", "offline") // Dropbox: the same
        .build().toString()

    /** Trades the code from the redirect for tokens and stores them. */
    suspend fun exchange(code: String, pkce: Pkce, redirectUri: String): OAuthTokens = tokenRequest(
        "grant_type" to "authorization_code", "code" to code, "code_verifier" to pkce.verifier, "redirect_uri" to redirectUri,
    ).also { save(it) }

    private suspend fun refresh(stale: OAuthTokens): OAuthTokens = lock.withLock {
        val current = tokens()
        if (current != null && current.access != stale.access) return current // someone else refreshed meanwhile
        val refresh = stale.refresh ?: return@withLock (renew ?: throw SignInRequired()).invoke().also { save(it) }
        tokenRequest("grant_type" to "refresh_token", "refresh_token" to refresh)
            .let { it.copy(refresh = it.refresh ?: refresh) }
            .also { save(it) }
    }

    /** Runs [request] with a valid bearer token; on 401 refreshes once and retries. The caller closes the response. */
    suspend fun authorized(request: (bearer: String) -> Request): Response = withContext(Dispatchers.IO) {
        var t = tokens() ?: throw SignInRequired()
        if (t.expiresAtMs < System.currentTimeMillis() + 60_000) t = refresh(t)
        val first = runInterruptible { http.newCall(request(t.access)).execute() }
        if (first.code != 401) return@withContext first
        first.close()
        t = refresh(t)
        val second = runInterruptible { http.newCall(request(t.access)).execute() }
        if (second.code == 401) { second.close(); throw SignInRequired() }
        second
    }

    private suspend fun tokenRequest(vararg fields: Pair<String, String>): OAuthTokens = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().apply {
            fields.forEach { (k, v) -> add(k, v) }
            add("client_id", config.clientId)
            config.clientSecret?.let { add("client_secret", it) }
        }.build()
        val body = runInterruptible {
            http.newCall(Request.Builder().url(config.tokenUrl).post(form).build()).execute().use { r ->
                if (r.code == 400 || r.code == 401) throw SignInRequired()
                if (!r.isSuccessful) throw IOException("token endpoint HTTP ${r.code}")
                r.body.string()
            }
        }
        val o = json.parseToJsonElement(body) as JsonObject
        val access = (o["access_token"] as? JsonPrimitive)?.content ?: throw IOException("no access token")
        val expires = (o["expires_in"] as? JsonPrimitive)?.longOrNull ?: 3600
        OAuthTokens(access, (o["refresh_token"] as? JsonPrimitive)?.content, System.currentTimeMillis() + expires * 1000)
    }
}

/** Signed out, or the refresh token was revoked: the user has to sign in again. */
class SignInRequired : IOException("sign-in required")
