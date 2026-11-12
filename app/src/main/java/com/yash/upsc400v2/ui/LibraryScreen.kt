package com.yash.upsc400v2.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.upsc400v2.AppModel
import com.yash.upsc400v2.ReaderArgs
import com.yash.upsc400v2.data.BookRow
import com.yash.upsc400v2.data.ItemRow
import com.yash.upsc400v2.data.bookItems
import com.yash.upsc400v2.data.books
import com.yash.upsc400v2.data.saveCalibration
import com.yash.upsc400v2.data.setBookOffset
import com.yash.upsc400v2.data.syllabusProgress

@Composable
fun LibraryScreen(model: AppModel) {
    val pal = LocalPal.current
    var segment by remember { mutableStateOf(0) }
    val books = remember(model.rev) { model.store.books() }
    val syllabus = remember(model.rev) { model.store.syllabusProgress() }
    var offsetFor by remember { mutableStateOf<BookRow?>(null) }
    var calibrateFor by remember { mutableStateOf<BookRow?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Heading("Library")
            Gap(h = 8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NbChip("Books", { segment = 0 }, selected = segment == 0)
                NbChip("Syllabus", { segment = 1 }, selected = segment == 1)
            }
        }
        if (segment == 0) {
            items(books, key = { b -> b.res }) { b ->
                NbCard {
                    T(b.name, 18, FontWeight.ExtraBold)
                    Gap(h = 8)
                    ProgressBar(b.progress.toFloat(), pal.cons)
                    Gap(h = 6)
                    T(
                        "" + (b.progress * 100).toInt() + "% \u00b7 offset " + b.offset + " \u00b7 " +
                            (if (b.uri != null) "PDF attached" else "no PDF") +
                            (if (b.calibrated) " \u00b7 calibrated" else ""),
                        13, FontWeight.Bold
                    )
                    Gap(h = 10)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NbChip(if (b.uri == null) "Attach PDF" else "Change PDF", { model.pickPdf(b.res) })
                        if (b.uri != null) NbChip("Resume", { model.openReader(ReaderArgs(b.res, 0, 0, 0L)) }, selected = true)
                    }
                    Gap(h = 8)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NbChip("Offset", { offsetFor = b })
                        if (b.hasApprox) NbChip("Calibrate chapters", { calibrateFor = b })
                    }
                }
            }
        } else {
            items(syllabus, key = { r -> r.key }) { r ->
                NbCard {
                    T(r.name, 16, FontWeight.ExtraBold)
                    Gap(h = 6)
                    val f = if (r.total > 0) (r.done / r.total).toFloat() else 0f
                    ProgressBar(f, if (r.key.startsWith("M:")) pal.math else pal.gs)
                    Gap(h = 4)
                    T("" + (f * 100).toInt() + "%", 13, FontWeight.Bold)
                }
            }
        }
    }

    val o = offsetFor
    if (o != null) {
        var text by remember(o.res) { mutableStateOf(o.offset.toString()) }
        NbDialog({ offsetFor = null }, "PDF page offset") {
            T("PDF page = printed page + offset. Example: printed page 1 on PDF page 15 means offset 14.", 14)
            NbField(text, { v -> text = v }, "Offset (can be negative)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val cur = text.toIntOrNull() ?: 0
                NbChip("-10", { text = (cur - 10).toString() })
                NbChip("-1", { text = (cur - 1).toString() })
                NbChip("+1", { text = (cur + 1).toString() })
                NbChip("+10", { text = (cur + 10).toString() })
            }
            ButtonRow {
                NbButton("Cancel", { offsetFor = null }, modifier = Modifier.weight(1f), color = pal.card)
                NbButton("Save", {
                    model.store.setBookOffset(o.res, text.toIntOrNull() ?: 0)
                    offsetFor = null
                    model.bump()
                }, modifier = Modifier.weight(1f), color = pal.gs)
            }
        }
    }

    val cb = calibrateFor
    if (cb != null) {
        val items = remember(cb.res) { model.store.bookItems(cb.res) }
        val starts = remember(cb.res) { mutableStateListOf<String>().apply { addAll(items.map { i -> i.p0.toString() }) } }
        var last by remember(cb.res) { mutableStateOf(if (items.isEmpty()) "" else items[items.size - 1].p1.toString()) }
        var error by remember(cb.res) { mutableStateOf("") }
        NbDialog({ calibrateFor = null }, "Calibrate chapters") {
            T("Type the printed start page of each chapter. Each chapter ends where the next begins.", 14)
            for (k in items.indices) {
                NbField(starts[k], { v -> starts[k] = v }, items[k].title + (if (items[k].approx) " (estimated)" else ""))
            }
            NbField(last, { v -> last = v }, "Last page of the final chapter")
            if (error.isNotEmpty()) T(error, 14, FontWeight.Bold)
            ButtonRow {
                NbButton("Cancel", { calibrateFor = null }, modifier = Modifier.weight(1f), color = pal.card)
                NbButton("Save", {
                    val parsed = IntArray(items.size)
                    var ok = true
                    for (k in items.indices) {
                        val v = starts[k].toIntOrNull()
                        if (v == null) ok = false else parsed[k] = v
                    }
                    val lastV = last.toIntOrNull()
                    if (lastV == null) ok = false
                    if (ok) {
                        for (k in 1 until parsed.size) if (parsed[k] < parsed[k - 1]) ok = false
                    }
                    if (!ok || lastV == null) {
                        error = "Check the page numbers: all must be numbers and not go backwards."
                    } else {
                        model.store.saveCalibration(cb.res, items, parsed, lastV)
                        calibrateFor = null
                        model.bump()
                    }
                }, modifier = Modifier.weight(1f), color = pal.gs)
            }
        }
    }
}
