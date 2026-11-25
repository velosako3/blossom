// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A scrollbar drawn over the edge of a scrolling list or page: always there when the content is longer than the view,
 * and draggable (or click the track to jump), so it works without a mouse wheel or a touchpad. Put it on the list's
 * own modifier: `LazyColumn(Modifier.scrollbar(state), state = state)`, `Column(Modifier.scrollbar(s).verticalScroll(s))`.
 */
fun Modifier.scrollbar(state: LazyListState, horizontal: Boolean = false): Modifier = scrollbar(horizontal, state) { LazyTarget(state) }

fun Modifier.scrollbar(state: LazyGridState): Modifier = scrollbar(false, state) { GridTarget(state) }

fun Modifier.scrollbar(state: ScrollState, horizontal: Boolean = false): Modifier = scrollbar(horizontal, state) { PlainTarget(state) }

/** Where the view is (0..1 of the way), how much of the content it shows, and how to jump. */
private interface ScrollTarget {
    val position: Float
    val shown: Float
    suspend fun jumpTo(fraction: Float)
}

private class PlainTarget(val s: ScrollState) : ScrollTarget {
    override val position get() = if (s.maxValue <= 0) 0f else s.value / s.maxValue.toFloat()
    override val shown get() = if (s.maxValue <= 0 || s.maxValue == Int.MAX_VALUE) 1f else s.viewportSize / (s.maxValue + s.viewportSize).toFloat()
    override suspend fun jumpTo(fraction: Float) { s.scrollTo((fraction * s.maxValue).toInt()) }
}

/** Lazy lists only know the items on screen; sizes are estimated from them (rows of different heights move the thumb a little). */
private class LazyTarget(val s: LazyListState) : ScrollTarget {
    private fun avg(): Float = s.layoutInfo.visibleItemsInfo.let { v -> if (v.isEmpty()) 1f else v.sumOf { it.size }.toFloat() / v.size }.coerceAtLeast(1f)
    private fun viewport(): Int = s.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }
    private fun pages(): Float = (s.layoutInfo.totalItemsCount - viewport() / avg()).coerceAtLeast(0f)
    override val position get() = if (pages() <= 0f) 0f else ((s.firstVisibleItemIndex + s.firstVisibleItemScrollOffset / avg()) / pages()).coerceIn(0f, 1f)
    override val shown get() = s.layoutInfo.totalItemsCount.let { n -> if (n == 0 || !(s.canScrollForward || s.canScrollBackward)) 1f else (viewport() / avg() / n).coerceIn(0f, 1f) }
    override suspend fun jumpTo(fraction: Float) {
        val exact = fraction * pages()
        s.scrollToItem(exact.toInt(), ((exact - exact.toInt()) * avg()).toInt())
    }
}

private class GridTarget(val s: LazyGridState) : ScrollTarget {
    private fun total() = s.layoutInfo.totalItemsCount
    private fun visible() = s.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    override val position get() = (total() - visible()).let { p -> if (p <= 0) 0f else (s.firstVisibleItemIndex / p.toFloat()).coerceIn(0f, 1f) }
    override val shown get() = if (total() == 0 || !(s.canScrollForward || s.canScrollBackward)) 1f else (visible() / total().toFloat()).coerceIn(0f, 1f)
    override suspend fun jumpTo(fraction: Float) = s.scrollToItem((fraction * (total() - visible()).coerceAtLeast(0)).toInt())
}

