// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.composeApp.resources.Res
import io.github.velosako3.blossom.composeApp.resources.*
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTokens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun Destination.titleRes(): StringResource = when (this) {
    Destination.Home -> Res.string.nav_home
    Destination.Search -> Res.string.nav_search
    Destination.Library -> Res.string.nav_library
    Destination.Stats -> Res.string.nav_stats
    Destination.Settings -> Res.string.nav_settings
    is Destination.Album -> Res.string.dest_album
    is Destination.Artist -> Res.string.dest_artist
    is Destination.Playlist -> Res.string.dest_playlist
    is Destination.Import -> Res.string.dest_import
    Destination.NowPlaying -> Res.string.dest_now_playing
    is Destination.Wrapped -> Res.string.dest_wrapped
    Destination.Liked -> Res.string.dest_liked
    Destination.LocalFiles -> Res.string.dest_local_files
}

/**
 * Renders the current destination. Screens arrive in later milestones; until then each shows a titled placeholder.
 * [showSettingsAction] puts the Settings button in the Library top bar (compact layout has no Settings tab).
 */
@Composable
fun BlossomNavHost(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    showSettingsAction: Boolean = false,
    screen: @Composable (Destination) -> Boolean = { false },
) {
    BackHandler(enabled = navigator.canGoBack) { navigator.back() }
    val reduce = BlossomTheme.reduceMotion
    AnimatedContent(
        navigator.current,
        modifier,
        transitionSpec = {
            if (reduce) fadeIn(snap()) togetherWith fadeOut(snap())
            else fadeIn(tween(BlossomTokens.motionStandard)) togetherWith fadeOut(tween(BlossomTokens.motionFast))
        },
    ) { destination ->
        Box(Modifier.fillMaxSize()) {
            if (!screen(destination)) {
                PlaceholderScreen(destination, navigator, showSettingsAction && destination == Destination.Library)
            }
        }
    }
}

@Composable
fun ScreenTopBar(title: String, navigator: Navigator, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (navigator.canGoBack) {
            GlassIconButton({ navigator.back() }, Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.nav_back))
        }
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun PlaceholderScreen(destination: Destination, navigator: Navigator, settingsAction: Boolean) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(destination.titleRes()), navigator) {
            if (settingsAction) {
                GlassIconButton({ navigator.navigate(Destination.Settings) }, Icons.Rounded.Settings, stringResource(Res.string.nav_settings))
            }
        }
        Spacer(Modifier.padding(8.dp))
        Text(
            stringResource(Res.string.placeholder_coming),
            style = MaterialTheme.typography.bodyLarge,
            color = BlossomTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}
