// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.ui

import io.github.velosako3.blossom.core.designsystem.components.verticalScrollWithBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassChip
import io.github.velosako3.blossom.core.designsystem.components.GlassDialog
import io.github.velosako3.blossom.core.designsystem.components.GlassTextField
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.feature.sync.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

private val KIND_LABEL = mapOf(
    BackendKind.GOOGLE_DRIVE to Res.string.sync_gdrive,
    BackendKind.DROPBOX to Res.string.sync_dropbox,
    BackendKind.WEBDAV to Res.string.sync_webdav,
    BackendKind.FOLDER to Res.string.sync_folder,
)

/** "just now", "2 min ago", "3 h ago", "2 days ago". */
@Composable
fun timeAgo(then: Instant, now: Instant): String {
    val minutes = (now - then).inWholeMinutes
    return when {
        minutes < 1 -> stringResource(Res.string.sync_just_now)
        minutes < 60 -> stringResource(Res.string.sync_minutes_ago, minutes.toInt())
        minutes < 48 * 60 -> stringResource(Res.string.sync_hours_ago, (minutes / 60).toInt())
        else -> stringResource(Res.string.sync_days_ago, (minutes / (24 * 60)).toInt())
    }
}

/**
 * Settings → Sync: the backend (or none), status with "Sync now" and errors with a retry, this device's name, the
 * device list, and pairing. [onChoose] gets the WebDAV password as its second argument.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncSettingsScreen(
    state: SyncUi,
    availableKinds: List<BackendKind>,
    onChoose: (BackendConfig, String?) -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    onRename: (String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onPair: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
    topBar: @Composable () -> Unit = {},
) {
    val muted = BlossomTheme.colors.onSurfaceVariant
    var asking by remember { mutableStateOf<BackendKind?>(null) }
    var removing by remember { mutableStateOf<DeviceRow?>(null) }
    val pickFolder = rememberFolderPicker { onChoose(BackendConfig(BackendKind.FOLDER, it), null) }
    Column(modifier.fillMaxSize()) {
        topBar()
        Column(Modifier.verticalScrollWithBar().padding(16.dp).padding(bottom = io.github.velosako3.blossom.core.designsystem.glass.LocalBarsPadding.current), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.sync_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(Res.string.sync_intro), color = muted, style = MaterialTheme.typography.bodyMedium)
            Section(stringResource(Res.string.sync_backend)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(state.backend == null, { if (state.backend != null) onDisconnect() }, stringResource(Res.string.sync_none))
                    availableKinds.forEach { kind ->
                        GlassChip(state.backend?.kind == kind, {
                            when (kind) {
                                BackendKind.WEBDAV -> asking = kind
                                BackendKind.FOLDER -> if (pickFolder != null) pickFolder() else asking = kind
                                else -> onChoose(BackendConfig(kind), null)
                            }
                        }, stringResource(KIND_LABEL.getValue(kind)))
                    }
                }
                if (BackendKind.FOLDER in availableKinds) Text(stringResource(Res.string.sync_folder_hint), color = muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
            }
            if (state.backend != null) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.backend.location.isNotEmpty()) Text(state.backend.location, color = muted, style = MaterialTheme.typography.bodySmall)
                        Text(
                            state.lastSync?.let { stringResource(Res.string.sync_last, timeAgo(it, now)) } ?: stringResource(Res.string.sync_never),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        state.error?.let { Text(stringResource(Res.string.sync_error, it), color = BlossomTheme.colors.error, style = MaterialTheme.typography.bodyMedium) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(onSyncNow, enabled = !state.syncing, prominent = state.error != null) {
                                Icon(Icons.Rounded.Sync, null)
                                Text(stringResource(if (state.error != null) Res.string.sync_retry else Res.string.sync_now))
                            }
                            GlassButton(onDisconnect) { Text(stringResource(Res.string.sync_disconnect)) }
                        }
                    }
                }
            }
            Section(stringResource(Res.string.sync_device_name)) {
                var name by remember(state.deviceName) { mutableStateOf(state.deviceName) }
                GlassTextField(
                    name, { name = it },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRename(name) }),
                    trailing = if (name != state.deviceName) ({ GlassButton({ onRename(name) }) { Text(stringResource(Res.string.sync_save)) } }) else null,
                )
            }
            Section(stringResource(Res.string.sync_devices)) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        state.devices.forEach { d ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Icon(Icons.Rounded.Devices, null, tint = muted)
                                Column(Modifier.weight(1f)) {
                                    Text(d.name, style = MaterialTheme.typography.titleMedium)
                                    val detail = when {
                                        d.isThis -> stringResource(Res.string.sync_this_device)
                                        else -> listOfNotNull(
                                            stringResource(if (d.paired) Res.string.sync_paired else Res.string.sync_through_backend),
                                            d.lastSeen?.let { stringResource(Res.string.sync_seen, timeAgo(it, now)) },
                                        ).joinToString(" · ")
                                    }
                                    Text(detail, color = muted, style = MaterialTheme.typography.bodySmall)
                                }
                                if (d.paired) IconButton({ removing = d }) { Icon(Icons.Rounded.Close, stringResource(Res.string.sync_remove)) }
                            }
                        }
                    }
                }
                GlassButton(onPair) {
                    Icon(Icons.Rounded.QrCode2, null)
                    Text(stringResource(Res.string.sync_pair))
                }
            }
        }
    }
    asking?.let { kind -> BackendDialog(kind, state.backend?.takeIf { it.kind == kind }, onDismiss = { asking = null }) { c, p -> asking = null; onChoose(c, p) } }
    removing?.let { d ->
        GlassDialog(
            onDismissRequest = { removing = null },
            title = stringResource(Res.string.sync_remove),
            text = d.name,
            confirmButton = { GlassButton({ removing = null; onRemoveDevice(d.id) }, prominent = true) { Text(stringResource(Res.string.sync_remove)) } },
            dismissButton = { GlassButton({ removing = null }) { Text(stringResource(Res.string.sync_cancel)) } },
        )
    }
}

/** WebDAV address, user and password; or a folder path where there's no system folder picker. */
@Composable
private fun BackendDialog(kind: BackendKind, current: BackendConfig?, onDismiss: () -> Unit, onConnect: (BackendConfig, String?) -> Unit) {
    var location by remember { mutableStateOf(current?.location.orEmpty()) }
    var user by remember { mutableStateOf(current?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = stringResource(KIND_LABEL.getValue(kind)),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTextField(location, { location = it }, placeholder = stringResource(if (kind == BackendKind.WEBDAV) Res.string.sync_webdav_url else Res.string.sync_folder),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                if (kind == BackendKind.WEBDAV) {
                    GlassTextField(user, { user = it }, placeholder = stringResource(Res.string.sync_webdav_user))
                    GlassTextField(password, { password = it }, placeholder = stringResource(Res.string.sync_webdav_password),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
                }
            }
        },
        confirmButton = {
            GlassButton({ onConnect(BackendConfig(kind, location.trim(), user.trim()), password.takeIf { kind == BackendKind.WEBDAV }) }, enabled = location.isNotBlank(), prominent = true) {
                Text(stringResource(Res.string.sync_connect))
            }
        },
        dismissButton = { GlassButton(onDismiss) { Text(stringResource(Res.string.sync_cancel)) } },
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
        content()
    }
}

/** The system folder picker, or null where blossom asks for a path instead. Returns a launcher. */
@Composable
expect fun rememberFolderPicker(onPicked: (String) -> Unit): (() -> Unit)?

