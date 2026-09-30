package com.myothuonion.languagetalk.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myothuonion.languagetalk.data.*
import com.myothuonion.languagetalk.model.*

@Composable
internal fun PracticeChoices(selected: PracticeMode, onSelect: (PracticeMode) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        PracticeMode.entries.forEach { mode -> FilterChip(selected == mode, { onSelect(mode) }, label = { Text(mode.label) }) }
    }
}

@Composable
internal fun VoiceEditor(voice: String, style: String, pace: String, silence: Int, corrections: Boolean,
    onVoice: (String) -> Unit, onStyle: (String) -> Unit, onPace: (String) -> Unit,
    onSilence: (Int) -> Unit, onCorrections: (Boolean) -> Unit, onPreview: (() -> Unit)? = null) {
    Text("Voice & teaching style", fontWeight = FontWeight.SemiBold)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DefaultVoicePresets.forEach { preset -> FilterChip(voice == preset.voice && style == preset.style,
            { onVoice(preset.voice); onStyle(preset.style) }, label = { Text(preset.name) }) }
    }
    OutlinedTextField(voice, onVoice, label = { Text("Gemini voice name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(style, onStyle, label = { Text("အသံနဲ့ သင်ပေးပုံ") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(pace == "SLOW", { onPace("SLOW") }, label = { Text("Slow") })
        FilterChip(pace == "NATURAL", { onPace("NATURAL") }, label = { Text("Natural") })
    }
    Text("မင်းအဖြေပြီးတာ စောင့်ချိန်: ${"%.1f".format(silence / 1000f)} s", fontSize = 13.sp)
    Slider(silence.toFloat(), { onSilence(it.toInt()) }, valueRange = 800f..4000f, steps = 15)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("အမှားပြင်ချက်ကို အသံနဲ့ပြော", modifier = Modifier.weight(1f))
        Switch(corrections, onCorrections)
    }
    onPreview?.let { OutlinedButton(it) { Text("Preview this voice") } }
}

@Composable
internal fun LearningHomeScreen(settings: AppSettings, chats: List<ChatEntity>, progress: List<LearningProgressEntity>,
    reviews: List<ReviewItemEntity>, onContinue: (Long) -> Unit, onNew: () -> Unit, onQuick: (TutorConfig) -> Unit,
    onReview: () -> Unit, onTranslate: () -> Unit, onNames: () -> Unit) {
    val latest = progress.firstOrNull { item -> chats.any { it.id == item.chatId } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Text("안녕하세요, ${settings.displayName}", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("ဒီနေ့ နားလည်ပြီး ကိုယ်တိုင်ပြောနိုင်ဖို့", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Your 30-minute practice", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Useful phrases: ${latest?.completed ?: 0}/5 · Review: ${reviews.count { it.dueAt <= System.currentTimeMillis() }}")
                    Text(latest?.goal ?: "စက်ရုံနဲ့ နေ့စဉ်ဘဝအတွက် အသုံးဝင်တဲ့စကား ၅ ကြောင်း")
                    latest?.takeIf { it.targetSentence.isNotBlank() }?.let { Text(it.targetSentence, color = Mint, fontSize = 19.sp) }
                    Button({ if (latest != null) onContinue(latest.chatId) else onNew() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (latest != null) "Continue my lesson" else "Start Guided Practice")
                    }
                    OutlinedButton(onReview, modifier = Modifier.fillMaxWidth()) { Text("Review & my recordings") }
                }
            }
        }
        item { Text("Choose your practice", fontWeight = FontWeight.Bold, fontSize = 19.sp) }
        item { Button(onNew, modifier = Modifier.fillMaxWidth()) { Text("New goal · customize voice") } }
        item { OutlinedButton({ onQuick(TutorConfig(topic = "အလုပ်ဖော်နဲ့ နုတ်ဆက်၊ အကူအညီတောင်းခြင်း", role = TutorRole.COWORKER, practiceMode = PracticeMode.ROLEPLAY)) }, modifier = Modifier.fillMaxWidth()) { Text("Workplace Roleplay") } }
        item { OutlinedButton({ onQuick(TutorConfig(topic = "ဒီနေ့ အလုပ်နဲ့ နေ့စဉ်ဘဝ", practiceMode = PracticeMode.FREE_TALK)) }, modifier = Modifier.fillMaxWidth()) { Text("Free Talk · my topic") } }
        if (chats.isNotEmpty()) {
            item { Text("Recent lessons", fontWeight = FontWeight.Bold) }
            items(chats.take(4), key = { it.id }) { chat ->
                OutlinedCard(onClick = { onContinue(chat.id) }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(chat.title, fontWeight = FontWeight.SemiBold)
                        Text("${chat.practiceMode.replace('_', ' ')} · ${chat.level}", fontSize = 12.sp)
                        if (chat.summary.isNotBlank()) Text(chat.summary.takeLast(180), fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onTranslate) { Text("Quick Translate") }
                TextButton(onNames) { Text("Korean Name") }
            }
        }
    }
}

