package com.yash.upsc400v2.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Bars with an optional line (moving average) and a dashed marker (for example 8 hours). */
@Composable
fun BarChart(values: List<Float>, line: List<Float>?, marker: Float, barColor: Color, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(10.dp)
    Canvas(modifier.fillMaxWidth().height(150.dp).border(3.dp, pal.ink, shape).background(pal.card, shape).padding(6.dp)) {
        if (values.isEmpty()) return@Canvas
        var maxV = marker
        for (v in values) if (v > maxV) maxV = v
        if (maxV <= 0f) maxV = 1f
        maxV *= 1.1f
        val n = values.size
        val gap = 3f
        val bw = ((size.width - gap * (n + 1)) / n).coerceAtLeast(1f)
        for (i in 0 until n) {
            val bh = values[i] / maxV * size.height
            val tl = Offset(gap + i * (bw + gap), size.height - bh)
            if (bh > 0.5f) {
                drawRect(barColor, tl, Size(bw, bh))
                drawRect(pal.ink, tl, Size(bw, bh), style = Stroke(2f))
            }
        }
        if (marker > 0f) {
            val y = size.height - marker / maxV * size.height
            drawLine(pal.ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        }
        if (line != null && line.size > 1) {
            val path = Path()
            for (i in line.indices) {
                val x = gap + i * (bw + gap) + bw / 2f
                val y = size.height - line[i] / maxV * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, pal.ink, style = Stroke(5f))
        }
    }
}

/** Horizontal labelled bars. */
@Composable
fun HBars(items: List<Pair<String, Float>>, unit: String, color: Color) {
    val pal = LocalPal.current
    var maxV = 0f
    for (p in items) if (p.second > maxV) maxV = p.second
    if (maxV <= 0f) maxV = 1f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in items) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    T(p.first, 13, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                    T(String.format("%.1f %s", p.second, unit), 13, FontWeight.Bold)
                }
                ProgressBar(p.second / maxV, color)
            }
        }
    }
}

/** Planned (outlined) versus actual (filled) minutes for recent sessions. */
@Composable
fun PlannedActualChart(planned: List<Int>, actual: List<Int>, color: Color) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(10.dp)
    Canvas(Modifier.fillMaxWidth().height(150.dp).border(3.dp, pal.ink, shape).background(pal.card, shape).padding(6.dp)) {
        val n = planned.size
        if (n == 0) return@Canvas
        var maxV = 1
        for (i in 0 until n) {
            if (planned[i] > maxV) maxV = planned[i]
            if (actual[i] > maxV) maxV = actual[i]
        }
        val group = size.width / n
        val bw = group * 0.38f
        for (i in 0 until n) {
            val ph = planned[i].toFloat() / maxV * size.height
            val ah = actual[i].toFloat() / maxV * size.height
            val x0 = i * group + group * 0.08f
            drawRect(pal.ink, Offset(x0, size.height - ph), Size(bw, ph), style = Stroke(3f))
            drawRect(color, Offset(x0 + bw + 2f, size.height - ah), Size(bw, ah))
            drawRect(pal.ink, Offset(x0 + bw + 2f, size.height - ah), Size(bw, ah), style = Stroke(2f))
        }
    }
}
