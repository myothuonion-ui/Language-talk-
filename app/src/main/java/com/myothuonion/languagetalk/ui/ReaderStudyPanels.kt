package com.myothuonion.languagetalk.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myothuonion.languagetalk.model.*

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun ReaderSelectionPanel(viewModel: ReaderViewModel, onPractice: (String) -> Unit) {
    val ui by viewModel.ui.collectAsState()
    var editing by remember(ui.selection) { mutableStateOf(false) }
    var corrected by remember(ui.selection) { mutableStateOf(ui.selection) }
    var noteOpen by remember(ui.selection) { mutableStateOf(false) }
    var note by remember(ui.selection) { mutableStateOf("") }
    var color by remember { mutableLongStateOf(0xFFFFD666) }
    var questionOpen by remember(ui.selection, ui.page) { mutableStateOf(false) }
    var question by remember(ui.selection, ui.page) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).background(MaterialTheme.colorScheme.surface)
        .verticalScroll(rememberScrollState()).padding(14.dp).testTag("reader-selection"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ui.selection, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 3)
            IconButton({ editing = !editing }) { Icon(Icons.Default.Edit, "Correct selected text") }
            IconButton(viewModel::dismissLookup) { Icon(Icons.Default.Close, "Close selection") }
        }
        if (ui.translating) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.translation?.let { value ->
            Text(value.meaning, fontSize = 17.sp, lineHeight = 28.sp)
            if (value.grammar.isNotBlank()) Text(value.grammar, lineHeight = 25.sp)
            TextButton(viewModel::saveWord, enabled = !ui.saved) { Text(if (ui.saved) "ဝေါဟာရသိမ်းပြီး" else "ဝေါဟာရအဖြစ်သိမ်း") }
        }
        if (editing) {
            OutlinedTextField(corrected, { corrected = it.take(1200) }, Modifier.fillMaxWidth(), label = { Text("OCR စာသားပြင်") })
            TextButton({ viewModel.lookup(corrected, ui.sentence, ui.regions, translate = true) }, enabled = corrected.isNotBlank()) { Text("ပြင်ထားတဲ့စာသားကို ရှင်းပြ") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton({ viewModel.annotate("HIGHLIGHT", color = color) }, Modifier.testTag("add-highlight")) { Text("Highlight") }
            OutlinedButton({ viewModel.annotate("UNDERLINE", color = color) }) { Text("မျဉ်းသား") }
            OutlinedButton({ viewModel.annotate("BOLD") }) { Text("Bold · Aa") }
            OutlinedButton({ noteOpen = !noteOpen }) { Text("မှတ်စု") }
            OutlinedButton({ viewModel.annotate("SAVE") }) { Text("စာသားသိမ်း") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0xFFFFD666L, 0xFF7BDDB1L, 0xFF91C9FFL, 0xFFFFAFCFL).forEach { value ->
                FilterChip(color == value, { color = value }, label = { Box(Modifier.size(18.dp).background(androidx.compose.ui.graphics.Color(value))) })
            }
        }
        if (noteOpen) {
            OutlinedTextField(note, { note = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text("ကိုယ့်မှတ်စု") })
            Button({ viewModel.annotate("NOTE", note, color) }, enabled = note.isNotBlank()) { Text("မှတ်စုသိမ်း") }
        }
        Text("Bold ကို Text (Aa) ပုံစံမှာ ဖတ်နိုင်မယ်။", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TextButton(viewModel::translateSelection, enabled = !ui.translating) { Icon(Icons.Default.Translate, null); Spacer(Modifier.width(5.dp)); Text("မြန်မာလိုရှင်းပြ") }
            TextButton({ viewModel.listen() }) { Icon(Icons.Default.VolumeUp, null); Spacer(Modifier.width(5.dp)); Text("AI အသံ") }
            TextButton({ viewModel.practiceCard(onSaved = onPractice) }) { Text("ဒီစာသားကို လေ့ကျင့်") }
            TextButton({ questionOpen = true }) { Text("ဒီစာသားကို မေးမယ်") }
            if (VoiceText.korean(ui.selection).isNotBlank()) TextButton({ viewModel.startShadow(ui.selection) }) { Text("နားထောင်ပြီး လိုက်ပြော") }
        }

    }
    if (questionOpen) AlertDialog(onDismissRequest = { questionOpen = false }, title = { Text("ဒီစာသားအကြောင်း မေးမယ်") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(ui.selection, fontSize = 17.sp)
            OutlinedTextField(question, { question = it.take(1200) }, label = { Text("အဓိပ္ပာယ် / grammar / သုံးပုံ မေးပါ") }, modifier = Modifier.fillMaxWidth())
            if (ui.asking) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (ui.questionAnswer.isNotBlank()) Text(ui.questionAnswer, lineHeight = 28.sp)
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        } }, confirmButton = { TextButton({ viewModel.ask(question) }, enabled = question.isNotBlank() && !ui.asking) { Text("မေးမယ်") } },
        dismissButton = { TextButton({ questionOpen = false }) { Text("ပိတ်") } })
}