@Composable
internal fun ReviewScreen(reviews: List<ReviewItemEntity>, recordings: List<MessageEntity>,
    onReview: (ReviewItemEntity) -> Unit, onSpeak: (ReviewItemEntity) -> Unit, onRecording: (MessageEntity) -> Unit,
    onStop: () -> Unit, onDeleteRecording: (MessageEntity) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Review & recordings", modifier = Modifier.statusBarsPadding(), fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item { Text("အရင်က ကိုယ်ပြောခဲ့တာကို ပြန်နားထောင်ပြီး အခြေအနေသစ်မှာ ပြန်သုံးမယ်") }
        if (reviews.isEmpty()) item { Text("ပထမ lesson လေ့ကျင့်ပြီးတာနဲ့ review စာကြောင်းတွေ ဒီမှာပေါ်မယ်") }
        items(reviews.take(50), key = { "review-${it.id}" }) { item ->
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(item.sentence, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    if (item.correction.isNotBlank()) Text(item.correction)
                    Text(if (item.dueAt <= System.currentTimeMillis()) "Ready to review" else "Review tomorrow", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ onSpeak(item) }) { Text("Listen") }
                        Button({ onReview(item) }) { Text("Try again") }
                    }
                }
            }
        }
        item { Text("My recordings", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        item { Text("အသံတွေကို app ထဲမှာသိမ်းထားတယ်။ My Context မှာ recording သိမ်းခြင်း ပိတ်နိုင်တယ်။", fontSize = 12.sp) }
        items(recordings, key = { "recording-${it.id}" }) { message ->
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(message.content)
                    Row {
                        TextButton({ onRecording(message) }) { Text("Play my voice") }
                        TextButton({ onDeleteRecording(message) }) { Text("Delete audio") }
                    }
                }
            }
        }
        item { TextButton(onStop) { Text("Stop playback") } }
    }
}

