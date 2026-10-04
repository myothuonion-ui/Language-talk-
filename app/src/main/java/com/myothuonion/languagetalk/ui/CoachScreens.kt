@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myothuonion.languagetalk.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.myothuonion.languagetalk.data.AppSettings
import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.network.LivePhase
import java.time.LocalDate

@Composable
internal fun CoachScreen(viewModel: CoachViewModel, settings: AppSettings, onBooks: () -> Unit,
    onConversation: () -> Unit, onSettings: () -> Unit) {
    val ui by viewModel.ui.collectAsState()
    val state by viewModel.learning.collectAsState()
    if (ui.open) { CoachLessonScreen(viewModel, settings); return }
    var chooseLevel by remember { mutableStateOf(false) }
    var topic by rememberSaveable { mutableStateOf("All") }
    var chooseTopic by remember { mutableStateOf(false) }
    val today = state.studySeconds[LocalDate.now().toString()] ?: 0L
    val due = CoachEngine.due(state, System.currentTimeMillis()).size
    val units = viewModel.curriculum.filter { it.level == state.level && (topic == "All" || it.topic == topic) }
    LazyColumn(Modifier.fillMaxSize().testTag("coach-home"), contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Text("YOUR KOREAN", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                Text("တစ်နေ့ နည်းနည်းစီ", fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
                Text("နားလည်ပြီး ကိုယ်တိုင်ပြောနိုင်အောင်", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ဒီနေ့လေ့ကျင့်မယ်", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text("ပြန်လေ့ကျင့်ရန် " + due + " ခု · သင်ခန်းစာ တစ်ဆင့်ချင်း")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 30).forEach { minutes -> FilterChip(state.dailyMinutes == minutes, { viewModel.minutes(minutes) },
                            label = { Text(minutes.toString() + " min") }) }
                    }
                    LinearProgressIndicator(progress = { (today.toFloat() / (state.dailyMinutes * 60)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth())
                    Text("ဒီနေ့ လေ့ကျင့်ချိန် " + (today / 60) + " မိနစ်", fontSize = 12.sp)
                    Button(viewModel::openToday, enabled = !ui.busy, modifier = Modifier.fillMaxWidth().testTag("start-today")) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("စလေ့ကျင့်မယ်")
                    }
                }
            }
        }
        ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onSettings) { Text("AI ချိတ်ဆက်မှု") } } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box {
                    TextButton({ chooseLevel = true }) { Text(CoachEngine.levels[state.level]); Icon(Icons.Default.ExpandMore, null) }
                    DropdownMenu(chooseLevel, { chooseLevel = false }) {
                        CoachEngine.levels.forEachIndexed { level, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { viewModel.level(level); topic = "All"; chooseLevel = false })
                        }
                    }
                }
                TextButton(viewModel::placement, enabled = !ui.busy) { Text("အဆင့်စမ်းမယ်") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("သင်ခန်းစာများ", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Box {
                    TextButton({ chooseTopic = true }) { Text(if (topic == "All") "အားလုံး" else topic); Icon(Icons.Default.ExpandMore, null) }
                    DropdownMenu(chooseTopic, { chooseTopic = false }) {
                        (listOf("All") + viewModel.curriculum.filter { it.level == state.level }.map { it.topic }.distinct()).forEach { value ->
                            DropdownMenuItem(text = { Text(value) }, onClick = { topic = value; chooseTopic = false })
                        }
                    }
                }
            }
        }
        items(units, key = { it.id }) { unit ->
            val progress = CoachEngine.progress(state, unit.id)
            val number = viewModel.curriculum.filter { it.level == state.level }.indexOf(unit) + 1
            Row(Modifier.fillMaxWidth().clickable { viewModel.openUnit(unit.id) }.padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                    if (progress.completed) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) else Text(number.toString())
                }
                Column(Modifier.weight(1f)) {
                    Text(unit.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(unit.goal, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (progress.attempts > 0) Text(if (progress.completed) "အသုံးချစွမ်းရည် စစ်ပြီးပြီ" else CoachStep.entries[progress.step].label,
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.Default.ChevronRight, null)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onBooks, Modifier.weight(1f)) { Text("စာအုပ်သင်ခန်းစာ") }
                OutlinedButton(onConversation, Modifier.weight(1f)) { Text("Live စကားပြော") }
            }
        }
    }
}

