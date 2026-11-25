// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.settings

import io.github.velosako3.blossom.core.designsystem.components.verticalScrollWithBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassChip
import io.github.velosako3.blossom.core.designsystem.components.GlassDialog
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.offline.OfflineSettings
import io.github.velosako3.blossom.feature.settings.resources.*
import io.github.velosako3.blossom.source.api.AudioQuality
import org.jetbrains.compose.resources.stringResource

/** "1.2 GB", "340 MB": decimal units, like the operating systems' own storage screens. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%d MB".format(bytes / 1_000_000)
    bytes >= 1_000 -> "%d KB".format(bytes / 1_000)
    else -> "$bytes B"
}

/** Saved music: space used, limit, download quality, Wi-Fi only, and removing everything (with confirmation). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageScreen(
    usageBytes: Long,
    settings: OfflineSettings,
    onChange: ((OfflineSettings) -> OfflineSettings) -> Unit,
    onRemoveAll: () -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
) {
    var confirming by remember { mutableStateOf(false) }
    val muted = BlossomTheme.colors.onSurfaceVariant
    Column(modifier.fillMaxSize()) {
        topBar()
        Column(Modifier.verticalScrollWithBar().padding(16.dp).padding(bottom = io.github.velosako3.blossom.core.designsystem.glass.LocalBarsPadding.current), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.storage_title), style = MaterialTheme.typography.headlineLarge)
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    val limit = settings.limitBytes
                    Text(
                        if (limit == null) stringResource(Res.string.storage_used, formatBytes(usageBytes))
                        else stringResource(Res.string.storage_used_limit, formatBytes(usageBytes), formatBytes(limit)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (limit != null) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (usageBytes.toFloat() / limit).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = BlossomTheme.colors.accent,
                        )
                    }
                }
            }
            Section(stringResource(Res.string.storage_limit)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(settings.limitBytes == null, { onChange { it.copy(limitBytes = null) } }, stringResource(Res.string.storage_unlimited))
                    OfflineSettings.LIMIT_OPTIONS_GB.forEach { gb ->
                        val bytes = gb * 1_000_000_000L
                        GlassChip(settings.limitBytes == bytes, { onChange { it.copy(limitBytes = bytes) } }, stringResource(Res.string.storage_gb, gb))
                    }
                }
            }
            Section(stringResource(Res.string.storage_quality)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AudioQuality.LOW to Res.string.storage_quality_low,
                        AudioQuality.NORMAL to Res.string.storage_quality_normal,
                        AudioQuality.HIGH to Res.string.storage_quality_high,
                    ).forEach { (q, label) -> GlassChip(settings.quality == q, { onChange { it.copy(quality = q) } }, stringResource(label)) }
                }
            }
            GlassSurface(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.toggleable(settings.wifiOnly, role = Role.Switch) { on -> onChange { it.copy(wifiOnly = on) } }.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.storage_wifi_only), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(Res.string.storage_wifi_only_hint), color = muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = settings.wifiOnly,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = BlossomTheme.colors.accent, checkedThumbColor = BlossomTheme.colors.onAccent),
                    )
                }
            }
            GlassButton({ confirming = true }, enabled = usageBytes > 0) {
                Icon(Icons.Rounded.DeleteOutline, null)
                Text(stringResource(Res.string.storage_remove_all))
            }
        }
    }
    if (confirming) {
        GlassDialog(
            onDismissRequest = { confirming = false },
            title = stringResource(Res.string.storage_remove_title),
            text = stringResource(Res.string.storage_remove_body),
            confirmButton = { GlassButton({ confirming = false; onRemoveAll() }, prominent = true) { Text(stringResource(Res.string.storage_remove_confirm)) } },
            dismissButton = { GlassButton({ confirming = false }) { Text(stringResource(Res.string.storage_cancel)) } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
        content()
    }
}
