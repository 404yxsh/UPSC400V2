package com.yash.upsc400v2.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.yash.upsc400v2.engine.CarryOver
import com.yash.upsc400v2.engine.PageMap
import com.yash.upsc400v2.engine.Piece
import com.yash.upsc400v2.engine.Plan
import com.yash.upsc400v2.engine.QueueBuilder
import com.yash.upsc400v2.engine.ReviewScheduler
import com.yash.upsc400v2.engine.Rollnum
import com.yash.upsc400v2.engine.Rollover
import com.yash.upsc400v2.engine.Session
import com.yash.upsc400v2.engine.SkipSwap
import java.util.TimeZone

class Store(private val ctx: Context) {

    val db: SQLiteDatabase = Db(ctx).writableDatabase

    init {
        // readers must not block while the plan is seeded on a background thread
        db.enableWriteAheadLogging()
    }

    // ------------------------------------------------------------------ settings

    fun getSetting(key: String, def: String): String {
        val c = db.rawQuery("SELECT v FROM settings WHERE k=?", arrayOf(key))
        try {
            if (c.moveToFirst()) return c.getString(0) ?: def
        } finally {
            c.close()
        }
        return def
    }

    fun setSetting(key: String, value: String) {
        val cv = ContentValues()
        cv.put("k", key)
        cv.put("v", value)
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun rolloverMin(): Int = getSetting("rollover_min", Rollover.DEFAULT_MIN.toString()).toIntOrNull() ?: Rollover.DEFAULT_MIN

    fun dayKeyOf(ms: Long): Int = Rollover.dayKey(ms, TimeZone.getDefault().getOffset(ms).toLong(), rolloverMin())

    fun todayKey(): Int = dayKeyOf(System.currentTimeMillis())

    // ------------------------------------------------------------------ seeding

    fun isSeeded(): Boolean = getSetting("seeded", "0") == "1"

    fun seed() {
        val text = ctx.assets.open("plan.json").bufferedReader(Charsets.UTF_8).use { r -> r.readText() }
        val plan = Plan.parse(text)
        val queue = QueueBuilder.build(plan, 0)
        db.beginTransaction()
        try {
            var order = 0
            for (item in plan.items) {
                val cv = ContentValues()
                cv.put("id", item.id)
                cv.put("res", item.res)
                cv.put("rname", item.rname)
                cv.put("title", item.title)
                cv.put("tier", item.tier)
                cv.put("kind", item.kind)
                cv.put("unit", item.unit)
                cv.put("block", item.block)
                cv.put("pages", item.pages)
                cv.put("p0", item.p0)
                cv.put("p1", item.p1)
                cv.put("approx", if (item.approx) 1 else 0)
                cv.put("total_min", item.totalMin)
                cv.put("ord", order++)
                db.insert("plan_items", null, cv)
            }
            for (s in queue) insertSession(s)
            db.execSQL("INSERT OR IGNORE INTO books(res, name) SELECT res, rname FROM plan_items WHERE kind IN ('gs','math') GROUP BY res")
            setSetting("seeded", "1")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertSession(s: Session): Long {
        val cv = ContentValues()
        cv.put("seq", s.seq)
        cv.put("stream", s.stream.toString())
        cv.put("kind", s.kind)
        cv.put("plan_index", s.planIndex)
        cv.put("subject", s.subject)
        cv.put("block", s.block)
        cv.put("planned_min", s.plannedMin)
        cv.put("status", s.status)
        cv.put("skip_count", s.skipCount)
        cv.put("summary", s.summary)
        val id = db.insert("sessions", null, cv)
        s.id = id
        var ord = 0
        for (p in s.pieces) {
            insertPiece(id, ord++, p)
        }
        return id
    }

    private fun insertPiece(sessionId: Long, ord: Int, p: Piece): Long {
        val cv = ContentValues()
        cv.put("session_id", sessionId)
        cv.put("ord", ord)
        cv.put("item", p.item)
        cv.put("kind", p.kind)
        cv.put("title", p.title)
        cv.put("resource", p.resource)
        cv.put("tier", p.tier)
        cv.put("pages", p.pages)
        cv.put("minutes", p.minutes)
        cv.put("f0", p.f0)
        cv.put("f1", p.f1)
        cv.put("approx", if (p.approx) 1 else 0)
        cv.put("ticked", if (p.ticked) 1 else 0)
        cv.put("mode", p.mode)
        cv.put("objective", p.objective)
        cv.put("syllabus", p.syllabus)
        cv.put("pyq", p.pyq)
        cv.put("note", p.note)
        cv.put("prereq", p.prerequisites)
        cv.put("unit", p.unit)
        return db.insert("pieces", null, cv)
    }

    // ------------------------------------------------------------------ queue

    private val sessionCols =
        "s.id, s.seq, s.stream, s.kind, s.subject, s.block, s.planned_min, s.status, s.skip_count, s.summary, " +
            "(SELECT COALESCE(SUM(l.ms),0) FROM log_entries l WHERE l.session_id=s.id), s.done_day, s.problems"

    private fun readSession(c: Cursor): SessionRow = SessionRow(
        id = c.getLong(0),
        seq = c.getLong(1),
        stream = c.getString(2),
        kind = c.getString(3),
        subject = c.getString(4) ?: "",
        block = c.getString(5) ?: "",
        plannedMin = c.getInt(6),
        status = c.getString(7),
        skipCount = c.getInt(8),
        summary = c.getString(9) ?: "",
        loggedMs = c.getLong(10),
        doneDay = c.getInt(11),
        problems = c.getInt(12)
    )

    private fun querySessions(where: String, args: Array<String>?, order: String): List<SessionRow> {
        val out = ArrayList<SessionRow>()
        val c = db.rawQuery("SELECT $sessionCols FROM sessions s WHERE $where ORDER BY $order", args)
        try {
            while (c.moveToNext()) out.add(readSession(c))
        } finally {
            c.close()
        }
        return out
    }

    fun openSessions(): List<SessionRow> = querySessions("s.status='todo'", null, "s.seq ASC")

    fun historySessions(): List<SessionRow> = querySessions("s.status IN ('done','skipped')", null, "s.done_ts DESC, s.id DESC")

    fun session(id: Long): SessionRow? = querySessions("s.id=?", arrayOf(id.toString()), "s.id").firstOrNull()

    fun pieces(sessionId: Long): List<PieceRow> {
        ensureRevisionPieces(sessionId)
        val out = ArrayList<PieceRow>()
        val c = db.rawQuery(
            "SELECT id, session_id, item, kind, title, resource, tier, pages, minutes, f0, f1, approx, ticked, mode, objective, syllabus, pyq, note, prereq, unit " +
                "FROM pieces WHERE session_id=? ORDER BY ord ASC, id ASC",
            arrayOf(sessionId.toString())
        )
        try {
            while (c.moveToNext()) {
                out.add(
                    PieceRow(
                        id = c.getLong(0), sessionId = c.getLong(1), item = c.getString(2),
                        kind = c.getString(3) ?: "", title = c.getString(4) ?: "", resource = c.getString(5) ?: "",
                        tier = c.getString(6) ?: "", pages = c.getString(7), minutes = c.getInt(8),
                        f0 = c.getDouble(9), f1 = c.getDouble(10), approx = c.getInt(11) == 1, ticked = c.getInt(12) == 1,
                        mode = c.getString(13), objective = c.getString(14), syllabus = c.getString(15),
                        pyq = c.getString(16), note = c.getString(17), prereq = c.getString(18), unit = c.getString(19)
                    )
                )
            }
        } finally {
            c.close()
        }
        return out
    }

    fun setTick(pieceId: Long, ticked: Boolean) {
        db.execSQL("UPDATE pieces SET ticked=? WHERE id=?", arrayOf<Any>(if (ticked) 1 else 0, pieceId))
    }

    private fun loadOpenEngine(): MutableList<Session> {
        val out = ArrayList<Session>()
        val c = db.rawQuery(
            "SELECT id, seq, stream, kind, subject, block, planned_min, status, skip_count FROM sessions WHERE status='todo' ORDER BY seq ASC",
            null
        )
        try {
            while (c.moveToNext()) {
                val s = Session()
                s.id = c.getLong(0)
                s.seq = c.getLong(1)
                s.stream = c.getString(2)[0]
                s.kind = c.getString(3)
                s.subject = c.getString(4) ?: ""
                s.block = c.getString(5) ?: ""
                s.plannedMin = c.getInt(6)
                s.status = c.getString(7)
                s.skipCount = c.getInt(8)
                out.add(s)
            }
        } finally {
            c.close()
        }
        return out
    }

    private fun saveOrder(open: List<Session>) {
        db.beginTransaction()
        try {
            for (s in open) {
                db.execSQL("UPDATE sessions SET seq=?, skip_count=? WHERE id=?", arrayOf<Any>(s.seq, s.skipCount, s.id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun canSkip(sessionId: Long): Boolean {
        val open = loadOpenEngine()
        val idx = open.indexOfFirst { s -> s.id == sessionId }
        if (idx < 0) return false
        return SkipSwap.partner(open, idx) != SkipSwap.NONE
    }

    fun skip(sessionId: Long): Boolean {
        val open = loadOpenEngine()
        val idx = open.indexOfFirst { s -> s.id == sessionId }
        if (idx < 0) return false
        val p = SkipSwap.partner(open, idx)
        if (p == SkipSwap.NONE) return false
        if (p == SkipSwap.DROP) {
            db.execSQL(
                "UPDATE sessions SET status='skipped', skip_count=skip_count+1, done_day=?, done_ts=? WHERE id=?",
                arrayOf<Any>(todayKey(), System.currentTimeMillis(), sessionId)
            )
            return true
        }
        if (!SkipSwap.apply(open, idx)) return false
        saveOrder(open)
        return true
    }

    /** Keeps the queue from running dry once the plan content is used up. */
    fun ensureQueueTail() {
        val open = loadOpenEngine()
        if (open.size >= 8) return
        val blocks = ArrayList<String>()
        val c = db.rawQuery("SELECT subject FROM sessions WHERE stream='G' AND kind='plan' GROUP BY subject ORDER BY MIN(id)", null)
        try {
            while (c.moveToNext()) blocks.add(c.getString(0))
        } finally {
            c.close()
        }
        val practiceCount = scalarInt("SELECT COUNT(*) FROM sessions WHERE kind='practice'")
        val block = if (blocks.isEmpty()) "GS" else blocks[practiceCount % blocks.size]
        val last = scalarLong("SELECT COALESCE(MAX(seq),0) FROM sessions")
        var seq = last
        db.beginTransaction()
        try {
            for (s in QueueBuilder.composedCycle(block)) {
                seq += 1000
                s.seq = seq
                insertSession(s)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun scalarInt(sql: String): Int {
        val c = db.rawQuery(sql, null)
        try {
            return if (c.moveToFirst()) c.getInt(0) else 0
        } finally {
            c.close()
        }
    }

    private fun scalarLong(sql: String): Long {
        val c = db.rawQuery(sql, null)
        try {
            return if (c.moveToFirst()) c.getLong(0) else 0L
        } finally {
            c.close()
        }
    }

    // ------------------------------------------------------------------ revision content

    private fun loadDoneTopics(): List<ReviewScheduler.Done> {
        val out = ArrayList<ReviewScheduler.Done>()
        val c = db.rawQuery("SELECT item, title, tier, done_day, d1, d7, d30 FROM reviews", null)
        try {
            while (c.moveToNext()) {
                out.add(
                    ReviewScheduler.Done(
                        c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3),
                        c.getInt(4) == 1, c.getInt(5) == 1, c.getInt(6) == 1
                    )
                )
            }
        } finally {
            c.close()
        }
        return out
    }

    private fun ensureRevisionPieces(sessionId: Long) {
        val c = db.rawQuery("SELECT kind, planned_min FROM sessions WHERE id=?", arrayOf(sessionId.toString()))
        var kind = ""
        var planned = 0
        try {
            if (c.moveToFirst()) {
                kind = c.getString(0)
                planned = c.getInt(1)
            }
        } finally {
            c.close()
        }
        if (kind != "revision") return
        if (scalarInt("SELECT COUNT(*) FROM pieces WHERE session_id=$sessionId") > 0) return
        val due = ReviewScheduler.compute(loadDoneTopics(), todayKey(), ReviewScheduler.CAP)
        var ord = 0
        for (d in due) {
            val p = Piece()
            p.item = d.itemId
            p.kind = "review"
            p.title = "Revise: " + d.title
            p.tier = d.tier
            p.minutes = ReviewScheduler.MIN_PER_TOPIC
            p.unit = d.interval.toString()
            p.objective = "Recall the key points from memory, then check your notebook (due at " + d.interval + " days)."
            insertPiece(sessionId, ord++, p)
        }
        val lastBlock = lastCompletedGsBlock()
        val q = Piece()
        q.kind = "pyq"
        q.title = "Question practice"
        q.minutes = ReviewScheduler.pyqMinutes(due.size, planned)
        q.objective = "Use the question sets in your own books for " + lastBlock +
            "; where a book has none, write Mains-style answer outlines for what you covered."
        insertPiece(sessionId, ord, q)
    }

    private fun lastCompletedGsBlock(): String {
        val c = db.rawQuery(
            "SELECT subject FROM sessions WHERE status='done' AND stream='G' AND kind='plan' ORDER BY done_ts DESC LIMIT 1", null
        )
        try {
            if (c.moveToFirst()) return c.getString(0) ?: "your current block"
        } finally {
            c.close()
        }
        return "your current block"
    }

    // ------------------------------------------------------------------ finishing

    fun finish(sessionId: Long, problems: Int) {
        val now = System.currentTimeMillis()
        val ps = pieces(sessionId)
        val srow = session(sessionId) ?: return
        val timer = timer()
        if (timer != null && timer.running && timer.sessionId == sessionId) timerPause(now)
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE sessions SET status='done', done_day=?, done_ts=?, problems=? WHERE id=?",
                arrayOf<Any>(dayKeyOf(now), now, problems, sessionId)
            )
            val today = dayKeyOf(now)
            for (p in ps) {
                if (!p.ticked) continue
                if (srow.stream == "R") {
                    if (p.kind == "review" && p.item != null) {
                        val col = when (p.unit) {
                            "1" -> "d1"
                            "7" -> "d7"
                            else -> "d30"
                        }
                        db.execSQL("UPDATE reviews SET $col=1 WHERE item=?", arrayOf<Any>(p.item))
                    }
                } else if (p.item != null && p.f1 >= 0.9995) {
                    val cv = ContentValues()
                    cv.put("item", p.item)
                    cv.put("title", p.title)
                    cv.put("tier", p.tier)
                    cv.put("done_day", today)
                    db.insertWithOnConflict("reviews", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
            val eng = Session()
            eng.id = sessionId
            eng.stream = srow.stream[0]
            eng.kind = srow.kind
            eng.subject = srow.subject
            eng.block = srow.block
            eng.plannedMin = srow.plannedMin
            for (p in ps) {
                val ep = Piece()
                ep.item = p.item; ep.kind = p.kind; ep.title = p.title; ep.resource = p.resource; ep.tier = p.tier
                ep.pages = p.pages; ep.mode = p.mode; ep.objective = p.objective; ep.syllabus = p.syllabus
                ep.pyq = p.pyq; ep.note = p.note; ep.prerequisites = p.prereq; ep.unit = p.unit
                ep.minutes = p.minutes; ep.f0 = p.f0; ep.f1 = p.f1; ep.approx = p.approx; ep.ticked = p.ticked
                eng.pieces.add(ep)
            }
            val carry = CarryOver.make(eng)
            if (carry != null) {
                val open = loadOpenEngine()
                CarryOver.insert(open, carry)
                // the carry session has id 0 until inserted
                for (s in open) {
                    if (s === carry) {
                        insertSession(s)
                    }
                }
                saveOrder(open)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        ensureQueueTail()
    }

    /** Removes the session's logged time from the log and stops its timer. */
    fun resetTimer(sessionId: Long) {
        val t = timer()
        if (t != null && t.sessionId == sessionId) db.execSQL("DELETE FROM timer")
        db.execSQL("DELETE FROM log_entries WHERE session_id=?", arrayOf<Any>(sessionId))
    }

    // ------------------------------------------------------------------ timer

    fun timer(): TimerState? {
        val c = db.rawQuery("SELECT session_id, running, started_at FROM timer WHERE id=1", null)
        try {
            if (c.moveToFirst()) return TimerState(c.getLong(0), c.getInt(1) == 1, c.getLong(2))
        } finally {
            c.close()
        }
        return null
    }

    fun timerStart(sessionId: Long, now: Long) {
        val cv = ContentValues()
        cv.put("id", 1)
        cv.put("session_id", sessionId)
        cv.put("running", 1)
        cv.put("started_at", now)
        db.insertWithOnConflict("timer", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Writes the running segment to the log and marks the timer paused. */
    fun timerPause(now: Long) {
        val t = timer() ?: return
        if (t.running) {
            val ms = now - t.startedAt
            if (ms > 0) {
                var stream = "M"
                var label = ""
                val c = db.rawQuery("SELECT stream, subject FROM sessions WHERE id=?", arrayOf(t.sessionId.toString()))
                try {
                    if (c.moveToFirst()) {
                        stream = c.getString(0)
                        label = c.getString(1) ?: ""
                    }
                } finally {
                    c.close()
                }
                addLog(t.sessionId, stream, label, t.startedAt, ms, "timer")
            }
        }
        db.execSQL("UPDATE timer SET running=0 WHERE id=1")
    }

    fun loggedMs(sessionId: Long): Long =
        scalarLong("SELECT COALESCE(SUM(ms),0) FROM log_entries WHERE session_id=$sessionId")

    private fun addLog(sessionId: Long, stream: String, label: String, startTs: Long, ms: Long, source: String): Long {
        val cv = ContentValues()
        cv.put("session_id", sessionId)
        cv.put("stream", stream)
        cv.put("label", label)
        cv.put("start_ts", startTs)
        cv.put("ms", ms)
        cv.put("day", dayKeyOf(startTs))
        cv.put("source", source)
        return db.insert("log_entries", null, cv)
    }

    // ------------------------------------------------------------------ log and statistics

    fun dayTotals(): Map<Int, Long> {
        val out = HashMap<Int, Long>()
        val c = db.rawQuery("SELECT day, SUM(ms) FROM log_entries GROUP BY day", null)
        try {
            while (c.moveToNext()) out[c.getInt(0)] = c.getLong(1)
        } finally {
            c.close()
        }
        return out
    }

    fun totalMs(): Long = scalarLong("SELECT COALESCE(SUM(ms),0) FROM log_entries")

    /** Epoch day of the first log entry, or -1 when nothing is logged. */
    fun firstDay(): Int {
        val c = db.rawQuery("SELECT MIN(day) FROM log_entries", null)
        try {
            if (c.moveToFirst() && !c.isNull(0)) return c.getInt(0)
        } finally {
            c.close()
        }
        return -1
    }

    fun lastLogEnd(): Long {
        val c = db.rawQuery("SELECT MAX(start_ts + ms) FROM log_entries", null)
        try {
            if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
        } finally {
            c.close()
        }
        return 0L
    }

    fun entriesForDay(day: Int): List<LogRow> {
        val out = ArrayList<LogRow>()
        val c = db.rawQuery(
            "SELECT l.id, COALESCE(l.session_id,0), l.stream, COALESCE(s.subject, l.label, ''), l.ms, l.day, l.source " +
                "FROM log_entries l LEFT JOIN sessions s ON s.id=l.session_id WHERE l.day=? ORDER BY l.start_ts ASC",
            arrayOf(day.toString())
        )
        try {
            while (c.moveToNext()) {
                out.add(LogRow(c.getLong(0), c.getLong(1), c.getString(2) ?: "M", c.getString(3) ?: "", c.getLong(4), c.getInt(5), c.getString(6) ?: ""))
            }
        } finally {
            c.close()
        }
        return out
    }

    fun updateEntryMs(id: Long, ms: Long) {
        db.execSQL("UPDATE log_entries SET ms=? WHERE id=?", arrayOf<Any>(ms, id))
    }

    fun deleteEntry(id: Long) {
        db.execSQL("DELETE FROM log_entries WHERE id=?", arrayOf<Any>(id))
    }

    fun addManual(day: Int, stream: String, label: String, ms: Long) {
        val cv = ContentValues()
        cv.put("session_id", 0)
        cv.put("stream", stream)
        cv.put("label", label)
        cv.put("start_ts", day * 86400000L + 12 * 3600000L)
        cv.put("ms", ms)
        cv.put("day", day)
        cv.put("source", "manual")
        db.insert("log_entries", null, cv)
    }

    fun subjectTotals(): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        val c = db.rawQuery(
            "SELECT COALESCE(NULLIF(s.subject,''), NULLIF(l.label,''), 'Other') AS name, SUM(l.ms) AS t " +
                "FROM log_entries l LEFT JOIN sessions s ON s.id=l.session_id GROUP BY name ORDER BY t DESC LIMIT 10",
            null
        )
        try {
            while (c.moveToNext()) out.add(Pair(c.getString(0) ?: "", c.getLong(1)))
        } finally {
            c.close()
        }
        return out
    }

    fun streamTotals(): Map<String, Long> {
        val out = HashMap<String, Long>()
        val c = db.rawQuery("SELECT stream, SUM(ms) FROM log_entries GROUP BY stream", null)
        try {
            while (c.moveToNext()) out[c.getString(0) ?: "M"] = c.getLong(1)
        } finally {
            c.close()
        }
        return out
    }

    fun recentPlannedActual(n: Int): List<PlannedActual> {
        val out = ArrayList<PlannedActual>()
        val c = db.rawQuery(
            "SELECT s.id, s.subject, s.planned_min, (SELECT COALESCE(SUM(l.ms),0) FROM log_entries l WHERE l.session_id=s.id) " +
                "FROM sessions s WHERE s.status='done' ORDER BY s.done_ts DESC LIMIT $n",
            null
        )
        try {
            while (c.moveToNext()) out.add(PlannedActual(c.getString(1) ?: "", c.getInt(2), (c.getLong(3) / 60000L).toInt()))
        } finally {
            c.close()
        }
        out.reverse()
        return out
    }

    fun remainingPlannedMs(): Long =
        scalarLong("SELECT COALESCE(SUM(planned_min),0) FROM sessions WHERE status='todo'") * 60000L

    /** Pages per hour and problems per hour from finished sessions, or null when there is no data. */
    fun paceFigures(): Pair<Double, Double> {
        var pages = 0.0
        var pageMs = 0L
        var problems = 0.0
        var problemMs = 0L
        val c = db.rawQuery(
            "SELECT s.id, s.stream, s.problems, (SELECT COALESCE(SUM(l.ms),0) FROM log_entries l WHERE l.session_id=s.id) " +
                "FROM sessions s WHERE s.status='done'",
            null
        )
        val rows = ArrayList<LongArray>()
        try {
            while (c.moveToNext()) {
                rows.add(longArrayOf(c.getLong(0), if (c.getString(1) == "M") 1L else 0L, c.getLong(2), c.getLong(3)))
            }
        } finally {
            c.close()
        }
        for (r in rows) {
            val ms = r[3]
            if (ms <= 0) continue
            if (r[1] == 1L && r[2] > 0) {
                problems += r[2].toDouble()
                problemMs += ms
            }
            var sessionPages = 0
            val pc = db.rawQuery("SELECT pages FROM pieces WHERE session_id=? AND ticked=1 AND item IS NOT NULL AND kind<>'lec'", arrayOf(r[0].toString()))
            try {
                while (pc.moveToNext()) sessionPages += PageMap.pageCount(pc.getString(0))
            } finally {
                pc.close()
            }
            if (sessionPages > 0) {
                pages += sessionPages
                pageMs += ms
            }
        }
        val pph = if (pageMs > 0) pages / (pageMs / 3600000.0) else 0.0
        val prh = if (problemMs > 0) problems / (problemMs / 3600000.0) else 0.0
        return Pair(pph, prh)
    }

    // ------------------------------------------------------------------ notes

    fun notes(): List<NoteRow> {
        val out = ArrayList<NoteRow>()
        val c = db.rawQuery("SELECT id, ts, COALESCE(session_id,0), text FROM notes ORDER BY ts DESC", null)
        try {
            while (c.moveToNext()) out.add(NoteRow(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3) ?: ""))
        } finally {
            c.close()
        }
        return out
    }

    fun addNote(text: String, sessionId: Long) {
        val cv = ContentValues()
        cv.put("ts", System.currentTimeMillis())
        cv.put("session_id", sessionId)
        cv.put("text", text)
        db.insert("notes", null, cv)
    }

    fun updateNote(id: Long, text: String) {
        db.execSQL("UPDATE notes SET text=? WHERE id=?", arrayOf<Any>(text, id))
    }

    fun deleteNote(id: Long) {
        db.execSQL("DELETE FROM notes WHERE id=?", arrayOf<Any>(id))
    }
}
