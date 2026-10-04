@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myothuonion.languagetalk.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myothuonion.languagetalk.model.ReaderBook
import com.myothuonion.languagetalk.util.PdfWord
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun BookshelfScreen(viewModel: ReaderViewModel, onRead: (String) -> Unit, onStudy: (String) -> Unit) {
    val learning by viewModel.learning.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.import(it) } }
    var remove by remember { mutableStateOf<ReaderBook?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("bookshelf"), contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR LIBRARY", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Text("My Books", fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                    Text("ကိုယ့်အရှိန်နဲ့ ဖတ်ပြီး လေ့လာမယ်", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton({ picker.launch(arrayOf("application/pdf")) }, enabled = !ui.loading,
                    modifier = Modifier.testTag("import-pdf")) { Icon(Icons.Default.Add, "Import PDF") }
            }
        }
        if (ui.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            OutlinedButton({ picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth(), enabled = !ui.loading) {
                Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Import PDF")
            }
        }
        items(learning.books, key = { it.id }) { book ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(86.dp).height(126.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer).testTag("read-" + book.id).clickable { onRead(book.id) }) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(if (book.assetId.isNotBlank()) if (book.id.contains("beginner")) "BEGINNER" else "INTERMEDIATE" else "PDF",
                            fontSize = 9.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(book.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("စာမျက်နှာ " + (book.page + 1) + " / " + book.pageCount, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { (book.page + 1).toFloat() / book.pageCount },
                        modifier = Modifier.fillMaxWidth().height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ onRead(book.id) }) { Text(if (book.page == 0) "ဖတ်မယ်" else "ဆက်ဖတ်မယ်") }
                        if (book.assetId.isNotBlank()) TextButton({ onStudy(book.id) },
                            modifier = Modifier.testTag(if (book.id == "ttmik-beginner") "open-books" else "open-intermediate")) { Text("သင်ခန်းစာ") }
                        else IconButton({ remove = book }) { Icon(Icons.Default.DeleteOutline, "Remove book") }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        }
        item { Text("စာလုံးဖိရွေးပြီး မြန်မာလို အဓိပ္ပာယ်ရှာနိုင်မယ်။ ဖတ်နေရာနဲ့ Bookmark ကို အလိုအလျောက်သိမ်းမယ်။",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    remove?.let { book -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("စာအုပ်ဖယ်မလား?") },
        text = { Text("App ထဲက PDF မိတ္တူကို ဖယ်မယ်။ သိမ်းထားတဲ့ဝေါဟာရတွေ ဆက်ရှိမယ်။") },
        confirmButton = { TextButton({ viewModel.remove(book); remove = null }) { Text("ဖယ်မယ်") } },
        dismissButton = { TextButton({ remove = null }) { Text("မဖယ်တော့ပါ") } }) }
}

