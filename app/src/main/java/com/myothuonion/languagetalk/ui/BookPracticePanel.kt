@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myothuonion.languagetalk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myothuonion.languagetalk.data.AppSettings
import com.myothuonion.languagetalk.model.*
import kotlinx.serialization.decodeFromString
import com.myothuonion.languagetalk.network.LiveState

@Composable
internal fun BookPracticePanel(viewModel: BookViewModel,ui: BookUiState,state: BookProgress,
    book: BookCourse,section: BookSection,live: LiveState,settings: AppSettings,
    microphone: (String) -> Unit,onRead: (Int) -> Unit,onApply: (TutorConfig) -> Unit) {
    val chapter = BookEngine.chapter(book,state)
    val target = BookEngine.target(book,state)
    val finished = BookEngine.finished(book,state)
    val count = if (section.turns.isNotEmpty()) section.turns.size else section.exercises.size
    val control = !ui.busy && !live.connected && !ui.recording
    var mode by rememberSaveable(book.id,state.chapter,state.section) { mutableStateOf(if (section.turns.isNotEmpty()) "REPEAT" else "EXERCISE") }
    var hint by rememberSaveable(book.id,state.chapter,state.section,state.activity) { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    var answer by remember(book.id,state.chapter,state.section,state.activity) { mutableStateOf("") }
    var help by remember { mutableStateOf(false) }
    var voiceSettings by remember { mutableStateOf(false) }
    var chooseSection by remember { mutableStateOf(false) }
    var script by remember { mutableStateOf(false) }
    var english by rememberSaveable(book.id,state.chapter,state.section) { mutableStateOf(false) }
    var answerKey by rememberSaveable(book.id,state.chapter,state.section) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(),verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(chapter.title,style = MaterialTheme.typography.headlineSmall,fontWeight = FontWeight.SemiBold)
                Text(section.label + " · " + (state.section+1) + "/" + chapter.sections.size,
                    color = MaterialTheme.colorScheme.primary,style = MaterialTheme.typography.labelLarge)
            }
            IconButton({ voiceSettings = true }) { GlassIconTile(Icons.Default.Tune,label = "Practice settings") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ onRead(section.pages.first()) }) { Icon(Icons.Default.MenuBook,null); Text("မူရင်း " + section.pages.joinToString()) }
            Spacer(Modifier.weight(1f))
            TextButton({ chooseSection = true },enabled = control) { Text("အပိုင်းရွေး"); Icon(Icons.Default.ExpandMore,null) }
        }
        if (count > 0) {
            LinearProgressIndicator(progress = { ((state.activity + if (state.readyNext) 1 else 0).toFloat()/count).coerceIn(0f,1f) },modifier = Modifier.fillMaxWidth())
            Text("လက်ရှိအပိုင်းကို လေ့ကျင့်ပြီး ကိုယ်တိုင် ဆက်မယ်",style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (section.turns.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("LISTEN" to "နားထောင်","REPEAT" to "လိုက်ပြော","ROLEPLAY" to "Roleplay").forEach { (id,label) ->
                    FilterChip(mode == id,{ mode = id },label = { Text(label) },enabled = control)
                }
            }
            if (mode == "ROLEPLAY") Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                section.roles.forEach { role -> FilterChip(BookEngine.role(section,state) == role,
                    { if (role != BookEngine.role(section,state)) viewModel.restart(role) },enabled = control,label = { Text(role) }) }
            }
        }
        if (mode == "LISTEN" && section.turns.isNotEmpty()) {
            section.turns.forEach { line ->
                OutlinedCard {
                    Row(Modifier.padding(14.dp),verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.speaker,color = MaterialTheme.colorScheme.primary,style = MaterialTheme.typography.labelLarge)
                            Text(line.text,fontSize = 21.sp)
                        }
                        IconButton({ viewModel.speak(line.text) },enabled = control) { Icon(Icons.Default.VolumeUp,"Listen to " + line.speaker) }
                    }
                }
            }
        } else {
            if (state.lastAssistantCaption.isNotBlank() && section.turns.isNotEmpty()) Card {
                Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI အလှည့်",Modifier.weight(1f),color = MaterialTheme.colorScheme.primary,fontWeight = FontWeight.SemiBold)
                        IconButton({ viewModel.speak(state.lastAssistantText) },enabled = control) { Icon(Icons.Default.VolumeUp,"Replay AI line") }
                    }
                    Text(state.lastAssistantCaption,fontSize = 21.sp,lineHeight = 31.sp)
                }
            }
            target?.let { task ->
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (section.exercises.isNotEmpty()) "လေ့ကျင့်ခန်း " + (state.activity+1) else "မင်းအလှည့် · " + BookEngine.role(section,state),
                                Modifier.weight(1f),fontWeight = FontWeight.SemiBold,color = MaterialTheme.colorScheme.primary)
                            IconButton({ viewModel.speak(task.text) },enabled = control) { Icon(Icons.Default.VolumeUp,"Listen to model answer") }
                        }
                        if (section.exercises.isNotEmpty()) Text(task.prompt,fontSize = 19.sp)
                        if (mode == "REPEAT" || hint) Text(task.text,fontSize = 23.sp,lineHeight = 34.sp)
                        else Text("ကိုရီးယားလို ပြန်ပြောကြည့်ပါ။")
                        if (english && section.turns.isNotEmpty()) Text(section.turns.getOrNull(state.activity)?.english.orEmpty())
                        Row {
                            TextButton({ hint = !hint }) { Text(if (hint) "အကူအညီဖျောက်" else "အဖြေပုံစံ") }
                            TextButton({ help = true; viewModel.explain(true) },enabled = control) { Text("မြန်မာလိုရှင်းပြ") }
                        }
                    }
                }
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp),horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        FilledIconButton({
                            if (live.connected) viewModel.toggleMic() else microphone("record")
                        },enabled = !ui.busy && !state.readyNext,modifier = Modifier.size(64.dp).testTag("book-microphone")) {
                            Icon(if (ui.recording) Icons.Default.Stop else Icons.Default.Mic,"Record or submit answer",Modifier.size(30.dp))
                        }
                        MicrophoneStatus(ui.recording,ui.inputLevel,ui.recordingSeconds.toLong(),ui.busy,
                            live.takeIf { it.connected || it.error != null },state.heard,Modifier.fillMaxWidth(),playback = (ui.speechPhase == "SPEAKING"))
                        if (state.feedback.isNotBlank()) Text(state.feedback,color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton({ typing = !typing }) { Icon(Icons.Default.Keyboard,null); Text("ရိုက်ဖြေ") }
                            if (live.connected) TextButton({ viewModel.stopVoice() }) { Text("အသံရပ်မယ်") }
                        }
                        if (typing) {
                            OutlinedTextField(answer,{ answer = it },Modifier.fillMaxWidth().testTag("book-answer"),label = { Text("ကိုရီးယားလို ဖြေပါ") })
                            Button({ viewModel.send(answer) },enabled = control && answer.isNotBlank() && !state.readyNext) { Text("အဖြေစစ်") }
                        }
                    }
                }
            }
        }
        if (section.turns.isEmpty() && section.exercises.isEmpty()) {
            Text(section.sourceText,fontSize = 18.sp,lineHeight = 30.sp)
            OutlinedButton({ viewModel.reviewed() },enabled = control && !state.reviewed) {
                Text(if (state.reviewed) "ဖတ်လေ့လာပြီးပြီ" else "ဒီအပိုင်း ဖတ်လေ့လာပြီးပြီ")
            }
        }
        if ((ui.speechPhase == "SPEAKING")) TextButton({ viewModel.stopPlayback() }) { Icon(Icons.Default.Stop,null); Text("အသံရပ်မယ်") }
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ viewModel.retryItem() },Modifier.weight(1f),enabled = control) { Text("ထပ်လေ့ကျင့်") }
            Button({ viewModel.next() },Modifier.weight(1f).testTag(if (state.readyNext) "book-continue-turn" else "book-next"),
                enabled = control && (state.readyNext || finished)) { Text(if (state.readyNext) "ဆက်မယ်" else "နောက်အပိုင်း") }
        }
        Row {
            TextButton({ help = true; viewModel.explain() },enabled = control) { Icon(Icons.Default.AutoAwesome,null); Text("ဒီအပိုင်း ရှင်းပြ") }
            TextButton({ script = !script }) { Text("မူရင်းစာသား") }
        }
        if (script) Text(section.sourceText,fontSize = 17.sp,lineHeight = 29.sp)
        if (section.answerText.isNotBlank()) {
            TextButton({ answerKey = !answerKey }) { Text(if (answerKey) "စာအုပ်အဖြေဖျောက်" else "စာအုပ်အဖြေကြည့်") }
            if (answerKey) Text(section.answerText, lineHeight = 28.sp)
        }
        if (finished) Text("ဒီအပိုင်းကို ပြီးမြောက်ပြီ။ အပိုင်းသစ်ကို ကိုယ်တိုင်ရွေးနိုင်ပါတယ်။",color = MaterialTheme.colorScheme.primary)
        if (state.section == chapter.sections.lastIndex && finished) {
            OutlinedButton({ viewModel.close(); viewModel.applicationPractice()?.let(onApply) },Modifier.fillMaxWidth(),enabled = control) {
                Text("နေ့စဉ်ဘဝမှာ အသုံးချပြောမယ်")
            }
        }
    }
    if (chooseSection) AlertDialog(onDismissRequest = { chooseSection = false },title = { Text("လေ့ကျင့်ချင်တဲ့အပိုင်း ရွေးပါ") },
        text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            chapter.sections.forEachIndexed { index,item ->
                TextButton({ viewModel.selectSection(index); chooseSection = false },Modifier.fillMaxWidth()) {
                    Text((index+1).toString() + ". " + item.label,Modifier.weight(1f))
                    if (index == state.section) Icon(Icons.Default.Check,null)
                }
            }
        } },confirmButton = { TextButton({ chooseSection = false }) { Text("ပိတ်") } })
    if (voiceSettings) ModalBottomSheet(onDismissRequest = { voiceSettings = false }) {
        Column(Modifier.padding(22.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("လေ့ကျင့်အသံ",style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.slow,{ viewModel.pace(true) },label = { Text("Slow") })
                FilterChip(!state.slow,{ viewModel.pace(false) },label = { Text("Natural") })
            }
            Text("ကိုရီးယားအသံ · မြန်မာစာရှင်းပြချက် · AI အသံ",style = MaterialTheme.typography.bodySmall)
            if (section.turns.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("စာအုပ် English အဓိပ္ပာယ်", Modifier.weight(1f)); Switch(english,{ english = it })
            }
            OutlinedButton({ voiceSettings = false; microphone("live") },Modifier.fillMaxWidth(),
                enabled = control && target != null && !state.readyNext && !finished) { Text("Hands-free လေ့ကျင့်မယ်") }
            Text("အဖြေတစ်ခု လေ့ကျင့်ပြီးရင် ရပ်စောင့်မယ်။ ဆက်မယ်နှိပ်မှ နောက်အလှည့်သွားမယ်။")
            Spacer(Modifier.navigationBarsPadding())
        }
    }
    if (help) ModalBottomSheet(onDismissRequest = { help = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("မြန်မာရှင်းပြချက်",style = MaterialTheme.typography.titleLarge)
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (ui.explanation.isNotBlank()) {
                val teaching = remember(ui.explanation) { runCatching { PageTeachingCodec.json.decodeFromString<PageTeaching>(ui.explanation) }.getOrNull() }
                if (teaching != null) BookTeachingCards(teaching,onListen = { viewModel.speak(it) })
                else Text(ui.explanation)
            }
            ui.error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
