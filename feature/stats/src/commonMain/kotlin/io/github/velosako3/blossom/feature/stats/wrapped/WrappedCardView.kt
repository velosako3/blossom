// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.wrapped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.components.Artwork
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTokens
import io.github.velosako3.blossom.feature.stats.Period
import io.github.velosako3.blossom.feature.stats.resources.*
import io.github.velosako3.blossom.feature.stats.ui.HourOfDayChart
import io.github.velosako3.blossom.feature.stats.ui.label
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One Wrapped card, full bleed at 9:16 over the artwork-tuned background. Everything scales with the card's width, so
 * the same card renders on a phone, on desktop and at 1080×1920 for export.
 */
@Composable
fun WrappedCardView(card: WrappedCard, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.aspectRatio(9f / 16f)) {
        // 360 dp wide is the reference design; everything is laid out in "card units".
        val u = maxWidth / 360f
        val palette = BlossomTheme.palette
        BlossomBackground(palette, Modifier.fillMaxSize())
        // A soft vignette so white type stays readable on any artwork colour.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.45f)))))
        Column(Modifier.fillMaxSize().padding(horizontal = 28 * u, vertical = 36 * u)) {
            Content(card, u)
        }
        Text(
            "blossom", color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Bold, fontSize = (15 * u.value).sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 28 * u, bottom = 22 * u),
        )
    }
}

private operator fun Int.times(u: Dp): Dp = u * this.toFloat()

