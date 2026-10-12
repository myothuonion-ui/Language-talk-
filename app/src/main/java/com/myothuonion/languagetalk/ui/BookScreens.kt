@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myothuonion.languagetalk.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.myothuonion.languagetalk.data.AppSettings
import com.myothuonion.languagetalk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Composable
internal fun BookCoursesScreen(viewModel: BookViewModel, settings: AppSettings,
    onBack: () -> Unit, onSettings: () -> Unit, onRead: ((String, Int) -> Unit)? = null,
    aiViewModel: AppViewModel? = null, onApply: (TutorConfig) -> Unit) {
    val ui by viewModel.state.collectAsState()
    val shelf by viewModel.shelf.collectAsState()
    val live by viewModel.live.collectAsState()
    var lessonOpen by rememberSaveable { mutableStateOf(false) }
    var reviewOpen by rememberSaveable { mutableStateOf(false) }
    var pdfPage by rememberSaveable { mutableIntStateOf(0) }
    val book = ui.courses.firstOrNull { it.id == ui.bookId }
    val state = book?.let { course -> shelf.progress.firstOrNull { it.bookId == course.id && BookEngine.validate(course, it) } ?: BookProgress(course.id, course.version) }
    val context = LocalContext.current
    var voiceAction by remember { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (voiceAction == "live") viewModel.startVoice(settings.defaultSilenceMs) else viewModel.toggleRecording()
        } else viewModel.microphoneDenied()
    }
    val microphone: (String) -> Unit = { action ->
        voiceAction = action
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (action == "live") viewModel.startVoice(settings.defaultSilenceMs) else viewModel.toggleRecording()
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    BackHandler {
        viewModel.close()
        if (reviewOpen) reviewOpen = false else if (lessonOpen) lessonOpen = false else onBack()
    }
    DisposableEffect(Unit) { onDispose { viewModel.close() } }
    androidx.lifecycle.compose.LifecycleResumeEffect(ui.bookId) { onPauseOrDispose { viewModel.stopVoice() } }

    if (book == null || state == null) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onBack) { Text("Back") }
            Text("Built-in စာအုပ်များ ဖွင့်နေပါတယ်")
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (ui.busy) CircularProgressIndicator()
        }
        return
    }
    val chapter = BookEngine.chapter(book, state)
    val section = BookEngine.section(book, state)
    val target = BookEngine.target(book, state)
    val finished = BookEngine.finished(book, state)
    val canControl = !ui.busy && !live.connected && !ui.recording
    var answer by remember(book.id, state.chapter, state.section, state.activity) { mutableStateOf("") }
    var hint by remember(book.id, state.chapter, state.section, state.activity) { mutableStateOf(section.kind == "DIALOGUE") }
    var script by remember(book.id, state.chapter, state.section) { mutableStateOf(false) }
    var english by remember(book.id, state.chapter, state.section) { mutableStateOf(false) }
    var keys by remember(book.id, state.chapter, state.section) { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().testTag("book-courses"), contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ viewModel.close(); if (reviewOpen) reviewOpen = false else if (lessonOpen) lessonOpen = false else onBack() }) { Icon(Icons.Default.ArrowBack, "Back") }
                Text(if (reviewOpen) "စာအုပ် · ပြန်လေ့ကျင့်ရန်" else if (lessonOpen) "${book.level} · Lesson ${state.chapter}" else "My Books", modifier = Modifier.weight(1f), maxLines = 2, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                aiViewModel?.let { AiTaskPicker(AiTask.BOOK, it) }
            }
        }
        ui.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (reviewOpen) {
            if (state.mistakes.isEmpty()) item { Text("လေ့ကျင့်ရာမှာ ပြင်ဖို့လိုတဲ့ စာကြောင်းတွေ ဒီမှာသိမ်းမယ်") }
            items(state.mistakes.reversed(), key = { it.key }) { mistake ->
                OutlinedCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(mistake.target, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("မင်းပြောခဲ့တာ: ${mistake.heard}")
                        Text(mistake.feedback)
                        Text("စာမျက်နှာ ${mistake.page} · ပြန်ဖြေမှန် ${mistake.successes} ကြိမ်", fontSize = 12.sp)
                        Row { TextButton({ viewModel.speak(mistake.target) }, enabled = canControl) { Text("နားထောင်") }
                            TextButton({ pdfPage = mistake.page }) { Text("မူရင်းစာမျက်နှာ") } }
                    }
                }
            }
        } else if (!lessonOpen) {
            item {
                Text("TTMIK · Real-Life Korean Conversations", fontWeight = FontWeight.SemiBold)
                Text("စာအုပ် ၂ အုပ် · အခန်း ၇၀ · PDF ပြန်တင်စရာမလိုပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("မူရင်းစာအုပ်ကို offline ဖတ်နိုင်ပါတယ်။ မြန်မာရှင်းပြချက်နဲ့ အသံအတွက် Gemini key လိုပါတယ်။", fontSize = 12.sp)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ui.courses.forEach { course ->
                        FilterChip(course.id == book.id, { viewModel.selectBook(course.id) }, enabled = canControl,
                            label = { Text("${course.level} · ${course.chapters.size}") })
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                        Text(book.title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("${book.pageCount} စာမျက်နှာ · လေ့လာပြီး ${state.completedChapters.size}/${book.chapters.size} ခန်း")
                        Text("ရပ်ထားတဲ့နေရာ: ${state.chapter}. ${chapter.title} · ${section.label}")
                        Button({ viewModel.resume(); lessonOpen = true }, enabled = canControl, modifier = Modifier.fillMaxWidth().testTag("continue-book")) { Text("Continue book lesson") }
                        Row {
                            TextButton({ pdfPage = 1 }) { Text("စာအုပ်ဖတ်ရန်") }
                            TextButton({ reviewOpen = true }) { Text("ပြန်လေ့ကျင့်ရန် ${state.mistakes.size}") }
                        }
                    }
                }
            }
            items(book.chapters, key = { it.number }) { unit ->
                val unlocked = true
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${unit.number}. ${unit.title}", fontWeight = FontWeight.Bold)
                        Text("${unit.category} · စာမျက်နှာ ${unit.startPage}–${unit.endPage}", fontSize = 12.sp)
                        if (unit.number in state.completedChapters) Text("လေ့လာပြီး", color = MaterialTheme.colorScheme.primary)
                        Row {
                            TextButton({ if (unit.number != state.chapter) viewModel.selectChapter(unit.number) else viewModel.resume(); lessonOpen = true }, enabled = unlocked && canControl) {
                                Text(if (unlocked) "သင်ခန်းစာဖွင့်" else "အရင်အခန်းပြီးမှ ဖွင့်မယ်")
                            }
                            TextButton({ pdfPage = unit.startPage }) { Text("စာအုပ်ကြည့်") }
                        }
                    }
                }
            }
            item { TextButton({ viewModel.close(); onSettings() }) { Text("အသံနဲ့ Gemini settings") } }
        } else {
            item {
                BookPracticePanel(viewModel, ui, state, book, section, live, settings, microphone,
                    onRead = { viewModel.stopVoice(); pdfPage = it }, onApply = onApply)
            }
        }
    }
    if (pdfPage > 0) {
        if (onRead != null) LaunchedEffect(pdfPage) { onRead(book.id, pdfPage); pdfPage = 0 }
        else BookPdfDialog(book, pdfPage, viewModel, { pdfPage = 0 })
    }
}

