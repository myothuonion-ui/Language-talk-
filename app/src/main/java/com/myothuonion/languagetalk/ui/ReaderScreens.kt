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
import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.data.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.myothuonion.languagetalk.util.PdfWord
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun BookshelfScreen(viewModel: ReaderViewModel, onRead: (String) -> Unit, onStudy: (String) -> Unit, onSaved: () -> Unit = {}) {
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
        item { OutlinedButton(onSaved, Modifier.fillMaxWidth()) { Icon(Icons.Default.CollectionsBookmark, null); Spacer(Modifier.width(8.dp)); Text("သိမ်းထားတာနဲ့ မှတ်စုများ") } }
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
internal fun PdfReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit, aiViewModel: AppViewModel? = null,
    onPractice: (String) -> Unit = {}) {
    val ui by viewModel.ui.collectAsState()
    val learning by viewModel.learning.collectAsState()
    val annotations by viewModel.annotations.collectAsState()
    val tracks by viewModel.audioAssets.collectAsState()
    val book = learning.books.firstOrNull { it.id == ui.bookId }
    var menu by remember { mutableStateOf(false) }
    var navigation by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchBusy by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    var audioPicker by remember { mutableStateOf(false) }
    var trackInput by remember { mutableStateOf("") }
    var selecting by remember(ui.page, ui.bookId) { mutableStateOf(false) }
    var zoomed by remember(ui.page, ui.bookId) { mutableStateOf(false) }
    val audioImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { trackInput.toIntOrNull()?.let { track -> viewModel.importAudio(track, it) } }
    }
    BackHandler { when { ui.selection.isNotBlank() -> viewModel.dismissLookup(); ui.explanationOpen -> viewModel.showExplanation(false); else -> onBack() } }
    DisposableEffect(Unit) { onDispose { searchJob?.cancel(); viewModel.close() } }
    val background = when (learning.readerTheme) { "NIGHT" -> Color(0xFF202722); "SEPIA" -> Color(0xFFF5EDDB); else -> Color.White }
    val ink = if (learning.readerTheme == "NIGHT") Color(0xFFE7EDDE) else Color(0xFF30392E)
    Column(Modifier.fillMaxSize().background(background).testTag("pdf-reader")) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(book?.title ?: "Reader", Modifier.weight(1f), maxLines = 2, fontSize = 14.sp)
            IconButton(viewModel::bookmark, enabled = book != null) {
                Icon(if (book?.bookmarks?.contains(ui.page) == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Bookmark page")
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.Tune, "Reading appearance") }
                DropdownMenu(menu, { menu = false }) {
                    listOf("DAY" to "Original", "SEPIA" to "Sepia", "NIGHT" to "Night").forEach { (id, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.theme(id); menu = false })
                    }
                    DropdownMenuItem(text = { Text("ဘယ်/ညာ လှန်ဖတ်") }, onClick = { viewModel.reading(flow = "HORIZONTAL"); menu = false })
                    DropdownMenuItem(text = { Text("အပေါ်/အောက် ဆက်တိုက်ဖတ်") }, onClick = { viewModel.reading(flow = "VERTICAL"); menu = false })
                    DropdownMenuItem(text = { Text("စာအုပ်ထဲ ရှာမယ်") }, onClick = { search = true; menu = false })
                    DropdownMenuItem(text = { Text("Bookmarks / Contents") }, onClick = { navigation = true; menu = false })
                    DropdownMenuItem(text = { Text("Track အသံဖိုင်ချိတ်မယ်") }, onClick = { audioPicker = true; menu = false })
                }
            }
        }
        if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.error?.let { Text(it, Modifier.padding(horizontal = 14.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        if (ui.notice.isNotBlank()) Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ui.notice, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            if (ui.undoId.isNotBlank()) TextButton(viewModel::undo) { Text("Undo") }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            if (book != null) key(book.id, learning.readerFlow) {
                val focusRegions = ui.explanation?.blocks()?.firstOrNull { it.id == ui.sourceFocus }?.rectangles.orEmpty()
                @Composable fun page(index: Int, continuous: Boolean) {
                    ReaderPageSurface(book, index, viewModel, learning, annotations.filter { it.bookId == book.id && it.page == index &&
                        it.fingerprint == book.fingerprint.ifBlank { book.id } }, if (index == ui.page) focusRegions else emptyList(), continuous,
                        onSelecting = { selecting = it }, onZoom = { zoomed = it },
                        onSelect = { quote, context, rects, start, end -> viewModel.lookup(quote, context, rects, start, end, translate = false) })
                }
                if (learning.readerFlow == "HORIZONTAL") {
                    val pager = rememberPagerState(initialPage = ui.page.coerceIn(0, book.pageCount - 1)) { book.pageCount }
                    val current by rememberUpdatedState(ui)
                    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { index ->
                        if (!current.loading && current.page != index) viewModel.page(index)
                    } }
                    LaunchedEffect(ui.page) { if (pager.currentPage != ui.page && !pager.isScrollInProgress) pager.scrollToPage(ui.page) }
                    HorizontalPager(pager, Modifier.fillMaxSize().testTag("reader-pager"),
                        userScrollEnabled = !selecting && !zoomed && ui.selection.isBlank()) { index -> page(index, false) }
                } else {
                    val list = rememberLazyListState(initialFirstVisibleItemIndex = ui.page)
                    val current by rememberUpdatedState(ui)
                    LaunchedEffect(list) { snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
                        if (!current.loading && current.page != index && current.selection.isBlank()) viewModel.page(index)
                    } }
                    LaunchedEffect(ui.page) { if (list.firstVisibleItemIndex != ui.page && !list.isScrollInProgress) list.scrollToItem(ui.page) }
                    LazyColumn(Modifier.fillMaxSize().testTag("reader-continuous"), state = list, userScrollEnabled = !selecting && !zoomed) {
                        items(book.pageCount, key = { it }) { index -> page(index, true); HorizontalDivider() }
                    }
                }
            } else if (ui.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (ui.selection.isNotBlank()) ReaderSelectionPanel(viewModel, onPractice)
        if (ui.explanationOpen) ReaderTeachingPanel(viewModel, onPractice)
        if (ui.preparingAudio || ui.speaking.isNotBlank()) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(ui.audioSource + if (ui.preparingAudio) " · ပြင်ဆင်နေ" else " · ဖွင့်နေ", Modifier.weight(1f), fontSize = 12.sp)
            TextButton({ viewModel.pace(!ui.slow) }) { Text(if (ui.slow) "Slow" else "Natural") }
            IconButton(viewModel::stopAudio) { Icon(Icons.Default.Stop, "Stop audio") }
        }
        if (learning.readerMode == "TEXT") Row(Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Aa", fontSize = 14.sp); Slider(learning.readerFontSize, { viewModel.reading(fontSize = it) }, valueRange = 16f..32f, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton({ viewModel.page(ui.page - 1) }, enabled = !ui.loading && ui.page > 0) { Icon(Icons.Default.ChevronLeft, "Previous page") }
            TextButton({ navigation = true }, enabled = book != null) { Text("${ui.page + 1}/${book?.pageCount ?: 0}") }
            TextButton({ viewModel.reading(mode = if (learning.readerMode == "TEXT") "PDF" else "TEXT") }) { Text(if (learning.readerMode == "TEXT") "PDF" else "Aa") }
            IconButton({ if (ui.explanation != null) viewModel.showExplanation(!ui.explanationOpen) else viewModel.explain() },
                enabled = ui.pageData != null, modifier = Modifier.testTag("explain-page")) { Icon(Icons.Default.AutoAwesome, "ဒီစာမျက်နှာကို ရှင်းပြမယ်") }
            IconButton({ search = true }) { Icon(Icons.Default.Search, "Search book") }
            IconButton({ viewModel.page(ui.page + 1) }, enabled = !ui.loading && book != null && ui.page + 1 < book.pageCount) { Icon(Icons.Default.ChevronRight, "Next page") }
        }
    }
    if (navigation && book != null) ReaderNavigationDialog(book, viewModel, { navigation = false })
    if (search && book != null) AlertDialog(onDismissRequest = { searchJob?.cancel(); searchBusy = false; search = false },
        title = { Text("စာအုပ်ထဲ ရှာမယ်") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it.take(1200) }, label = { Text("စာလုံး / ဝါကျ") }, singleLine = true)
                Row {
                    Button({
                        searchJob?.cancel(); searchResults = emptyList(); searchBusy = true; searchError = ""
                        searchJob = scope.launch {
                            try { for (index in 0 until book.pageCount) {
                                val text = viewModel.loadPage(book, index).layer.text
                                if (text.contains(query, ignoreCase = true)) searchResults = (searchResults + (index to text.take(200))).take(200)
                                if (searchResults.size >= 200) break
                            } } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (failure: Exception) { searchError = failure.message.orEmpty() }
                            finally { searchBusy = false }
                        }
                    }, enabled = query.isNotBlank() && !searchBusy) { Text("ရှာမယ်") }
                    if (searchBusy) TextButton({ searchJob?.cancel(); searchBusy = false }) { Text("ရပ်မယ်") }
                }
                Text("PDF စာသား/OCR နဲ့ ရှာမယ်။ Scanned စာအုပ်မှာ အချိန်ယူနိုင်ပါတယ်။", fontSize = 12.sp)
                if (searchError.isNotBlank()) Text(searchError, color = MaterialTheme.colorScheme.error)
                if (searchBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    searchResults.forEach { (index, text) -> TextButton({ searchJob?.cancel(); viewModel.page(index); search = false }) {
                        Text("${index + 1} · $text", maxLines = 3)
                    } }
                }
            }
        }, confirmButton = { TextButton({ searchJob?.cancel(); searchBusy = false; search = false }) { Text("ပိတ်") } })
    if (audioPicker) AlertDialog(onDismissRequest = { audioPicker = false }, title = { Text("စာအုပ်ရဲ့ Track အသံ") },
        text = { Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("မူရင်းအသံဖိုင်ကို ကိုယ်တိုင်ရွေးချိတ်ပါ။ ဖိုင်မရှိရင် ကိုရီးယား TTS ကို AI အသံလို့ ပြထားပါတယ်။", fontSize = 13.sp)
            tracks.filter { it.bookId == ui.bookId }.forEach { asset -> TextButton({ viewModel.playOriginal(asset); audioPicker = false }) { Text("Track ${asset.track} · ဖွင့်မယ်") } }
            OutlinedTextField(trackInput, { trackInput = it.filter(Char::isDigit).take(4) }, label = { Text("Track နံပါတ်") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button({ audioImport.launch(arrayOf("audio/*")); audioPicker = false }, enabled = trackInput.toIntOrNull()?.let { it in 1..9999 } == true) { Text("အသံဖိုင်ရွေး") }
        } }, confirmButton = { TextButton({ audioPicker = false }) { Text("ပိတ်") } })
}

