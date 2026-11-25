// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.model.PlayEvent
import io.github.velosako3.blossom.core.platform.NetworkMonitor
import io.github.velosako3.blossom.core.platform.PreferenceStore
import io.github.velosako3.blossom.core.platform.SecureStore
import io.github.velosako3.blossom.core.player.PlaybackState
import io.github.velosako3.blossom.feature.integrations.scrobble.LastFmClient
import io.github.velosako3.blossom.feature.integrations.scrobble.ListenBrainzClient
import io.github.velosako3.blossom.feature.integrations.scrobble.Scrobble
import io.github.velosako3.blossom.feature.integrations.scrobble.ScrobbleQueue
import io.github.velosako3.blossom.feature.integrations.scrobble.ScrobbleRule
import io.github.velosako3.blossom.feature.integrations.scrobble.ScrobbleTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Last.fm and ListenBrainz: "now playing" when a song starts, a scrobble when a listen passes [ScrobbleRule]. Both
 * are off until signed in (Last.fm session key, ListenBrainz token, kept in the secure store).
 */
class Scrobbling(
    private val secure: SecureStore,
    prefs: PreferenceStore,
    private val state: StateFlow<PlaybackState>,
    private val network: NetworkMonitor,
    private val scope: CoroutineScope,
) {
    val lastFm = LastFmClient(BlossomSecrets.LASTFM_API_KEY, BlossomSecrets.LASTFM_API_SECRET, { secure.get(LASTFM_SESSION) })
    val listenBrainz = ListenBrainzClient({ secure.get(LISTENBRAINZ_TOKEN) })
    private val queue = ScrobbleQueue(prefs, listOf(lastFm, listenBrainz), network, scope)
    /** The signed-in Last.fm user, and whether a ListenBrainz token is set: for Settings. */
    val lastFmUser = MutableStateFlow<String?>(null)
    val listenBrainzOn = MutableStateFlow(false)

    init {
        scope.launch {
            lastFmUser.value = secure.get(LASTFM_USER)
            lastFm.hasSession = secure.get(LASTFM_SESSION) != null
            listenBrainz.hasToken = secure.get(LISTENBRAINZ_TOKEN) != null
            listenBrainzOn.value = listenBrainz.hasToken
            queue.start()
        }
        scope.launch {
            state.map { s -> s.current?.takeIf { s.isPlaying } }.distinctUntilChanged { a, b -> a?.id == b?.id }.filter { it != null }.collect { t ->
                if (!network.status.value.online) return@collect
                val track = ScrobbleTrack(t!!.artistNames, t.title, t.album?.title, t.durationMs)
                listOf(lastFm, listenBrainz).filter { it.enabled }.forEach { s -> launch { runCatching { s.nowPlaying(track) } } }
            }
        }
    }

    /** From the play tracker: every finished listen. */
    suspend fun onPlay(e: PlayEvent) {
        if (!ScrobbleRule.shouldScrobble(e.track.durationMs, e.playedMs)) return
        queue.enqueue(Scrobble(ScrobbleTrack(e.track.artistNames, e.track.title, e.track.album?.title, e.track.durationMs), e.startedAt))
    }

    /** Step 1 of Last.fm sign-in: the page to approve blossom on, and the token to finish with. */
    suspend fun startLastFm(): Pair<String, String> = lastFm.token().let { lastFm.authUrl(it) to it }

    /** Step 2, after the user approved in the browser. */
    suspend fun finishLastFm(token: String) {
        val (key, name) = lastFm.session(token)
        secure.put(LASTFM_SESSION, key); secure.put(LASTFM_USER, name)
        lastFm.hasSession = true; lastFmUser.value = name
    }

    suspend fun signOutLastFm() {
        secure.remove(LASTFM_SESSION); secure.remove(LASTFM_USER)
        lastFm.hasSession = false; lastFmUser.value = null
    }

    suspend fun setListenBrainzToken(token: String?) {
        if (token.isNullOrBlank()) secure.remove(LISTENBRAINZ_TOKEN) else secure.put(LISTENBRAINZ_TOKEN, token.trim())
        listenBrainz.hasToken = !token.isNullOrBlank(); listenBrainzOn.value = listenBrainz.hasToken
    }

    val lastFmAvailable get() = BlossomSecrets.LASTFM_API_KEY.isNotEmpty()

    private companion object {
        const val LASTFM_SESSION = "lastfm.session"
        const val LASTFM_USER = "lastfm.user"
        const val LISTENBRAINZ_TOKEN = "listenbrainz.token"
    }
}
