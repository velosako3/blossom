// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.velosako3.blossom.core.designsystem.components.GlassChip
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.feature.lyrics.Lyrics
import io.github.velosako3.blossom.feature.lyrics.resources.Res
import io.github.velosako3.blossom.feature.lyrics.resources.lyrics_back_to_current
import io.github.velosako3.blossom.feature.lyrics.resources.lyrics_none
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Synced lyrics that follow [positionMs]; plain lyrics as scrollable text; a quiet message when there are none.
 * [color] is the text colour: the artwork's on-background colour over the background, the surface's inside glass panels.
 */
@Composable
fun LyricsView(
    lyrics: Lyrics?,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = BlossomTheme.palette.onBackground,
) {
    when (lyrics) {
        null -> NoLyrics(color, modifier)
        is Lyrics.Plain -> Text(
            lyrics.text,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        )
        is Lyrics.LineSynced -> SyncedLyrics(lyrics.lines, false, positionMs, onSeek, color, modifier)
        is Lyrics.WordSynced -> SyncedLyrics(lyrics.lines, true, positionMs, onSeek, color, modifier)
    }
}

@Composable
private fun NoLyrics(color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val c = color.copy(alpha = 0.6f)
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.MusicNote, null, tint = c, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.lyrics_none), color = c, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<io.github.velosako3.blossom.feature.lyrics.LyricLine>,
    wordSynced: Boolean,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
) {
    val items = remember(lines) { lyricsItems(lines) }
    val pos = rememberUpdatedState(positionMs)
    val position = remember { { pos.value } }
    val active = activeItemIndex(items, positionMs)
    val motion = !BlossomTheme.reduceMotion
    // Blur only at FULL: per-line blur is the costliest part of a frame, so the frame monitor's first step down drops it.
    val blurOn = BlossomTheme.glassQuality == GlassQuality.FULL
    val palette = BlossomTheme.palette
    val controller = remember { LyricsScrollController(Clock.System) }
    var userScrolls by remember { mutableIntStateOf(0) }
    var paused by remember { androidx.compose.runtime.mutableStateOf(false) }
    val list = rememberLazyListState()
    val cascade = remember { Cascade() }
    val userScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                controller.onUserScroll(); paused = true; userScrolls++
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(userScrolls) {
        if (userScrolls == 0) return@LaunchedEffect
        delay(LyricsScrollController.PAUSE.inWholeMilliseconds)
        if (controller.shouldAutoScroll()) paused = false
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val expanded = maxWidth >= 600.dp
        val style = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = if (expanded) 40.sp else 30.sp,
            lineHeight = 1.25.em,
        )
        val anchorPx = with(LocalDensity.current) { (maxHeight * 0.35f).toPx() }

        // Item 0 is a spacer of 35% of the height, so line k sits at the anchor when scrolled to the top of item k+1.
        LaunchedEffect(active, paused, anchorPx) {
            if (paused || active < 0) return@LaunchedEffect
            // Effects can start inside the composition's apply phase; scrolling forces a synchronous remeasure, which
            // must not subcompose list items until that phase is over.
            withFrameNanos { }
            scrollToActive(list, active + 1, anchorPx, motion, cascade, active)
        }

        LazyColumn(Modifier.fillMaxSize().nestedScroll(userScroll), state = list) {
            item { Spacer(Modifier.height(maxHeight * 0.35f)) }
            itemsIndexed(items, key = { i, _ -> i }) { i, item ->
                val distance = if (active < 0) i + 1 else kotlin.math.abs(i - active)
                when (item) {
                    is LyricsItem.Line -> LyricLineView(
                        item, i, LineLook(active = i == active, past = active >= 0 && i < active, distance = distance), wordSynced,
                        position, style, color, palette.vibrant, blurOn, motion, cascade, onSeek,
                    )
                    is LyricsItem.Gap -> Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) {
                        InstrumentalDots(
                            active = i == active,
                            progress = { ((position() - item.startMs).toFloat() / (item.endMs - item.startMs)).coerceIn(0f, 1f) },
                            color = color,
                            breathe = motion,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(maxHeight * 0.65f)) }
        }

        if (paused) {
            GlassChip(
                selected = false,
                onClick = { controller.resume(); paused = false },
                label = stringResource(Res.string.lyrics_back_to_current),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}

private suspend fun scrollToActive(list: LazyListState, target: Int, anchorPx: Float, motion: Boolean, cascade: Cascade, activeItem: Int) {
    val visible = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
    if (visible == null || !motion) {
        list.scrollToItem(target)
        list.scrollBy(-anchorPx)
        cascade.jump()
        return
    }
    val delta = visible.offset - anchorPx
    if (kotlin.math.abs(delta) < 0.5f) return
    val consumed = list.scrollBy(delta)
    cascade.start(consumed, activeItem)
}
