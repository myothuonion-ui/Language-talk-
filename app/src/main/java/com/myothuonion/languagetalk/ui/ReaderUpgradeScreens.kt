@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.myothuonion.languagetalk.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.min
import kotlin.math.max

@Composable
internal fun PdfReaderScreen(viewModel: ReaderViewModel,onBack: () -> Unit,aiViewModel: AppViewModel? = null,
    onSaved: () -> Unit = {},onPractice: () -> Unit = {}) {
    val ui by viewModel.ui.collectAsState()
    val learning by viewModel.learning.collectAsState()
    val book = learning.books.firstOrNull { it.id == ui.bookId }
    var chrome by rememberSaveable { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    var thumbnails by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    var noteOpen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var explainOpen by rememberSaveable(ui.bookId,ui.page) { mutableStateOf(false) }
    var pageInput by remember(ui.page) { mutableStateOf((ui.page+1).toString()) }
    val audioImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.importAudio(it) } }
    val pageExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri -> uri?.let { viewModel.exportPage(it) } }
    BackHandler { if (ui.selection.isNotBlank()) viewModel.dismissLookup() else onBack() }
    DisposableEffect(Unit) { onDispose { viewModel.close() } }
    androidx.lifecycle.compose.LifecycleResumeEffect(ui.bookId) {
        onPauseOrDispose { viewModel.stopAudio(); viewModel.cancelSearch() }
    }
    Column(Modifier.fillMaxSize().testTag("pdf-reader").background(MaterialTheme.colorScheme.background)) {
        if (chrome || ui.selection.isNotBlank()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 5.dp),verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Default.ArrowBack,"Back") }
                Text(book?.title ?: "Reader",Modifier.weight(1f),maxLines = 2,style = MaterialTheme.typography.titleSmall)
                IconButton({ searching = true },enabled = book != null) { Icon(Icons.Default.Search,"Search entire book") }
                IconButton({ viewModel.bookmark() },enabled = book != null) {
                    Icon(if (book?.bookmarks?.contains(ui.page) == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,"Bookmark page")
                }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Default.Tune,"Reading appearance") }
                    DropdownMenu(menu,{ menu = false }) {
                        listOf("DAY" to "Original / Day","SEPIA" to "Sepia","NIGHT" to "Night").forEach { (id,label) ->
                            DropdownMenuItem(text = { Text(label) },onClick = { viewModel.theme(id); menu = false })
                        }
                        DropdownMenuItem(text = { Text(if (learning.readerTextMode) "မူရင်း PDF" else "Text / Study mode") },
                            onClick = { viewModel.reading(textMode = !learning.readerTextMode); menu = false })
                        DropdownMenuItem(text = { Text(if (learning.readerNavigation == "PAGED") "Vertical swipe" else "Horizontal swipe") },
                            onClick = { viewModel.reading(navigation = if (learning.readerNavigation == "PAGED") "CONTINUOUS" else "PAGED"); menu = false })
                        DropdownMenuItem(text = { Text("Fit width") },onClick = { viewModel.zoom(1f); menu = false })
                        DropdownMenuItem(text = { Text("ဒီစာမျက်နှာကို လေ့ကျင့်မယ်") },onClick = {
                            menu = false; viewModel.dismissLookup(); onPractice()
                        },enabled = ui.pageData != null)
                        DropdownMenuItem(text = { Text("စာသားရိုက်ပြီး ရှာမယ်") },onClick = { manual = true; menu = false })
                        DropdownMenuItem(text = { Text("စာမျက်နှာအသေးပုံများ") },onClick = { thumbnails = true; menu = false })
                        DropdownMenuItem(text = { Text("သိမ်းထားတာတွေ") },onClick = { menu = false; onSaved() })
                        DropdownMenuItem(text = { Text("မူရင်း Track ဖိုင်ချိတ်မယ်") },onClick = { menu = false; audioImport.launch(arrayOf("audio/*")) })
                        DropdownMenuItem(text = { Text("မူရင်းစာမျက်နှာပုံ Export") },onClick = {
                            menu = false; pageExport.launch("book-page-" + (ui.page+1) + ".png")
                        },enabled = ui.pageData != null)
                    }
                }
            }
        }
        if (ui.loading || ui.explaining || ui.audioLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.error?.let { error ->
            Row(Modifier.padding(horizontal = 14.dp),verticalAlignment = Alignment.CenterVertically) {
                Text(error,Modifier.weight(1f),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.error)
                TextButton({ viewModel.page(ui.page) }) { Text("ထပ်စမ်း") }
            }
        }
        if (book != null) {
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                ReaderPagePager(viewModel,book,ui,learning) { chrome = !chrome }
            }
        } else Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        if (ui.selection.isNotBlank()) {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.padding(horizontal = 10.dp,vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ui.selection,Modifier.weight(1f),maxLines = 2,style = MaterialTheme.typography.bodyMedium)
                        IconButton({ viewModel.dismissLookup() }) { Icon(Icons.Default.Close,"Clear text selection") }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        ReaderAction(Icons.Default.Highlight,"Highlight") { viewModel.annotate("HIGHLIGHT") }
                        ReaderAction(Icons.Default.FormatUnderlined,"မျဉ်းသား") { viewModel.annotate("UNDERLINE") }
                        ReaderAction(Icons.Default.FormatBold,"Bold · Text") { viewModel.reading(textMode = true); viewModel.annotate("BOLD") }
                        ReaderAction(Icons.Default.EditNote,"မှတ်စု") { note = ""; noteOpen = true }
                        ReaderAction(Icons.Default.BookmarkAdd,"သိမ်း") { viewModel.annotate("SAVED") }
                        ReaderAction(Icons.Default.Translate,"ရှင်းပြ") { viewModel.lookup() }
                        ReaderAction(Icons.Default.VolumeUp,"နားထောင်") { viewModel.listen() }
                        ReaderAction(Icons.Default.Mic,"လေ့ကျင့်") { onPractice() }
                    }
                }
            }
        }
        if (ui.lookupOpen) {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().heightIn(max = 170.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ui.translating) Text("မြန်မာအဓိပ္ပာယ် ရှာနေပါတယ်…")
                    ui.translation?.let { value ->
                        Text(value.meaning,style = MaterialTheme.typography.bodyLarge)
                        if (value.grammar.isNotBlank()) Text(value.grammar)
                        TextButton({ viewModel.saveWord() },enabled = !ui.saved) { Text(if (ui.saved) "သိမ်းပြီးပြီ" else "ဝေါဟာရ သိမ်းမယ်") }
                    }
                }
            }
        }
        if (explainOpen) {
            Surface(Modifier.weight(.85f).fillMaxWidth(),tonalElevation = 2.dp) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("စာမျက်နှာ " + (ui.page+1) + " · မြန်မာရှင်းပြချက်",Modifier.weight(1f),fontWeight = FontWeight.SemiBold)
                        IconButton({ explainOpen = false; viewModel.cancelExplanation() }) { Icon(Icons.Default.Close,"Close page explanation") }
                    }
                    if (ui.explaining) {
                        Text("မူရင်းစာမျက်နှာကို အခြေခံပြီး ရှင်းပြနေပါတယ်။")
                        TextButton({ viewModel.cancelExplanation() }) { Text("ရပ်မယ်") }
                    }
                    ui.explanation?.let { result ->
                        Text(if (result.vision) "စာမျက်နှာပုံနှင့် စာသားကို အခြေခံထားသော AI ရှင်းပြချက်" else "ဖော်ထုတ်ထားသောစာသားကို အခြေခံထားသော AI ရှင်းပြချက်",
                            style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PageTeachingCards(result.teaching,onListen = { viewModel.listen(it) },
                            onSaveWord = { viewModel.saveTeachingWord(it) },onSource = { value ->
                                viewModel.focusSource(value); explainOpen = false
                            })
                    }
                    TextButton({ viewModel.explainPage(force = true) },enabled = !ui.explaining) { Text("ပြန်ရှင်းပြမယ်") }
                    if (aiViewModel != null) AiTaskPicker(AiTask.READER,aiViewModel)
                }
            }
        }
        if (ui.status.isNotBlank() || ui.playing) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),verticalAlignment = Alignment.CenterVertically) {
            Text(ui.status,Modifier.weight(1f),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.primary)
            if (ui.playing) IconButton({ viewModel.stopAudio() }) { Icon(Icons.Default.Stop,"Stop audio") }
            else if (ui.status.contains("Undo")) TextButton({ viewModel.undo() }) { Text("Undo") }
        }
        if (chrome) {
            if (learning.readerTextMode) Row(Modifier.padding(horizontal = 14.dp),verticalAlignment = Alignment.CenterVertically) {
                Text("Aa")
                Slider(learning.readerFontSize.toFloat(),{ viewModel.reading(size = it.toInt()) },valueRange = 16f..32f,modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding(),verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton({ viewModel.page(ui.page-1) },enabled = book != null && ui.page > 0) { Icon(Icons.Default.ChevronLeft,"Previous page") }
                TextButton({ goTo = true },enabled = book != null) { Text((ui.page+1).toString() + " / " + (book?.pageCount ?: 0)) }
                IconButton({ viewModel.reading(textMode = !learning.readerTextMode) }) { Icon(Icons.Default.TextFields,"Toggle text mode") }
                IconButton({ explainOpen = true; viewModel.explainPage() },enabled = ui.pageData != null) { Icon(Icons.Default.AutoAwesome,"Explain entire page") }
                IconButton({ viewModel.page(ui.page+1) },enabled = book != null && ui.page+1 < book.pageCount) { Icon(Icons.Default.ChevronRight,"Next page") }
            }
            val track = learning.audioTracks.firstOrNull { it.bookId == ui.bookId && it.page == ui.page }
            if (track != null) TextButton({ viewModel.listen(original = true) },modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.VolumeUp,null); Text("မူရင်း Track " + track.track + " · " + track.name)
            }
        }
    }
    if (manual) AlertDialog(onDismissRequest = { manual = false },title = { Text("အဓိပ္ပာယ်ရှာ") },
        text = { OutlinedTextField(manualText,{ manualText = it.take(1200) },label = { Text("ဝေါဟာရ သို့မဟုတ် ဝါကျ") }) },
        confirmButton = { TextButton({ viewModel.lookup(manualText,ui.pageData?.layer?.text.orEmpty()); manual = false },enabled = manualText.isNotBlank()) { Text("ရှာမယ်") } },
        dismissButton = { TextButton({ manual = false }) { Text("ပိတ်") } })
    if (goTo && book != null) AlertDialog(onDismissRequest = { goTo = false },title = { Text("စာမျက်နှာသို့ သွားမယ်") },
        text = { OutlinedTextField(pageInput,{ pageInput = it.filter(Char::isDigit) },keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),singleLine = true,label = { Text("1 – " + book.pageCount) }) },
        confirmButton = { TextButton({ pageInput.toIntOrNull()?.takeIf { it in 1..book.pageCount }?.let { viewModel.page(it-1); goTo = false } },
            enabled = pageInput.toIntOrNull()?.let { it in 1..book.pageCount } == true) { Text("သွားမယ်") } },
        dismissButton = { TextButton({ goTo = false }) { Text("ပိတ်") } })
    if (noteOpen) AlertDialog(onDismissRequest = { noteOpen = false },title = { Text("မှတ်စု") },
        text = { OutlinedTextField(note,{ note = it.take(4000) },Modifier.fillMaxWidth(),label = { Text("ကိုယ့်မှတ်စု") }) },
        confirmButton = { TextButton({ viewModel.annotate("NOTE",note); noteOpen = false }) { Text("သိမ်းမယ်") } },
        dismissButton = { TextButton({ noteOpen = false }) { Text("ပိတ်") } })
    if (searching && book != null) ReaderSearchDialog(viewModel,book,ui) { searching = false; viewModel.cancelSearch() }
    if (thumbnails && book != null) AlertDialog(onDismissRequest = { thumbnails = false },title = { Text("စာမျက်နှာအသေးပုံများ") },
        text = {
            LazyVerticalGrid(GridCells.Adaptive(88.dp),Modifier.fillMaxWidth().heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(book.pageCount,key = { it }) { page ->
                    val thumb by produceState<android.graphics.Bitmap?>(null,book.id,page) {
                        value = runCatching { viewModel.thumbnail(page) }.getOrNull()
                    }
                    Column(Modifier.clickable { viewModel.page(page); thumbnails = false },horizontalAlignment = Alignment.CenterHorizontally) {
                        thumb?.let { Image(it.asImageBitmap(),"Page " + (page+1),Modifier.fillMaxWidth().height(110.dp)) }
                            ?: Box(Modifier.fillMaxWidth().height(110.dp).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment = Alignment.Center) {
                                Text((page+1).toString())
                            }
                        Text((page+1).toString(),style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },confirmButton = { TextButton({ thumbnails = false }) { Text("ပိတ်") } })
}

@Composable
private fun ReaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector,label: String,onClick: () -> Unit) {
    TextButton(onClick) { Icon(icon,null,Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(label,style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun ReaderPagePager(viewModel: ReaderViewModel,book: ReaderBook,ui: ReaderUiState,learning: LearningState,onTap: () -> Unit) {
    val pager = rememberPagerState(initialPage = ui.page.coerceIn(0,book.pageCount-1)) { book.pageCount }
    val current by rememberUpdatedState(ui)
    var zoom by remember(book.id) { mutableFloatStateOf(book.zoom) }
    LaunchedEffect(ui.page,ui.loading) {
        if (!ui.loading && pager.currentPage != ui.page && !pager.isScrollInProgress) pager.scrollToPage(ui.page)
    }
    LaunchedEffect(pager,book.id) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            if (!current.loading && current.pageData != null && page != current.page) viewModel.page(page)
        }
    }
    val enabled = ui.selection.isBlank() && (learning.readerTextMode || zoom <= 1.01f)
    val content: @Composable (Int) -> Unit = { page ->
        val data by produceState<ReadablePdfPage?>(if (page == ui.page) ui.pageData else null,book.id,page) {
            value = runCatching { viewModel.pageData(page) }.getOrNull()
        }
        val annotations = learning.annotations.filter { it.bookId == book.id && it.page == page &&
            (it.fingerprint.isBlank() || it.fingerprint == book.fingerprint) }
        data?.let {
            if (learning.readerTextMode) ReaderTextPage(it,annotations,learning.readerFontSize,viewModel)
            else ReaderImagePage(it,annotations,book.zoom,learning.readerTheme,viewModel,onTap,page+1) { zoom = it }
        } ?: Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
    if (learning.readerNavigation == "PAGED") HorizontalPager(pager,Modifier.fillMaxSize(),userScrollEnabled = enabled) { content(it) }
    else VerticalPager(pager,Modifier.fillMaxSize(),userScrollEnabled = enabled) { content(it) }
}

@Composable
private fun ReaderImagePage(data: ReadablePdfPage,annotations: List<ReaderAnnotation>,initialZoom: Float,theme: String,
    viewModel: ReaderViewModel,onTap: () -> Unit,pageNumber: Int,onZoom: (Float) -> Unit) {
    var scale by remember(data) { mutableFloatStateOf(initialZoom) }
    var pan by remember(data) { mutableStateOf(Offset.Zero) }
    var selection by remember(data) { mutableStateOf<List<PdfWord>>(emptyList()) }
    var selecting by remember(data) { mutableStateOf(false) }
    val tint = remember(theme) { when (theme) {
        "NIGHT" -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(-.82f,0f,0f,0f,239f,0f,-.8f,0f,0f,240f,0f,0f,-.82f,0f,235f,0f,0f,0f,1f,0f)))
        "SEPIA" -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(.96f,0f,0f,0f,0f,0f,.93f,0f,0f,0f,0f,0f,.86f,0f,0f,0f,0f,0f,1f,0f)))
        else -> null
    } }
    val ink = MaterialTheme.colorScheme.primary
    val current by viewModel.ui.collectAsState()
    LaunchedEffect(current.selection) { if (current.selection.isBlank()) selection = emptyList() }
    LaunchedEffect(initialZoom) { if (initialZoom <= 1f && scale > 1f) { scale = 1f; pan = Offset.Zero } }
    LaunchedEffect(scale) { onZoom(scale); delay(350); viewModel.zoom(scale) }
    Box(Modifier.fillMaxSize().clipToBounds().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().aspectRatio(data.bitmap.width.toFloat()/data.bitmap.height)
            .graphicsLayer(scaleX = scale,scaleY = scale,translationX = pan.x,translationY = pan.y)
            .pointerInput(data) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f,4f)
                            pan = if (scale <= 1f) Offset.Zero else pan + event.calculatePan()
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1.01f && !selecting && event.changes.any { it.pressed }) {
                            pan += event.calculatePan(); event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(data) { detectTapGestures(onTap = { onTap() }) }
            .pointerInput(data) {
                var anchor = -1; var end = -1; var point = Offset.Zero
                fun hit(p: Offset): Int {
                    val x = p.x/size.width; val y = p.y/size.height
                    return data.layer.words.indexOfFirst { x >= it.left-.008f && x <= it.right+.008f && y >= it.top-.008f && y <= it.bottom+.008f }
                }
                fun update() { selection = if (anchor >= 0 && end >= 0) data.layer.words.subList(min(anchor,end),max(anchor,end)+1) else emptyList() }
                detectDragGesturesAfterLongPress(onDragStart = { selecting = true; point = it; anchor = hit(it); end = anchor; update() },
                    onDrag = { change,amount -> change.consume(); point += amount; val target = hit(point); if (target >= 0) { end = target; update() } },
                    onDragCancel = { selecting = false },onDragEnd = { selecting = false; if (selection.isNotEmpty()) viewModel.select(selection.joinToString(" ") { it.text },
                        selection.map { it.sentence }.distinct().joinToString(" "),selection.map { ReaderRect(it.left,it.top,it.right,it.bottom) }) })
            }) {
            Image(data.bitmap.asImageBitmap(),"PDF page " + pageNumber,Modifier.fillMaxSize(),colorFilter = tint)
            Canvas(Modifier.fillMaxSize()) {
                annotations.forEach { value ->
                    value.rectangles.forEach { rect ->
                        val origin = Offset(rect.left*size.width,rect.top*size.height)
                        val width = (rect.right-rect.left)*size.width
                        val height = (rect.bottom-rect.top)*size.height
                        when (value.kind) {
                            "HIGHLIGHT","NOTE" -> drawRect(Color(0xFFFFC857).copy(alpha = .35f),origin,Size(width,height))
                            "UNDERLINE" -> drawLine(ink,Offset(origin.x,origin.y+height),Offset(origin.x+width,origin.y+height),strokeWidth = 2.dp.toPx())
                        }
                    }
                }
                val active = if (current.selection.isNotBlank() && current.page+1 == pageNumber) current.rectangles
                    else selection.map { ReaderRect(it.left,it.top,it.right,it.bottom) }
                active.forEach { rect -> drawRect(ink.copy(alpha = .25f),Offset(rect.left*size.width,rect.top*size.height),
                    Size((rect.right-rect.left)*size.width,(rect.bottom-rect.top)*size.height)) }
            }
        }
    }
}

