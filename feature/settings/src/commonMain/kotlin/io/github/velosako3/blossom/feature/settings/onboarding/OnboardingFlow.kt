// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.settings.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassChip
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.designsystem.theme.ThemeMode
import io.github.velosako3.blossom.feature.settings.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Where an optional onboarding step leads; the app opens it after the welcome closes. */
enum class OnboardingExtra { LOCAL_FILES, SYNC, IMPORT }

/**
 * First run only, skippable at every step: a welcome glass card, the theme, then optional extras (music folders,
 * sync or pairing, importing a playlist). [onFinish] gets the extra the user picked, if any.
 */
@Composable
fun OnboardingFlow(theme: ThemeMode, onTheme: (ThemeMode) -> Unit, onFinish: (OnboardingExtra?) -> Unit, modifier: Modifier = Modifier) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val muted = BlossomTheme.colors.onSurfaceVariant
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(stringResource(Res.string.onb_step, step + 1, 3), color = muted, style = MaterialTheme.typography.labelLarge)
                AnimatedContent(step) { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (s) {
                            0 -> {
                                Title(Res.string.onb_welcome_title)
                                Text(stringResource(Res.string.onb_welcome_body), style = MaterialTheme.typography.bodyLarge)
                            }
                            1 -> {
                                Title(Res.string.onb_theme_title)
                                Text(stringResource(Res.string.onb_theme_body), color = muted, style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(ThemeMode.SYSTEM to Res.string.settings_theme_system, ThemeMode.LIGHT to Res.string.settings_theme_light, ThemeMode.DARK to Res.string.settings_theme_dark)
                                        .forEach { (m, label) -> GlassChip(theme == m, { onTheme(m) }, stringResource(label)) }
                                }
                            }
                            else -> {
                                Title(Res.string.onb_extras_title)
                                Text(stringResource(Res.string.onb_extras_body), color = muted, style = MaterialTheme.typography.bodyMedium)
                                Extra(Res.string.onb_local) { onFinish(OnboardingExtra.LOCAL_FILES) }
                                Extra(Res.string.onb_sync) { onFinish(OnboardingExtra.SYNC) }
                                Extra(Res.string.onb_import) { onFinish(OnboardingExtra.IMPORT) }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (step < 2) GlassButton({ onFinish(null) }) { Text(stringResource(Res.string.onb_skip)) }
                    GlassButton({ if (step < 2) step++ else onFinish(null) }, prominent = true) {
                        Text(stringResource(when (step) { 0 -> Res.string.onb_start; 1 -> Res.string.onb_next; else -> Res.string.onb_done }))
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: StringResource) =
    Text(stringResource(text), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })

@Composable
private fun Extra(text: StringResource, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(text), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = BlossomTheme.colors.onSurfaceVariant)
    }
}
