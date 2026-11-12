package com.yash.upsc400v2.reader

import android.content.Context
import android.net.Uri
import android.os.Build

/**
 * Text search through android.graphics.pdf.PdfRendererPreV (Android 15, or Android 13/14 with SDK extension 13).
 * The class is reached by reflection so the project still compiles with compileSdk 34. It returns the PDF pages
 * (0-based) that contain the text, or null when the device cannot search this file.
 */
object PreVSearch {

    fun mightBeAvailable(): Boolean = Build.VERSION.SDK_INT >= 35

    fun search(
        ctx: Context,
        uri: Uri,
        query: String,
        onProgress: (Int, Int) -> Unit,
        isCancelled: () -> Boolean
    ): List<Int>? {
        val pfd: android.os.ParcelFileDescriptor? = try {
            ctx.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Throwable) {
            null
        }
        if (pfd == null) return null
        try {
            val cls = Class.forName("android.graphics.pdf.PdfRendererPreV")
            val renderer = cls.getConstructor(android.os.ParcelFileDescriptor::class.java).newInstance(pfd)
            try {
                val count = cls.getMethod("getPageCount").invoke(renderer) as Int
                val open = cls.getMethod("openPage", Int::class.javaPrimitiveType)
                val results = ArrayList<Int>()
                for (i in 0 until count) {
                    if (isCancelled()) break
                    val page = open.invoke(renderer, i) ?: continue
                    try {
                        val found = page.javaClass.getMethod("searchText", String::class.java).invoke(page, query) as? List<*>
                        if (found != null && found.isNotEmpty()) results.add(i)
                    } finally {
                        page.javaClass.getMethod("close").invoke(page)
                    }
                    if (i % 10 == 0) onProgress(i, count)
                }
                return results
            } finally {
                try {
                    cls.getMethod("close").invoke(renderer)
                } catch (e: Throwable) {
                    // ignore
                }
            }
        } catch (e: Throwable) {
            return null
        } finally {
            try {
                pfd.close()
            } catch (e: Throwable) {
                // ignore
            }
        }
    }
}
