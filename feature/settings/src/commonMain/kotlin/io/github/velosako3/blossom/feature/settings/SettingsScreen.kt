// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassChip
import io.github.velosako3.blossom.core.designsystem.components.GlassDialog
import io.github.velosako3.blossom.core.designsystem.components.GlassTextField
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.designsystem.theme.ThemeMode
import io.github.velosako3.blossom.feature.settings.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Everything Settings shows. Rows for things a platform doesn't have are null/false and hidden. */
data class SettingsUi(
    val theme: ThemeMode = ThemeMode.DARK,
    val glass: io.github.velosako3.blossom.core.designsystem.glass.GlassPreference = io.github.velosako3.blossom.core.designsystem.glass.GlassPreference.AUTO,
    val reduceMotion: Boolean = false,
    /** Desktop only (null elsewhere). */
    val systemTitleBar: Boolean? = null,
    val crossfadeSeconds: Int = 0,
    val autoplay: Boolean = true,
    val untitledAutoSave: Boolean = true,
    val ytmSignedIn: Boolean = false,
    /** Null where Spotify sign-in isn't available (desktop). */
    val spotifySignedIn: Boolean? = null,
    /** Null when this build has no Last.fm key. */
    val lastFmUser: String? = null,
    val lastFmAvailable: Boolean = false,
    val listenBrainzOn: Boolean = false,
    /** Desktop only (null elsewhere). */
    val discord: Boolean? = null,
    val updateCheck: Boolean = true,
    val version: String = "",
    /** Rows that open other screens; false hides them (no graph pieces in tests). */
    val hasSync: Boolean = true,
)

/** What Settings can do; the app wires each to its graph and navigation. */
interface SettingsActions {
    fun theme(mode: ThemeMode)
    fun reduceMotion(on: Boolean)
    fun glass(preference: io.github.velosako3.blossom.core.designsystem.glass.GlassPreference) {}
    fun systemTitleBar(on: Boolean) {}
    fun crossfade(seconds: Int)
    fun autoplay(on: Boolean)
    fun untitledAutoSave(on: Boolean)
    fun clearLyrics()
    fun open(page: SettingsPage)
    fun signIn(account: SettingsAccount)
    fun signOut(account: SettingsAccount)
    /** Last.fm web auth: opens the approval page; [finishLastFm] completes it. */
    fun startLastFm()
    fun finishLastFm()
    fun listenBrainzToken(token: String?)
    fun discord(on: Boolean) {}
    fun updateCheck(on: Boolean)
    fun openUrl(url: String)
}

enum class SettingsPage { EQUALIZER, STORAGE, LOCAL_FILES, SYNC, IMPORT, SPOTIFY_LIKED, LICENSES, ONBOARDING }
enum class SettingsAccount { YOUTUBE_MUSIC, SPOTIFY, LASTFM }

const val SOURCE_URL = "https://github.com/velosako3/blossom"

