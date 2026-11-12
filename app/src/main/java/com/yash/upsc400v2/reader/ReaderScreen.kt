package com.yash.upsc400v2.reader

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.util.LruCache
import com.yash.upsc400v2.AppModel
import com.yash.upsc400v2.ReaderArgs
import com.yash.upsc400v2.data.BookRow
import com.yash.upsc400v2.data.addBookmark
import com.yash.upsc400v2.data.book
import com.yash.upsc400v2.data.bookItems
import com.yash.upsc400v2.data.bookmarks
import com.yash.upsc400v2.data.deleteBookmark
import com.yash.upsc400v2.data.setLastPage
import com.yash.upsc400v2.engine.PageMap
import com.yash.upsc400v2.engine.TimerMath
import com.yash.upsc400v2.ui.ButtonRow
import com.yash.upsc400v2.ui.Gap
import com.yash.upsc400v2.ui.LocalPal
import com.yash.upsc400v2.ui.NbButton
import com.yash.upsc400v2.ui.NbChip
import com.yash.upsc400v2.ui.NbDialog
import com.yash.upsc400v2.ui.NbField
import com.yash.upsc400v2.ui.T
import com.yash.upsc400v2.ui.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NIGHT = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.9f, 0f, 0f, 0f, 240f,
            0f, -0.9f, 0f, 0f, 240f,
            0f, 0f, -0.9f, 0f, 240f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

private val SEPIA = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.96f, 0f, 0f, 0f, 0f,
            0f, 0.90f, 0f, 0f, 0f,
            0f, 0f, 0.74f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

private class PageSizes(count: Int) {
    val w = IntArray(count)
    val h = IntArray(count)
    var defAspect = 1.414f

    fun aspect(i: Int): Float = if (h[i] > 0 && w[i] > 0) h[i].toFloat() / w[i] else defAspect

    fun set(i: Int, s: IntArray) {
        w[i] = s[0]
        h[i] = s[1]
        if (s[0] > 0) defAspect = s[1].toFloat() / s[0]
    }
}

private class PageCache(maxBytes: Int) {
    private val lru = object : LruCache<Int, Bitmap>(maxBytes) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    fun get(i: Int): Bitmap? = lru.get(i)

    fun put(i: Int, b: Bitmap) {
        lru.put(i, b)
    }
}

private class Tile(val bmp: Bitmap, val left: Int, val top: Int, val dispW: Int)

