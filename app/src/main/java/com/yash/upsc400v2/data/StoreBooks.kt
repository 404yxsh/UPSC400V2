package com.yash.upsc400v2.data

import android.content.ContentValues
import android.database.Cursor
import com.yash.upsc400v2.engine.BackupCodec
import com.yash.upsc400v2.engine.PageMap

private val GS_NAMES = mapOf(
    "POL" to "Polity and Constitution", "GOV" to "Governance and Social Justice", "GEO" to "Geography",
    "ENV" to "Environment and Ecology", "ECO" to "Economy and Agriculture", "ST" to "Science and Technology",
    "ANC" to "Ancient India", "MED" to "Medieval India", "ART" to "Art and Culture",
    "MOD" to "Modern India and Post-independence", "WORLD" to "World History", "SOC" to "Indian Society",
    "IR" to "International Relations", "IS" to "Internal Security", "DM" to "Disaster Management",
    "ETH" to "Ethics, Integrity and Aptitude"
)

private val MATH_NAMES = mapOf(
    "LA" to "Linear Algebra", "CAL" to "Calculus", "AG" to "Analytic Geometry", "VEC" to "Vector Analysis",
    "ODE" to "Ordinary Differential Equations", "DS" to "Dynamics and Statics", "ALG" to "Algebra",
    "RA" to "Real Analysis", "CX" to "Complex Analysis", "LPP" to "Linear Programming", "PDE" to "Partial Differential Equations",
    "NUM" to "Numerical Analysis", "MECH" to "Mechanics", "FD" to "Fluid Dynamics"
)

/** Printed page range text for a piece, honouring calibration. */
fun pagesText(item: String?, f0: Double, f1: Double, plain: String?, map: Map<String, IntArray>): String? {
    if (item != null) {
        val ov = map[item]
        if (ov != null) return PageMap.format(PageMap.pieceRange(ov[0], ov[1], f0, f1))
    }
    return plain
}

fun Store.itemPages(): Map<String, IntArray> {
    val out = HashMap<String, IntArray>()
    val c = db.rawQuery("SELECT item, p0, p1 FROM item_pages", null)
    try {
        while (c.moveToNext()) out[c.getString(0)] = intArrayOf(c.getInt(1), c.getInt(2))
    } finally {
        c.close()
    }
    return out
}

fun Store.itemRes(itemId: String): String? {
    val c = db.rawQuery("SELECT res FROM plan_items WHERE id=? AND kind IN ('gs','math')", arrayOf(itemId))
    try {
        if (c.moveToFirst()) return c.getString(0)
    } finally {
        c.close()
    }
    return null
}

private fun Store.progressByRes(): Map<String, Double> {
    val out = HashMap<String, Double>()
    val c = db.rawQuery(
        "SELECT i.res, SUM(i.total_min), SUM(CASE WHEN c.cov IS NULL THEN 0 WHEN c.cov>1 THEN i.total_min ELSE c.cov*i.total_min END) " +
            "FROM plan_items i LEFT JOIN (SELECT item, SUM(f1-f0) cov FROM pieces WHERE ticked=1 GROUP BY item) c ON c.item=i.id " +
            "WHERE i.kind IN ('gs','math') GROUP BY i.res",
        null
    )
    try {
        while (c.moveToNext()) {
            val tot = c.getDouble(1)
            out[c.getString(0)] = if (tot > 0) c.getDouble(2) / tot else 0.0
        }
    } finally {
        c.close()
    }
    return out
}

private fun readBook(c: Cursor, progress: Map<String, Double>, approx: Set<String>): BookRow = BookRow(
    res = c.getString(0), name = c.getString(1) ?: "", uri = c.getString(2), offset = c.getInt(3),
    lastPage = c.getInt(4), calibrated = c.getInt(5) == 1, hasApprox = approx.contains(c.getString(0)),
    progress = progress[c.getString(0)] ?: 0.0
)

private fun Store.approxResources(): Set<String> {
    val out = HashSet<String>()
    val c = db.rawQuery("SELECT DISTINCT res FROM plan_items WHERE approx=1", null)
    try {
        while (c.moveToNext()) out.add(c.getString(0))
    } finally {
        c.close()
    }
    return out
}

fun Store.books(): List<BookRow> {
    val prog = progressByRes()
    val approx = approxResources()
    val out = ArrayList<BookRow>()
    val c = db.rawQuery(
        "SELECT b.res, b.name, b.uri, b.page_offset, b.last_pdf_page, b.calibrated FROM books b " +
            "ORDER BY (SELECT MIN(ord) FROM plan_items i WHERE i.res=b.res)",
        null
    )
    try {
        while (c.moveToNext()) out.add(readBook(c, prog, approx))
    } finally {
        c.close()
    }
    return out
}

fun Store.book(res: String): BookRow? = books().firstOrNull { b -> b.res == res }

fun Store.setBookUri(res: String, uri: String) {
    db.execSQL("UPDATE books SET uri=? WHERE res=?", arrayOf<Any>(uri, res))
}

fun Store.setBookOffset(res: String, offset: Int) {
    db.execSQL("UPDATE books SET page_offset=? WHERE res=?", arrayOf<Any>(offset, res))
}

fun Store.setLastPage(res: String, pdfPage: Int) {
    db.execSQL("UPDATE books SET last_pdf_page=? WHERE res=?", arrayOf<Any>(pdfPage, res))
}

