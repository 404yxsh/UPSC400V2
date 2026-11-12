package com.yash.upsc400v2.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.upsc400v2.AppModel

@Composable
fun HistoryScreen(model: AppModel) {
    BackHandler { model.showHistory = false }
    val pal = LocalPal.current
    val rows = remember(model.rev) { model.store.historySessions() }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NbChip("\u2039 Back", { model.showHistory = false })
                Gap(w = 12)
                Heading("History")
            }
        }
        if (rows.isEmpty()) item { T("Finished and skipped sessions appear here.", 15) }
        items(rows, key = { r -> r.id }) { r ->
            NbCard(color = pal.card) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Tag(typeLabel(r.stream, r.kind), pal.forSession(r.stream, r.kind))
                    Gap(w = 8)
                    T(if (r.status == "skipped") "skipped" else "done", 13, FontWeight.Bold)
                    Box(Modifier.weight(1f))
                    if (r.doneDay >= 0) T(fmtDay(r.doneDay), 13, FontWeight.Bold)
                }
                Gap(h = 6)
                T(r.subject, 16, FontWeight.ExtraBold)
                T("planned " + r.plannedMin + " min \u00b7 logged " + fmtHm(r.loggedMs), 13)
            }
        }
    }
}
