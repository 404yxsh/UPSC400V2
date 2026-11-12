package com.yash.upsc400v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.upsc400v2.AppModel
import com.yash.upsc400v2.reader.ReaderScreen
import kotlinx.coroutines.delay

@Composable
fun AppRoot(model: AppModel, systemDark: Boolean) {
    val themeSetting = remember(model.rev) { model.store.getSetting("theme", "0") }
    val dark = when (themeSetting) {
        "1" -> false
        "2" -> true
        else -> systemDark
    }
    NbTheme(dark) {
        val pal = LocalPal.current
        LaunchedEffect(model.confetti) {
            if (model.confetti > 0) {
                model.cheer = true
                delay(2500)
                model.cheer = false
            }
        }
        Box(Modifier.fillMaxSize().background(pal.bg)) {
            if (!model.ready) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Mascot(Mood.SLEEP, 120.dp)
                    Gap(h = 12)
                    T("Preparing your plan\u2026", 20, FontWeight.ExtraBold)
                }
            } else {
                val reader = model.reader
                val sid = model.openSession
                when {
                    reader != null -> ReaderScreen(model, reader)
                    sid != null -> SessionScreen(model, sid)
                    model.showHistory -> HistoryScreen(model)
                    else -> MainTabs(model)
                }
            }
            Confetti(model.confetti)
            val msg = model.message
            if (msg != null) {
                Box(Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = 72.dp)) {
                    NbCard(color = pal.gs, onClick = { model.message = null }) {
                        T(msg, 15, FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabs(model: AppModel) {
    val pal = LocalPal.current
    val labels = listOf("Queue", "Library", "Log", "Notes", "Settings")
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (model.tab) {
                0 -> QueueScreen(model)
                1 -> LibraryScreen(model)
                2 -> LogScreen(model)
                3 -> NotesScreen(model)
                else -> SettingsScreen(model)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(pal.card).border(3.dp, pal.ink).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in labels.indices) {
                val shape = RoundedCornerShape(10.dp)
                val sel = model.tab == i
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp)
                        .border(if (sel) 3.dp else 0.dp, pal.ink, shape)
                        .background(if (sel) pal.gs else pal.card, shape)
                        .clickable { model.tab = i },
                    contentAlignment = Alignment.Center
                ) {
                    T(labels[i], 12, if (sel) FontWeight.Black else FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
