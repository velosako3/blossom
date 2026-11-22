// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.backends

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/**
 * Desktop sign-in: opens the provider's page in the browser and catches the redirect on 127.0.0.1. Google's
 * "desktop app" clients accept any port ([port] 0); Dropbox needs the exact registered redirect
 * (`http://127.0.0.1:53682/dropbox`).
 */
object DesktopOAuth {
    const val DROPBOX_PORT = 53682
    const val DROPBOX_PATH = "/dropbox"

    suspend fun signIn(client: OAuthClient, port: Int = 0, path: String = "/"): OAuthTokens {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        val result = CompletableDeferred<Map<String, String>>()
        server.createContext(path) { ex ->
            val query = ex.requestURI.rawQuery.orEmpty().split('&').filter { '=' in it }
                .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
            val page = "<!doctype html><meta charset=utf-8><title>blossom</title><body style=\"font-family:sans-serif;padding:3em\"><h2>You're signed in.</h2><p>You can close this tab and return to blossom.</p>".toByteArray()
            ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            ex.sendResponseHeaders(200, page.size.toLong())
            ex.responseBody.use { it.write(page) }
            result.complete(query)
        }
        server.start()
        try {
            val redirect = "http://127.0.0.1:${server.address.port}$path"
            val pkce = Pkce.generate()
            val state = UUID.randomUUID().toString()
            Desktop.getDesktop().browse(URI(client.authorizeUrl(pkce, redirect, state)))
            val query = withTimeout(5.minutes) { result.await() }
            if (query["state"] != state) throw SignInRequired()
            val code = query["code"] ?: throw SignInRequired() // the user declined
            return client.exchange(code, pkce, redirect)
        } finally {
            server.stop(0)
        }
    }
}
