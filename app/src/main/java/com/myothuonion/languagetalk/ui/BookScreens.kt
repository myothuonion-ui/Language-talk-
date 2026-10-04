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
import kotlinx.coroutines.withContext

@Composable
internal fun BookCoursesScreen(viewModel: BookViewModel, settings: AppSettings,
    onBack: () -> Unit, onSettings: () -> Unit, onApply: (TutorConfig) -> Unit) {
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
    var hint by remember(book.id, state.chapter, state.section, state.activity) { mutableStateOf(false) }
    var script by remember(book.id, state.chapter, state.section) { mutableStateOf(false) }
    var english by remember(book.id, state.chapter, state.section) { mutableStateOf(false) }
    var keys by remember(book.id, state.chapter, state.section) { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().testTag("book-courses"), contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ viewModel.close(); if (reviewOpen) reviewOpen = false else if (lessonOpen) lessonOpen = false else onBack() }) { Icon(Icons.Default.ArrowBack, "Back") }
                Text(if (reviewOpen) "စာအုပ် · ပြန်လေ့ကျင့်ရန်" else if (lessonOpen) "${book.level} · Lesson ${state.chapter}" else "My Books", fontSize = 23.sp, fontWeight = FontWeight.Bold)
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
                        Icon(Icons.Default.MenuBook, null, tint = Mint)
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
                val unlocked = unit.number <= BookEngine.frontier(book, state)
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${unit.number}. ${unit.title}", fontWeight = FontWeight.Bold)
                        Text("${unit.category} · စာမျက်နှာ ${unit.startPage}–${unit.endPage}", fontSize = 12.sp)
                        if (unit.number in state.completedChapters) Text("လေ့လာပြီး", color = Mint)
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
                Text(chapter.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${state.section + 1}/${chapter.sections.size} · ${section.label}", color = Mint)
                if (section.sourceNote.isNotBlank()) Text(section.sourceNote, color = Coral, fontSize = 13.sp)
                LinearProgressIndicator(progress = { (state.section + 1f) / chapter.sections.size }, modifier = Modifier.fillMaxWidth())
                TextButton({ viewModel.stopVoice(); pdfPage = section.pages.first() }, enabled = !ui.busy) { Text("မူရင်းစာမျက်နှာ ${section.pages.joinToString()}") }
            }
            if (section.turns.isNotEmpty()) item {
                Text("မင်းရဲ့ role ကို ရွေးပါ", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    section.roles.forEach { role -> FilterChip(BookEngine.role(section, state) == role,
                        { if (role != BookEngine.role(section, state)) viewModel.restart(role) }, enabled = canControl, label = { Text(role) }) }
                }
                Text("AI က ကျန် role တွေကို ပြောပြီး မင်းအလှည့်ကို စောင့်မယ်။", fontSize = 12.sp)
            }
            if (state.lastAssistantCaption.isNotBlank()) item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("AI အလှည့်", color = Mint, fontWeight = FontWeight.Bold)
                        Text(state.lastAssistantCaption, fontSize = 19.sp)
                        TextButton({ viewModel.speak(state.lastAssistantText) }, enabled = canControl) { Text("ထပ်နားထောင်") }
                    }
                }
            }
            if (target != null) item {
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(if (section.exercises.isNotEmpty()) "လေ့ကျင့်ခန်း ${state.activity + 1}/${section.exercises.size}" else "မင်းအလှည့် · ${BookEngine.role(section, state)}", fontWeight = FontWeight.Bold)
                        if (section.exercises.isNotEmpty()) Text(target.prompt, fontSize = 17.sp)
                        if ((section.kind == "DIALOGUE") || hint) Text(target.text, fontSize = 22.sp, color = Mint)
                        else Text("အဖြေကို မကြည့်ဘဲ အရင်ကြိုးစားပါ")
                        if (english && section.turns.isNotEmpty()) Text(section.turns[state.activity].english)
                        Row {
                            TextButton({ hint = !hint }) { Text(if (hint) "အဖြေဖျောက်" else "အဖြေကြည့်") }
                            TextButton({ viewModel.explain(true) }, enabled = canControl) { Text("မြန်မာလိုရှင်းပြ") }
                        }
                        OutlinedTextField(answer, { answer = it }, label = { Text("ကိုရီးယားလို ဖြေပါ") }, modifier = Modifier.fillMaxWidth().testTag("book-answer"), enabled = canControl)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ viewModel.send(answer) }, enabled = canControl && answer.isNotBlank()) { Text("အဖြေစစ်") }
                            OutlinedButton({ microphone("record") }, enabled = !ui.busy && !live.connected) { Text(if (ui.recording) "အသံပို့" else "အသံနဲ့ဖြေ") }
                        }
                    }
                }
            }
            if (state.heard.isNotBlank()) item {
                Text("ကြားခဲ့တာ: ${state.heard}")
                if (state.feedback.isNotBlank()) Text(state.feedback, color = Coral)
            }
            if (section.turns.isNotEmpty() || section.exercises.isNotEmpty()) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.slow, { viewModel.pace(true) }, enabled = canControl, label = { Text("Slow") })
                    FilterChip(!state.slow, { viewModel.pace(false) }, enabled = canControl, label = { Text("Natural") })
                }
                if (!live.connected) Button({ microphone("live") }, enabled = canControl && !finished, modifier = Modifier.fillMaxWidth()) { Text("Hands-free book conversation") }
                else {
                    Text("${live.phase} · ${if (live.micEnabled) "နားထောင်နေပါတယ်" else "Mic ပိတ်ထားပါတယ်"}")
                    if (live.userCaption.isNotBlank()) Text(live.userCaption)
                    if (live.aiCaption.isNotBlank()) Text(live.aiCaption)
                    Row { OutlinedButton(viewModel::toggleMic) { Text(if (live.micEnabled) "Mic ပိတ်" else "Mic ဖွင့်") }
                        TextButton(viewModel::stopVoice) { Text("Stop") } }
                }
                live.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("‘ထပ်ပြော၊ ဖြည်းဖြည်း၊ မြန်မာလိုရှင်းပြ’ ဆိုရင် အလှည့်မကျော်ပါ။ အသံကို Settings မှာပြောင်းနိုင်ပါတယ်။", fontSize = 12.sp)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton({ viewModel.explain() }, enabled = canControl) { Text("ဒီအပိုင်းကို မြန်မာလိုသင်ပေး") }
                    if (section.turns.isNotEmpty()) TextButton({ english = !english }) { Text("English") }
                }
                if (ui.explanation.isNotBlank()) {
                    Text("မြန်မာရှင်းပြချက်", fontWeight = FontWeight.Bold, color = Mint)
                    Text(ui.explanation, fontSize = 17.sp)
                    Text("မူရင်းစာကို အခြေခံထားတဲ့ AI ရှင်းပြချက်", fontSize = 11.sp)
                    TextButton({ viewModel.speak(ui.explanation) }, enabled = canControl) { Text("ရှင်းပြချက်နားထောင်") }
                }
            }
            item {
                if (section.turns.isNotEmpty()) TextButton({ script = !script }) { Text(if (script) "Dialogue စာသားဖျောက်" else "မူရင်း Dialogue စာသားကြည့်") }
                if (section.turns.isEmpty() || script) Text(section.sourceText, fontSize = 17.sp)
                if (section.answerText.isNotBlank()) {
                    TextButton({ keys = !keys }) { Text(if (keys) "Answer key ဖျောက်" else "စာအုပ် Answer key ကြည့်") }
                    if (keys) Text(section.answerText)
                }
            }
            item {
                if (finished) Text("ဒီအပိုင်းကို ဆက်နိုင်ပါပြီ။ လေ့လာပြီးမှုနဲ့ အလွတ်သုံးနိုင်မှုကို သီးခြားလေ့ကျင့်မယ်။", color = Mint)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(viewModel::previous, enabled = canControl && state.section > 0) { Text("အရင်အပိုင်း") }
                    Button(viewModel::next, enabled = canControl && finished, modifier = Modifier.testTag("book-next")) { Text("နောက်အပိုင်း") }
                }
                TextButton({ viewModel.restart() }, enabled = canControl) { Text("ဒီအပိုင်း ပြန်လေ့ကျင့်") }
                if (state.section == chapter.sections.lastIndex && finished) {
                    OutlinedButton({ viewModel.close(); viewModel.applicationPractice()?.let(onApply) }, enabled = canControl, modifier = Modifier.fillMaxWidth()) { Text("ကိုယ့်အလုပ်နဲ့ နေ့စဉ်ဘဝမှာ အသုံးချပြောမယ်") }
                }
                if (state.completedChapters.size == book.chapters.size && book.id == "ttmik-beginner") {
                    Button({ viewModel.selectBook("ttmik-intermediate"); lessonOpen = false }, enabled = canControl) { Text("Intermediate ကို ဆက်သင်မယ်") }
                }
            }
        }
    }
    if (pdfPage > 0) BookPdfDialog(book, pdfPage, viewModel, { pdfPage = 0 })
}

@Composable
private fun BookPdfDialog(book: BookCourse, initialPage: Int, viewModel: BookViewModel, onDismiss: () -> Unit) {
    var page by remember(book.id, initialPage) { mutableIntStateOf(initialPage.coerceIn(1, book.pageCount)) }
    var zoom by remember(page) { mutableFloatStateOf(1f) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }
    var error by remember(page) { mutableStateOf<String?>(null) }
    val bitmap by produceState<Bitmap?>(null, book.id, page) {
        value = try {
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
        } catch (failure: Exception) { error = failure.message; null }
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
