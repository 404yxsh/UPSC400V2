package com.yash.upsc400v2.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

class Pal(
    val bg: Color,
    val card: Color,
    val ink: Color,
    val math: Color,
    val gs: Color,
    val rev: Color,
    val cons: Color,
    val dark: Boolean
) {
    fun forSession(stream: String, kind: String): Color = when {
        kind == "consolidation" -> cons
        stream == "M" -> math
        stream == "G" -> gs
        else -> rev
    }
}

val LightPal = Pal(
    bg = Color(0xFFFFF4DE), card = Color(0xFFFFFFFF), ink = Color(0xFF141414),
    math = Color(0xFF8EC5FF), gs = Color(0xFFFFD84D), rev = Color(0xFFFF9EC4), cons = Color(0xFF9BE3A7), dark = false
)

val DarkPal = Pal(
    bg = Color(0xFF17140F), card = Color(0xFF241F17), ink = Color(0xFFFFF4DE),
    math = Color(0xFF2F4F73), gs = Color(0xFF6B5A14), rev = Color(0xFF6E2F4A), cons = Color(0xFF2F5A38), dark = true
)

val LocalPal = compositionLocalOf { LightPal }

@Composable
fun NbTheme(dark: Boolean, content: @Composable () -> Unit) {
    val pal = if (dark) DarkPal else LightPal
    val scheme = if (dark) {
        darkColorScheme(
            primary = pal.ink, onPrimary = pal.bg, background = pal.bg, onBackground = pal.ink,
            surface = pal.card, onSurface = pal.ink, surfaceVariant = pal.card, onSurfaceVariant = pal.ink,
            outline = pal.ink, secondary = pal.gs, onSecondary = pal.ink
        )
    } else {
        lightColorScheme(
            primary = pal.ink, onPrimary = pal.bg, background = pal.bg, onBackground = pal.ink,
            surface = pal.card, onSurface = pal.ink, surfaceVariant = pal.card, onSurfaceVariant = pal.ink,
            outline = pal.ink, secondary = pal.gs, onSecondary = pal.ink
        )
    }
    CompositionLocalProvider(LocalPal provides pal) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Text with the app's defaults. */
@Composable
fun T(
    text: String,
    size: Int = 16,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalPal.current.ink,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        fontFamily = FontFamily.SansSerif,
        lineHeight = (size * 1.25f).sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align
    )
}