@Composable
private fun Content(card: WrappedCard, u: Dp) {
    val fs = { n: Int -> (n * u.value).sp }
    when (card) {
        is WrappedCard.Intro -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            PopIn { Art(card.artworkUrl, 220 * u, glow = true) }
            Spacer(Modifier.height(36 * u))
            Big(periodTitle(card.period), fs(44), lineHeight = fs(46))
            Text(label(card.period), color = Color.White.copy(alpha = 0.8f), fontSize = fs(18), modifier = Modifier.padding(top = 10 * u))
        }
        is WrappedCard.TotalMinutes -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Small(stringResource(Res.string.wr_listened_for), fs(22))
            CountUp(card.minutes, fs(96))
            Big(stringResource(Res.string.wr_minutes), fs(40))
            Small(stringResource(Res.string.wr_across_plays, io.github.velosako3.blossom.core.platform.groupedNumber(card.plays.toLong())), fs(18), Modifier.padding(top = 18 * u))
        }
        is WrappedCard.TopSong -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Small(stringResource(Res.string.wr_top_song), fs(22))
            Spacer(Modifier.height(20 * u))
            PopIn { Art(card.song.item.artworkUrl, 260 * u, glow = true) }
            Spacer(Modifier.height(24 * u))
            Big(card.song.item.title, fs(34), maxLines = 2)
            Small(card.song.item.artistNames, fs(20))
            Small(stringResource(Res.string.wr_plays, card.song.plays.toString()), fs(16), Modifier.padding(top = 10 * u))
        }
        is WrappedCard.TopSongs -> Ranking(stringResource(Res.string.wr_top_songs), card.songs.map { Triple(it.item.title, it.item.artistNames, it.item.artworkUrl) }, u, artwork = true)
        is WrappedCard.TopArtist -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Small(stringResource(Res.string.wr_top_artist), fs(22))
            Spacer(Modifier.height(24 * u))
            PopIn { Art(card.artworkUrl, 240 * u, glow = true, round = true) }
            Spacer(Modifier.height(24 * u))
            Big(card.artist.item.name, fs(44), maxLines = 2, lineHeight = fs(46))
            Small(stringResource(Res.string.wr_plays, card.artist.plays.toString()), fs(18), Modifier.padding(top = 8 * u))
        }
        is WrappedCard.TopArtists -> Ranking(stringResource(Res.string.wr_top_artists), card.artists.map { Triple(it.item.name, stringResource(Res.string.wr_plays, it.plays.toString()), null) }, u)
        is WrappedCard.TopAlbum -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Small(stringResource(Res.string.wr_top_album), fs(22))
            Spacer(Modifier.height(20 * u))
            PopIn { Art(card.artworkUrl, 260 * u, glow = true) }
            Spacer(Modifier.height(24 * u))
            Big(card.album.item.title, fs(36), maxLines = 2)
            Small(stringResource(Res.string.wr_plays, card.album.plays.toString()), fs(18), Modifier.padding(top = 8 * u))
        }
        is WrappedCard.ListeningClock -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Big(stringResource(when (card.peakHour) {
                in 5..10 -> Res.string.wr_clock_morning
                in 11..16 -> Res.string.wr_clock_afternoon
                in 17..21 -> Res.string.wr_clock_evening
                else -> Res.string.wr_clock_night
            }), fs(40), lineHeight = fs(42))
            Spacer(Modifier.height(32 * u))
            HourOfDayChart(card.byHourMs.toLongArray(), "", Modifier.fillMaxWidth())
            Small(stringResource(Res.string.wr_peak, card.peakHour.toString().padStart(2, '0') + ":00"), fs(18), Modifier.padding(top = 20 * u))
        }
        is WrappedCard.NewArtists -> NewArtistsCard(card, u)
        is WrappedCard.Streak -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            PopIn { Icon(Icons.Rounded.LocalFireDepartment, null, tint = BlossomTheme.palette.vibrant, modifier = Modifier.size(96 * u)) }
            CountUp(card.days.toLong(), fs(120))
            Big(stringResource(Res.string.wr_streak), fs(36))
            Small(stringResource(Res.string.wr_streak_sub), fs(18), Modifier.padding(top = 12 * u))
        }
        is WrappedCard.Comparison -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            val up = card.percent >= 0
            PopIn { Icon(if (up) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown, null, tint = BlossomTheme.palette.vibrant, modifier = Modifier.size(80 * u)) }
            Big((if (up) "+" else "−") + "${abs(card.percent).roundToInt()}%", fs(104))
            Big(stringResource(if (up) Res.string.wr_more else Res.string.wr_less), fs(28), lineHeight = fs(32))
        }
        is WrappedCard.Summary -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18 * u, Alignment.CenterVertically)) {
            PopIn { Art(card.artworkUrl, 180 * u, glow = true) }
            Big(stringResource(Res.string.wr_summary, periodWord(card.period)), fs(32))
            Stat(stringResource(Res.string.wr_summary_minutes), io.github.velosako3.blossom.core.platform.groupedNumber(card.minutes.toLong()), u)
            card.topSong?.let { Stat(stringResource(Res.string.wr_summary_song), it.title, u) }
            card.topArtist?.let { Stat(stringResource(Res.string.wr_summary_artist), it.name, u) }
        }
        WrappedCard.NotEnoughData -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Big(stringResource(Res.string.wr_not_enough), fs(40), lineHeight = fs(44))
            Small(stringResource(Res.string.wr_not_enough_sub), fs(18), Modifier.padding(top = 16 * u))
        }
    }
}

@Composable
private fun Big(text: String, size: TextUnit, maxLines: Int = 3, lineHeight: TextUnit = TextUnit.Unspecified) = Text(
    text, color = Color.White, fontSize = size, fontWeight = FontWeight.Black, lineHeight = lineHeight, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    style = TextStyle(letterSpacing = (-0.5).sp),
)

@Composable
private fun Small(text: String, size: TextUnit, modifier: Modifier = Modifier) =
    Text(text, color = Color.White.copy(alpha = 0.82f), fontSize = size, fontWeight = FontWeight.SemiBold, modifier = modifier, maxLines = 2, overflow = TextOverflow.Ellipsis)