/**
 * The whole of Settings on one scrolling page, in sections: appearance, playback, lyrics, storage, local files,
 * sync, import, accounts, integrations and about. Sub-screens (equalizer, storage, sync, licenses) open from links.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: SettingsUi, actions: SettingsActions, modifier: Modifier = Modifier, topBar: @Composable () -> Unit = {}) {
    val colors = BlossomTheme.colors
    var lastFmPending by remember { mutableStateOf(false) }
    var editingToken by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        topBar()
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineLarge)

            Section(Res.string.settings_appearance) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ThemeMode.SYSTEM to Res.string.settings_theme_system, ThemeMode.LIGHT to Res.string.settings_theme_light, ThemeMode.DARK to Res.string.settings_theme_dark)
                            .forEach { (m, label) -> GlassChip(state.theme == m, { actions.theme(m) }, stringResource(label)) }
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.settings_glass), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.settings_glass_hint), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            io.github.velosako3.blossom.core.designsystem.glass.GlassPreference.AUTO to Res.string.settings_glass_auto,
                            io.github.velosako3.blossom.core.designsystem.glass.GlassPreference.FULL to Res.string.settings_glass_full,
                            io.github.velosako3.blossom.core.designsystem.glass.GlassPreference.REDUCED to Res.string.settings_glass_reduced,
                            io.github.velosako3.blossom.core.designsystem.glass.GlassPreference.OFF to Res.string.settings_glass_off,
                        ).forEach { (g, label) -> GlassChip(state.glass == g, { actions.glass(g) }, stringResource(label)) }
                    }
                }
                SwitchRow(Res.string.settings_reduce_motion, Res.string.settings_reduce_motion_hint, state.reduceMotion, actions::reduceMotion)
                state.systemTitleBar?.let { SwitchRow(Res.string.settings_system_title_bar, Res.string.settings_system_title_bar_hint, it, actions::systemTitleBar) }
            }

            Section(Res.string.settings_playback) {
                val crossfadeText = if (state.crossfadeSeconds == 0) stringResource(Res.string.settings_crossfade_off) else stringResource(Res.string.settings_crossfade_seconds, state.crossfadeSeconds)
                val crossfadeLabel = stringResource(Res.string.settings_crossfade)
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(crossfadeLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(crossfadeText, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        state.crossfadeSeconds.toFloat(), { actions.crossfade(it.roundToInt()) }, valueRange = 0f..12f, steps = 11,
                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
                        modifier = Modifier.semantics { contentDescription = crossfadeLabel; stateDescription = crossfadeText },
                    )
                }
                SwitchRow(Res.string.settings_autoplay, Res.string.settings_autoplay_hint, state.autoplay, actions::autoplay)
                LinkRow(Res.string.settings_equalizer) { actions.open(SettingsPage.EQUALIZER) }
            }

            Section(Res.string.settings_lyrics) {
                Text(stringResource(Res.string.settings_lyrics_hint), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 4.dp))
                LinkRow(Res.string.settings_clear_lyrics, arrow = false) { actions.clearLyrics() }
            }

            Section(Res.string.settings_library_section) {
                LinkRow(Res.string.settings_storage) { actions.open(SettingsPage.STORAGE) }
                LinkRow(Res.string.settings_local_files) { actions.open(SettingsPage.LOCAL_FILES) }
                if (state.hasSync) LinkRow(Res.string.settings_sync) { actions.open(SettingsPage.SYNC) }
            }

            Section(Res.string.settings_import) {
                LinkRow(Res.string.settings_import_playlists) { actions.open(SettingsPage.IMPORT) }
                if (state.spotifySignedIn == true) LinkRow(Res.string.settings_import_spotify_liked) { actions.open(SettingsPage.SPOTIFY_LIKED) }
                SwitchRow(Res.string.settings_untitled_autosave, Res.string.settings_untitled_autosave_hint, state.untitledAutoSave, actions::untitledAutoSave)
            }

            Section(Res.string.settings_accounts) {
                AccountRow(Res.string.settings_ytm, Res.string.settings_ytm_hint, state.ytmSignedIn, onSignIn = { actions.signIn(SettingsAccount.YOUTUBE_MUSIC) }) { actions.signOut(SettingsAccount.YOUTUBE_MUSIC) }
                state.spotifySignedIn?.let { on ->
                    AccountRow(Res.string.settings_spotify, Res.string.settings_spotify_hint, on, onSignIn = { actions.signIn(SettingsAccount.SPOTIFY) }) { actions.signOut(SettingsAccount.SPOTIFY) }
                }
            }

            Section(Res.string.settings_integrations) {
                if (state.lastFmAvailable) {
                    val user = state.lastFmUser
                    AccountRow(
                        Res.string.settings_lastfm, null, user != null, detail = user?.let { stringResource(Res.string.settings_lastfm_user, it) },
                        onSignIn = { lastFmPending = true; actions.startLastFm() }, onSignOut = { actions.signOut(SettingsAccount.LASTFM) },
                    )
                }
                AccountRow(
                    Res.string.settings_listenbrainz, null, state.listenBrainzOn, detail = if (state.listenBrainzOn) stringResource(Res.string.settings_listenbrainz_on) else null,
                    onSignIn = { editingToken = true }, onSignOut = { actions.listenBrainzToken(null) },
                )
                state.discord?.let { SwitchRow(Res.string.settings_discord, Res.string.settings_discord_hint, it, actions::discord) }
            }

            Section(Res.string.settings_about) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.settings_version, state.version), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.settings_license), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.settings_credits), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                LinkRow(Res.string.settings_source) { actions.openUrl(SOURCE_URL) }
                LinkRow(Res.string.settings_licenses) { actions.open(SettingsPage.LICENSES) }
                SwitchRow(Res.string.settings_update_check, Res.string.settings_update_check_hint, state.updateCheck, actions::updateCheck)
                LinkRow(Res.string.settings_onboarding) { actions.open(SettingsPage.ONBOARDING) }
            }
        }
    }
    if (lastFmPending) {
        GlassDialog(
            onDismissRequest = { lastFmPending = false },
            title = stringResource(Res.string.settings_lastfm),
            text = stringResource(Res.string.settings_lastfm_approve),
            confirmButton = { GlassButton({ lastFmPending = false; actions.finishLastFm() }, prominent = true) { Text(stringResource(Res.string.settings_done)) } },
            dismissButton = { GlassButton({ lastFmPending = false }) { Text(stringResource(Res.string.settings_cancel)) } },
        )
    }
    if (editingToken) {
        var token by remember { mutableStateOf("") }
        GlassDialog(
            onDismissRequest = { editingToken = false },
            title = stringResource(Res.string.settings_listenbrainz),
            content = {
                GlassTextField(token, { token = it }, placeholder = stringResource(Res.string.settings_listenbrainz_token),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
            },
            confirmButton = { GlassButton({ editingToken = false; actions.listenBrainzToken(token) }, enabled = token.isNotBlank(), prominent = true) { Text(stringResource(Res.string.settings_save)) } },
            dismissButton = { GlassButton({ editingToken = false }) { Text(stringResource(Res.string.settings_cancel)) } },
        )
    }
}

@Composable
private fun Section(title: StringResource, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = BlossomTheme.colors.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        GlassSurface(Modifier.fillMaxWidth()) { Column(Modifier.padding(vertical = 4.dp)) { content() } }
    }
}

/** A labelled switch: the whole row toggles, and is announced with its label. */
@Composable
private fun SwitchRow(title: StringResource, hint: StringResource?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = BlossomTheme.colors
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            hint?.let { Text(stringResource(it), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        }
        Switch(checked, null, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent))
    }
}

@Composable
private fun LinkRow(title: StringResource, arrow: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (arrow) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = BlossomTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun AccountRow(title: StringResource, hint: StringResource?, signedIn: Boolean, detail: String? = null, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    val colors = BlossomTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(detail ?: stringResource(if (signedIn) Res.string.settings_signed_in else Res.string.settings_signed_out), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            hint?.let { Text(stringResource(it), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
        if (signedIn) GlassButton(onSignOut) { Text(stringResource(Res.string.settings_sign_out)) }
        else GlassButton(onSignIn, prominent = true) { Text(stringResource(Res.string.settings_sign_in)) }
    }
}
