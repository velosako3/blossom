// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A deliberately busy backdrop — stripes, discs and text — so blur, refraction and the rim are easy to judge. */
@Composable
fun BusyBackground(modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF14121C))
            val stripe = 36f
            var x = -size.height
            var i = 0
            val hues = listOf(Color(0xFFE53935), Color(0xFFFFB300), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA))
            while (x < size.width) {
                val c = hues[i % hues.size].copy(alpha = 0.55f)
                drawLine(c, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = stripe / 2)
                x += stripe; i++
            }
            for (k in 0 until 9) {
                drawCircle(hues[k % hues.size], radius = 70f + 18f * (k % 3), center = Offset(90f + k * 120f, 160f + 220f * (k % 3)))
            }
            drawRect(Color.White, Offset(size.width * 0.62f, size.height * 0.12f), Size(28f, size.height * 0.76f))
        }
        Column(Modifier.padding(32.dp)) {
            repeat(9) {
                Text(
                    "blossom liquid glass ✦ 0123456789 the quick brown fox",
                    color = if (it % 2 == 0) Color.White else Color.Black,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
