package com.yash.upsc400v2.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.upsc400v2.AppModel

@Composable
fun SettingsScreen(model: AppModel) {
    val pal = LocalPal.current
    val store = model.store
    val theme = remember(model.rev) { store.getSetting("theme", "0") }
    val rollover = remember(model.rev) { store.rolloverMin() }
    val remOn = remember(model.rev) { store.getSetting("rem_on", "0") == "1" }
    val remH = remember(model.rev) { store.getSetting("rem_h", "7").toIntOrNull() ?: 7 }
    val remM = remember(model.rev) { store.getSetting("rem_m", "0").toIntOrNull() ?: 0 }
    val notif = remember(model.rev) { store.getSetting("timer_notif", "1") == "1" }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Heading("Settings")

        NbCard {
            T("Theme", 18, FontWeight.ExtraBold)
            Gap(h = 8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NbChip("System", { store.setSetting("theme", "0"); model.bump() }, selected = theme == "0")
                NbChip("Light", { store.setSetting("theme", "1"); model.bump() }, selected = theme == "1")
                NbChip("Dark", { store.setSetting("theme", "2"); model.bump() }, selected = theme == "2")
            }
        }

        NbCard {
            T("Day rolls over at", 18, FontWeight.ExtraBold)
            Gap(h = 8)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NbChip("-30 min", { store.setSetting("rollover_min", (rollover - 30).coerceAtLeast(0).toString()); model.bump() })
                T(String.format("%02d:%02d", rollover / 60, rollover % 60), 22, FontWeight.Black)
                NbChip("+30 min", { store.setSetting("rollover_min", (rollover + 30).coerceAtMost(720).toString()); model.bump() })
            }
        }

        NbCard {
            T("Daily reminder", 18, FontWeight.ExtraBold)
            Gap(h = 8)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NbChip(if (remOn) "On" else "Off", {
                    val on = !remOn
                    store.setSetting("rem_on", if (on) "1" else "0")
                    if (on) model.timer.scheduleReminder(remH, remM) else model.timer.cancelReminder()
                    model.bump()
                }, selected = remOn)
                NbChip("-1 h", {
                    val h = (remH + 23) % 24
                    store.setSetting("rem_h", h.toString())
                    if (remOn) model.timer.scheduleReminder(h, remM)
                    model.bump()
                })
                T(String.format("%02d:%02d", remH, remM), 22, FontWeight.Black)
                NbChip("+1 h", {
                    val h = (remH + 1) % 24
                    store.setSetting("rem_h", h.toString())
                    if (remOn) model.timer.scheduleReminder(h, remM)
                    model.bump()
                })
            }
        }

        NbCard {
            T("Timer notification", 18, FontWeight.ExtraBold)
            Gap(h = 8)
            NbChip(if (notif) "On" else "Off", { store.setSetting("timer_notif", if (notif) "0" else "1"); model.bump() }, selected = notif)
        }

        NbCard {
            T("Backup", 18, FontWeight.ExtraBold)
            Gap(h = 8)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NbButton("Export", { model.exportBackupFile() }, color = pal.cons)
                NbButton("Import", { model.importBackupFile() }, color = pal.rev)
            }
        }

        T("UPSC 400 v2 \u00b7 offline, no account, no network", 12, FontWeight.Bold)
    }
}