@Composable
internal fun ReaderShadowPanel(viewModel: ReaderViewModel) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.recordShadow() else viewModel.shadowMicrophoneDenied()
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 550.dp).verticalScroll(rememberScrollState()).padding(22.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("နားထောင် → လိုက်ပြော → ယှဉ်နားထောင်", Modifier.weight(1f), fontWeight = FontWeight.Medium)
            IconButton(viewModel::stopShadow) { Icon(Icons.Default.Close, "Close shadowing") }
        }
        Text(ui.shadowText, fontSize = 24.sp, lineHeight = 34.sp)
        if (ui.shadowBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton({ viewModel.playShadow("AI") }, enabled = ui.shadowSession.isNotBlank() && !ui.shadowRecording) {
            Icon(Icons.Default.VolumeUp, null); Spacer(Modifier.width(7.dp)); Text("AI နမူနာအသံ နားထောင်")
        }
        Button({
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viewModel.recordShadow()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }, enabled = !ui.shadowBusy, modifier = Modifier.fillMaxWidth().testTag("shadow-record")) {
            Icon(if (ui.shadowRecording) Icons.Default.Stop else Icons.Default.Mic, null)
            Spacer(Modifier.width(8.dp)); Text(if (ui.shadowRecording) "${ui.shadowSeconds}s · ရပ်ပြီး စစ်မယ်" else "ကိုယ့်အသံနဲ့ လိုက်ပြောမယ်")
        }
        if (ui.shadowHeard.isNotBlank()) { Text("ကြားထားတဲ့စာ", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary); Text(ui.shadowHeard, fontSize = 19.sp) }
        if (ui.shadowFeedback.isNotBlank()) Text(ui.shadowFeedback, lineHeight = 28.sp)
        OutlinedButton({ viewModel.playShadow("USER") }, enabled = !ui.shadowRecording) { Icon(Icons.Default.Headphones, null); Spacer(Modifier.width(7.dp)); Text("ငါ့အသံ ပြန်နားထောင်") }
        Text("အသံမှတ်တမ်းဖွင့်ထားရင် Replays ထဲမှာ နှစ်ယောက်စလုံးရဲ့ အသံကို နောက်မှလည်း နားထောင်နိုင်မယ်။", fontSize = 12.sp, lineHeight = 22.sp)
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    }
}

