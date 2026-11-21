// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTokens
import io.github.velosako3.blossom.feature.lyrics.wordProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.TimeSource

fun lyricLineTag(index: Int) = "lyric_line_$index"

private const val UNSUNG_ALPHA = 0.45f
private const val INACTIVE_ALPHA = 0.35f
private const val PAST_ALPHA = 0.25f

/**
 * One scroll step, shared by every line: the list jumps by [delta] at once and each line, offset back by the same
 * amount, springs into place [STAGGER_MS] later than the one above it. Lines read it without recomposing.
 */
internal class Cascade {
    var epoch by mutableIntStateOf(0)
        private set
    var delta = 0f
        private set
    var activeItem = 0
        private set
    private var mark = TimeSource.Monotonic.markNow()

    /** An instant jump (seek, first show): lines drop any offset still in flight. */
    var jump = false
        private set

    fun start(delta: Float, activeItem: Int) {
        this.delta = delta; this.activeItem = activeItem; jump = false; mark = TimeSource.Monotonic.markNow(); epoch++
    }

    fun jump() { delta = 0f; jump = true; mark = TimeSource.Monotonic.markNow(); epoch++ }

    /** Lines composed long after a step (the user scrolled to them) don't replay it. */
    val fresh: Boolean get() = mark.elapsedNow().inWholeMilliseconds < 400
}

internal const val STAGGER_MS = 30L

/** How a line looks this frame. [distance] counts lines from the active one (0 = active). */
internal data class LineLook(val active: Boolean, val past: Boolean, val distance: Int)

@Composable
internal fun LyricLineView(
    item: LyricsItem.Line,
    itemIndex: Int,
    look: LineLook,
    wordSynced: Boolean,
    position: () -> Long,
    style: TextStyle,
    color: Color,
    glow: Color,
    blurOn: Boolean,
    motion: Boolean,
    cascade: Cascade,
    onSeek: (Long) -> Unit,
) {
    val line = item.line
    val scale by animateFloatAsState(
        if (look.active && motion) 1.04f else 1f,
        spring(BlossomTokens.springDampingRatio, BlossomTokens.springStiffness),
    )
    val alpha by animateFloatAsState(
        when {
            look.active -> 1f
            look.past -> PAST_ALPHA
            else -> INACTIVE_ALPHA
        },
        if (motion) tween(200) else tween(0),
    )
    val offset = remember { Animatable(0f) }
    if (motion) LaunchedEffect(cascade) {
        var first = true
        androidx.compose.runtime.snapshotFlow { cascade.epoch }.collectLatest {
            if (first) { first = false; if (!cascade.fresh) return@collectLatest }
            if (cascade.jump) { offset.snapTo(0f); return@collectLatest }
            offset.snapTo(offset.value + cascade.delta)
            delay(((itemIndex - cascade.activeItem).coerceAtLeast(0)) * STAGGER_MS)
            offset.animateTo(0f, spring(0.85f, 220f))
        }
    }

    val sweep = look.active && wordSynced && line.words.isNotEmpty()
    val currentWord by remember(line, sweep) {
        derivedStateOf { if (!sweep) -1 else line.words.indexOfLast { it.startMs <= position() } }
    }
    val text = remember(line, currentWord, motion) {
        buildAnnotatedString {
            val r = item.wordRanges.getOrNull(currentWord)
            if (r == null || !motion || r.isEmpty()) append(line.text)
            else {
                append(line.text.substring(0, r.first))
                withStyle(SpanStyle(shadow = Shadow(glow.copy(alpha = 0.35f), Offset.Zero, 12f * 2))) { append(line.text.substring(r.first, r.last + 1)) }
                append(line.text.substring(r.last + 1))
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val blurRadius = if (blurOn && motion && !look.active) (minOf(look.distance, 4) * 1.5f).dp else 0.dp

    Text(
        text = text,
        style = style,
        color = color,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(lyricLineTag(item.index))
            .clickable(remember { MutableInteractionSource() }, indication = null) { onSeek(line.startMs) }
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
                translationY = offset.value
                this.alpha = alpha
                if (sweep) compositingStrategy = CompositingStrategy.Offscreen
            }
            .then(if (sweep) Modifier.drawWithContent { drawContent(); layout?.let { sweepMask(it, item, position()) } } else Modifier),
    )
}

/** Sung text keeps full alpha, unsung drops to 45%, with an 8 dp soft edge at the singing point. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.sweepMask(l: TextLayoutResult, item: LyricsItem.Line, pos: Long) {
    val words = item.line.words
    val len = l.layoutInput.text.length
    // Characters sung so far (fractional inside the current word).
    var sung = 0f
    for (i in words.indices) {
        val r = item.wordRanges[i]
        if (pos >= words[i].endMs) sung = (r.last + 1).toFloat()
        else if (pos >= words[i].startMs) { sung = r.first + (r.last + 1 - r.first) * wordProgress(words[i], pos); break }
        else break
    }
    val idx = sung.toInt().coerceIn(0, len)
    val row: Int
    val x: Float
    if (idx >= len) { row = l.lineCount; x = 0f } else {
        row = l.getLineForOffset(idx)
        val box = l.getBoundingBox(idx)
        x = box.left + (box.right - box.left) * (sung - idx)
    }
    val solid = Color.Black
    val dim = Color.Black.copy(alpha = UNSUNG_ALPHA)
    val edge = 4.dp.toPx()
    for (r in 0 until l.lineCount) {
        val top = l.getLineTop(r)
        val h = l.getLineBottom(r) - top
        val o = Offset(0f, top)
        val s = Size(size.width, h)
        when {
            r < row -> drawRect(solid, o, s, blendMode = BlendMode.DstIn)
            r > row -> drawRect(dim, o, s, blendMode = BlendMode.DstIn)
            // ponytail: one gradient brush per frame on the singing row; cache it if profiling shows GC churn.
            else -> drawRect(Brush.horizontalGradient(listOf(solid, dim), x - edge, x + edge), o, s, blendMode = BlendMode.DstIn)
        }
    }
}