@Composable
private fun ReaderNavigationDialog(book: ReaderBook, viewModel: ReaderViewModel, onClose: () -> Unit) {
    var input by remember { mutableStateOf((book.page + 1).toString()) }
    var chapters by remember { mutableStateOf<List<BookChapter>>(emptyList()) }
    var thumbnails by remember { mutableStateOf(false) }
    LaunchedEffect(book.id) { chapters = viewModel.chapters(book.id) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Pages / Contents") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(input, { input = it.filter(Char::isDigit) }, label = { Text("1 – ${book.pageCount}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            TextButton({ thumbnails = !thumbnails }) { Text("စာမျက်နှာနမူနာများ") }
            if (thumbnails) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (maxOf(0, book.page - 2)..minOf(book.pageCount - 1, book.page + 2)).forEach { index ->
                    var preview by remember(book.id, index) { mutableStateOf<com.myothuonion.languagetalk.util.ReadablePdfPage?>(null) }
                    LaunchedEffect(index) { preview = viewModel.loadPage(book, index) }
                    Column(Modifier.width(76.dp).clickable { viewModel.page(index); onClose() }) {
                        preview?.let { Image(it.bitmap.asImageBitmap(), "Page thumbnail ${index + 1}", Modifier.width(76.dp).height(102.dp)) }
                        Text("${index + 1}", fontSize = 11.sp)
                    }
                }
            }
            Text("Bookmarks", Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
            book.bookmarks.forEach { index -> TextButton({ viewModel.page(index); onClose() }) { Text("စာမျက်နှာ ${index + 1}") } }
            chapters.forEach { chapter -> TextButton({ viewModel.page(chapter.startPage - 1); onClose() }) { Text("${chapter.number}. ${chapter.title}") } }
            if (chapters.isEmpty()) Text("ကိုယ်တင်ထားတဲ့ PDF မှာ စာမျက်နှာနံပါတ်နဲ့ သွားနိုင်ပါတယ်။", fontSize = 12.sp)
        }
    }, confirmButton = { TextButton({ input.toIntOrNull()?.takeIf { it in 1..book.pageCount }?.let { viewModel.page(it - 1); onClose() } },
        enabled = input.toIntOrNull()?.let { it in 1..book.pageCount } == true) { Text("သွားမယ်") } },
        dismissButton = { TextButton(onClose) { Text("ပိတ်") } })
}
