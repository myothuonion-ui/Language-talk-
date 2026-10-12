package com.myothuonion.languagetalk.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import com.myothuonion.languagetalk.data.ReaderAnnotation
import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.util.*
import kotlinx.coroutines.CancellationException
import kotlin.math.*

@Composable
internal fun ReaderPageSurface(book: ReaderBook, index: Int, viewModel: ReaderViewModel, learning: LearningState,
    annotations: List<ReaderAnnotation>, focus: List<PageRect>, continuous: Boolean,
    onSelecting: (Boolean) -> Unit, onZoom: (Boolean) -> Unit,
    onSelect: (String, String, List<PageRect>, Int, Int) -> Unit) {
    var data by remember(book.id, index) { mutableStateOf<ReadablePdfPage?>(null) }
    var error by remember(book.id, index) { mutableStateOf("") }
    LaunchedEffect(book.id, index) {
        try { data = viewModel.loadPage(book, index) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message.orEmpty() }
    }
    val ui by viewModel.ui.collectAsState()
    var scale by remember(book.id, index) { mutableFloatStateOf(if (book.page == index) book.zoom else 1f) }
    var pan by remember(book.id, index) { mutableStateOf(Offset.Zero) }
    val ink = if (learning.readerTheme == "NIGHT") Color(0xFFE7EDDE) else Color(0xFF30392E)
    val matrix = remember(learning.readerTheme) { when (learning.readerTheme) {
        "NIGHT" -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(-.82f, 0f, 0f, 0f, 239f, 0f, -.80f, 0f, 0f, 240f, 0f, 0f, -.82f, 0f, 235f, 0f, 0f, 0f, 1f, 0f)))
        "SEPIA" -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(.96f, 0f, 0f, 0f, 0f, 0f, .93f, 0f, 0f, 0f, 0f, 0f, .86f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        else -> null
    } }
    val page = data
    if (page == null) {
        Box(Modifier.fillMaxWidth().height(380.dp), contentAlignment = Alignment.Center) {
            if (error.isBlank()) CircularProgressIndicator() else Text(error, color = MaterialTheme.colorScheme.error)
        }
        return
    }
    if (learning.readerMode == "TEXT") {
        val text = page.layer.text
        val styled = remember(text, annotations, focus) {
            buildAnnotatedString {
                append(text)
                annotations.forEach { value ->
                    // Reflow anchors are independent of bitmap coordinates. Verify the quote before using stored offsets.
                    val range = ReaderAnchors.range(text, value.quote, value.startOffset)
                    if (range != null) addStyle(SpanStyle(
                        fontWeight = if (value.kind == "BOLD") FontWeight.Bold else null,
                        textDecoration = if (value.kind == "UNDERLINE") TextDecoration.Underline else null,
                        background = if (value.kind in setOf("HIGHLIGHT", "NOTE")) Color(value.color).copy(alpha = .35f) else Color.Transparent
                    ), range.first, range.last + 1)
                }
            }
        }
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        var pointer by remember { mutableStateOf(Offset.Zero) }
        var anchor by remember { mutableIntStateOf(0) }
        var end by remember { mutableIntStateOf(0) }
        var selectionStart by remember { mutableIntStateOf(-1) }
        var selectionEnd by remember { mutableIntStateOf(-1) }
        val displayed = remember(styled, selectionStart, selectionEnd) { buildAnnotatedString {
            append(styled)
            if (selectionStart >= 0 && selectionEnd > selectionStart) addStyle(SpanStyle(background = Color(0x557ABF77)), selectionStart, selectionEnd)
        } }
        val modifier = if (continuous) Modifier.fillMaxWidth() else Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        Column(modifier.padding(22.dp)) {
            Text("${index + 1}", color = ink.copy(alpha = .6f), fontSize = 12.sp)
            if (text.isBlank()) Text("ဒီစာမျက်နှာမှာ OCR စာသားမရပါ။ PDF ကို ပြန်ကြည့်နိုင်ပါတယ်။", color = ink)
            Text(displayed, color = ink, fontSize = learning.readerFontSize.sp, lineHeight = (learning.readerFontSize * 1.65f).sp,
                onTextLayout = { layout = it }, modifier = Modifier.fillMaxWidth().testTag("reader-text").pointerInput(text) {
                    detectDragGesturesAfterLongPress(onDragStart = { point ->
                        pointer = point; anchor = layout?.getOffsetForPosition(point) ?: 0; end = anchor; onSelecting(true)
                    }, onDrag = { change, amount -> change.consume(); pointer += amount; end = layout?.getOffsetForPosition(pointer) ?: anchor },
                        onDragCancel = { onSelecting(false) }, onDragEnd = {
                            if (text.isNotBlank()) {
                                var start = min(anchor, end).coerceIn(0, text.lastIndex)
                                var finish = max(anchor, end).coerceIn(start, text.lastIndex) + 1
                                while (start > 0 && !text[start - 1].isWhitespace()) start--
                                while (finish < text.length && !text[finish].isWhitespace()) finish++
                                finish = min(finish, start + 1200)
                                selectionStart = start; selectionEnd = finish
                                val quote = text.substring(start, finish)
                                val rects = page.layer.words.filter { quote.contains(it.text) && it.text.isNotBlank() }.map { PageRect(it.left, it.top, it.right, it.bottom) }.filter { it.valid() }.take(250)
                                viewModel.activatePage(book, index, page)
                                onSelect(quote, text.substring(max(0, start - 200), min(text.length, finish + 200)), rects, start, finish)
                            }
                            onSelecting(false)
                        })
                })
        }
        LaunchedEffect(ui.selection) { if (ui.selection.isBlank()) { selectionStart = -1; selectionEnd = -1 } }
        return
    }
    var selected by remember(page) { mutableStateOf<List<PdfWord>>(emptyList()) }
    LaunchedEffect(ui.selection) { if (ui.selection.isBlank()) selected = emptyList() }
    LaunchedEffect(scale, ui.page) { if (ui.page == index) onZoom(scale > 1.01f) }
    val scroll = rememberScrollState()
    val outer = if (continuous) Modifier.fillMaxWidth() else Modifier.fillMaxSize().verticalScroll(scroll)
    Box(outer.clipToBounds()) {
        Box(Modifier.fillMaxWidth().aspectRatio(page.bitmap.width.toFloat() / page.bitmap.height)
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y)
            .pointerInput(page) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (!event.changes.any { it.isConsumed } && (event.changes.count { it.pressed } > 1 || scale > 1.01f)) {
                            val zoom = event.calculateZoom()
                            val movement = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            pan = if (scale <= 1.01f) Offset.Zero else {
                                val limitX = size.width * (scale - 1f) / 2f
                                val limitY = size.height * (scale - 1f) / 2f
                                Offset((pan.x + movement.x).coerceIn(-limitX, limitX), (pan.y + movement.y).coerceIn(-limitY, limitY))
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    viewModel.zoom(scale)
                }
            }.pointerInput(page) { detectTapGestures(onDoubleTap = { scale = 1f; pan = Offset.Zero; viewModel.zoom(1f) }) }
            .pointerInput(page) {
                var anchor = -1; var end = -1; var pointer = Offset.Zero
                fun hit(point: Offset) = page.layer.words.indexOfFirst {
                    val x = point.x / size.width; val y = point.y / size.height
                    x in (it.left - .008f)..(it.right + .008f) && y in (it.top - .008f)..(it.bottom + .008f)
                }
                fun update() { selected = if (anchor >= 0 && end >= 0) page.layer.words.subList(min(anchor, end), max(anchor, end) + 1) else emptyList() }
                detectDragGesturesAfterLongPress(onDragStart = { point -> pointer = point; anchor = hit(point); end = anchor; update(); onSelecting(true) },
                    onDrag = { change, amount -> change.consume(); pointer += amount; val target = hit(pointer); if (target >= 0) { end = target; update() } },
                    onDragCancel = { onSelecting(false) }, onDragEnd = {
                        if (selected.isNotEmpty()) {
                            val quote = selected.joinToString(" ") { it.text }.take(1200)
                            val context = selected.map { it.sentence }.distinct().joinToString(" ")
                            val prefix = page.layer.words.take(min(anchor, end)).joinToString(" ") { it.text }
                            val estimate = ReaderAnchors.range(page.layer.text, prefix)?.let { it.last + 1 } ?: -1
                            val range = ReaderAnchors.range(page.layer.text, quote, estimate)
                            val offset = range?.first ?: -1
                            viewModel.activatePage(book, index, page)
                            onSelect(quote, context, selected.map { PageRect(it.left, it.top, it.right, it.bottom) }.filter { it.valid() },
                                offset, range?.let { it.last + 1 } ?: -1)
                        }
                        onSelecting(false)
                    })
            }) {
            Image(page.bitmap.asImageBitmap(), "PDF page ${index + 1}", colorFilter = matrix, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                annotations.forEach { value -> value.regions().forEach { rect ->
                    val left = rect.left * size.width; val top = rect.top * size.height
                    val width = (rect.right - rect.left) * size.width; val height = (rect.bottom - rect.top) * size.height
                    if (value.kind == "UNDERLINE") drawLine(Color(value.color), Offset(left, top + height), Offset(left + width, top + height), strokeWidth = 2.dp.toPx())
                    else if (value.kind in setOf("HIGHLIGHT", "NOTE")) drawRect(Color(value.color).copy(alpha = .32f), Offset(left, top), Size(width, height))
                } }
                (focus + selected.map { PageRect(it.left, it.top, it.right, it.bottom) }).forEach { rect ->
                    drawRect(Color(0x557ABF77), Offset(rect.left * size.width, rect.top * size.height),
                        Size((rect.right - rect.left) * size.width, (rect.bottom - rect.top) * size.height))
                }
            }
        }
    }
}