@Composable
private fun CoachLessonScreen(viewModel: CoachViewModel, settings: AppSettings) {
    val ui by viewModel.ui.collectAsState()
    val state by viewModel.learning.collectAsState()
    val live by viewModel.live.collectAsState()
    val unit = if (ui.placement) viewModel.curriculum.filter { it.level == state.placementLevel }[state.placementIndex.coerceAtMost(1)]
        else viewModel.curriculum.firstOrNull { it.id == ui.unitId } ?: viewModel.curriculum.first()
    val progress = CoachEngine.progress(state, unit.id)
    val card = state.cards.firstOrNull { it.id == ui.reviewId }
    val task = when {
        ui.placement -> if (state.placementIndex == 0) CoachEngine.task(unit, 0) else CoachEngine.task(unit, CoachStep.CHECK.ordinal)
        card != null -> CoachTask(card.prompt, card.prompt, card.criterion, card.example, card.meaning, card.title)
        else -> CoachEngine.task(unit, progress.step)
    }
    val completed = when {
        ui.placement -> state.placementDone
        ui.reviewId != null -> card == null || card.dueAt > System.currentTimeMillis()
        else -> progress.completed
    }
    val context = LocalContext.current
    var action by remember { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { if (action == "live") viewModel.startVoice(settings.defaultSilenceMs) else viewModel.record() } else viewModel.denyMicrophone()
    }
    val microphone: (String) -> Unit = { value ->
        action = value
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (value == "live") viewModel.startVoice(settings.defaultSilenceMs) else viewModel.record()
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    var hint by remember(unit.id, progress.step, state.placementIndex, ui.reviewId) { mutableStateOf(!ui.placement && ui.reviewId == null && progress.step in listOf(1, 2)) }
    var typing by rememberSaveable { mutableStateOf(false) }
    var answer by remember(unit.id, progress.step, ui.reviewId, state.placementIndex) { mutableStateOf("") }
    val canControl = !ui.busy && !live.connected && !ui.recording
    val helpAllowed = !ui.placement && (card != null || progress.step <= CoachStep.ROLE_SWAP.ordinal)
    BackHandler { viewModel.closeLesson() }
    LifecycleResumeEffect(Unit) {
        val start = android.os.SystemClock.elapsedRealtime()
        onPauseOrDispose { viewModel.stopAll(); viewModel.study((android.os.SystemClock.elapsedRealtime() - start) / 1000) }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("coach-lesson"), contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(viewModel::closeLesson) { Icon(Icons.Default.ArrowBack, "Back") }
                Text(if (ui.placement) "အဆင့်စစ်ဆေးမှု" else if (ui.reviewId != null) "ပြန်လေ့ကျင့်မယ်" else unit.title,
                    fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(if (ui.placement) CoachEngine.levels[state.placementLevel] else task.canDo,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ui.placement && card == null) item {
            LinearProgressIndicator(progress = { if (completed) 1f else progress.step.toFloat() / CoachStep.entries.size }, modifier = Modifier.fillMaxWidth())
            Text(CoachStep.entries[progress.step].label + " · " + (progress.step + 1) + " / " + CoachStep.entries.size,
                color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        }
        if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (completed) {
            item {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (ui.placement) "စတင်သင့်တဲ့အဆင့်: " + CoachEngine.levels[state.level] else "ဒီလေ့ကျင့်မှု ပြီးပါပြီ။",
                    fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                if (ui.placement) Text("App အတွင်း လေ့ကျင့်မှုအတွက် ခန့်မှန်းချက်ပါ။ ကိုယ့်အဆင့်ကို ပြန်ရွေးနိုင်ပါတယ်။", fontSize = 12.sp)
                Button(viewModel::next, modifier = Modifier.fillMaxWidth()) { Text("သင်ခန်းစာ ဆက်မယ်") }
            }
        } else {
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(task.prompt, fontSize = 23.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("coach-task"))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton({ viewModel.listen(task.spoken) }, enabled = canControl) { Icon(Icons.Default.VolumeUp, "Listen to sample") }
                            Text("AI နမူနာအသံ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (helpAllowed) TextButton({ hint = !hint }) { Text(if (hint) "အကူအညီဖျောက်" else "အကူအညီ") }
                        }
                        if (hint && helpAllowed) {
                            if (task.example.isNotBlank()) Text(task.example, fontSize = 21.sp)
                            if (task.hint.isNotBlank()) Text(task.hint, lineHeight = 26.sp)
                            if (card == null) { Text(unit.pattern, color = MaterialTheme.colorScheme.primary); Text(unit.explanation, fontSize = 13.sp, lineHeight = 23.sp) }
                        }
                    }
                }
            }
            if (unit.level == 0 && hint && ui.reviewId == null && !ui.placement) item { HangulPractice(viewModel) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(ui.slow, { viewModel.slow(true) }, label = { Text("Slow") }, enabled = canControl)
                    FilterChip(!ui.slow, { viewModel.slow(false) }, label = { Text("Natural") }, enabled = canControl)
                    TextButton({ viewModel.listen(task.spoken, true) }, enabled = canControl) { Text("အခြားအသံ") }
                }
                if (live.connected) {
                    Text(when (live.phase) { LivePhase.LISTENING -> "မင်းအဖြေကို နားထောင်နေတယ်"; LivePhase.SPEAKING -> "နမူနာအသံ ပြောနေတယ်"; LivePhase.THINKING -> "အဖြေစစ်နေတယ်"; else -> "ချိတ်ဆက်နေတယ်" },
                        color = MaterialTheme.colorScheme.primary)
                    if (live.error != null) Text(live.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Button(viewModel::stopVoice, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("ရပ်မယ်") }
                } else {
                    Button({ microphone("live") }, enabled = !ui.busy && !ui.recording,
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("coach-voice"), shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text("Voice နဲ့ လေ့ကျင့်မယ်")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton({ microphone("record") }, enabled = !ui.busy) { Text(if (ui.recording) "အသံပို့မယ်" else "တစ်ကြောင်း အသံသွင်း") }
                        TextButton({ typing = !typing }, enabled = canControl) { Text("စာရိုက်ဖြေ") }
                    }
                }
                if (typing) {
                    OutlinedTextField(answer, { answer = it }, Modifier.fillMaxWidth().testTag("coach-answer"), label = { Text("ကိုယ့်အဖြေ") }, minLines = 2, enabled = canControl)
                    Button({ viewModel.send(answer) }, enabled = canControl && answer.isNotBlank()) { Text("အဖြေစစ်") }
                }
                if (live.phase == LivePhase.ERROR) Text(live.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton({ viewModel.listen(task.spoken) }, enabled = canControl) { Text("ပြန်ပြော") }
                    TextButton({ viewModel.slow(true); viewModel.listen(task.spoken) }, enabled = canControl) { Text("နှေးနှေးပြော") }
                    if (helpAllowed) TextButton({ hint = true; viewModel.explain(task) }, enabled = canControl) { Text("မြန်မာလိုရှင်း") }
                }
            }
        }
        if (ui.heard.isNotBlank() || ui.feedback.isNotBlank()) item {
            HorizontalDivider()
            if (ui.heard.isNotBlank()) { Text("မင်းပြောခဲ့တာ", fontSize = 12.sp); Text(ui.heard, fontSize = 18.sp) }
            if (ui.feedback.isNotBlank()) Text(ui.feedback, lineHeight = 28.sp)
            state.attempts.lastOrNull()?.takeIf { it.audioName.isNotBlank() }?.let { attempt ->
                TextButton({ viewModel.playRecording(attempt) }, enabled = canControl) { Text("ကိုယ့်အသံ ပြန်နားထောင်") }
            }
        }
        if (!ui.placement && ui.reviewId == null) item { TextButton(viewModel::restart, enabled = canControl) { Text("ဒီသင်ခန်းစာ ပြန်စမယ်") } }
    }
}

