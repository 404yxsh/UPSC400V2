package com.yash.upsc400v2.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "upsc400v2.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        for (sql in SCHEMA) db.execSQL(sql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first v2 schema; nothing to migrate.
    }

    companion object {
        val SCHEMA: List<String> = listOf(
            "CREATE TABLE plan_items(id TEXT PRIMARY KEY, res TEXT, rname TEXT, title TEXT, tier TEXT, kind TEXT, unit TEXT, block TEXT, pages INTEGER, p0 INTEGER, p1 INTEGER, approx INTEGER, total_min INTEGER, ord INTEGER)",
            "CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT, seq INTEGER, stream TEXT, kind TEXT, plan_index INTEGER, subject TEXT, block TEXT, planned_min INTEGER, status TEXT, skip_count INTEGER DEFAULT 0, summary TEXT, problems INTEGER DEFAULT -1, done_day INTEGER DEFAULT -1, done_ts INTEGER DEFAULT 0)",
            "CREATE INDEX idx_sessions_status_seq ON sessions(status, seq)",
            "CREATE TABLE pieces(id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER, ord INTEGER, item TEXT, kind TEXT, title TEXT, resource TEXT, tier TEXT, pages TEXT, minutes INTEGER, f0 REAL, f1 REAL, approx INTEGER, ticked INTEGER DEFAULT 0, mode TEXT, objective TEXT, syllabus TEXT, pyq TEXT, note TEXT, prereq TEXT, unit TEXT)",
            "CREATE INDEX idx_pieces_session ON pieces(session_id)",
            "CREATE TABLE timer(id INTEGER PRIMARY KEY, session_id INTEGER, running INTEGER, started_at INTEGER)",
            "CREATE TABLE log_entries(id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER, stream TEXT, label TEXT, start_ts INTEGER, ms INTEGER, day INTEGER, source TEXT)",
            "CREATE INDEX idx_log_day ON log_entries(day)",
            "CREATE INDEX idx_log_session ON log_entries(session_id)",
            "CREATE TABLE books(res TEXT PRIMARY KEY, name TEXT, uri TEXT, page_offset INTEGER DEFAULT 0, last_pdf_page INTEGER DEFAULT 1, calibrated INTEGER DEFAULT 0)",
            "CREATE TABLE item_pages(item TEXT PRIMARY KEY, p0 INTEGER, p1 INTEGER)",
            "CREATE TABLE bookmarks(id INTEGER PRIMARY KEY AUTOINCREMENT, res TEXT, pdf_page INTEGER, label TEXT)",
            "CREATE TABLE reviews(item TEXT PRIMARY KEY, title TEXT, tier TEXT, done_day INTEGER, d1 INTEGER DEFAULT 0, d7 INTEGER DEFAULT 0, d30 INTEGER DEFAULT 0)",
            "CREATE TABLE notes(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, session_id INTEGER, text TEXT)",
            "CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT)"
        )

        /** Tables included in a backup (plan_items is re-seeded from the app, timer is transient). */
        val BACKUP_TABLES: List<String> = listOf(
            "sessions", "pieces", "log_entries", "books", "item_pages", "bookmarks", "reviews", "notes", "settings"
        )
    }
}
