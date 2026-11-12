package com.yash.upsc400v2.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.upsc400v2.AppModel
import com.yash.upsc400v2.ReaderArgs
import com.yash.upsc400v2.data.PieceRow
import com.yash.upsc400v2.data.SessionRow
import com.yash.upsc400v2.data.itemPages
import com.yash.upsc400v2.data.itemRes
import com.yash.upsc400v2.data.pagesText
import com.yash.upsc400v2.engine.PageMap
import com.yash.upsc400v2.engine.TimerMath
import kotlinx.coroutines.delay

private fun lectureInfo(p: PieceRow): String {
    val n = p.item?.substringAfterLast(':') ?: ""
    var s = "Lecture $n"
    if (p.f0 > 0.001 || p.f1 < 0.999) s += " (" + (p.f0 * 100).toInt() + "\u2013" + (p.f1 * 100).toInt() + "%)"
    return s
}

private fun detailLine(p: PieceRow, pageMap: Map<String, IntArray>): String {
    val parts = ArrayList<String>()
    if (p.resource.isNotEmpty()) parts.add(p.resource)
    if (p.kind == "lec") parts.add(lectureInfo(p))
    else {
        val pg = pagesText(p.item, p.f0, p.f1, p.pages, pageMap)
        if (pg != null) parts.add(pg)
    }
    parts.add("" + p.minutes + " min")
    return parts.joinToString(" \u00b7 ")
}