@Composable
private fun BookPdfDialog(book: BookCourse, initialPage: Int, viewModel: BookViewModel, onDismiss: () -> Unit) {
    var page by remember(book.id, initialPage) { mutableIntStateOf(initialPage.coerceIn(1, book.pageCount)) }
    var zoom by remember(page) { mutableFloatStateOf(1f) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }
    var error by remember(page) { mutableStateOf<String?>(null) }
    var bitmap by remember(book.id, page) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(book.id, page) {
        bitmap = try {
            withContext(Dispatchers.IO) {
                val file = viewModel.sourcePdf(book.id)
                PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                    renderer.openPage(page - 1).use { source ->
                        Bitmap.createBitmap(1050, (1050f * source.height / source.width).toInt(), Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            source.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message; null }
    }
    val transform = rememberTransformableState { change, pan, _ -> zoom = (zoom * change).coerceIn(1f, 4f); offset += pan }
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${book.level} · မူရင်းစာမျက်နှာ $page/${book.pageCount}", fontWeight = FontWeight.Bold)
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Color.White).transformable(transform), contentAlignment = Alignment.Center) {
                    bitmap?.let { Image(it.asImageBitmap(), "Original book page $page", Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offset.x, translationY = offset.y)) }
                    if (bitmap == null && error == null) CircularProgressIndicator()
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                Text("လက်နှစ်ချောင်းနဲ့ ချဲ့ကြည့်နိုင်ပါတယ်", fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ page-- }, enabled = page > 1) { Text("Previous") }
                    TextButton(onDismiss) { Text("Close") }
                    TextButton({ page++ }, enabled = page < book.pageCount) { Text("Next") }
                }
            }
        }
    }
}
