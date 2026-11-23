// SPDX-License-Identifier: GPL-3.0-only
// Ported from BitChord (https://github.com/kushagrasinghx/BitChord) v1.9.1,
// app/src/main/java/com/music/bitchord/data/canvas/SpotifyToken.kt (GPL-3.0).
// Modified for blossom, 2026: implements blossom's SpotifyAuth; the cookie comes from SecureStore; no client token.
package io.github.velosako3.blossom

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.velosako3.blossom.core.platform.SecureStore
import io.github.velosako3.blossom.source.spotify.SpotifyAuth
import io.github.velosako3.blossom.source.spotify.SpotifyTokens
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The signed-in web player's bearer token. Spotify checks tokens it didn't mint itself, so this loads
 * open.spotify.com in an offscreen web view with the user's `sp_dc` cookie and reads the token the page fetches for
 * itself (fetch/XHR hooked before its bundle runs). Cached until shortly before it expires.
 */
class AndroidSpotifyAuth(private val context: Context, private val secure: SecureStore) : SpotifyAuth {
    private val lock = Mutex()
    /** The token, until when, and the session cookie it was minted for (another sign-in invalidates it). */
    @Volatile private var cached: Triple<String, Long, String>? = null

    private fun fresh(cookie: String) = cached?.takeIf { (_, until, forCookie) -> forCookie == cookie && System.currentTimeMillis() < until - 30_000 }

    override suspend fun tokens(): SpotifyTokens? {
        val cookie = secure.get(SPOTIFY_COOKIE_KEY) ?: return null.also { cached = null }
        fresh(cookie)?.let { return SpotifyTokens(it.first, null) }
        return lock.withLock {
            fresh(cookie)?.let { return@withLock SpotifyTokens(it.first, null) }
            val harvested = withContext(Dispatchers.Main) { harvest(cookie) } ?: return@withLock null
            cached = Triple(harvested.first, harvested.second, cookie)
            SpotifyTokens(harvested.first, null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun harvest(cookie: String): Pair<String, Long>? {
        val result = CompletableDeferred<Pair<String, Long>?>()
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setCookie("https://open.spotify.com/", "sp_dc=$cookie; Domain=.spotify.com; Path=/; Secure")
            setCookie("https://accounts.spotify.com/", "sp_dc=$cookie; Domain=.spotify.com; Path=/; Secure")
            flush()
        }
        // The player reuses a token from web storage and then never fetches one for the hook to see.
        runCatching { WebStorage.getInstance().deleteAllData() }
        var view: WebView? = null
        return try {
            view = WebView(context.applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = browserUserAgent(settings.userAgentString)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(Bridge(result), BRIDGE)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { view.evaluateJavascript(HOOK, null) }
                    override fun onPageFinished(view: WebView, url: String?) { view.evaluateJavascript(HOOK, null) }
                }
                loadUrl("https://open.spotify.com/")
            }
            withTimeoutOrNull(30_000) { result.await() }
        } finally {
            runCatching { view?.removeJavascriptInterface(BRIDGE); view?.stopLoading(); view?.destroy() }
        }
    }

    private class Bridge(private val result: CompletableDeferred<Pair<String, Long>?>) {
        @JavascriptInterface
        fun onTokenPayload(payload: String?) {
            if (payload.isNullOrBlank() || result.isCompleted) return
            runCatching {
                val root = Json.parseToJsonElement(payload).jsonObject
                val token = root["accessToken"]?.jsonPrimitive?.contentOrNull
                // The page first mints an anonymous token; wait for the signed-in one.
                if (token.isNullOrBlank() || root["isAnonymous"]?.jsonPrimitive?.contentOrNull == "true") return
                val until = root["accessTokenExpirationTimestampMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: (System.currentTimeMillis() + 3_000_000)
                result.complete(token to until)
            }
        }
    }

    private companion object {
        const val BRIDGE = "BlossomTokenBridge"
        val HOOK = """
            (function () {
              if (window.__blossomTokenHook) return;
              window.__blossomTokenHook = true;
              var report = function (body) { try { $BRIDGE.onTokenPayload(body); } catch (e) {} };
              var isToken = function (u) { try { return String(u).indexOf('/api/token') !== -1; } catch (e) { return false; } };
              var origFetch = window.fetch;
              if (origFetch) {
                window.fetch = function (input, init) {
                  var url = (input && input.url) ? input.url : input;
                  var result = origFetch.apply(this, arguments);
                  if (isToken(url)) { try { result.then(function (res) { res.clone().text().then(report).catch(function () {}); }).catch(function () {}); } catch (e) {} }
                  return result;
                };
              }
              var origOpen = XMLHttpRequest.prototype.open;
              XMLHttpRequest.prototype.open = function (method, url) { this.__blossomUrl = url; return origOpen.apply(this, arguments); };
              var origSend = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.send = function () {
                var xhr = this;
                try { xhr.addEventListener('load', function () { if (isToken(xhr.__blossomUrl)) { try { report(xhr.responseText); } catch (e) {} } }); } catch (e) {}
                return origSend.apply(this, arguments);
              };
            })();
        """.trimIndent()
    }
}