@Composable
internal fun ContextScreen(settings: AppSettings, memories: List<MemoryEntity>, progress: List<LearningProgressEntity>,
    sources: List<KnowledgeSourceEntity>, work: WorkState, onSaveSettings: (AppSettings) -> Unit,
    onAdd: (String, String) -> Unit, onEdit: (MemoryEntity, String, String) -> Unit,
    onToggle: (MemoryEntity) -> Unit, onDelete: (MemoryEntity) -> Unit,
    onNotes: (LearningProgressEntity, String, String) -> Unit,
    onExport: (android.net.Uri) -> Unit, onRestore: (android.net.Uri) -> Unit,
    onImportSource: (android.net.Uri) -> Unit, onToggleSource: (KnowledgeSourceEntity) -> Unit, onDeleteSource: (KnowledgeSourceEntity) -> Unit) {
    var profile by remember(settings.learnerProfile) { mutableStateOf(settings.learnerProfile) }
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf<LearningProgressEntity?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(onExport) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onRestore) }
    val source = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onImportSource) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("My Context", modifier = Modifier.statusBarsPadding(), fontSize = 27.sp, fontWeight = FontWeight.Bold) }
        item {
            OutlinedTextField(profile, { profile = it }, label = { Text("ကိုယ့်အခြေအနေနဲ့ သင်ယူမှုရည်ရွယ်ချက်") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            Button({ onSaveSettings(settings.copy(learnerProfile = profile)) }) { Text("Save profile") }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Learning notes အလိုအလျောက်သိမ်း", modifier = Modifier.weight(1f))
                Switch(settings.autoLearningMemory, { onSaveSettings(settings.copy(autoLearningMemory = it)) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ကိုယ်ပြောတဲ့အသံကိုသိမ်း", modifier = Modifier.weight(1f))
                Switch(settings.recordPractice, { onSaveSettings(settings.copy(recordPractice = it)) })
            }
            Text("Personal facts ကို ‘မှတ်ထား’ လို့ပြောမှ သိမ်းမယ်။ Roleplay အချက်တွေကို ကိုယ်ရေးအချက်အဖြစ် မသိမ်းဘူး။", fontSize = 12.sp)
        }
        item {
            Text("Backup & restore", fontWeight = FontWeight.Bold)
            Text("Chats, context, learning progress နဲ့ recordings ပါမယ်။ API keys မပါဘူး။ Restore က လက်ရှိ chats တွေနဲ့ပေါင်းထည့်မယ်။", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ export.launch("language-talk-backup.json") }) { Text("Export") }
                OutlinedButton({ restore.launch(arrayOf("application/json", "text/plain")) }) { Text("Restore") }
            }
            if (work.status != "Ready") Text(work.status, fontSize = 12.sp)
        }
        item { Text("Personal memories", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        item { OutlinedButton({ adding = true }) { Text("Add memory") } }
        items(memories, key = { "context-${it.id}" }) { memory ->
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(memory.title, fontWeight = FontWeight.Bold)
                    Text(if (memory.scopeChatId == null) "Global memory" else "Chat ${memory.scopeChatId} only", fontSize = 11.sp)
                    Text(memory.content)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({ editing = memory }) { Text("Edit") }
                        TextButton({ onDelete(memory) }) { Text("Delete") }
                        Switch(memory.enabled, { onToggle(memory) })
                    }
                }
            }
        }
        item { Text("Learning notes", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        items(progress, key = { "progress-${it.chatId}" }) { item ->
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(item.goal, fontWeight = FontWeight.Bold)
                    Text("${item.stage} · ${item.completed}/5 phrases")
                    Text(item.summary.ifBlank { "Lesson state saved; no notes yet" })
                    if (item.lastCorrection.isNotBlank()) Text(item.lastCorrection)
                    TextButton({ notes = item }) { Text("Edit / clear notes") }
                }
            }
        }
        item { Text("Photos & documents", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        item { OutlinedButton({ source.launch(arrayOf("image/*", "application/pdf", "text/plain")) }) { Text("Import context") } }
        items(sources, key = { "source-${it.id}" }) { item ->
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(item.name, fontWeight = FontWeight.Bold)
                    Text(item.summary)
                    Row {
                        Switch(item.enabled, { onToggleSource(item) })
                        TextButton({ onDeleteSource(item) }) { Text("Delete") }
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        val item = editing
        MemoryEditDialog(item?.title.orEmpty(), item?.content.orEmpty(), { adding = false; editing = null }) { title, content ->
            if (item == null) onAdd(title, content) else onEdit(item, title, content)
            adding = false; editing = null
        }
    }
    notes?.let { item ->
        MemoryEditDialog(item.summary, item.lastCorrection, { notes = null }, allowEmpty = true, titleLabel = "Learning notes", contentLabel = "Last correction") { summary, correction ->
            onNotes(item, summary, correction); notes = null
        }
    }
}

@Composable
internal fun MemoryEditDialog(initialTitle: String, initialContent: String, onDismiss: () -> Unit,
    allowEmpty: Boolean = false, titleLabel: String = "Title", contentLabel: String = "Memory", onSave: (String, String) -> Unit) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var content by remember(initialContent) { mutableStateOf(initialContent) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit context") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(titleLabel) })
            OutlinedTextField(content, { content = it }, label = { Text(contentLabel) }, minLines = 3)
        }
    }, confirmButton = { TextButton({ onSave(title, content) }, enabled = allowEmpty || (title.isNotBlank() && content.isNotBlank())) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
