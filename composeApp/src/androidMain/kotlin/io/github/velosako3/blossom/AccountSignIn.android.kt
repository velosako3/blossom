// SPDX-License-Identifier: GPL-3.0-only
// Ported from BitChord (https://github.com/kushagrasinghx/BitChord) v1.9.1,
// app/src/main/java/com/music/bitchord/auth/{SpotifyLoginScreen,YtMusicLoginScreen}.kt (GPL-3.0).
// Modified for blossom, 2026: one screen for both accounts, cookies go to blossom's SecureStore, YouTube Music keeps
// only the cookie (blossom's client needs nothing else), confirmation is an explicit button.
package io.github.velosako3.blossom

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.velosako3.blossom.composeApp.resources.Res
import io.github.velosako3.blossom.composeApp.resources.signin_confirm
import io.github.velosako3.blossom.composeApp.resources.signin_confirm_hint
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.platform.SecureStore
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val YTM_ORIGIN = "https://music.youtube.com"
private const val YTM_LOGIN = "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&prompt=select_account&continue=https%3A%2F%2Fmusic.youtube.com%2F"
private const val SPOTIFY_LOGIN = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
private val SPOTIFY_COOKIE_URLS = listOf("https://open.spotify.com", "https://accounts.spotify.com", "https://www.spotify.com", "https://spotify.com")
private val SPOTIFY_HOSTS = listOf("spotify.com", "scdn.co", "google.com", "gstatic.com", "facebook.com", "apple.com")
private val GOOGLE_HOSTS = listOf("google.com", "youtube.com", "gstatic.com", "googleusercontent.com", "googleapis.com")

private fun hostMatches(uri: Uri?, suffixes: List<String>): Boolean {
    if (uri?.scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    return suffixes.any { host == it || host.endsWith(".$it") }
}

/** The device's own Chrome user agent minus the WebView markers (Google and Spotify serve their real pages to it). */
internal fun browserUserAgent(platform: String?): String =
    platform?.replace("; wv", "")?.replace(Regex("""Version/\d+\.\d+ ?"""), "")?.replace("  ", " ")?.trim()?.takeIf { "Chrome/" in it }
        ?: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

private fun cookieValue(cookies: String?, name: String) = cookies?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.takeIf { it.isNotBlank() }

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun AccountSignInScreen(account: Account, secure: SecureStore, onDone: (Boolean) -> Unit, modifier: Modifier, topBar: @Composable () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    // YouTube Music: reaching music.youtube.com only enables confirming; the user may still be picking a channel.
    var onMusic by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize()) {
        topBar()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = browserUserAgent(settings.userAgentString)
                        setBackgroundColor(0xFF121212.toInt())
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        var sent = false
                        webViewClient = object : WebViewClient() {
                            // Only the service's own pages and the identity providers its sign-in hands off to.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                !hostMatches(request.url, if (account == Account.SPOTIFY) SPOTIFY_HOSTS else GOOGLE_HOSTS)

                            override fun onPageFinished(view: WebView, url: String?) {
                                canGoBack = view.canGoBack()
                                when (account) {
                                    Account.YOUTUBE_MUSIC -> onMusic = url?.startsWith(YTM_ORIGIN) == true
                                    Account.SPOTIFY -> {
                                        if (sent || url?.startsWith("https://open.spotify.com") != true) return
                                        val token = cookieValue(CookieManager.getInstance().getCookie("https://open.spotify.com"), "sp_dc") ?: return
                                        sent = true
                                        view.visibility = View.GONE // don't flash the web player while closing
                                        scope.launch { secure.put(SPOTIFY_COOKIE_KEY, token); onDone(true) }
                                    }
                                }
                            }
                        }
                        webView = this
                        loadUrl(if (account == Account.SPOTIFY) SPOTIFY_LOGIN else YTM_LOGIN)
                    }
                },
                onRelease = { it.destroy() },
            )
            if (account == Account.YOUTUBE_MUSIC && onMusic) {
                Column(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.signin_confirm_hint), style = MaterialTheme.typography.bodyMedium)
                    GlassButton({
                        val cookies = CookieManager.getInstance().getCookie(YTM_ORIGIN)
                        if (cookies != null && isYtmCookie(cookies)) {
                            CookieManager.getInstance().flush()
                            scope.launch { secure.put(Account.YOUTUBE_MUSIC.storeKey(), cookies); onDone(true) }
                        }
                    }, prominent = true) { Text(stringResource(Res.string.signin_confirm)) }
                }
            }
        }
    }
    BackHandler(enabled = canGoBack) { webView?.goBack() }
}


actual val spotifySignInSupported: Boolean = true

/** Expires the service's cookies in the shared web jar (it also holds the other service's sign-in). */
actual fun clearWebSession(account: Account) {
    val cookies = CookieManager.getInstance()
    val urls = if (account == Account.SPOTIFY) SPOTIFY_COOKIE_URLS else listOf(YTM_ORIGIN, "https://www.youtube.com", "https://accounts.google.com")
    val domain = if (account == Account.SPOTIFY) ".spotify.com" else ".youtube.com"
    for (url in urls) {
        cookies.getCookie(url)?.split(';')?.map { it.substringBefore('=').trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
            val expired = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
            cookies.setCookie(url, expired); cookies.setCookie(url, "$expired; Domain=$domain")
        }
    }
    cookies.flush()
}