/** The number counts up from zero (instant under reduce motion). */
@Composable
private fun CountUp(value: Long, size: TextUnit) {
    val reduce = BlossomTheme.reduceMotion
    var target by remember { mutableStateOf(if (reduce) value.toInt() else 0) }
    LaunchedEffect(value) { target = value.toInt() }
    val shown by animateIntAsState(target, if (reduce) tween(0) else tween(1_200))
    Text(io.github.velosako3.blossom.core.platform.groupedNumber(shown.toLong()), color = BlossomTheme.palette.vibrant.copy(alpha = 1f).let { lerpToWhite(it) }, fontSize = size, fontWeight = FontWeight.Black, maxLines = 1)
}

/** The palette's vibrant colour, lifted so it pops on the dark card. */
private fun lerpToWhite(c: Color) = Color(c.red + (1 - c.red) * 0.35f, c.green + (1 - c.green) * 0.35f, c.blue + (1 - c.blue) * 0.35f)

/** Artwork springs in (scale and fade) on the token spring. */
@Composable
private fun PopIn(content: @Composable () -> Unit) {
    val reduce = BlossomTheme.reduceMotion
    val scale = remember { Animatable(if (reduce) 1f else 0.82f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(BlossomTokens.springDampingRatio * 0.7f, BlossomTokens.springStiffness * 0.6f)) }
    Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = ((scale.value - 0.82f) / 0.18f).coerceIn(0f, 1f) }) { content() }
}

@Composable
private fun Art(url: String?, size: Dp, glow: Boolean = false, round: Boolean = false) {
    val shape = if (round) CircleShape else RoundedCornerShape(size / 14)
    Artwork(
        url, null,
        Modifier.size(size).then(if (glow) Modifier.shadow(size / 9, shape, ambientColor = BlossomTheme.palette.vibrant, spotColor = BlossomTheme.palette.vibrant) else Modifier),
        shape = shape, iconSize = size / 4,
    )
}

@Composable
private fun Ranking(title: String, rows: List<Triple<String, String, String?>>, u: Dp, artwork: Boolean = false) {
    val fs = { n: Int -> (n * u.value).sp }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Big(title, fs(38))
        Spacer(Modifier.height(28 * u))
        rows.take(5).forEachIndexed { i, (name, sub, art) ->
            Row(Modifier.padding(vertical = 9 * u), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = if (i == 0) lerpToWhite(BlossomTheme.palette.vibrant) else Color.White.copy(alpha = 0.6f), fontSize = fs(34), fontWeight = FontWeight.Black, modifier = Modifier.width(44 * u))
                if (artwork) { Art(art, 52 * u); Spacer(Modifier.width(14 * u)) }
                Column {
                    Text(name, color = Color.White, fontSize = fs(20), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sub, color = Color.White.copy(alpha = 0.7f), fontSize = fs(14), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewArtistsCard(card: WrappedCard.NewArtists, u: Dp) {
    val fs = { n: Int -> (n * u.value).sp }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        CountUp(card.artists.size.toLong(), fs(120))
        Big(stringResource(Res.string.wr_new_artists), fs(34), lineHeight = fs(38))
        Spacer(Modifier.height(24 * u))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8 * u), verticalArrangement = Arrangement.spacedBy(8 * u)) {
            card.artists.take(8).forEach { a ->
                Text(
                    a.name, color = Color.White, fontSize = fs(15), fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 14 * u, vertical = 8 * u),
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, u: Dp) {
    Column {
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.65f), fontSize = (13 * u.value).sp, fontWeight = FontWeight.Bold, style = TextStyle(letterSpacing = 1.5.sp))
        Text(value, color = Color.White, fontSize = (24 * u.value).sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun periodTitle(p: Period): String = when (p) {
    is Period.Week -> stringResource(Res.string.wr_week)
    is Period.Month -> stringResource(Res.string.wr_month, io.github.velosako3.blossom.core.platform.monthName(p.month, full = true))
    is Period.Year -> stringResource(Res.string.wr_year, p.year.toString())
}

@Composable
private fun periodWord(p: Period): String = when (p) {
    is Period.Week -> stringResource(Res.string.wr_word_week)
    is Period.Month -> io.github.velosako3.blossom.core.platform.monthName(p.month, full = true)
    is Period.Year -> p.year.toString()
}
