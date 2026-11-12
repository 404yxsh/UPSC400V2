package com.yash.upsc400v2.ui

import com.yash.upsc400v2.data.SessionRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun fmtDay(epochDay: Int): String =
    LocalDate.ofEpochDay(epochDay.toLong()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))

fun fmtHm(ms: Long): String {
    val totalMin = ms / 60000L
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) String.format("%d h %02d min", h, m) else String.format("%d min", m)
}

fun hours(ms: Long): Float = ms / 3600000f

fun typeLabel(stream: String, kind: String): String = when {
    kind == "consolidation" -> "REVIEW BLOCK"
    kind == "carry" -> "UNFINISHED"
    kind == "practice" -> "PRACTICE"
    stream == "M" -> "MATHS"
    stream == "G" -> "GS"
    else -> "REVISION"
}

/** The sessions making up the next cycle: first Mathematics, first two GS, first revision. */
fun nextCycleIds(rows: List<SessionRow>): Set<Long> {
    val ids = HashSet<Long>()
    var m = 0
    var g = 0
    var r = 0
    for (s in rows) {
        if (s.stream == "M" && m < 1) { ids.add(s.id); m++ }
        else if (s.stream == "G" && g < 2) { ids.add(s.id); g++ }
        else if (s.stream == "R" && r < 1) { ids.add(s.id); r++ }
    }
    return ids
}
