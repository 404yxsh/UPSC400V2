package com.yash.upsc400v2.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Mood { IDLE, FOCUS, CHEER, SLEEP }

/**
 * Round yellow creature with a bolt tail, drawn on a canvas. If the project contains
 * drawable resources mascot_idle / mascot_focus / mascot_cheer / mascot_sleep they are used instead.
 */
@Composable
fun Mascot(mood: Mood, size: Dp = 72.dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val ink = LocalPal.current.ink
    val name = when (mood) {
        Mood.IDLE -> "mascot_idle"
        Mood.FOCUS -> "mascot_focus"
        Mood.CHEER -> "mascot_cheer"
        Mood.SLEEP -> "mascot_sleep"
    }
    val resId = remember(name) { ctx.resources.getIdentifier(name, "drawable", ctx.packageName) }
    if (resId != 0) {
        Image(painter = painterResource(resId), contentDescription = null, modifier = modifier.size(size))
    } else {
        val inf = rememberInfiniteTransition(label = "bob")
        val bob by inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(if (mood == Mood.FOCUS) 700 else if (mood == Mood.CHEER) 450 else 1500, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "bobv"
        )
        Canvas(modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val lift = if (mood == Mood.SLEEP) 0f else bob * h * (if (mood == Mood.CHEER) 0.12f else 0.04f)
            val dy = -lift
            val yellow = Color(0xFFFFD84D)
            val orange = Color(0xFFFF9F1C)
            val pink = Color(0xFFFF8FB1)
            val line = w * 0.035f

            // bolt tail
            val tail = Path()
            tail.moveTo(w * 0.72f, h * 0.74f + dy)
            tail.lineTo(w * 0.86f, h * 0.54f + dy)
            tail.lineTo(w * 0.79f, h * 0.54f + dy)
            tail.lineTo(w * 0.96f, h * 0.28f + dy)
            tail.lineTo(w * 0.74f, h * 0.48f + dy)
            tail.lineTo(w * 0.80f, h * 0.48f + dy)
            tail.close()
            drawPath(tail, orange)
            drawPath(tail, ink, style = Stroke(line))

            // ears
            drawCircle(yellow, w * 0.10f, Offset(w * 0.27f, h * 0.24f + dy))
            drawCircle(ink, w * 0.10f, Offset(w * 0.27f, h * 0.24f + dy), style = Stroke(line))
            drawCircle(yellow, w * 0.10f, Offset(w * 0.60f, h * 0.24f + dy))
            drawCircle(ink, w * 0.10f, Offset(w * 0.60f, h * 0.24f + dy), style = Stroke(line))

            // body
            drawCircle(yellow, w * 0.34f, Offset(w * 0.44f, h * 0.60f + dy))
            drawCircle(ink, w * 0.34f, Offset(w * 0.44f, h * 0.60f + dy), style = Stroke(line))

            // cheeks
            drawCircle(pink, w * 0.055f, Offset(w * 0.25f, h * 0.68f + dy))
            drawCircle(pink, w * 0.055f, Offset(w * 0.63f, h * 0.68f + dy))

            // eyes
            val ey = h * 0.56f + dy
            if (mood == Mood.SLEEP) {
                drawLine(ink, Offset(w * 0.29f, ey), Offset(w * 0.39f, ey), strokeWidth = line)
                drawLine(ink, Offset(w * 0.49f, ey), Offset(w * 0.59f, ey), strokeWidth = line)
            } else if (mood == Mood.CHEER) {
                drawArc(ink, 200f, 140f, false, Offset(w * 0.29f, ey - w * 0.04f), Size(w * 0.10f, w * 0.10f), style = Stroke(line))
                drawArc(ink, 200f, 140f, false, Offset(w * 0.49f, ey - w * 0.04f), Size(w * 0.10f, w * 0.10f), style = Stroke(line))
            } else {
                drawCircle(ink, w * 0.04f, Offset(w * 0.34f, ey))
                drawCircle(ink, w * 0.04f, Offset(w * 0.54f, ey))
            }

            // mouth
            if (mood == Mood.SLEEP) {
                drawCircle(ink, w * 0.02f, Offset(w * 0.44f, h * 0.70f + dy))
            } else {
                drawArc(ink, 20f, 140f, false, Offset(w * 0.37f, h * 0.62f + dy), Size(w * 0.14f, w * 0.10f), style = Stroke(line))
            }
        }
    }
}
