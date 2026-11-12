package com.yash.upsc400v2.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.upsc400v2.AppModel
import com.yash.upsc400v2.data.NoteRow
import com.yash.upsc400v2.engine.Rollover
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotesScreen(model: AppModel) {
    val pal = LocalPal.current
    val notes = remember(model.rev) { model.store.notes() }
    var editing by remember { mutableStateOf<NoteRow?>(null) }
    var creating by remember { mutableStateOf(false) }
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault()) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Heading("Notes")
            Gap(h = 8)
            NbButton("New note", { creating = true }, color = pal.gs)
        }
        if (notes.isEmpty()) item { T("Short notes you write here stay on this phone.", 15) }
        items(notes, key = { n -> n.id }) { n ->
            NbCard(onClick = { editing = n }) {
                T(fmt.format(Instant.ofEpochMilli(n.ts)), 12, FontWeight.Bold)
                Gap(h = 4)
                T(n.text, 16, maxLines = 8)
            }
        }
    }

    if (creating) {
        var text by remember { mutableStateOf("") }
        NbDialog({ creating = false }, "New note") {
            NbField(text, { v -> text = v }, "Note", singleLine = false)
            ButtonRow {
                NbButton("Cancel", { creating = false }, modifier = Modifier.weight(1f), color = pal.card)
                NbButton("Save", {
                    if (text.isNotBlank()) model.store.addNote(text.trim(), 0L)
                    creating = false
                    model.bump()
                }, modifier = Modifier.weight(1f), color = pal.gs)
            }
        }
    }
    val e = editing
    if (e != null) {
        var text by remember(e.id) { mutableStateOf(e.text) }
        NbDialog({ editing = null }, "Edit note") {
            NbField(text, { v -> text = v }, "Note", singleLine = false)
            ButtonRow {
                NbButton("Delete", {
                    model.store.deleteNote(e.id)
                    editing = null
                    model.bump()
                }, modifier = Modifier.weight(1f), color = pal.rev)
                NbButton("Save", {
                    if (text.isNotBlank()) model.store.updateNote(e.id, text.trim())
                    editing = null
                    model.bump()
                }, modifier = Modifier.weight(1f), color = pal.gs)
            }
        }
    }
}
