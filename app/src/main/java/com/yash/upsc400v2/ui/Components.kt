package com.yash.upsc400v2.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun NbCard(
    modifier: Modifier = Modifier,
    color: Color = LocalPal.current.card,
    shadow: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth().padding(end = shadow, bottom = shadow)) {
        Box(Modifier.matchParentSize().offset(shadow, shadow).background(pal.ink, shape))
        var face = Modifier.fillMaxWidth().border(3.dp, pal.ink, shape).background(color, shape)
        if (onClick != null) face = face.clickable(onClick = onClick)
        Column(face.padding(14.dp), content = content)
    }
}

@Composable
fun NbButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalPal.current.gs,
    enabled: Boolean = true,
    big: Boolean = false
) {
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sh = if (big) 6.dp else 4.dp
    val off by animateDpAsState(if (pressed && enabled) sh else 0.dp, tween(80), label = "press")
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.padding(end = sh, bottom = sh).alpha(if (enabled) 1f else 0.45f)) {
        Box(Modifier.matchParentSize().offset(sh, sh).background(pal.ink, shape))
        Box(
            Modifier
                .offset(off, off)
                .border(3.dp, pal.ink, shape)
                .background(color, shape)
                .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
                .heightIn(min = if (big) 64.dp else 48.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            T(text, if (big) 20 else 16, FontWeight.ExtraBold, pal.ink)
        }
    }
}

/** Small flat chip button. */
@Composable
fun NbChip(text: String, onClick: () -> Unit, selected: Boolean = false, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .border(2.dp, pal.ink, shape)
            .background(if (selected) pal.gs else pal.card, shape)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        T(text, 14, FontWeight.Bold)
    }
}

@Composable
fun NbTick(checked: Boolean, onToggle: () -> Unit, color: Color) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(8.dp)
    val sc by animateFloatAsState(if (checked) 1f else 0.92f, tween(150), label = "tick")
    Box(
        Modifier
            .size(44.dp)
            .scale(sc)
            .border(3.dp, pal.ink, shape)
            .background(if (checked) color else pal.card, shape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        if (checked) T("\u2713", 24, FontWeight.Black)
    }
}

@Composable
fun Tag(text: String, color: Color) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(8.dp)
    Box(Modifier.border(2.dp, pal.ink, shape).background(color, shape).padding(horizontal = 8.dp, vertical = 3.dp)) {
        T(text, 12, FontWeight.ExtraBold)
    }
}

@Composable
fun Heading(text: String) {
    T(text, 34, FontWeight.Black)
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.fillMaxWidth().height(16.dp).border(2.dp, pal.ink, shape).background(pal.card, shape)) {
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            Box(Modifier.fillMaxWidth(f).height(16.dp).background(color, shape))
        }
    }
}

@Composable
fun NbDialog(onDismiss: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPal.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth(0.94f).padding(vertical = 24.dp)) {
            NbCard(color = pal.bg) {
                T(title, 22, FontWeight.Black)
                Column(
                    Modifier.padding(top = 10.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

@Composable
fun NbField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { T(label, 14) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text)
    )
}

@Composable
fun Gap(w: Int = 0, h: Int = 0) {
    Box(Modifier.width(w.dp).height(h.dp))
}
