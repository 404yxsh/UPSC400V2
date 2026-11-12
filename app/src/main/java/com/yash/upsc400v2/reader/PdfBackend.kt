package com.yash.upsc400v2.reader

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.content.Context
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** What the reader needs from a PDF engine. A PDFium backend can be added later behind this interface. */
interface PdfBackend {
    val pageCount: Int
    /** Page size in points (width, height), or null on failure. */
    suspend fun pageSize(index: Int): IntArray?
    /** Whole page rendered to the given pixel width. */
    suspend fun renderBase(index: Int, widthPx: Int): Bitmap?
    /** A rectangle of the page as it appears at displayWidthPx wide; the rectangle is in that display's pixels. */
    suspend fun renderTile(index: Int, displayWidthPx: Int, left: Int, top: Int, width: Int, height: Int): Bitmap?
    fun close()
}

/** Backend on the system PdfRenderer: one page open at a time, so every call runs on a single thread. */
class FrameworkPdfBackend private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : PdfBackend {

    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()

    override val pageCount: Int = renderer.pageCount

    override suspend fun pageSize(index: Int): IntArray? = withContext(dispatcher) {
        try {
            val page = renderer.openPage(index)
            val r = intArrayOf(page.width, page.height)
            page.close()
            r
        } catch (e: Throwable) {
            null
        }
    }

    override suspend fun renderBase(index: Int, widthPx: Int): Bitmap? = withContext(dispatcher) {
        try {
            val page = renderer.openPage(index)
            try {
                val w = widthPx.coerceIn(200, 2400)
                val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                val m = Matrix()
                m.setScale(w.toFloat() / page.width, h.toFloat() / page.height)
                page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            } finally {
                page.close()
            }
        } catch (e: Throwable) {
            null
        }
    }

    override suspend fun renderTile(index: Int, displayWidthPx: Int, left: Int, top: Int, width: Int, height: Int): Bitmap? =
        withContext(dispatcher) {
            try {
                val page = renderer.openPage(index)
                try {
                    val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    val scale = displayWidthPx.toFloat() / page.width
                    val m = Matrix()
                    m.setScale(scale, scale)
                    m.postTranslate(-left.toFloat(), -top.toFloat())
                    page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                } finally {
                    page.close()
                }
            } catch (e: Throwable) {
                null
            }
        }

    override fun close() {
        try {
            executor.execute {
                try {
                    renderer.close()
                } catch (e: Throwable) {
                    // already closed
                }
                try {
                    pfd.close()
                } catch (e: Throwable) {
                    // already closed
                }
            }
            executor.shutdown()
        } catch (e: Throwable) {
            // executor already shut down
        }
    }

    companion object {
        /** Opens the document, or returns null when it cannot be read. Call off the main thread. */
        fun open(ctx: Context, uri: Uri): PdfBackend? {
            return try {
                val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return null
                try {
                    FrameworkPdfBackend(pfd, PdfRenderer(pfd))
                } catch (e: Throwable) {
                    try {
                        pfd.close()
                    } catch (e2: Throwable) {
                        // ignore
                    }
                    null
                }
            } catch (e: Throwable) {
                null
            }
        }
    }
}
