package com.yash.upsc400v2

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.yash.upsc400v2.data.Store
import com.yash.upsc400v2.data.book
import com.yash.upsc400v2.data.setBookUri
import com.yash.upsc400v2.timer.TimerController

/** Which part of which book the reader should open, and the session it belongs to (if any). */
data class ReaderArgs(val res: String, val startPrinted: Int, val endPrinted: Int, val sessionId: Long)

class AppModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val timer = TimerController(app, store)

    var ready by mutableStateOf(false)
    var rev by mutableStateOf(0)
    var tab by mutableStateOf(0)
    var openSession by mutableStateOf<Long?>(null)
    var showHistory by mutableStateOf(false)
    var reader by mutableStateOf<ReaderArgs?>(null)
    var confetti by mutableStateOf(0)
    var cheer by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    private var pending: ReaderArgs? = null

    /** Set by the activity: opens the system file picker for the given book key. */
    var pickPdf: (String) -> Unit = {}
    var exportBackupFile: () -> Unit = {}
    var importBackupFile: () -> Unit = {}

    init {
        Thread {
            if (!store.isSeeded()) store.seed()
            store.ensureQueueTail()
            Handler(Looper.getMainLooper()).post { ready = true }
        }.start()
    }

    fun bump() {
        rev++
    }

    /** Opens the reader, asking for the PDF first when the book has none attached. */
    fun openReader(args: ReaderArgs) {
        val b = store.book(args.res)
        if (b == null || b.uri == null) {
            pending = args
            pickPdf(args.res)
        } else {
            reader = args
        }
    }

    fun attachPdf(res: String, uri: Uri) {
        store.setBookUri(res, uri.toString())
        bump()
        val p = pending
        pending = null
        if (p != null && p.res == res) reader = p
    }

    fun cancelPending() {
        pending = null
    }
}