@Composable
private fun HangulPractice(viewModel: CoachViewModel) {
    var group by remember { mutableStateOf(false) }
    val vowels = listOf("ㅏ" to "아", "ㅑ" to "야", "ㅓ" to "어", "ㅕ" to "여", "ㅗ" to "오", "ㅛ" to "요", "ㅜ" to "우", "ㅠ" to "유",
        "ㅡ" to "으", "ㅣ" to "이", "ㅐ" to "애", "ㅒ" to "얘", "ㅔ" to "에", "ㅖ" to "예", "ㅘ" to "와", "ㅙ" to "왜", "ㅚ" to "외",
        "ㅝ" to "워", "ㅞ" to "웨", "ㅟ" to "위", "ㅢ" to "의")
    val consonants = listOf("ㄱ" to "가", "ㄴ" to "나", "ㄷ" to "다", "ㄹ" to "라", "ㅁ" to "마", "ㅂ" to "바", "ㅅ" to "사", "ㅇ" to "아",
        "ㅈ" to "자", "ㅊ" to "차", "ㅋ" to "카", "ㅌ" to "타", "ㅍ" to "파", "ㅎ" to "하", "ㄲ" to "까", "ㄸ" to "따", "ㅃ" to "빠", "ㅆ" to "싸", "ㅉ" to "짜")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("한글 · အက္ခရာနဲ့ အသံ", fontWeight = FontWeight.SemiBold)
        Row { FilterChip(!group, { group = false }, label = { Text("သရ") }); Spacer(Modifier.width(8.dp)); FilterChip(group, { group = true }, label = { Text("ဗျည်း + ㅏ") }) }
        (if (group) consonants else vowels).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (letter, sound) -> OutlinedButton({ viewModel.listen(sound) }, modifier = Modifier.weight(1f)) { Text(letter, fontSize = 20.sp) } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun LearningProfileScreen(viewModel: CoachViewModel, settings: AppSettings, onPractice: () -> Unit,
    onRead: (String, Int) -> Unit, onSettings: () -> Unit, onContext: () -> Unit,
    onHistory: () -> Unit, onTranslate: () -> Unit, onNames: () -> Unit) {
    val state by viewModel.learning.collectAsState()
    var section by rememberSaveable { mutableStateOf("main") }
    if (section != "main") BackHandler { section = "main" }
    val words = state.cards.filter { it.kind == "WORD" }
    val mistakes = state.cards.filter { it.kind == "MISTAKE" }
    LazyColumn(Modifier.fillMaxSize().testTag("learning-profile"), contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                if (section != "main") IconButton({ section = "main" }) { Icon(Icons.Default.ArrowBack, "Back") }
                Text(when (section) { "words" -> "သိမ်းထားတဲ့ဝေါဟာရ"; "mistakes" -> "ပြန်လေ့ကျင့်ရန်"; "progress" -> "တိုးတက်မှု"; else -> settings.displayName.ifBlank { "Me" } },
                    fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (section == "main") {
            item {
                Text(CoachEngine.levels[state.level], color = MaterialTheme.colorScheme.primary)
                Text("ပြီးစီးသင်ခန်းစာ " + state.units.count { it.completed } + " · သိမ်းထားသောဝေါဟာရ " + words.size,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { ProfileRow("သိမ်းထားတဲ့ဝေါဟာရ", words.size.toString()) { section = "words" } }
            item { ProfileRow("ပြန်လေ့ကျင့်ရန်", mistakes.size.toString()) { section = "mistakes" } }
            item { ProfileRow("တိုးတက်မှုနှင့် အသံမှတ်တမ်း", "") { section = "progress" } }
            item { ProfileRow("My Context", "ကိုယ်ရေးအခြေအနေ၊ မှတ်ဉာဏ်၊ Backup") { onContext() } }
            item { ProfileRow("Review & recordings", "အရင်စကားပြောမှတ်တမ်း") { onHistory() } }
            item { ProfileRow("Settings", "အသံ၊ အရောင်၊ AI ချိတ်ဆက်မှု") { onSettings() } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onTranslate) { Text("Quick Translate") }
                    TextButton(onNames) { Text("Korean name") }
                }
            }
        } else if (section in listOf("words", "mistakes")) {
            val cards = if (section == "words") words else mistakes
            if (cards.isEmpty()) item { Text("ဖတ်ရင်းသိမ်းထားတဲ့ဝေါဟာရနဲ့ လေ့ကျင့်မှုအမှားတွေ ဒီမှာပေါ်မယ်။") }
            items(cards.reversed(), key = { it.id }) { card ->
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(card.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    if (card.meaning.isNotBlank()) Text(card.meaning)
                    if (card.context.isNotBlank()) Text(card.context, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    if (card.bookTitle.isNotBlank()) Text(card.bookTitle + " · စာမျက်နှာ " + (card.page + 1), fontSize = 11.sp)
                    Text(if (card.dueAt <= System.currentTimeMillis()) "အခု ပြန်လေ့ကျင့်နိုင်ပါတယ်" else "ပြန်လေ့ကျင့်ရက်: " + java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.getDefault()).format(java.util.Date(card.dueAt)),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        TextButton({ viewModel.openReview(card.id); onPractice() }) { Text("အသုံးချပြောမယ်") }
                        if (state.books.any { it.id == card.bookId }) TextButton({ onRead(card.bookId, card.page) }) { Text("မူရင်းစာမျက်နှာ") }
                        IconButton({ viewModel.deleteCard(card.id) }) { Icon(Icons.Default.DeleteOutline, "Delete saved item") }
                    }
                    HorizontalDivider()
                }
            }
        } else {
            item {
                Text("အကူအညီမပါဘဲ အသုံးချစစ်ဆေးမှုအောင်တဲ့ သင်ခန်းစာတွေကို အောက်မှာပြထားတယ်။", fontSize = 13.sp)
                CoachEngine.levels.forEachIndexed { level, label ->
                    val units = viewModel.curriculum.filter { it.level == level }
                    val completed = units.count { CoachEngine.progress(state, it.id).completed }
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) { Text(label, Modifier.weight(1f)); Text(completed.toString() + " / " + units.size) }
                    LinearProgressIndicator(progress = { completed.toFloat() / units.size }, modifier = Modifier.fillMaxWidth())
                }
                Text("App အတွင်း လေ့ကျင့်မှုမှတ်တမ်းပါ။", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.units.filter { it.completed }, key = { it.id }) { progress ->
                viewModel.curriculum.firstOrNull { it.id == progress.id }?.let { unit -> Text("✓ " + unit.goal, fontSize = 14.sp) }
            }
            item { Text("မကြာသေးမီက လေ့ကျင့်မှု", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
            items(state.attempts.takeLast(30).reversed(), key = { it.at.toString() + it.unitId + it.step }) { attempt ->
                Column {
                    Text(attempt.heard.ifBlank { "သေချာမကြားရသော အသံ" }, fontSize = 17.sp)
                    Text(attempt.feedback, fontSize = 13.sp)
                    if (attempt.audioName.isNotBlank()) Row {
                        TextButton({ viewModel.playRecording(attempt) }) { Text("ကိုယ့်အသံ") }
                        TextButton({ viewModel.deleteRecording(attempt) }) { Text("အသံဖျက်") }
                    }
                    HorizontalDivider(Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Default.ChevronRight, null)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
}
