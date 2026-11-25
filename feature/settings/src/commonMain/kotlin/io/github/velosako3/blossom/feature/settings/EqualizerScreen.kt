// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.settings

import io.github.velosako3.blossom.core.designsystem.components.verticalScrollWithBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassChip
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.player.eq.EQ_BANDS_HZ
import io.github.velosako3.blossom.core.player.eq.EqPreset
import io.github.velosako3.blossom.core.player.eq.EqSettings
import io.github.velosako3.blossom.feature.settings.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val PRESET_LABEL: Map<EqPreset, StringResource> = mapOf(
    EqPreset.FLAT to Res.string.eq_flat, EqPreset.BASS_BOOST to Res.string.eq_bass_boost, EqPreset.TREBLE_BOOST to Res.string.eq_treble_boost,
    EqPreset.VOCAL to Res.string.eq_vocal, EqPreset.ROCK to Res.string.eq_rock, EqPreset.POP to Res.string.eq_pop,
    EqPreset.ELECTRONIC to Res.string.eq_electronic, EqPreset.HIP_HOP to Res.string.eq_hip_hop, EqPreset.ACOUSTIC to Res.string.eq_acoustic,
    EqPreset.CUSTOM to Res.string.eq_custom,
)

/** "+6", "0", "−3.5": signed, at most one decimal. */
private fun db(v: Float): String {
    val r = (v * 2).roundToInt() / 2f
    val text = if (r == r.toInt().toFloat()) r.toInt().toString().removePrefix("-") else r.toString().removePrefix("-")
    return when { r > 0 -> "+$text"; r < 0 -> "−$text"; else -> "0" }
}

/** On/off, presets, a preamp and the 10 bands (±12 dB, half-dB steps). Dragging a band switches to Custom. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(settings: EqSettings, onChange: (EqSettings) -> Unit, modifier: Modifier = Modifier, topBar: @Composable () -> Unit = {}) {
    val colors = BlossomTheme.colors
    val sliderColors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
    Column(modifier.fillMaxSize()) {
        topBar()
        Column(Modifier.verticalScrollWithBar().padding(16.dp).padding(bottom = io.github.velosako3.blossom.core.designsystem.glass.LocalBarsPadding.current), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.eq_title), style = MaterialTheme.typography.headlineLarge)
            GlassSurface(Modifier.fillMaxWidth()) {
                // The whole row is the switch, so it's announced with its label.
                Row(
                    Modifier.toggleable(settings.enabled, role = Role.Switch) { onChange(settings.copy(enabled = it)) }.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.eq_enabled), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(Res.string.eq_enabled_hint), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(settings.enabled, null,
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent))
                }
            }
            Text(stringResource(Res.string.eq_presets), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EqPreset.entries.forEach { p -> GlassChip(settings.preset == p, { onChange(settings.choose(p).copy(enabled = true)) }, stringResource(PRESET_LABEL.getValue(p))) }
            }
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    BandRow(stringResource(Res.string.eq_preamp), settings.preampDb, settings.enabled, sliderColors) { onChange(settings.copy(preampDb = it)) }
                    EQ_BANDS_HZ.forEachIndexed { i, hz ->
                        val label = if (hz >= 1000) stringResource(Res.string.eq_khz, (hz / 1000).toString()) else stringResource(Res.string.eq_hz, hz.toString())
                        BandRow(label, settings.gains[i], settings.enabled, sliderColors) { onChange(settings.setBand(i, it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BandRow(label: String, value: Float, enabled: Boolean, colors: androidx.compose.material3.SliderColors, onChange: (Float) -> Unit) {
    val valueText = stringResource(Res.string.eq_db, db(value))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
        Slider(
            value, { onChange((it * 2).roundToInt() / 2f) }, enabled = enabled, valueRange = -12f..12f, colors = colors,
            modifier = Modifier.weight(1f).semantics { contentDescription = label; stateDescription = valueText },
        )
        Text(valueText, style = MaterialTheme.typography.labelLarge, color = BlossomTheme.colors.onSurfaceVariant, modifier = Modifier.width(60.dp).padding(start = 8.dp))
    }
}
