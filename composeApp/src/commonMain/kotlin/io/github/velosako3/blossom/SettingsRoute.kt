// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.runtime.Composable
import io.github.velosako3.blossom.composeApp.resources.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import io.github.velosako3.blossom.core.designsystem.theme.ThemeMode
import io.github.velosako3.blossom.feature.settings.SettingsAccount
import io.github.velosako3.blossom.feature.settings.SettingsActions
import io.github.velosako3.blossom.feature.settings.SettingsPage
import io.github.velosako3.blossom.feature.settings.SettingsScreen
import io.github.velosako3.blossom.feature.settings.SettingsUi
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Settings → its graph pieces and navigation. */
@Composable
fun SettingsRoute(graph: AppGraph, navigator: Navigator, topBar: @Composable () -> Unit) {
    val p = graph.preferences
    val uri = LocalUriHandler.current
    val accounts by graph.signedIn.collectAsState()
    val lastFmUser by (graph.scrobbling?.lastFmUser ?: remember { MutableStateFlow(null) }).collectAsState()
    val listenBrainz by (graph.scrobbling?.listenBrainzOn ?: remember { MutableStateFlow(false) }).collectAsState()
    var lastFmToken by remember { mutableStateOf<String?>(null) }
    val state = SettingsUi(
        theme = p.theme.state.collectAsState().value,
        glass = p.glass.state.collectAsState().value,
        reduceMotion = p.reduceMotion.state.collectAsState().value ?: false,
        systemTitleBar = graph.systemTitleBar?.state?.collectAsState()?.value,
        crossfadeSeconds = p.crossfade.state.collectAsState().value,
        autoplay = p.autoplay.state.collectAsState().value,
        untitledAutoSave = p.untitledAutoSave.state.collectAsState().value,
        ytmSignedIn = Account.YOUTUBE_MUSIC in accounts,
        spotifySignedIn = if (spotifySignInSupported) Account.SPOTIFY in accounts else null,
        lastFmUser = lastFmUser,
        lastFmAvailable = graph.scrobbling?.lastFmAvailable == true,
        listenBrainzOn = listenBrainz,
        discord = if (graph.desktop) graph.discordEnabled.collectAsState().value else null,
        updateCheck = p.updateCheck.state.collectAsState().value,
        version = BlossomBuildInfo.VERSION,
        hasSync = graph.sync != null,
    )
    val actions = object : SettingsActions {
        override fun theme(mode: ThemeMode) = p.theme.set(mode)
        override fun reduceMotion(on: Boolean) = p.reduceMotion.set(on)
        override fun glass(preference: io.github.velosako3.blossom.core.designsystem.glass.GlassPreference) = p.glass.set(preference)
        override fun systemTitleBar(on: Boolean) { graph.systemTitleBar?.set(on) }
        override fun crossfade(seconds: Int) = p.crossfade.set(seconds)
        override fun autoplay(on: Boolean) = p.autoplay.set(on)
        override fun untitledAutoSave(on: Boolean) = p.untitledAutoSave.set(on)
        override fun clearLyrics() { graph.scope.launch { graph.clearLyrics() } }
        override fun open(page: SettingsPage) = navigator.navigate(
            when (page) {
                SettingsPage.EQUALIZER -> Destination.Equalizer
                SettingsPage.STORAGE -> Destination.Storage
                SettingsPage.LOCAL_FILES -> Destination.LocalFiles
                SettingsPage.SYNC -> Destination.Sync
                SettingsPage.IMPORT -> Destination.Import()
                SettingsPage.SPOTIFY_LIKED -> Destination.Import(io.github.velosako3.blossom.source.spotify.SpotifyImporter.LIKED_SONGS)
                SettingsPage.LICENSES -> Destination.Licenses
                SettingsPage.ONBOARDING -> { p.onboarded.set(false); Destination.Home }
            },
        )
        override fun signIn(account: SettingsAccount) {
            when (account) {
                SettingsAccount.YOUTUBE_MUSIC -> navigator.navigate(Destination.SignIn(Account.YOUTUBE_MUSIC.name))
                SettingsAccount.SPOTIFY -> navigator.navigate(Destination.SignIn(Account.SPOTIFY.name))
                SettingsAccount.LASTFM -> startLastFm()
            }
        }
        override fun signOut(account: SettingsAccount) {
            graph.scope.launch {
                when (account) {
                    SettingsAccount.YOUTUBE_MUSIC -> graph.signOut(Account.YOUTUBE_MUSIC)
                    SettingsAccount.SPOTIFY -> graph.signOut(Account.SPOTIFY)
                    SettingsAccount.LASTFM -> graph.scrobbling?.signOutLastFm()
                }
            }
        }
        override fun startLastFm() {
            graph.scope.launch {
                runCatching { graph.scrobbling?.startLastFm() }.getOrNull()?.let { (url, token) -> lastFmToken = token; runCatching { uri.openUri(url) } }
            }
        }
        override fun finishLastFm() { lastFmToken?.let { t -> graph.scope.launch { runCatching { graph.scrobbling?.finishLastFm(t) } } } }
        override fun listenBrainzToken(token: String?) { graph.scope.launch { graph.scrobbling?.setListenBrainzToken(token) } }
        override fun discord(on: Boolean) = graph.setDiscord(on)
        override fun updateCheck(on: Boolean) = p.updateCheck.set(on)
        override fun openUrl(url: String) { runCatching { uri.openUri(url) } }
    }
    SettingsScreen(state, actions, topBar = topBar)
}

/** Sleep timer choices: 5–60 minutes, the end of the current song, or off. */
@Composable
fun SleepTimerDialog(timer: io.github.velosako3.blossom.core.player.SleepTimer, onDismiss: () -> Unit) {
    val running by timer.state.collectAsState()
    io.github.velosako3.blossom.core.designsystem.components.GlassDialog(
        onDismissRequest = onDismiss,
        title = org.jetbrains.compose.resources.stringResource(Res.string.sleep_timer),
        content = {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                io.github.velosako3.blossom.core.player.SleepTimer.OPTIONS.forEach { d ->
                    io.github.velosako3.blossom.core.designsystem.components.GlassChip(false, { timer.start(d); onDismiss() },
                        org.jetbrains.compose.resources.stringResource(Res.string.sleep_minutes, d.inWholeMinutes.toInt()))
                }
                io.github.velosako3.blossom.core.designsystem.components.GlassChip(running == io.github.velosako3.blossom.core.player.SleepState.EndOfTrack, { timer.endOfTrack(); onDismiss() },
                    org.jetbrains.compose.resources.stringResource(Res.string.sleep_end_of_track))
            }
        },
        confirmButton = {
            if (running != null) io.github.velosako3.blossom.core.designsystem.components.GlassButton({ timer.cancel(); onDismiss() }) {
                androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(Res.string.sleep_off))
            } else io.github.velosako3.blossom.core.designsystem.components.GlassButton(onDismiss) {
                androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(Res.string.sleep_close))
            }
        },
    )
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
