package com.yash.upsc400v2.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/** About 40 pieces of confetti for 1.2 seconds each time [trigger] increases. */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    val t = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            t.snapTo(0f)
            t.animateTo(1f, tween(1200, easing = LinearEasing))
        }
    }
    val colors = remember {
        listOf(Color(0xFFFFD84D), Color(0xFF8EC5FF), Color(0xFFFF9EC4), Color(0xFF9BE3A7))
    }
    if (t.value < 1f) {
        Canvas(modifier.fillMaxSize()) {
            val p = t.value
            for (i in 0 until 40) {
                val ang = (i * 37 % 360) * Math.PI / 180.0
                val speed = 0.30f + (i % 7) * 0.07f
                val x = size.width * 0.5f + cos(ang).toFloat() * size.width * speed * p
                val y = size.height * 0.30f + sin(ang).toFloat() * size.height * 0.35f * speed * p + size.height * 0.55f * p * p
                drawRect(colors[i % 4], Offset(x, y), Size(16f, 16f))
            }
        }
    }
}
