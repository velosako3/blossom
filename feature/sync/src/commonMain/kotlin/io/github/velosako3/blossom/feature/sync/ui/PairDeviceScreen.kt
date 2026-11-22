// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassTextField
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.feature.sync.lan.PairingPayload
import io.github.velosako3.blossom.feature.sync.resources.*
import org.jetbrains.compose.resources.stringResource

sealed interface PairStatus {
    data object Waiting : PairStatus
    data object Working : PairStatus
    data class Done(val name: String) : PairStatus
    data class Failed(val reason: String) : PairStatus
}

/** This device's code (QR [payload], and [words] when it listens on the fixed port) and the pairing's progress. */
data class PairUi(val payload: PairingPayload? = null, val words: List<String>? = null, val status: PairStatus = PairStatus.Waiting)

/**
 * Pairing: shows this device's QR code (and six words on desktop), scans another device's code where there's a
 * camera, or takes six words or a pasted `blossom://pair` link.
 */
@Composable
fun PairDeviceScreen(
    state: PairUi,
    canScan: Boolean,
    onScanned: (String) -> Unit,
    onWords: (String) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
) {
    val muted = BlossomTheme.colors.onSurfaceVariant
    var scanning by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize()) {
        topBar()
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.pair_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.fillMaxWidth())
            val status = when (val s = state.status) {
                PairStatus.Waiting -> null
                PairStatus.Working -> stringResource(Res.string.pair_waiting)
                is PairStatus.Done -> stringResource(Res.string.pair_done, s.name)
                is PairStatus.Failed -> stringResource(Res.string.pair_failed, s.reason)
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = if (state.status is PairStatus.Failed) BlossomTheme.colors.error else BlossomTheme.colors.onSurface,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (scanning && canScan) {
                QrScanner({ scanning = false; onScanned(it) }, Modifier.fillMaxWidth().aspectRatio(1f))
            } else state.payload?.let { p ->
                Text(stringResource(Res.string.pair_show_hint), color = muted, style = MaterialTheme.typography.bodyMedium)
                QrCode(p.toQrString(), stringResource(Res.string.pair_title))
            }
            state.words?.let { w ->
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.pair_words_hint), color = muted, style = MaterialTheme.typography.bodyMedium)
                        Text(w.joinToString(" "), style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
            if (canScan) GlassButton({ scanning = !scanning }, prominent = true) { Text(stringResource(Res.string.pair_scan)) }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.pair_enter), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
                GlassTextField(
                    typed, { typed = it }, placeholder = stringResource(Res.string.pair_enter_hint),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (typed.isNotBlank()) onWords(typed) }),
                    trailing = { GlassButton({ onWords(typed) }, enabled = typed.isNotBlank() && state.status != PairStatus.Working) { Text(stringResource(Res.string.pair_connect)) } },
                )
            }
        }
    }
}
