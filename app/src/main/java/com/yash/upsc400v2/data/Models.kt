package com.yash.upsc400v2.data

data class SessionRow(
    val id: Long,
    val seq: Long,
    val stream: String,
    val kind: String,
    val subject: String,
    val block: String,
    val plannedMin: Int,
    val status: String,
    val skipCount: Int,
    val summary: String,
    val loggedMs: Long,
    val doneDay: Int,
    val problems: Int
)

data class PieceRow(
    val id: Long,
    val sessionId: Long,
    val item: String?,
    val kind: String,
    val title: String,
    val resource: String,
    val tier: String,
    val pages: String?,
    val minutes: Int,
    val f0: Double,
    val f1: Double,
    val approx: Boolean,
    val ticked: Boolean,
    val mode: String?,
    val objective: String?,
    val syllabus: String?,
    val pyq: String?,
    val note: String?,
    val prereq: String?,
    val unit: String?
)

data class TimerState(val sessionId: Long, val running: Boolean, val startedAt: Long)

data class BookRow(
    val res: String,
    val name: String,
    val uri: String?,
    val offset: Int,
    val lastPage: Int,
    val calibrated: Boolean,
    val hasApprox: Boolean,
    val progress: Double
)

data class ItemRow(val id: String, val title: String, val tier: String, val p0: Int, val p1: Int, val approx: Boolean)

data class LogRow(
    val id: Long,
    val sessionId: Long,
    val stream: String,
    val label: String,
    val ms: Long,
    val day: Int,
    val source: String
)

data class NoteRow(val id: Long, val ts: Long, val sessionId: Long, val text: String)

data class BookmarkRow(val id: Long, val res: String, val pdfPage: Int, val label: String)

data class ProgressRow(val key: String, val name: String, val total: Double, val done: Double)

data class PlannedActual(val label: String, val plannedMin: Int, val actualMin: Int)