@Composable
private fun ReaderTextPage(data: ReadablePdfPage,annotations: List<ReaderAnnotation>,font: Int,viewModel: ReaderViewModel) {
    var layout by remember(data) { mutableStateOf<TextLayoutResult?>(null) }
    val marked = remember(data.layer.text,annotations) {
        buildAnnotatedString {
            append(data.layer.text)
            annotations.forEach { value ->
                var at = data.layer.text.indexOf(value.quote)
                if (at < 0) at = data.layer.text.replace("\n"," ").indexOf(value.quote)
                if (at >= 0 && at+value.quote.length <= length) addStyle(when (value.kind) {
                    "BOLD" -> SpanStyle(fontWeight = FontWeight.Bold)
                    "UNDERLINE" -> SpanStyle(textDecoration = TextDecoration.Underline)
                    "HIGHLIGHT","NOTE" -> SpanStyle(background = Color(0x55FFC857))
                    else -> SpanStyle()
                },at,at+value.quote.length)
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        if (data.layer.text.isBlank()) Text("စာသား မဖော်ထုတ်နိုင်သေးပါ။ မူရင်း PDF ကို ဆက်ဖတ်နိုင်ပါတယ်။")
        Text(marked,fontSize = font.sp,lineHeight = (font*1.8f).sp,onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth().testTag("reader-text").pointerInput(data) {
                var anchor = 0; var end = 0; var pointer = Offset.Zero
                detectDragGesturesAfterLongPress(onDragStart = { point -> pointer = point; anchor = layout?.getOffsetForPosition(point) ?: 0; end = anchor },
                    onDrag = { change,amount -> change.consume(); pointer += amount; end = layout?.getOffsetForPosition(pointer) ?: anchor },
                    onDragEnd = {
                        val text = data.layer.text
                        if (text.isNotBlank()) {
                            var a = min(anchor,end).coerceIn(0,text.lastIndex)
                            var b = max(anchor,end).coerceIn(a,text.lastIndex)+1
                            while (a > 0 && !text[a-1].isWhitespace()) a--
                            while (b < text.length && !text[b].isWhitespace()) b++
                            viewModel.select(text.substring(a,b),text.substring((a-200).coerceAtLeast(0),(b+200).coerceAtMost(text.length)))
                        }
                    })
            })
    }
}

@Composable
private fun ReaderSearchDialog(viewModel: ReaderViewModel,book: ReaderBook,ui: ReaderUiState,onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose,title = { Text("စာအုပ်တစ်အုပ်လုံး ရှာမယ်") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(query,{ query = it.take(120) },Modifier.fillMaxWidth(),label = { Text("ဝေါဟာရ / ဝါကျ") },singleLine = true)
                Row {
                    Button({ viewModel.search(query) },enabled = query.isNotBlank()) { Text("ရှာမယ်") }
                    if (ui.searching) TextButton({ viewModel.cancelSearch() }) { Text("ရပ်မယ်") }
                }
                if (ui.searching) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("စာမျက်နှာ " + ui.searchPage + " / " + book.pageCount,style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(ui.searchHits,key = { it.page }) { hit ->
                        TextButton({ viewModel.page(hit.page); onClose() }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text("စာမျက်နှာ " + (hit.page+1),fontWeight = FontWeight.SemiBold)
                                Text(hit.excerpt,style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },confirmButton = { TextButton(onClose) { Text("ပိတ်") } })
}