@Composable
fun SessionScreen(model: AppModel, id: Long) {
    BackHandler { model.openSession = null }
    val pal = LocalPal.current
    val store = model.store
    val s = remember(model.rev, id) { store.session(id) }
    if (s == null) {
        LaunchedEffect(Unit) { model.openSession = null }
        Box(Modifier.fillMaxSize())
        return
    }
    val pieces = remember(model.rev, id) { store.pieces(id) }
    val pageMap = remember(model.rev) { store.itemPages() }
    val canSkip = remember(model.rev, id) { store.canSkip(id) }
    val color = pal.forSession(s.stream, s.kind)

    var showInfo by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var showRead by remember { mutableStateOf(false) }

    val readable = remember(model.rev, id) {
        val out = ArrayList<Triple<PieceRow, String, IntArray>>()
        for (p in pieces) {
            val item = p.item
            if (p.ticked || item == null || p.kind == "lec") continue
            val res = store.itemRes(item) ?: continue
            val rng = PageMap.parseRange(pagesText(item, p.f0, p.f1, p.pages, pageMap)) ?: continue
            out.add(Triple(p, res, rng))
        }
        out
    }

    val timerState = remember(model.rev) { store.timer() }
    val running = timerState != null && timerState.running && timerState.sessionId == id
    val logged = remember(model.rev, id) { store.loggedMs(id) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NbChip("\u2039 Back", { model.openSession = null })
            Box(Modifier.weight(1f))
            NbChip("i", { showInfo = true })
        }
        Column {
            Tag(typeLabel(s.stream, s.kind), color)
            Gap(h = 6)
            T(s.subject, 28, FontWeight.Black)
            if (s.summary.isNotEmpty()) T(s.summary, 14, maxLines = 3)
        }

        TimerCard(model, s)

        NbCard {
            if (pieces.isEmpty()) T("Nothing to tick here.", 15)
            for (p in pieces) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    NbTick(p.ticked, { store.setTick(p.id, !p.ticked); model.bump() }, color)
                    Gap(w = 12)
                    Column(Modifier.weight(1f)) {
                        T(p.title, 17, FontWeight.ExtraBold)
                        T(detailLine(p, pageMap), 14)
                        val obj = p.objective
                        if (obj != null && obj.isNotEmpty()) T(obj, 13, maxLines = 4)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NbButton(
                if (running) "Pause" else if (logged > 0) "Resume" else "Start",
                {
                    if (running) model.timer.pause() else model.timer.start(id)
                    model.bump()
                },
                modifier = Modifier.weight(1f), color = color, big = true
            )
            NbButton("Done", { showDone = true }, modifier = Modifier.weight(1f), color = pal.cons, big = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NbButton(
                "Read",
                {
                    if (readable.size == 1) {
                        val r = readable[0]
                        model.openReader(ReaderArgs(r.second, r.third[0], r.third[1], id))
                    } else showRead = true
                },
                modifier = Modifier.weight(1f), color = pal.math, enabled = readable.isNotEmpty()
            )
            NbButton(
                "Skip",
                {
                    if (store.skip(id)) {
                        model.openSession = null
                        model.bump()
                    }
                },
                modifier = Modifier.weight(1f), color = pal.rev, enabled = canSkip && logged == 0L && !running
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NbChip("Note", { showNote = true })
            NbChip("Reset timer", { showReset = true })
        }
    }

    if (showRead) {
        NbDialog({ showRead = false }, "Open which part?") {
            for (r in readable) {
                NbButton(
                    r.first.title + " (" + (pagesText(r.first.item, r.first.f0, r.first.f1, r.first.pages, pageMap) ?: "") + ")",
                    {
                        showRead = false
                        model.openReader(ReaderArgs(r.second, r.third[0], r.third[1], id))
                    },
                    modifier = Modifier.fillMaxWidth(), color = pal.math
                )
            }
        }
    }
    if (showReset) {
        NbDialog({ showReset = false }, "Reset timer?") {
            T("This clears the time logged for this session and removes it from the log.", 15)
            ButtonRow {
                NbButton("Cancel", { showReset = false }, modifier = Modifier.weight(1f), color = pal.card)
                NbButton("Reset", {
                    store.resetTimer(id)
                    model.timer.cancelAll()
                    showReset = false
                    model.bump()
                }, modifier = Modifier.weight(1f), color = pal.rev)
            }
        }
    }
    if (showDone) {
        var problems by remember { mutableStateOf("") }
        val askProblems = s.stream == "M" && (s.kind == "plan" || s.kind == "composed")
        NbDialog({ showDone = false }, "Done?") {
            if (askProblems) NbField(problems, { v -> problems = v }, "Problems solved (optional)", number = true)
            ButtonRow {
                NbButton("Cancel", { showDone = false }, modifier = Modifier.weight(1f), color = pal.card)
                NbButton("Done", {
                    store.finish(id, problems.toIntOrNull() ?: -1)
                    model.timer.cancelAll()
                    showDone = false
                    model.openSession = null
                    model.confetti++
                    model.bump()
                }, modifier = Modifier.weight(1f), color = pal.cons)
            }
        }
    }
    if (showNote) {
        var text by remember { mutableStateOf("") }
        NbDialog({ showNote = false }, "Note") {
            NbField(text, { v -> text = v }, "Write a note", singleLine = false)
            ButtonRow {
                NbButton("Cancel", { showNote = false }, modifier = Modifier.weight(1f), color = pal.card)
                NbButton("Save", {
                    if (text.isNotBlank()) store.addNote(text.trim(), id)
                    showNote = false
                    model.bump()
                }, modifier = Modifier.weight(1f), color = pal.gs)
            }
        }
    }
    if (showInfo) {
        NbDialog({ showInfo = false }, "Details") {
            InfoLine("Planned", "" + s.plannedMin + " min")
            InfoLine("Skipped", "" + s.skipCount + " time(s)")
            fun distinct(sel: (PieceRow) -> String?): String =
                pieces.mapNotNull { p -> sel(p) }.filter { v -> v.isNotEmpty() }.distinct().joinToString("; ")
            InfoLine("Mode", distinct { p -> p.mode })
            InfoLine("Tier", distinct { p -> p.tier })
            InfoLine("Prerequisites", distinct { p -> p.prereq })
            InfoLine("Syllabus", distinct { p -> p.syllabus })
            InfoLine("PYQ focus", distinct { p -> p.pyq })
            InfoLine("Notes", distinct { p -> p.note })
            if (pieces.any { p -> p.approx }) InfoLine("Approximate", "Some page ranges are estimates. Calibrate chapters in Library.")
            NbButton("Close", { showInfo = false }, modifier = Modifier.fillMaxWidth(), color = pal.gs)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isEmpty()) return
    Column {
        T(label.uppercase(), 11, FontWeight.ExtraBold)
        T(value, 14)
    }
}

@Composable
private fun TimerCard(model: AppModel, s: SessionRow) {
    val pal = LocalPal.current
    val st = remember(model.rev) { model.store.timer() }
    val logged = remember(model.rev, s.id) { model.store.loggedMs(s.id) }
    val running = st != null && st.running && st.sessionId == s.id
    val now by produceState(System.currentTimeMillis(), running) {
        while (true) {
            value = System.currentTimeMillis()
            delay(if (running) 200L else 1000L)
        }
    }
    val elapsed = TimerMath.elapsed(logged, running, st?.startedAt ?: 0L, now)
    val plannedMs = s.plannedMin * 60000L
    val over = TimerMath.overtime(s.plannedMin.toLong(), elapsed)
    val pulse by animateFloatAsState(if (running && (elapsed / 1000) % 2L == 0L) 1f else 1.03f, tween(250), label = "pulse")
    val mood = if (model.cheer) Mood.CHEER else if (running) Mood.FOCUS else Mood.IDLE

    NbCard(color = if (over) pal.rev else pal.card, shadow = 6.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(
                    if (over) "+" + TimerMath.format(elapsed - plannedMs) else TimerMath.format(elapsed),
                    56, FontWeight.Black, modifier = Modifier.scale(pulse)
                )
                T(
                    if (over) "overtime \u00b7 planned " + TimerMath.format(plannedMs) else "of " + TimerMath.format(plannedMs),
                    14, FontWeight.Bold
                )
            }
            Mascot(mood, 80.dp)
        }
        Gap(h = 12)
        val segs = (s.plannedMin + 14) / 15
        val shape = RoundedCornerShape(4.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (k in 0 until segs) {
                val full = elapsed >= (k + 1) * 900000L
                val part = elapsed > k * 900000L
                Box(
                    Modifier.weight(1f).height(16.dp).border(2.dp, pal.ink, shape)
                        .background(if (full) pal.gs else if (part) pal.gs.copy(alpha = 0.5f) else pal.card, shape)
                )
            }
        }
    }
}
