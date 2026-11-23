// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassChip
import io.github.velosako3.blossom.core.designsystem.components.GlassDialog
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.components.GlassListItem
import io.github.velosako3.blossom.core.designsystem.components.GlassNavBar
import io.github.velosako3.blossom.core.designsystem.components.GlassNavItem
import io.github.velosako3.blossom.core.designsystem.components.GlassSheet
import io.github.velosako3.blossom.core.designsystem.components.GlassSidebar
import io.github.velosako3.blossom.core.designsystem.components.GlassSidebarItem
import io.github.velosako3.blossom.core.designsystem.components.GlassSlider
import io.github.velosako3.blossom.core.designsystem.components.GlassTextField
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import kotlin.test.Test

const val SNAPSHOTS = "src/desktopTest/snapshots"

@OptIn(ExperimentalTestApi::class)
fun snapshot(
    name: String,
    width: Int = 1080,
    height: Int = 720,
    dark: Boolean = true,
    quality: GlassQuality = GlassQuality.FULL,
    backdrop: @Composable (Modifier) -> Unit = { BusyBackground(it) },
    content: @Composable () -> Unit,
) = runDesktopComposeUiTest(width, height) {
    setContent {
        BlossomTheme(darkTheme = dark, palette = ArtworkPalette.Default, glassQuality = quality, reduceMotion = true) {
            Box(Modifier.fillMaxSize()) {
                backdrop(Modifier.fillMaxSize().glassBackdropSource())
                content()
            }
        }
    }
    waitForIdle()
    onRoot().captureRoboImage("$SNAPSHOTS/$name.png")
}

class GlassSnapshotTest {
    @Composable
    private fun SamplePanel() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.size(620.dp, 360.dp)) {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Now playing", style = MaterialTheme.typography.labelLarge, color = BlossomTheme.colors.onSurfaceVariant)
                    Text("Liquid glass", style = MaterialTheme.typography.displayMedium)
                    Text(
                        "Frosted, softly refracting glass with a lit rim over whatever sits behind it. Text stays readable on any backdrop.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(onClick = {}, prominent = true) { Icon(Icons.Rounded.PlayArrow, null); Text("Play") }
                        GlassButton(onClick = {}) { Text("Shuffle") }
                        GlassIconButton(onClick = {}, icon = Icons.Rounded.Favorite, contentDescription = "Like")
                    }
                }
            }
        }
    }

    @Test fun glassSurface_full_dark() = snapshot("glassSurface_full_dark", quality = GlassQuality.FULL) { SamplePanel() }
    @Test fun glassSurface_reduced_dark() = snapshot("glassSurface_reduced_dark", quality = GlassQuality.REDUCED) { SamplePanel() }
    @Test fun glassSurface_off_dark() = snapshot("glassSurface_off_dark", quality = GlassQuality.OFF) { SamplePanel() }
    @Test fun glassSurface_full_light() = snapshot("glassSurface_full_light", dark = false, quality = GlassQuality.FULL) { SamplePanel() }

    @Test fun components_gallery_dark() = snapshot(
        "components_gallery_dark",
        width = 1440,
        height = 900,
        backdrop = { BlossomBackground(ArtworkPalette.Default, it) },
    ) {
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassSidebar {
                Text("blossom", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(12.dp))
                GlassSidebarItem("Home", selected = true, onClick = {}, icon = Icons.Rounded.Home)
                GlassSidebarItem("Search", selected = false, onClick = {}, icon = Icons.Rounded.Search)
                GlassSidebarItem("Library", selected = false, onClick = {}, icon = Icons.Rounded.LibraryMusic)
                GlassSidebarItem("Stats", selected = false, onClick = {}, icon = Icons.Rounded.QueryStats)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassTextField("", {}, placeholder = "Search songs, albums, artists", leadingIcon = Icons.Rounded.Search)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(true, {}, "Songs")
                    GlassChip(false, {}, "Albums")
                    GlassChip(false, {}, "Artists")
                    GlassChip(false, {}, "Playlists")
                }
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        GlassListItem("Pink + White", supporting = "Frank Ocean · Blonde",
                            leading = { Icon(Icons.Rounded.MusicNote, null) }, trailing = { Icon(Icons.Rounded.MoreHoriz, "More") }, onClick = {})
                        GlassListItem("Unavailable track", supporting = "unavailable", dimmed = true,
                            leading = { Icon(Icons.Rounded.MusicNote, null) })
                    }
                }
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassSlider(0.42f, {}, contentDescription = "Seek")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassIconButton({}, Icons.Rounded.SkipPrevious, "Previous")
                            GlassIconButton({}, Icons.Rounded.PlayArrow, "Play", size = 56.dp, iconSize = 30.dp)
                            GlassIconButton({}, Icons.Rounded.SkipNext, "Next")
                            GlassButton({}) { Text("Queue") }
                            GlassButton({}, prominent = true) { Text("Save offline") }
                        }
                    }
                }
                GlassNavBar(
                    listOf(
                        GlassNavItem("Home", Icons.Rounded.Home), GlassNavItem("Search", Icons.Rounded.Search),
                        GlassNavItem("Library", Icons.Rounded.LibraryMusic), GlassNavItem("Stats", Icons.Rounded.QueryStats),
                    ),
                    selectedIndex = 0, onSelect = {}, modifier = Modifier.width(420.dp),
                )
            }
            Box(Modifier.width(380.dp).fillMaxSize()) {
                GlassDialog(onDismissRequest = {}, title = "Delete playlist?", text = "This removes it from every synced device.",
                    confirmButton = { GlassButton({}, prominent = true) { Text("Delete") } },
                    dismissButton = { GlassButton({}) { Text("Cancel") } })
                GlassSheet(visible = true, onDismissRequest = {}) {
                    GlassListItem("Add to playlist", onClick = {})
                    GlassListItem("Save offline", onClick = {})
                }
            }
        }
    }
}