private fun Modifier.scrollbar(horizontal: Boolean, key: Any, target: () -> ScrollTarget): Modifier = composed {
    val t = remember(key) { target() }
    // Vertical bars end above the floating player/tab bars, which the page runs under.
    val endInset = if (horizontal) 0f else with(androidx.compose.ui.platform.LocalDensity.current) { io.github.velosako3.blossom.core.designsystem.glass.LocalBarsPadding.current.toPx() }
    val color = BlossomTheme.colors.onSurface
    var dragging by remember { mutableStateOf(false) }
    this
        .pointerInput(t, horizontal, endInset) {
            val grip = 20.dp.toPx() // the strip along the edge that grabs the pointer
            val minThumb = 32.dp.toPx()
            coroutineScope {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val across = if (horizontal) down.position.y else down.position.x
                    val crossSize = if (horizontal) size.height else size.width
                    if (t.shown >= 1f || across < crossSize - grip) return@awaitEachGesture
                    val length = ((if (horizontal) size.width else size.height) - endInset).coerceAtLeast(minThumb)
                    val thumb = (t.shown * length).coerceAtLeast(minThumb)
                    val travel = (length - thumb).coerceAtLeast(1f)
                    fun along(o: Offset) = if (horizontal) o.x else o.y
                    val thumbStart = t.position * travel
                    // Grabbing the thumb keeps it under the pointer; clicking the track centres the thumb there.
                    val grab = along(down.position).let { p -> if (p in thumbStart..thumbStart + thumb) p - thumbStart else thumb / 2 }
                    down.consume()
                    dragging = true
                    launch { t.jumpTo(((along(down.position) - grab) / travel).coerceIn(0f, 1f)) }
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) { change.consume(); break }
                        change.consume()
                        val f = ((along(change.position) - grab) / travel).coerceIn(0f, 1f)
                        launch { t.jumpTo(f) }
                    }
                    dragging = false
                }
            }
        }
        .drawWithContent {
            drawContent()
            val shown = t.shown
            if (shown >= 1f) return@drawWithContent
            val length = ((if (horizontal) size.width else size.height) - endInset).coerceAtLeast(32.dp.toPx())
            val thickness = (if (dragging) 8.dp else 5.dp).toPx()
            val inset = 3.dp.toPx()
            val thumb = (shown * length).coerceAtLeast(32.dp.toPx()).coerceAtMost(length)
            val start = t.position * (length - thumb)
            val radius = CornerRadius(thickness / 2)
            val alpha = if (dragging) 0.55f else 0.32f
            if (horizontal) {
                drawRoundRect(color.copy(alpha = alpha), Offset(start, size.height - thickness - inset), Size(thumb, thickness), radius)
            } else {
                drawRoundRect(color.copy(alpha = alpha), Offset(size.width - thickness - inset, start), Size(thickness, thumb), radius)
            }
        }
}

/** [androidx.compose.foundation.lazy.LazyColumn] with [scrollbar]. */
@androidx.compose.runtime.Composable
fun ScrollbarLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    verticalArrangement: androidx.compose.foundation.layout.Arrangement.Vertical = androidx.compose.foundation.layout.Arrangement.Top,
    horizontalAlignment: androidx.compose.ui.Alignment.Horizontal = androidx.compose.ui.Alignment.Start,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) = androidx.compose.foundation.lazy.LazyColumn(
    modifier.scrollbar(state), state, contentPadding, verticalArrangement = verticalArrangement, horizontalAlignment = horizontalAlignment, content = content,
)

/** [androidx.compose.foundation.lazy.LazyRow] with [scrollbar] along the bottom. */
@androidx.compose.runtime.Composable
fun ScrollbarLazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    horizontalArrangement: androidx.compose.foundation.layout.Arrangement.Horizontal = androidx.compose.foundation.layout.Arrangement.Start,
    verticalAlignment: androidx.compose.ui.Alignment.Vertical = androidx.compose.ui.Alignment.Top,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) = androidx.compose.foundation.lazy.LazyRow(
    modifier.scrollbar(state, horizontal = true), state, contentPadding, horizontalArrangement = horizontalArrangement, verticalAlignment = verticalAlignment, content = content,
)

/** `verticalScroll(rememberScrollState())` with [scrollbar]. */
fun Modifier.verticalScrollWithBar(): Modifier = composed {
    val s = androidx.compose.foundation.rememberScrollState()
    scrollbar(s).then(Modifier.verticalScroll(s))
}

/** `horizontalScroll(rememberScrollState())` with [scrollbar] along the bottom. */
fun Modifier.horizontalScrollWithBar(): Modifier = composed {
    val s = androidx.compose.foundation.rememberScrollState()
    scrollbar(s, horizontal = true).then(Modifier.horizontalScroll(s))
}
