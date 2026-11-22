// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.wrapped

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.feature.stats.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Wrapped as a story: segmented progress at the top, each card for 6 s. Tap right for the next card, left for the
 * previous, hold to pause; on desktop ← → and Space. [muted]/[onToggleMute] control the background song.
 */
@Composable
fun WrappedScreen(
    cards: List<WrappedCard>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    onToggleMute: (() -> Unit)? = null,
) {
    if (cards.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val reduce = BlossomTheme.reduceMotion
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val layer = rememberGraphicsLayer()

    fun go(to: Int) { index = to.coerceIn(0, cards.lastIndex) }

    // 6 s per card, continuing from where a pause left it; the last card stays full. The reset happens here, keyed on
    // the card, so the timer never reads the previous card's progress.
    LaunchedEffect(index) {
        progress.snapTo(0f)
        snapshotFlow { paused }.collectLatest { isPaused ->
            if (isPaused) return@collectLatest
            val left = ((1f - progress.value) * CARD_MS).toInt()
            progress.animateTo(1f, tween(left, easing = LinearEasing))
            if (index < cards.lastIndex) go(index + 1)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    DisposableEffect(layer) {
        WrappedExporter.captureWith { layer.toImageBitmap() }
        onDispose { WrappedExporter.captureWith(null) }
    }

    Box(
        modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus).focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { go(index + 1); true }
                    Key.DirectionLeft -> { go(index - 1); true }
                    Key.Spacebar -> { paused = !paused; true }
                    Key.Escape -> { onClose(); true }
                    else -> false
                }
            }
            .pointerInput(cards.size) {
                detectTapGestures(
                    onPress = { paused = true; tryAwaitRelease(); paused = false },
                    onTap = { offset -> if (offset.x < size.width / 3f) go(index - 1) else go(index + 1) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = index,
            transitionSpec = { if (reduce) fadeIn(tween(0)) togetherWith fadeOut(tween(0)) else fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
        ) { i ->
            WrappedCardView(
                cards[i],
                Modifier.fillMaxHeight().widthIn(max = 600.dp).clip(RoundedCornerShape(16.dp))
                    // Only the card being shown is captured (during the crossfade both are drawn).
                    .drawWithContent { if (i == index) { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) } else drawContent() },
            )
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().widthIn(max = 600.dp).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cards.indices.forEach { i ->
                    val fill = when { i < index -> 1f; i == index -> progress.value; else -> 0f }
                    Box(
                        Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f))
                            .testTag(STORY_SEGMENT_TAG)
                            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fill, 0f..1f) },
                    ) {
                        Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(Color.White))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                if (onToggleMute != null) GlassIconButton(onToggleMute, if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    stringResource(if (muted) Res.string.wr_unmute else Res.string.wr_mute), glass = false)
                GlassIconButton({ scope.launch { runCatching { WrappedExporter.share(WrappedExporter.exportPng(cards[index])) } } },
                    Icons.Rounded.IosShare, stringResource(Res.string.wr_share), glass = false)
                GlassIconButton(onClose, Icons.Rounded.Close, stringResource(Res.string.wr_close), glass = false)
            }
        }
    }
}

private const val CARD_MS = 6_000

/** Test tag of each story progress segment (its progress is in the semantics). */
const val STORY_SEGMENT_TAG = "wrapped_segment"