@Composable
fun ReaderScreen(model: AppModel, args: ReaderArgs) {
    BackHandler { model.reader = null }
    val ctx = LocalContext.current
    val pal = LocalPal.current
    val book = remember(args.res) { model.store.book(args.res) }
    val uriText = book?.uri
    if (book == null || uriText == null) {
        LaunchedEffect(Unit) { model.reader = null }
        Box(Modifier.fillMaxSize())
        return
    }
    var backend by remember { mutableStateOf<PdfBackend?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uriText) {
        val b = withContext(Dispatchers.IO) { FrameworkPdfBackend.open(ctx, Uri.parse(uriText)) }
        if (b == null) error = "Could not open this PDF. Attach it again from Library." else backend = b
    }
    DisposableEffect(Unit) {
        onDispose { backend?.close() }
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF8A8A8A))) {
        val b = backend
        val err = error
        if (b != null) {
            ReaderBody(model, args, book, uriText, b)
        } else if (err != null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                T(err, 18, FontWeight.ExtraBold, color = Color.White)
                Gap(h = 16)
                NbButton("Back", { model.reader = null }, color = pal.gs)
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                T("Opening\u2026", 20, FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

private fun visibleRegion(list: LazyListState, h: ScrollState, idx: Int, dispW: Int, pageH: Int, vwPx: Int): IntArray? {
    val info = list.layoutInfo.visibleItemsInfo.firstOrNull { i -> i.index == idx } ?: return null
    val vh = list.layoutInfo.viewportSize.height
    val top = maxOf(0, -info.offset)
    val bottom = minOf(pageH, vh - info.offset)
    if (bottom <= top) return null
    val left = h.value.coerceIn(0, maxOf(0, dispW - vwPx))
    val right = minOf(dispW, left + vwPx)
    if (right <= left) return null
    return intArrayOf(left, top, right - left, bottom - top)
}

@Composable
private fun ReaderBody(model: AppModel, args: ReaderArgs, book: BookRow, uriText: String, backend: PdfBackend) {
    val ctx = LocalContext.current
    val pal = LocalPal.current
    val store = model.store
    val scope = rememberCoroutineScope()
    val count = backend.pageCount
    val sizes = remember(backend) { PageSizes(count) }
    val cache = remember(backend) {
        PageCache((Runtime.getRuntime().maxMemory() / 8).toInt().coerceIn(24 * 1024 * 1024, 96 * 1024 * 1024))
    }
    var zoom by remember { mutableStateOf(1f) }
    var mode by remember { mutableStateOf(store.getSetting("reader_mode", "0").toIntOrNull() ?: 0) }
    var paged by remember { mutableStateOf(false) }
    var barVisible by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf(0) }

    val rangeFirst = if (args.startPrinted > 0 && args.endPrinted > 0) args.startPrinted + book.offset - 1 else -1
    val rangeLast = if (args.startPrinted > 0 && args.endPrinted > 0) args.endPrinted + book.offset - 1 else -1
    val startIndex = remember(backend) {
        val last = (book.lastPage - 1).coerceIn(0, maxOf(0, count - 1))
        if (rangeFirst >= 0) {
            if (last in rangeFirst..rangeLast) last else rangeFirst.coerceIn(0, maxOf(0, count - 1))
        } else last
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val hState = rememberScrollState()

    val bgColor = when (mode) {
        1 -> Color(0xFF8C7B5A)
        2 -> Color(0xFF000000)
        else -> Color(0xFF8A8A8A)
    }
    val filter: ColorFilter? = when (mode) {
        1 -> SEPIA
        2 -> NIGHT
        else -> null
    }
    val placeholder = when (mode) {
        1 -> Color(0xFFF5E6BD)
        2 -> Color(0xFF0A0A0A)
        else -> Color.White
    }

    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { i ->
            delay(700)
            store.setLastPage(book.res, i + 1)
        }
    }
    LaunchedEffect(zoom <= 1.0f) {
        if (zoom <= 1.0f) hState.scrollTo(0)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(bgColor)) {
        val vwPx = constraints.maxWidth
        val vhPx = constraints.maxHeight
        val density = LocalDensity.current
        val widthDp = with(density) { (vwPx * zoom).toDp() }
        val cur = listState.firstVisibleItemIndex

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { c -> c.pressed } >= 2) {
                                val z = event.calculateZoom()
                                if (z != 1f) zoom = (zoom * z).coerceIn(1f, 5f)
                                event.changes.forEach { c -> if (c.positionChanged()) c.consume() }
                            }
                        } while (event.changes.any { c -> c.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { barVisible = !barVisible },
                        onDoubleTap = { zoom = if (zoom > 1.2f) 1f else 2.5f }
                    )
                }
        ) {
            Box(Modifier.fillMaxSize().horizontalScroll(hState)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(widthDp).fillMaxHeight(),
                    userScrollEnabled = !(paged && zoom <= 1.05f)
                ) {
                    items(count = count, key = { i -> i }) { idx ->
                        PageItem(
                            idx = idx, backend = backend, sizes = sizes, cache = cache, vwPx = vwPx, zoom = zoom,
                            listState = listState, hState = hState, filter = filter, placeholder = placeholder,
                            inRange = rangeFirst >= 0 && idx >= rangeFirst && idx <= rangeLast,
                            isFirst = rangeFirst >= 0 && idx == rangeFirst,
                            isLast = rangeFirst >= 0 && idx == rangeLast,
                            rangeText = "Session: " + PageMap.format(intArrayOf(args.startPrinted, args.endPrinted))
                        )
                    }
                }
            }
        }

        if (barVisible) {
            Column(
                Modifier.align(Alignment.TopStart).fillMaxWidth().background(pal.bg.copy(alpha = 0.95f)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NbChip("\u2039 Back", { model.reader = null })
                    Gap(w = 8)
                    T(
                        "p. " + PageMap.pdfToPrinted(cur + 1, book.offset) + " \u00b7 PDF " + (cur + 1) + " of " + count,
                        14, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1
                    )
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NbChip("Go to", { dialog = 1 })
                    NbChip("Marks", { dialog = 2 })
                    if (PreVSearch.mightBeAvailable()) NbChip("Search", { dialog = 3 })
                    NbChip(listOf("Day", "Sepia", "Night")[mode], {
                        mode = (mode + 1) % 3
                        store.setSetting("reader_mode", mode.toString())
                    })
                    NbChip(if (paged) "Paged" else "Scroll", { paged = !paged })
                }
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(pal.bg.copy(alpha = 0.95f)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (paged) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NbButton("\u2039 Prev", {
                            scope.launch { listState.animateScrollToItem((cur - 1).coerceAtLeast(0)) }
                        }, modifier = Modifier.weight(1f), color = pal.card)
                        NbButton("Next \u203a", {
                            scope.launch { listState.animateScrollToItem((cur + 1).coerceAtMost(maxOf(0, count - 1))) }
                        }, modifier = Modifier.weight(1f), color = pal.gs)
                    }
                }
                var drag by remember { mutableStateOf<Float?>(null) }
                Slider(
                    value = drag ?: (cur + 1).toFloat(),
                    onValueChange = { v ->
                        drag = v
                        scope.launch { listState.scrollToItem((v.toInt() - 1).coerceIn(0, maxOf(0, count - 1))) }
                    },
                    onValueChangeFinished = { drag = null },
                    valueRange = 1f..maxOf(2, count).toFloat()
                )
            }
        }

        if (args.sessionId != 0L) {
            var chipX by remember { mutableStateOf(0f) }
            var chipY by remember { mutableStateOf(0f) }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset { IntOffset(chipX.toInt(), chipY.toInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            chipX += drag.x
                            chipY += drag.y
                        }
                    }
            ) {
                TimerChip(model, args.sessionId)
            }
        }
    }

    fun jumpTo(index: Int) {
        scope.launch { listState.scrollToItem(index.coerceIn(0, maxOf(0, count - 1))) }
    }

    if (dialog == 1) {
        var text by remember { mutableStateOf("") }
        NbDialog({ dialog = 0 }, "Go to page") {
            NbField(text, { v -> text = v }, "Page number", number = true)
            ButtonRow {
                NbButton("Printed", {
                    val n = text.toIntOrNull()
                    if (n != null) jumpTo(PageMap.printedToPdf(n, book.offset) - 1)
                    dialog = 0
                }, modifier = Modifier.weight(1f), color = pal.gs)
                NbButton("PDF page", {
                    val n = text.toIntOrNull()
                    if (n != null) jumpTo(n - 1)
                    dialog = 0
                }, modifier = Modifier.weight(1f), color = pal.math)
            }
        }
    }
    if (dialog == 2) {
        var marksRev by remember { mutableStateOf(0) }
        val marks = remember(marksRev) { store.bookmarks(book.res) }
        val chapters = remember(book.res, model.rev) { store.bookItems(book.res) }
        NbDialog({ dialog = 0 }, "Bookmarks and chapters") {
            NbButton("Bookmark this page", {
                val pdfPage = listState.firstVisibleItemIndex + 1
                store.addBookmark(book.res, pdfPage, "p. " + PageMap.pdfToPrinted(pdfPage, book.offset))
                marksRev++
            }, modifier = Modifier.fillMaxWidth(), color = pal.gs)
            for (m in marks) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NbChip(m.label + " (PDF " + m.pdfPage + ")", { jumpTo(m.pdfPage - 1); dialog = 0 }, modifier = Modifier.weight(1f))
                    NbChip("Delete", { store.deleteBookmark(m.id); marksRev++ })
                }
            }
            if (chapters.isNotEmpty()) T("Chapters", 16, FontWeight.ExtraBold)
            for (c in chapters) {
                NbChip(
                    c.title + " \u00b7 p. " + c.p0,
                    { jumpTo(PageMap.printedToPdf(c.p0, book.offset) - 1); dialog = 0 },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    if (dialog == 3) {
        var q by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<Int>?>(null) }
        var busy by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var progress by remember { mutableStateOf("") }
        NbDialog({ busy = false; dialog = 0 }, "Search in this PDF") {
            NbField(q, { v -> q = v }, "Text to find")
            NbButton(if (busy) "Searching " + progress else "Search", {
                if (!busy && q.isNotBlank()) {
                    busy = true
                    failed = false
                    results = null
                    val query = q.trim()
                    scope.launch(Dispatchers.IO) {
                        val r = PreVSearch.search(ctx, Uri.parse(uriText), query, { i, n -> progress = "$i/$n" }, { !busy })
                        withContext(Dispatchers.Main) {
                            results = r
                            failed = r == null
                            busy = false
                        }
                    }
                }
            }, modifier = Modifier.fillMaxWidth(), color = pal.gs, enabled = !busy)
            if (failed) T("Search is not available for this file on this phone.", 14, FontWeight.Bold)
            val res = results
            if (res != null) {
                if (res.isEmpty()) T("No matches. Scanned pages have no searchable text.", 14, FontWeight.Bold)
                for (p in res.take(100)) {
                    NbChip(
                        "p. " + PageMap.pdfToPrinted(p + 1, book.offset) + " (PDF " + (p + 1) + ")",
                        { jumpTo(p); dialog = 0 },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerChip(model: AppModel, sessionId: Long) {
    val st = remember(model.rev) { model.store.timer() }
    val logged = remember(model.rev, sessionId) { model.store.loggedMs(sessionId) }
    val running = st != null && st.running && st.sessionId == sessionId
    val now by produceState(System.currentTimeMillis(), running) {
        while (true) {
            value = System.currentTimeMillis()
            delay(if (running) 500L else 1000L)
        }
    }
    val elapsed = TimerMath.elapsed(logged, running, st?.startedAt ?: 0L, now)
    NbChip(
        TimerMath.format(elapsed) + if (running) "  Pause" else "  Start",
        {
            if (running) model.timer.pause() else model.timer.start(sessionId)
            model.bump()
        },
        selected = running
    )
}

@Composable
private fun PageItem(
    idx: Int,
    backend: PdfBackend,
    sizes: PageSizes,
    cache: PageCache,
    vwPx: Int,
    zoom: Float,
    listState: LazyListState,
    hState: ScrollState,
    filter: ColorFilter?,
    placeholder: Color,
    inRange: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    rangeText: String
) {
    val pal = LocalPal.current
    val density = LocalDensity.current
    var aspect by remember(idx) { mutableStateOf(sizes.aspect(idx)) }
    LaunchedEffect(idx) {
        if (sizes.h[idx] == 0) {
            val s = backend.pageSize(idx)
            if (s != null && s[0] > 0) {
                sizes.set(idx, s)
                aspect = s[1].toFloat() / s[0]
            }
        } else {
            aspect = sizes.aspect(idx)
        }
    }
    val dispW = (vwPx * zoom).toInt().coerceAtLeast(1)
    val pageH = (dispW * aspect).toInt().coerceAtLeast(1)

    var base by remember(idx) { mutableStateOf<Bitmap?>(cache.get(idx)) }
    LaunchedEffect(idx) {
        if (base == null) {
            val bmp = backend.renderBase(idx, vwPx)
            if (bmp != null) {
                cache.put(idx, bmp)
                base = bmp
            }
        }
    }

    var tile by remember(idx) { mutableStateOf<Tile?>(null) }
    val zoomKey = (zoom * 20).toInt()
    LaunchedEffect(idx, zoomKey) {
        if (zoom <= 1.15f) {
            tile = null
        } else {
            val dw = (vwPx * zoom).toInt().coerceAtLeast(1)
            val ph = (dw * aspect).toInt().coerceAtLeast(1)
            snapshotFlow { visibleRegion(listState, hState, idx, dw, ph, vwPx) }.collectLatest { r ->
                if (r != null) {
                    delay(180)
                    val bmp = backend.renderTile(idx, dw, r[0], r[1], r[2], r[3])
                    if (bmp != null) tile = Tile(bmp, r[0], r[1], dw)
                }
            }
        }
    }

    val baseNow = base
    val tileNow = tile
    val baseImg = remember(baseNow) { baseNow?.asImageBitmap() }
    val tileImg = remember(tileNow) { tileNow?.bmp?.asImageBitmap() }

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(with(density) { pageH.toDp() })) {
            Canvas(Modifier.fillMaxSize()) {
                if (baseImg != null) {
                    drawImage(baseImg, dstSize = IntSize(size.width.toInt(), size.height.toInt()), colorFilter = filter)
                } else {
                    drawRect(placeholder)
                }
                if (tileNow != null && tileImg != null) {
                    val r = size.width / tileNow.dispW
                    drawImage(
                        tileImg,
                        dstOffset = IntOffset((tileNow.left * r).toInt(), (tileNow.top * r).toInt()),
                        dstSize = IntSize((tileNow.bmp.width * r).toInt(), (tileNow.bmp.height * r).toInt()),
                        colorFilter = filter
                    )
                }
            }
            if (inRange) Box(Modifier.align(Alignment.CenterStart).width(6.dp).fillMaxHeight().background(pal.gs))
            if (isFirst) Box(Modifier.align(Alignment.TopStart).padding(10.dp)) { Tag(rangeText, pal.gs) }
            if (isLast) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp).background(pal.ink.copy(alpha = 0.45f)))
        }
        Spacer(Modifier.height(6.dp))
    }
}
