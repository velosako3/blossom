// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.feature.integrations.scrobble.LastFmClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** The build's Last.fm key and secret against the real API: a signed `auth.getToken` (no account involved). */
class LastFmLiveTest {
    @Test fun signedTokenRequest() = runBlocking {
        if (BlossomSecrets.LASTFM_API_KEY.isEmpty()) return@runBlocking println("lastfm: no key in this build, skipped")
        val token = LastFmClient(BlossomSecrets.LASTFM_API_KEY, BlossomSecrets.LASTFM_API_SECRET, { null }).token()
        println("lastfm: signed auth.getToken accepted, token of ${token.length} chars")
        assertTrue(token.length >= 16)
    }
}
