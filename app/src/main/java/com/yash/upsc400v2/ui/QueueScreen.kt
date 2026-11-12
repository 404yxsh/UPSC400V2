package com.yash.upsc400v2.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.upsc400v2.AppModel
import com.yash.upsc400v2.data.SessionRow

@Composable
fun QueueScreen(model: AppModel) {
    val pal = LocalPal.current
    val rows = remember(model.rev) { model.store.openSessions() }
    val next = remember(rows) { nextCycleIds(rows) }
    val sleepy = remember(model.rev) {
        val t = model.store.timer()
        val last = model.store.lastLogEnd()
        !(t != null && t.running) && last > 0 && System.currentTimeMillis() - last > 30 * 60000L
    }
    val running = remember(model.rev) { model.store.timer()?.running == true }
    val mood = when {
        model.cheer -> Mood.CHEER
        running -> Mood.FOCUS
        sleepy -> Mood.SLEEP
        else -> Mood.IDLE
    }
    var plannedMin = 0L
    for (r in rows) plannedMin += r.plannedMin

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Heading("Queue")
                    T("" + rows.size + " open \u00b7 " + (plannedMin / 60) + " h planned", 14, FontWeight.Bold)
                    Gap(h = 8)
                    NbChip("History", { model.showHistory = true })
                }
                Mascot(mood, 88.dp)
            }
        }
        if (rows.isEmpty()) {
            item { T("Nothing open. New sessions appear here automatically.", 16) }
        }
        itemsIndexed(rows, key = { _, r -> r.id }) { index, r ->
            QueueRow(model, r, index + 1, next.contains(r.id))
        }
    }
}

@Composable
private fun QueueRow(model: AppModel, r: SessionRow, order: Int, highlighted: Boolean) {
    val pal = LocalPal.current
    val color = pal.forSession(r.stream, r.kind)
    NbCard(color = if (highlighted) color else pal.card, shadow = if (highlighted) 6.dp else 4.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("#" + order, 18, FontWeight.Black)
            Gap(w = 10)
            Tag(typeLabel(r.stream, r.kind), if (highlighted) pal.card else color)
            Gap(w = 10)
            if (highlighted) Tag("NEXT", pal.card)
            Box(Modifier.weight(1f))
            T("" + r.plannedMin + " min", 15, FontWeight.ExtraBold)
        }
        Gap(h = 8)
        T(r.subject, 18, FontWeight.ExtraBold)
        if (r.summary.isNotEmpty()) T(r.summary, 14, maxLines = 2)
        Gap(h = 10)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val status = if (r.loggedMs > 0) "in progress \u00b7 " + fmtHm(r.loggedMs) else ""
            T(status, 13, FontWeight.Bold, modifier = Modifier.weight(1f))
            NbButton("Open", { model.openSession = r.id }, color = if (highlighted) pal.card else color)
        }
    }
}
