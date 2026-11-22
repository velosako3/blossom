// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.SystemUpdate
import io.github.velosako3.blossom.composeApp.resources.Res
import io.github.velosako3.blossom.composeApp.resources.offline_banner
import io.github.velosako3.blossom.composeApp.resources.update_chip
import io.github.velosako3.blossom.composeApp.resources.offline_saved_icon
import io.github.velosako3.blossom.composeApp.resources.storage_paused_banner
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import org.jetbrains.compose.resources.stringResource

/** "You're offline. Showing saved music." */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) = Pill(Icons.Rounded.CloudOff, stringResource(Res.string.offline_banner), modifier)

/** Shown while downloads wait because the storage limit is reached (nothing is evicted). */
@Composable
fun StoragePausedBanner(modifier: Modifier = Modifier) = Pill(Icons.Rounded.Storage, stringResource(Res.string.storage_paused_banner), modifier)

@Composable
private fun Pill(icon: ImageVector, text: String, modifier: Modifier) {
    GlassSurface(modifier) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = BlossomTheme.colors.onSurface, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = BlossomTheme.colors.onSurface)
        }
    }
}

/** The small filled circle with an arrow that marks a saved song. */
@Composable
fun SavedIcon(modifier: Modifier = Modifier) {
    Icon(Icons.Rounded.DownloadForOffline, stringResource(Res.string.offline_saved_icon), tint = BlossomTheme.colors.accent, modifier = modifier.size(16.dp))
}

/** A newer blossom is out: opens its release page (or the APK on Android). Never installs anything itself. */
@Composable
fun UpdateChip(version: String, url: String, modifier: Modifier = Modifier) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    io.github.velosako3.blossom.core.designsystem.components.GlassChip(
        selected = true, onClick = { runCatching { uri.openUri(url) } },
        label = stringResource(Res.string.update_chip, version), modifier = modifier,
        leadingIcon = androidx.compose.material.icons.Icons.Rounded.SystemUpdate,
    )
}
