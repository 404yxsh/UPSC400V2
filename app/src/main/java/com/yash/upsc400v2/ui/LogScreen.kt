package com.yash.upsc400v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.upsc400v2.AppModel
import com.yash.upsc400v2.data.LogRow
import com.yash.upsc400v2.engine.Rewards
import com.yash.upsc400v2.engine.Stats
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val TARGET: LocalDate = LocalDate.of(2028, 1, 8)

@Composable
fun LogScreen(model: AppModel) {
    val pal = LocalPal.current
    val store = model.store
    val today = remember(model.rev) { store.todayKey() }
    val totals = remember(model.rev) { store.dayTotals() }
    val totalMs = remember(model.rev) { store.totalMs() }
    val first = remember(model.rev) { store.firstDay() }
    val remaining = remember(model.rev) { store.remainingPlannedMs() }
    val pace = remember(model.rev) { store.paceFigures() }
    val subjects = remember(model.rev) { store.subjectTotals() }
    val streams = remember(model.rev) { store.streamTotals() }
    val planActual = remember(model.rev) { store.recentPlannedActual(12) }

    val v = Rewards.vTotal(totalMs)
    val streak = Rewards.streak(totals, today)
    val avg = if (first >= 0) Stats.avgPerDay(totalMs, first, today) else 0.0
    val finish = Stats.projectedFinish(remaining, avg, today)
    val nextBadge = Rewards.nextBadge(v)

    var ym by remember { mutableStateOf(YearMonth.now()) }
    var selDay by remember { mutableStateOf(today) }
    var editRow by remember { mutableStateOf<LogRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    val entries = remember(model.rev, selDay) { store.entriesForDay(selDay) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item { Heading("Log") }
        item {
            NbCard(color = pal.gs, shadow = 6.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("" + v + " V", 44, FontWeight.Black)
                        T(if (nextBadge > 0) "next badge at " + nextBadge + " V" else "all badges earned", 13, FontWeight.Bold)
                    }
                    Column {
                        T("streak " + streak[0], 18, FontWeight.ExtraBold)
                        T("best " + streak[1], 13, FontWeight.Bold)
                        T(if (streak[2] == 1) "rest token ready" else "rest token used", 12, FontWeight.Bold)
                    }
                }
                Gap(h = 8)
                T("Average " + String.format("%.1f", avg / 3600000.0) + " h/day over " + (if (first >= 0) today - first + 1 else 0) + " days", 14, FontWeight.Bold)
                val line = if (finish < 0) "At this pace: log some time to see a finish date"
                else "At this pace: finish " + fmtDay(finish) + " (target " + fmtDay(TARGET.toEpochDay().toInt()) + ")"
                T(line, 14, FontWeight.Bold)
                if (pace.first > 0 || pace.second > 0) {
                    T("Pace: " + String.format("%.0f", pace.first) + " pages/h, " + String.format("%.1f", pace.second) + " problems/h", 13)
                }
            }
        }
        // calendar
        item {
            NbCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NbChip("\u2039", { ym = ym.minusMonths(1) })
                    Box(Modifier.weight(1f)) {
                        T(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")), 18, FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Center))
                    }
                    NbChip("\u203a", { ym = ym.plusMonths(1) })
                }
                Gap(h = 8)
                val lead = ym.atDay(1).dayOfWeek.value - 1
                val len = ym.lengthOfMonth()
                val rows = (lead + len + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in 0 until 7) {
                            val dayNum = r * 7 + c - lead + 1
                            if (dayNum < 1 || dayNum > len) {
                                Box(Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                val epoch = ym.atDay(dayNum).toEpochDay().toInt()
                                val h = hours(totals[epoch] ?: 0L)
                                val shade = (h / 8f).coerceIn(0f, 1f)
                                val shape = RoundedCornerShape(6.dp)
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f)
                                        .border(if (epoch == selDay) 3.dp else 1.dp, pal.ink, shape)
                                        .background(if (h > 0f) pal.gs.copy(alpha = 0.3f + 0.7f * shade) else pal.card, shape)
                                        .clickable { selDay = epoch },
                                    contentAlignment = Alignment.Center
                                ) {
                                    T("" + dayNum, 13, if (epoch == today) FontWeight.Black else FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
        // selected day
        item {
            NbCard {
                T(fmtDay(selDay) + " \u00b7 " + fmtHm(totals[selDay] ?: 0L), 16, FontWeight.ExtraBold)
                Gap(h = 6)
                if (entries.isEmpty()) T("Nothing logged.", 14)
                for (e in entries) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editRow = e }, verticalAlignment = Alignment.CenterVertically) {
                        Tag(typeLabel(e.stream, "plan"), pal.forSession(e.stream, "plan"))
                        Gap(w = 8)
                        T(e.label.ifEmpty { "Other" }, 14, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                        T(fmtHm(e.ms), 14, FontWeight.ExtraBold)
                    }
                }
                Gap(h = 8)
                NbChip("Add time", { adding = true })
            }
        }
        // charts
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("Hours per day, last 30 days", 16, FontWeight.ExtraBold)
                val vals = (0 until 30).map { i -> hours(totals[today - 29 + i] ?: 0L) }
                val avg7 = Stats.movingAvg(DoubleArray(30) { i -> vals[i].toDouble() }, 7).map { d -> d.toFloat() }
                BarChart(vals, avg7, 8f, pal.gs)
                T("Bars: hours. Line: 7-day average. Dashed: 8 h.", 12)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("Weekly totals, last 8 weeks", 16, FontWeight.ExtraBold)
                val ws = Stats.weekStart(today)
                val weeks = (7 downTo 0).map { k ->
                    val start = ws - 7 * k
                    var sum = 0L
                    for (d in start until start + 7) sum += totals[d] ?: 0L
                    hours(sum)
                }
                BarChart(weeks, null, 0f, pal.math)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("Hours by subject", 16, FontWeight.ExtraBold)
                val st = listOf(
                    Pair("Mathematics", hours(streams["M"] ?: 0L)),
                    Pair("GS", hours(streams["G"] ?: 0L)),
                    Pair("Revision", hours(streams["R"] ?: 0L))
                )
                HBars(st, "h", pal.gs)
                Gap(h = 6)
                HBars(subjects.map { p -> Pair(p.first, hours(p.second)) }, "h", pal.math)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("Planned vs actual, last 12 sessions", 16, FontWeight.ExtraBold)
                PlannedActualChart(planActual.map { p -> p.plannedMin }, planActual.map { p -> p.actualMin }, pal.cons)
                T("Outline: planned minutes. Filled: actual.", 12)
            }
        }
    }

    val er = editRow
    if (er != null) {
        var text by remember(er.id) { mutableStateOf((er.ms / 60000L).toString()) }
        NbDialog({ editRow = null }, "Edit time") {
            T(er.label.ifEmpty { "Other" }, 15, FontWeight.Bold)
            NbField(text, { v2 -> text = v2 }, "Minutes", number = true)
            ButtonRow {
                NbButton("Delete", {
                    store.deleteEntry(er.id)
                    editRow = null
                    model.bump()
                }, modifier = Modifier.weight(1f), color = pal.rev)
                NbButton("Save", {
                    val m = text.toLongOrNull()
                    if (m != null && m >= 0) store.updateEntryMs(er.id, m * 60000L)
                    editRow = null
                    model.bump()
                }, modifier = Modifier.weight(1f), color = pal.gs)
            }
        }
    }
    if (adding) {
        var minutes by remember { mutableStateOf("") }
        var stream by remember { mutableStateOf("M") }
        var label by remember { mutableStateOf("") }
        NbDialog({ adding = false }, "Add time to " + fmtDay(selDay)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NbChip("Maths", { stream = "M" }, selected = stream == "M")
                NbChip("GS", { stream = "G" }, selected = stream == "G")
                NbChip("Revision", { stream = "R" }, selected = stream == "R")
            }
            NbField(minutes, { v2 -> minutes = v2 }, "Minutes", number = true)
            NbField(label, { v2 -> label = v2 }, "What was it? (optional)")
            ButtonRow {
                NbButton("Cancel", { adding = false }, modifier = Modifier.weight(1f), color = pal.card)
                NbButton("Add", {
                    val m = minutes.toLongOrNull()
                    if (m != null && m > 0) store.addManual(selDay, stream, label.trim(), m * 60000L)
                    adding = false
                    model.bump()
                }, modifier = Modifier.weight(1f), color = pal.gs)
            }
        }
    }
}