fun Store.bookItems(res: String): List<ItemRow> {
    val ov = itemPages()
    val out = ArrayList<ItemRow>()
    val c = db.rawQuery(
        "SELECT id, title, tier, p0, p1, approx FROM plan_items WHERE res=? AND kind IN ('gs','math') ORDER BY ord",
        arrayOf(res)
    )
    try {
        while (c.moveToNext()) {
            val id = c.getString(0)
            val o = ov[id]
            out.add(
                ItemRow(
                    id, c.getString(1) ?: "", c.getString(2) ?: "",
                    o?.get(0) ?: c.getInt(3), o?.get(1) ?: c.getInt(4), c.getInt(5) == 1
                )
            )
        }
    } finally {
        c.close()
    }
    return out
}

/** Saves typed chapter start pages; each end is the next start minus one, the last end is lastPage. */
fun Store.saveCalibration(res: String, items: List<ItemRow>, starts: IntArray, lastPage: Int) {
    val ranges = PageMap.calibrate(starts, lastPage)
    db.beginTransaction()
    try {
        for (k in items.indices) {
            val cv = ContentValues()
            cv.put("item", items[k].id)
            cv.put("p0", ranges[k][0])
            cv.put("p1", ranges[k][1])
            db.insertWithOnConflict("item_pages", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        }
        db.execSQL("UPDATE books SET calibrated=1 WHERE res=?", arrayOf<Any>(res))
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

fun Store.bookmarks(res: String): List<BookmarkRow> {
    val out = ArrayList<BookmarkRow>()
    val c = db.rawQuery("SELECT id, res, pdf_page, label FROM bookmarks WHERE res=? ORDER BY pdf_page", arrayOf(res))
    try {
        while (c.moveToNext()) out.add(BookmarkRow(c.getLong(0), c.getString(1), c.getInt(2), c.getString(3) ?: ""))
    } finally {
        c.close()
    }
    return out
}

fun Store.addBookmark(res: String, pdfPage: Int, label: String) {
    val cv = ContentValues()
    cv.put("res", res)
    cv.put("pdf_page", pdfPage)
    cv.put("label", label)
    db.insert("bookmarks", null, cv)
}

fun Store.deleteBookmark(id: Long) {
    db.execSQL("DELETE FROM bookmarks WHERE id=?", arrayOf<Any>(id))
}

/** Progress per syllabus heading (GS blocks and Mathematics units). */
fun Store.syllabusProgress(): List<ProgressRow> {
    val out = ArrayList<ProgressRow>()
    val c = db.rawQuery(
        "SELECT CASE WHEN i.block='MATH' THEN 'M:' || COALESCE(i.unit,'') ELSE i.block END AS grp, SUM(i.total_min), " +
            "SUM(CASE WHEN c.cov IS NULL THEN 0 WHEN c.cov>1 THEN i.total_min ELSE c.cov*i.total_min END), MIN(i.ord) AS o " +
            "FROM plan_items i LEFT JOIN (SELECT item, SUM(f1-f0) cov FROM pieces WHERE ticked=1 GROUP BY item) c ON c.item=i.id " +
            "GROUP BY grp ORDER BY o",
        null
    )
    try {
        while (c.moveToNext()) {
            val key = c.getString(0) ?: ""
            val name = if (key.startsWith("M:")) "Mathematics: " + (MATH_NAMES[key.substring(2)] ?: key.substring(2)) else (GS_NAMES[key] ?: key)
            out.add(ProgressRow(key, name, c.getDouble(1), c.getDouble(2)))
        }
    } finally {
        c.close()
    }
    return out
}

// ---------------------------------------------------------------------- backup

fun Store.exportBackup(): String {
    val tables = LinkedHashMap<String, List<Map<String, Any?>>>()
    for (t in Db.BACKUP_TABLES) {
        val rows = ArrayList<Map<String, Any?>>()
        val c = db.rawQuery("SELECT * FROM $t", null)
        try {
            val names = c.columnNames
            while (c.moveToNext()) {
                val row = LinkedHashMap<String, Any?>()
                for (i in names.indices) {
                    row[names[i]] = when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        else -> c.getString(i)
                    }
                }
                if (t == "settings" && row["k"] == "seeded") continue
                rows.add(row)
            }
        } finally {
            c.close()
        }
        tables[t] = rows
    }
    @Suppress("UNCHECKED_CAST")
    val typed = tables as Map<String, List<Map<String, Any>>>
    return BackupCodec.encode(typed, System.currentTimeMillis())
}

/** Replaces all user data with the backup. Throws IllegalArgumentException for a wrong or newer file. */
fun Store.importBackup(json: String) {
    val data = BackupCodec.decode(json)
    db.beginTransaction()
    try {
        for (t in Db.BACKUP_TABLES) {
            if (t == "settings") db.execSQL("DELETE FROM settings WHERE k<>'seeded'") else db.execSQL("DELETE FROM $t")
        }
        db.execSQL("DELETE FROM timer")
        for (t in Db.BACKUP_TABLES) {
            val rows = data[t] ?: continue
            for (row in rows) {
                val cv = ContentValues()
                for ((k, v) in row) {
                    when (v) {
                        null -> cv.putNull(k)
                        is Long -> cv.put(k, v)
                        is Int -> cv.put(k, v.toLong())
                        is Double -> cv.put(k, v)
                        is Boolean -> cv.put(k, if (v) 1L else 0L)
                        else -> cv.put(k, v.toString())
                    }
                }
                db.insertWithOnConflict(t, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
        db.execSQL("INSERT OR IGNORE INTO books(res, name) SELECT res, rname FROM plan_items WHERE kind IN ('gs','math') GROUP BY res")
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}