@Composable
internal fun PdfReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit, aiViewModel: AppViewModel? = null) {
    val ui by viewModel.ui.collectAsState()
    val learning by viewModel.learning.collectAsState()
    val book = learning.books.firstOrNull { it.id == ui.bookId }
    var chrome by rememberSaveable { mutableStateOf(true) }
    var textMode by rememberSaveable { mutableStateOf(false) }
    var fontSize by rememberSaveable { mutableFloatStateOf(21f) }
    var goTo by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    var pageInput by remember(ui.page) { mutableStateOf((ui.page + 1).toString()) }
    var scale by remember(ui.page, ui.bookId) { mutableFloatStateOf(1f) }
    var pan by remember(ui.page, ui.bookId) { mutableStateOf(Offset.Zero) }
    var selected by remember(ui.pageData) { mutableStateOf<List<PdfWord>>(emptyList()) }
    var textSelection by remember(ui.pageData) { mutableStateOf("") }
    BackHandler { if (ui.selection.isNotBlank()) viewModel.dismissLookup() else onBack() }
    DisposableEffect(Unit) { onDispose { viewModel.close() } }
    LaunchedEffect(ui.selection) { if (ui.selection.isBlank()) { selected = emptyList(); textSelection = "" } }
    val background = when (learning.readerTheme) { "NIGHT" -> Color(0xFF242A25); "SEPIA" -> Color(0xFFF5EDDB); else -> Color(0xFFFFFDF8) }
    val ink = if (learning.readerTheme == "NIGHT") Color(0xFFE7EDDE) else Color(0xFF30392E)
    val matrix = remember(learning.readerTheme) {
        when (learning.readerTheme) {
            "NIGHT" -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(-.82f, 0f, 0f, 0f, 239f,
                0f, -.80f, 0f, 0f, 240f, 0f, 0f, -.82f, 0f, 235f, 0f, 0f, 0f, 1f, 0f)))
            "SEPIA" -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(.96f, 0f, 0f, 0f, 0f,
                0f, .93f, 0f, 0f, 0f, 0f, 0f, .86f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
            else -> null
        }
    }
    Column(Modifier.fillMaxSize().background(background).testTag("pdf-reader")) {
        if (chrome) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(book?.title ?: "Reader", Modifier.weight(1f), maxLines = 2, fontSize = 14.sp)
            IconButton(viewModel::bookmark, enabled = book != null) {
                Icon(if (book?.bookmarks?.contains(ui.page) == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Bookmark page")
            }
            var appearance by remember { mutableStateOf(false) }
            Box {
                IconButton({ appearance = true }) { Icon(Icons.Default.Tune, "Reading appearance") }
                DropdownMenu(appearance, { appearance = false }) {
                    listOf("DAY" to "Day", "SEPIA" to "Sepia", "NIGHT" to "Night").forEach { (id, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.theme(id); appearance = false })
                    }
                    DropdownMenuItem(text = { Text("Bookmarks") }, onClick = { bookmarks = true; appearance = false })
                    DropdownMenuItem(text = { Text("Fit width") }, onClick = { scale = 1f; pan = Offset.Zero; appearance = false })
                }
            }
        }
        if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.error?.let { error -> Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            if (!ui.loading && ui.selection.isBlank()) TextButton({ viewModel.page(ui.page) }) { Text("ထပ်စမ်း") }
        } }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val data = ui.pageData
            if (data != null && textMode) {
                var layout by remember(data) { mutableStateOf<TextLayoutResult?>(null) }
                var anchor by remember(data) { mutableIntStateOf(0) }
                var rangeEnd by remember(data) { mutableIntStateOf(0) }
                var pointer by remember(data) { mutableStateOf(Offset.Zero) }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    if (data.layer.text.isBlank()) Text("စာသားမဖော်ထုတ်နိုင်သေးပါ။ PDF ပုံစံသို့ ပြန်ပြောင်းပြီး စာသားကို ရိုက်ရှာနိုင်ပါတယ်။", color = ink)
                    Text(data.layer.text, color = ink, fontSize = fontSize.sp, lineHeight = (fontSize * 1.8f).sp,
                        onTextLayout = { layout = it }, modifier = Modifier.fillMaxWidth().testTag("reader-text")
                            .pointerInput(data) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { point -> pointer = point; anchor = layout?.getOffsetForPosition(point) ?: 0; rangeEnd = anchor },
                                    onDrag = { change, amount -> change.consume(); pointer += amount; rangeEnd = layout?.getOffsetForPosition(pointer) ?: anchor },
                                    onDragEnd = {
                                        val text = data.layer.text
                                        if (text.isNotBlank()) {
                                            val a = min(anchor, rangeEnd).coerceIn(0, text.lastIndex)
                                            val b = max(anchor, rangeEnd).coerceIn(a, text.lastIndex)
                                            var start = a
                                            while (start > 0 && !text[start - 1].isWhitespace()) start--
                                            var end = b + 1
                                            while (end < text.length && !text[end].isWhitespace()) end++
                                            textSelection = text.substring(start, end).trim()
                                            viewModel.lookup(textSelection, text.substring(max(0, start - 200), min(text.length, end + 200)))
                                        }
                                    })
                            })
                }
            } else if (data != null) {
                val words = data.layer.words
                var anchor by remember(data) { mutableIntStateOf(-1) }
                var end by remember(data) { mutableIntStateOf(-1) }
                var pointer by remember(data) { mutableStateOf(Offset.Zero) }
                Box(Modifier.fillMaxSize().pointerInput(data) {
                    detectTransformGestures { _, amount, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 4f); pan = if (scale == 1f) Offset.Zero else pan + amount }
                }.verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().aspectRatio(data.bitmap.width.toFloat() / data.bitmap.height)
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y)
                        .pointerInput(data) { detectTapGestures(onTap = { chrome = !chrome }) }
                        .pointerInput(data) {
                            fun hit(point: Offset): Int {
                                val x = point.x / size.width; val y = point.y / size.height
                                return words.indexOfFirst { x >= it.left - .008f && x <= it.right + .008f && y >= it.top - .008f && y <= it.bottom + .008f }
                            }
                            fun update() { selected = if (anchor >= 0 && end >= 0) words.subList(min(anchor, end), max(anchor, end) + 1) else emptyList() }
                            detectDragGesturesAfterLongPress(
                                onDragStart = { point -> pointer = point; anchor = hit(point); end = anchor; update() },
                                onDrag = { change, amount -> change.consume(); pointer += amount; val target = hit(pointer); if (target >= 0) { end = target; update() } },
                                onDragEnd = { if (selected.isNotEmpty()) viewModel.lookup(selected.joinToString(" ") { it.text }, selected.map { it.sentence }.distinct().joinToString(" ")) })
                        }) {
                        Image(data.bitmap.asImageBitmap(), "PDF page " + (ui.page + 1), colorFilter = matrix, modifier = Modifier.fillMaxSize())
                        Canvas(Modifier.fillMaxSize()) {
                            selected.forEach { word ->
                                drawRect(Color(0x557ABF77), Offset(word.left * size.width, word.top * size.height),
                                    Size((word.right - word.left) * size.width, (word.bottom - word.top) * size.height))
                            }
                        }
                    }
                }
            } else if (ui.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (chrome) {
            if (textMode) Row(Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Aa", fontSize = 14.sp); Slider(fontSize, { fontSize = it }, valueRange = 16f..32f, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton({ viewModel.page(ui.page - 1) }, enabled = !ui.loading && ui.page > 0) { Icon(Icons.Default.ChevronLeft, "Previous page") }
                TextButton({ goTo = true }, enabled = book != null) { Text((ui.page + 1).toString() + " / " + (book?.pageCount ?: 0)) }
                TextButton({ textMode = !textMode }) { Text(if (textMode) "PDF" else "Aa") }
                IconButton({ manual = true }) { Icon(Icons.Default.Translate, "Look up a word on this page") }
                IconButton({ viewModel.page(ui.page + 1) }, enabled = !ui.loading && book != null && ui.page + 1 < book.pageCount) { Icon(Icons.Default.ChevronRight, "Next page") }
            }
        }
    }
    if (manual) AlertDialog(onDismissRequest = { manual = false }, title = { Text("စာမျက်နှာထဲမှာ အဓိပ္ပာယ်ရှာ") },
        text = { OutlinedTextField(manualText, { manualText = it.take(1200) }, label = { Text("ဝေါဟာရ သို့မဟုတ် ဝါကျ") }) },
        confirmButton = { TextButton({ viewModel.lookup(manualText, ui.pageData?.layer?.text.orEmpty().take(4000)); manual = false },
            enabled = manualText.isNotBlank()) { Text("ရှာမယ်") } },
        dismissButton = { TextButton({ manual = false }) { Text("ပိတ်") } })
    if (goTo && book != null) AlertDialog(onDismissRequest = { goTo = false }, title = { Text("စာမျက်နှာသို့ သွားမယ်") },
        text = { OutlinedTextField(pageInput, { pageInput = it.filter(Char::isDigit) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, label = { Text("1 – " + book.pageCount) }) },
        confirmButton = { TextButton({ pageInput.toIntOrNull()?.takeIf { it in 1..book.pageCount }?.let { viewModel.page(it - 1); goTo = false } },
            enabled = pageInput.toIntOrNull()?.let { it in 1..book.pageCount } == true) { Text("သွားမယ်") } },
        dismissButton = { TextButton({ goTo = false }) { Text("ပိတ်") } })
    if (bookmarks && book != null) AlertDialog(onDismissRequest = { bookmarks = false }, title = { Text("Bookmarks") },
        text = { Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            if (book.bookmarks.isEmpty()) Text("Bookmark မရှိသေးပါ။")
            book.bookmarks.forEach { page -> TextButton({ viewModel.page(page); bookmarks = false }) { Text("စာမျက်နှာ " + (page + 1)) } }
        } },
        confirmButton = { TextButton({ bookmarks = false }) { Text("ပိတ်") } })
    if (ui.selection.isNotBlank()) {
        var edit by remember(ui.selection) { mutableStateOf(false) }
        var input by remember(ui.selection) { mutableStateOf(ui.selection) }
        var grammar by remember(ui.selection) { mutableStateOf(false) }
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::dismissLookup, sheetState = sheet) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("한국어 / English → မြန်မာ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                aiViewModel?.let { AiTaskPicker(com.myothuonion.languagetalk.model.AiTask.READER, it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (edit) OutlinedTextField(input, { input = it.take(1200) }, Modifier.weight(1f), label = { Text("ရွေးထားသောစာသား") })
                    else Text(ui.selection, Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    IconButton({ edit = !edit }) { Icon(Icons.Default.Edit, "Correct selected text") }
                    IconButton(viewModel::dismissLookup) { Icon(Icons.Default.Close, "Close translation") }
                }
                if (edit) Button({ viewModel.lookup(input, ui.sentence) }, enabled = input.isNotBlank() && !ui.translating) { Text("ဘာသာပြန်") }
                if (ui.translating) LinearProgressIndicator(Modifier.fillMaxWidth())
                ui.translation?.let { value ->
                    Text(value.lemma, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(value.meaning, fontSize = 21.sp, lineHeight = 34.sp)
                    if (value.grammar.isNotBlank()) {
                        TextButton({ grammar = !grammar }) { Text("Grammar ရှင်းချက်") }
                        if (grammar) Text(value.grammar, lineHeight = 25.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(viewModel::listen, Modifier.weight(1f)) { Icon(Icons.Default.VolumeUp, null); Spacer(Modifier.width(6.dp)); Text("နားထောင်") }
                        Button(viewModel::saveWord, Modifier.weight(1f), enabled = !ui.saved) { Text(if (ui.saved) "သိမ်းပြီးပြီ" else "သိမ်းမယ်") }
                    }
                }
                if (!ui.translating && ui.translation == null) {
                    ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button({ viewModel.lookup(input, ui.sentence) }) { Text("ထပ်စမ်း") }
                }
                if (ui.sentence.isNotBlank() && ui.selection != ui.sentence)
                    TextButton({ viewModel.lookup(ui.sentence.take(1200), ui.sentence) }, enabled = !ui.translating) { Text("ဝါကျအဓိပ္ပာယ် ကြည့်မယ်") }
            }
        }
    }
}