@Composable
internal fun ReaderTeachingPanel(viewModel: ReaderViewModel, onPractice: (String) -> Unit) {
    val ui by viewModel.ui.collectAsState()
    val value = ui.explanation?.value()
    var tab by remember(ui.page, ui.bookId) { mutableStateOf("MEANING") }
    Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).background(MaterialTheme.colorScheme.surfaceContainerLow)
        .verticalScroll(rememberScrollState()).padding(16.dp).testTag("page-teaching"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("စာမျက်နှာ ${ui.page + 1} · ရှင်းပြချက်", fontWeight = FontWeight.Bold)
                Text(if (value != null) "သိမ်းထားပြီး · offline ပြန်ဖတ်နိုင်" else "မူရင်းစာမျက်နှာကို အခြေခံမယ်", fontSize = 11.sp)
            }
            IconButton({ viewModel.showExplanation(false) }) { Icon(Icons.Default.Close, "Close explanation") }
        }
        if (ui.explaining) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(viewModel::cancelExplanation) { Text("ရပ်မယ်") }
        }
        if (value == null && !ui.explaining) Button({ viewModel.explain() }) { Text("ဒီစာမျက်နှာကို ရှင်းပြမယ်") }
        value?.let { teaching ->
            val missing = ui.explanation?.blocks()?.let { PageTeachingCodec.missing(teaching, it) }.orEmpty()
            if (missing.isNotEmpty()) {
                Text("ရှင်းပြရန် စာပိုင်း ${missing.size} ခု ကျန်တယ် · ရထားတဲ့အပိုင်းတွေ သိမ်းထားပြီးပြီ", fontSize = 12.sp)
                TextButton({ viewModel.explain() }, enabled = !ui.explaining) { Text("ကျန်တာ ဆက်ရှင်းပြမယ်") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("MEANING" to "အဓိပ္ပာယ်", "WORDS" to "ဝေါဟာရ", "GRAMMAR" to "Grammar", "EXERCISES" to "လေ့ကျင့်ခန်း").forEach { (id, title) ->
                    FilterChip(tab == id, { tab = id }, label = { Text(title) })
                }
            }
            Text(teaching.overview, fontSize = 17.sp, lineHeight = 29.sp)
            teaching.sentences.filter { tab == "MEANING" }.forEach { sentence ->
                TeachingCard("ဝါကျ · မူရင်းစာ", sentence.sourceId, viewModel) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sentence.korean, Modifier.weight(1f), fontSize = 21.sp, lineHeight = 29.sp)
                        if (VoiceText.korean(sentence.korean).isNotBlank()) IconButton({ viewModel.listen(sentence.korean) }) { Icon(Icons.Default.VolumeUp, "Listen to Korean sentence") }
                    }
                    Text(sentence.meaning, fontSize = 17.sp, lineHeight = 28.sp)
                    Row { TextButton({ viewModel.practiceCard(sentence.korean, onPractice) }) { Text("ဒီဝါကျကို လေ့ကျင့်") }
                        if (VoiceText.korean(sentence.korean).isNotBlank()) TextButton({ viewModel.startShadow(sentence.korean) }) { Text("လိုက်ပြော") } }
                }
            }
            if (tab == "WORDS" && teaching.vocabulary.isNotEmpty()) Text("ဝေါဟာရ", fontWeight = FontWeight.Bold)
            teaching.vocabulary.filter { tab == "WORDS" }.forEach { word ->
                TeachingCard(word.lemma, word.sourceId, viewModel) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(word.word + " · " + word.meaning, Modifier.weight(1f), fontSize = 17.sp, lineHeight = 28.sp)
                        IconButton({ viewModel.listen(word.word) }) { Icon(Icons.Default.VolumeUp, "Listen to Korean word") }
                    }
                    if (word.usage.isNotBlank()) Text(word.usage, lineHeight = 26.sp)
                    TextButton({ viewModel.saveTeachingWord(word) }) { Text("အဓိပ္ပာယ်နဲ့ သိမ်းမယ်") }
                }
            }
            teaching.grammar.filter { tab == "GRAMMAR" }.forEach { grammar ->
                TeachingCard(grammar.pattern, grammar.sourceId, viewModel) {
                    Text("အဓိပ္ပာယ် · " + grammar.meaning, lineHeight = 27.sp)
                    Text("ပေါင်းစပ်ပုံ · " + grammar.construction, lineHeight = 27.sp)
                    if (grammar.originalExample.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(grammar.originalExample, Modifier.weight(1f), fontSize = 21.sp)
                        IconButton({ viewModel.listen(grammar.originalExample) }) { Icon(Icons.Default.VolumeUp, "Listen to grammar example") }
                    }
                    if (grammar.explanation.isNotBlank()) Text(grammar.explanation, lineHeight = 27.sp)
                    if (grammar.commonError.isNotBlank()) Text("မှားလွယ်တဲ့အချက် · " + grammar.commonError, lineHeight = 27.sp)
                    if (grammar.practice.isNotBlank()) Text("AI ထပ်လေ့ကျင့်ရန် · " + grammar.practice, lineHeight = 27.sp)
                    TextButton({ viewModel.saveGrammar(grammar) }) { Text("Grammar မှတ်စုသိမ်းမယ်") }
                }
            }
            teaching.notes.forEach { Text(it, lineHeight = 27.sp) }
            teaching.unreadable.forEach { Text("ဖတ်မရ/မစစ်ရသေး · $it", color = MaterialTheme.colorScheme.error, lineHeight = 27.sp) }
            teaching.exercises.filter { tab == "EXERCISES" }.forEach { exercise ->
                TeachingCard("စာအုပ်လေ့ကျင့်ခန်း", exercise.sourceId, viewModel) {
                    HiddenAnswerExercise(exercise.question, exercise.hint, exercise.answer)
                }
            }
            teaching.addedPractice.filter { tab == "EXERCISES" }.forEach { exercise ->
                OutlinedCard { Column(Modifier.padding(12.dp)) {
                    Text("AI ထပ်ဖန်တီးထားတဲ့ လေ့ကျင့်ခန်း", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    HiddenAnswerExercise(exercise.question, exercise.hint, exercise.answer)
                } }
            }
            Text(ui.explanation?.review.orEmpty(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton({ viewModel.explain(regenerate = true) }, enabled = !ui.explaining) { Text("အသစ်ပြန်ရှင်းပြ") }
                TextButton(viewModel::clearFocus) { Text("မူရင်းနေရာ အမှတ်ဖျောက်") }
            }
        }
    }
}

@Composable
private fun TeachingCard(title: String, source: String, viewModel: ReaderViewModel, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { viewModel.focusSource(source) })
            content()
            TextButton({ viewModel.focusSource(source) }) { Text("မူရင်းနေရာကြည့်", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun HiddenAnswerExercise(question: String, hint: String, answer: String) {
    var hintOpen by remember(question) { mutableStateOf(false) }
    var answerOpen by remember(question) { mutableStateOf(false) }
    var attempt by remember(question) { mutableStateOf("") }
    Text(question, fontSize = 19.sp, lineHeight = 29.sp)
    TextButton({ hintOpen = !hintOpen }) { Text("Hint") }
    if (hintOpen) Text(hint, lineHeight = 27.sp)
    OutlinedTextField(attempt, { attempt = it.take(1200) }, Modifier.fillMaxWidth(), label = { Text("ကိုယ်တိုင်အရင်ဖြေ") })
    TextButton({ answerOpen = !answerOpen }) { Text(if (answerOpen) "အဖြေဖျောက်" else "အဖြေကြည့်") }
    if (answerOpen) Text(answer, lineHeight = 28.sp)
}
