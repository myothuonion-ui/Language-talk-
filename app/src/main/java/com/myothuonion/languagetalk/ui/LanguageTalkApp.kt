@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myothuonion.languagetalk.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.myothuonion.languagetalk.data.AppSettings
import com.myothuonion.languagetalk.data.ChatEntity
import com.myothuonion.languagetalk.data.KnowledgeSourceEntity
import com.myothuonion.languagetalk.data.MemoryEntity
import com.myothuonion.languagetalk.data.MessageEntity
import com.myothuonion.languagetalk.model.AppLanguage
import com.myothuonion.languagetalk.model.BrainMode
import com.myothuonion.languagetalk.model.CorrectionMode
import com.myothuonion.languagetalk.model.DefaultVoicePresets
import com.myothuonion.languagetalk.model.TutorConfig
import com.myothuonion.languagetalk.model.TutorRole
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Screen { HOME, NEW_CHAT, HISTORY, MEMORY, SETTINGS, CHAT }

@Composable
fun LanguageTalkApp(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    LanguageTalkTheme(settings.darkTheme) {
        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
        val chats by viewModel.chats.collectAsState()
        val currentId by viewModel.currentChatId.collectAsState()
        val messages by viewModel.messages.collectAsState()
        val work by viewModel.work.collectAsState()
        val memories by viewModel.memories.collectAsState()
        val sources by viewModel.sources.collectAsState()
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(work.error) {
            work.error?.let {
                snackbar.showSnackbar(it)
                viewModel.clearError()
            }
        }

        val openChat: (Long) -> Unit = {
            viewModel.openChat(it)
            screen = Screen.CHAT
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (screen in listOf(Screen.HOME, Screen.HISTORY, Screen.MEMORY, Screen.SETTINGS)) {
                    AppBottomBar(screen) { screen = it }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        chats = chats,
                        work = work,
                        onNewChat = { screen = Screen.NEW_CHAT },
                        onQuickStart = { config ->
                            viewModel.createChat(config) { screen = Screen.CHAT }
                        },
                        onOpenChat = openChat,
                        onSettings = { screen = Screen.SETTINGS }
                    )
                    Screen.NEW_CHAT -> NewChatScreen(
                        defaultBrain = settings.brainMode,
                        onBack = { screen = Screen.HOME },
                        onStart = { config -> viewModel.createChat(config) { screen = Screen.CHAT } }
                    )
                    Screen.HISTORY -> HistoryScreen(chats, openChat, viewModel::deleteChat)
                    Screen.MEMORY -> MemoryScreen(
                        memories, sources, work,
                        viewModel::addMemory,
                        viewModel::toggleMemory,
                        viewModel::deleteMemory,
                        viewModel::toggleSource,
                        viewModel::deleteSource,
                        viewModel::importSource
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        hasGeminiKey = viewModel.hasGeminiKey(),
                        hasNvidiaKey = viewModel.hasNvidiaKey(),
                        work = work,
                        onSave = viewModel::saveSettings,
                        onPreviewVoice = viewModel::previewVoice
                    )
                    Screen.CHAT -> {
                        val chat = chats.firstOrNull { it.id == currentId }
                        if (chat != null) {
                            ChatScreen(
                                chat, messages, work,
                                onBack = { viewModel.closeChat(); screen = Screen.HOME },
                                onSend = viewModel::send,
                                onMic = viewModel::toggleRecording,
                                onSpeak = { text -> viewModel.speak(chat.id, text) },
                                onStopSpeaking = viewModel::stopSpeaking
                            )
                        } else {
                            LoadingPane("Opening conversation…")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBottomBar(screen: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)) {
        listOf(
            Triple(Screen.HOME, Icons.Default.Home, "Home"),
            Triple(Screen.HISTORY, Icons.Default.History, "History"),
            Triple(Screen.MEMORY, Icons.Default.AutoAwesome, "Memory"),
            Triple(Screen.SETTINGS, Icons.Default.Settings, "Settings")
        ).forEach { (target, icon, label) ->
            NavigationBarItem(
                selected = screen == target,
                onClick = { onSelect(target) },
                icon = { Icon(icon, label) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    chats: List<ChatEntity>,
    work: WorkState,
    onNewChat: () -> Unit,
    onQuickStart: (TutorConfig) -> Unit,
    onOpenChat: (Long) -> Unit,
    onSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("LANGUAGE TALK AI", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("안녕하세요, Myo Min Thu", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("ဒီနေ့ ဘာအကြောင်းပြောမလဲ?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
            }
        }
        item { VoiceHero(work, onNewChat) }
        item {
            Text("Quick practice", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickModeCard("Korean Daily Talk", "နေ့စဉ်သုံး Korean စကားပြော", Icons.Default.ChatBubbleOutline, Violet) {
                    onQuickStart(TutorConfig(topic = "နေ့စဉ် Korean စကားပြော"))
                }
                QuickModeCard("Workplace Korean", "စက်ရုံ၊ မန်နေဂျာ၊ အလုပ်ဖော် roleplay", Icons.Default.Person, Mint) {
                    onQuickStart(TutorConfig(topic = "ကိုရီးယားစက်ရုံ အလုပ်ခွင်", role = TutorRole.COWORKER))
                }
                QuickModeCard("English Talk", "Natural English conversation", Icons.Default.Translate, Color(0xFF5AA7FF)) {
                    onQuickStart(TutorConfig(language = AppLanguage.ENGLISH, topic = "Daily English conversation"))
                }
                QuickModeCard("EPS-TOPIK", "မေးခွန်း၊ listening နဲ့ vocabulary", Icons.Default.Description, Coral) {
                    onQuickStart(TutorConfig(topic = "EPS-TOPIK စကားပြောနှင့် မေးခွန်း", customPrompt = "Use official EPS-TOPIK style. Ask one question at a time and wait for my answer."))
                }
            }
        }
        if (chats.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Continue learning", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${chats.size} chats", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(chats.take(4), key = { it.id }) { chat ->
                ChatRow(chat, onClick = { onOpenChat(chat.id) })
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun VoiceHero(work: WorkState, onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onStart),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF34205C), Color(0xFF1A293F))))
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Mint, Violet, Color(0xFF2C194A))))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.GraphicEq, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Start a conversation", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("AI ဆရာနဲ့ အသံဖြင့်အပြန်အလှန်ပြောပါ", color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.height(10.dp))
                AssistChip(
                    onClick = onStart,
                    label = { Text(if (work.isSending) work.status else "New voice chat") },
                    leadingIcon = { Icon(Icons.Default.Mic, null, Modifier.size(17.dp)) }
                )
            }
        }
    }
}

@Composable
private fun QuickModeCard(title: String, subtitle: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = accent) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Icon(Icons.Default.PlayArrow, null, tint = accent)
        }
    }
}

@Composable
private fun NewChatScreen(defaultBrain: BrainMode, onBack: () -> Unit, onStart: (TutorConfig) -> Unit) {
    var language by rememberSaveable { mutableStateOf(AppLanguage.KOREAN) }
    var level by rememberSaveable { mutableStateOf("Beginner") }
    var topic by rememberSaveable { mutableStateOf("နေ့စဉ်စကားပြော") }
    var role by rememberSaveable { mutableStateOf(TutorRole.TEACHER) }
    var correction by rememberSaveable { mutableStateOf(CorrectionMode.AFTER_REPLY) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var brain by rememberSaveable { mutableStateOf(defaultBrain) }
    var voiceIndex by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AppTopBar("New conversation", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SettingSection("ဘာသာစကား") {
                ChoiceRow(AppLanguage.entries.toList(), language, { it.label }) { language = it }
            }
            SettingSection("အဆင့်") {
                ChoiceRow(listOf("Beginner", "Intermediate", "Advanced"), level, { it }) { level = it }
            }
            OutlinedTextField(
                value = topic, onValueChange = { topic = it },
                label = { Text("ဘာအကြောင်းပြောမလဲ") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            SettingSection("AI ရဲ့အခန်းကဏ္ဍ") {
                ChoiceRow(TutorRole.entries.toList(), role, { it.label }) { role = it }
            }
            SettingSection("အမှားပြင်ပုံ") {
                ChoiceRow(CorrectionMode.entries.toList(), correction, { it.label }) { correction = it }
            }
            SettingSection("Brain mode") {
                BrainMode.entries.forEach { mode ->
                    SelectableCard(mode.label, mode.description, brain == mode) { brain = mode }
                    Spacer(Modifier.height(7.dp))
                }
            }
            SettingSection("Gemini voice") {
                DefaultVoicePresets.forEachIndexed { index, preset ->
                    FilterChip(
                        selected = index == voiceIndex,
                        onClick = { voiceIndex = index },
                        label = { Text(preset.name) },
                        leadingIcon = if (index == voiceIndex) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.padding(end = 5.dp)
                    )
                }
            }
            OutlinedTextField(
                value = prompt, onValueChange = { prompt = it },
                label = { Text("Custom prompt") },
                placeholder = { Text("ဥပမာ—အမှားကို မြန်မာလိုတိုတိုရှင်းပြပါ") },
                minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
            )
            Button(
                onClick = {
                    val voice = DefaultVoicePresets[voiceIndex]
                    onStart(TutorConfig(language, level, topic, role, correction, prompt, voice.voice, voice.style, brain))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(9.dp))
                Text("Start conversation", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun <T> ChoiceRow(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(items) { item ->
            FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(label(item), maxLines = 1) })
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        content()
    }
}

@Composable
private fun SelectableCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) VioletLight else MaterialTheme.colorScheme.outline.copy(alpha = .35f)),
        colors = CardDefaults.outlinedCardColors(containerColor = if (selected) Violet.copy(alpha = .12f) else Color.Transparent),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.Check, null, tint = Mint)
        }
    }
}

@Composable
private fun ChatScreen(
    chat: ChatEntity,
    messages: List<MessageEntity>,
    work: WorkState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onMic()
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Mint, Violet))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.GraphicEq, null, tint = Color.White) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(chat.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (work.error == null) Mint else Coral))
                    Spacer(Modifier.width(5.dp))
                    Text(work.status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = if (work.isSpeaking) onStopSpeaking else {}) {
                Icon(if (work.isSpeaking) Icons.Default.Stop else Icons.Default.MoreHoriz, null)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .12f))
        if (work.isSending) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mint)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item { EmptyChatIntro(chat) }
            }
            items(messages, key = { it.id }) { message ->
                MessageBubble(message, onSpeak)
            }
            if (work.isSending) {
                item { TypingBubble(work.status) }
            }
        }

        SurfaceInputBar(
            input = input,
            onInput = { input = it },
            sending = work.isSending,
            recording = work.isRecording,
            onSend = {
                val value = input.trim()
                if (value.isNotBlank()) {
                    input = ""
                    onSend(value)
                }
            },
            onMic = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    onMic()
                } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )
    }
}

@Composable
private fun EmptyChatIntro(chat: ChatEntity) {
    Column(
        Modifier.fillMaxWidth().padding(top = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(92.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Mint, Violet, Color(0xFF281A45)))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("စကားစပြောလိုက်ပါ", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "${chat.topic} · ${chat.level}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "စာရိုက်နိုင်သလို microphone ကိုနှိပ်ပြီး အသံနဲ့လည်းပြောနိုင်ပါတယ်။ AI ကပြန်ဖြေပြီး မေးခွန်းတစ်ခုဆက်မေးပါမယ်။",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, onSpeak: (String) -> Unit) {
    val user = message.role == "USER"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(.88f)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp, topEnd = 20.dp,
                        bottomStart = if (user) 20.dp else 5.dp,
                        bottomEnd = if (user) 5.dp else 20.dp
                    )
                )
                .background(if (user) Violet.copy(alpha = .82f) else MaterialTheme.colorScheme.surface)
                .padding(15.dp)
        ) {
            Text(message.content, lineHeight = 23.sp)
            if (!user && message.translation.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                DetailPanel("မြန်မာအဓိပ္ပာယ်", message.translation, Mint)
            }
            if (!user && message.correction.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                DetailPanel("ပြင်ဆင်ချက်", message.correction, Coral)
            }
            if (!user && message.explanation.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                DetailPanel("ရှင်းလင်းချက်", message.explanation, VioletLight)
            }
            if (!user) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    AssistChip(
                        onClick = { onSpeak(message.content) },
                        label = { Text("Listen") },
                        leadingIcon = { Icon(Icons.Default.VolumeUp, null, Modifier.size(16.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailPanel(title: String, value: String, accent: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .09f)).padding(10.dp)
    ) {
        Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TypingBubble(status: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Mint)
                Spacer(Modifier.width(9.dp))
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SurfaceInputBar(
    input: String,
    onInput: (String) -> Unit,
    sending: Boolean,
    recording: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.weight(1f),
            placeholder = { Text(if (recording) "နားထောင်နေသည်…" else "Message…") },
            minLines = 1,
            maxLines = 4,
            enabled = !sending && !recording,
            shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() })
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = if (input.isNotBlank()) onSend else onMic,
            enabled = !sending,
            modifier = Modifier.size(52.dp),
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                containerColor = if (recording) Coral else if (input.isNotBlank()) Violet else Mint,
                contentColor = if (input.isBlank()) Color(0xFF06291F) else Color.White
            )
        ) {
            Icon(
                when {
                    input.isNotBlank() -> Icons.Default.Send
                    recording -> Icons.Default.Stop
                    else -> Icons.Default.Mic
                }, null
            )
        }
    }
}

@Composable
private fun HistoryScreen(chats: List<ChatEntity>, onOpen: (Long) -> Unit, onDelete: (Long) -> Unit) {
    var deleteTarget by remember { mutableStateOf<ChatEntity?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Text("Chat history", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("စကားဝိုင်းဟောင်းကိုပြန်ဖွင့်ပြီး ဆက်လေ့ကျင့်နိုင်ပါတယ်", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (chats.isEmpty()) {
            item { EmptyState(Icons.Default.History, "History မရှိသေးပါ", "Home မှာ conversation အသစ်စတင်ပါ") }
        } else {
            items(chats, key = { it.id }) { chat ->
                ChatRow(chat, onClick = { onOpen(chat.id) }, onDelete = { deleteTarget = chat })
            }
        }
    }
    deleteTarget?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Chat ဖျက်မလား?") },
            text = { Text("${chat.title} နှင့် message အားလုံးကိုဖျက်ပါမယ်။") },
            confirmButton = { TextButton(onClick = { onDelete(chat.id); deleteTarget = null }) { Text("ဖျက်မယ်", color = Coral) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("မဖျက်တော့ဘူး") } }
        )
    }
}

@Composable
private fun ChatRow(chat: ChatEntity, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(45.dp).clip(RoundedCornerShape(14.dp)).background(Violet.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.ChatBubbleOutline, null, tint = VioletLight) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(chat.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${chat.language.lowercase().replaceFirstChar { it.uppercase() }} · ${chat.level} · ${formatDate(chat.updatedAt)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            onDelete?.let { action ->
                IconButton(onClick = action) { Icon(Icons.Default.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun MemoryScreen(
    memories: List<MemoryEntity>,
    sources: List<KnowledgeSourceEntity>,
    work: WorkState,
    onAddMemory: (String, String) -> Unit,
    onToggleMemory: (MemoryEntity) -> Unit,
    onDeleteMemory: (MemoryEntity) -> Unit,
    onToggleSource: (KnowledgeSourceEntity) -> Unit,
    onDeleteSource: (KnowledgeSourceEntity) -> Unit,
    onImport: (android.net.Uri) -> Unit
) {
    var addDialog by remember { mutableStateOf(false) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Text("Memory & Context", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("AI က မင်းအကြောင်းနဲ့ စာရွက်စာတမ်းတွေကို သက်ဆိုင်တဲ့အချိန်မှာအသုံးပြုမယ်", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = { addDialog = true }, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Text memory")
                }
                OutlinedButton(
                    onClick = { fileLauncher.launch(arrayOf("image/*", "application/pdf", "text/*")) },
                    enabled = !work.isImporting,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (work.isImporting) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(6.dp))
                    Text("ပုံ / PDF")
                }
            }
        }
        if (sources.isNotEmpty()) {
            item { SectionTitle("Knowledge sources", "${sources.count { it.enabled }} enabled") }
            items(sources, key = { "source-${it.id}" }) { source ->
                SourceCard(source, { onToggleSource(source) }, { onDeleteSource(source) })
            }
        }
        item { SectionTitle("Personal memory", "${memories.count { it.enabled }} enabled") }
        if (memories.isEmpty()) {
            item {
                EmptyState(
                    Icons.Default.AutoAwesome,
                    "Memory မထည့်ရသေးပါ",
                    "နာမည်၊ အလုပ်၊ ရည်မှန်းချက်နဲ့ သင်ကြားပုံလိုအပ်ချက်တွေကိုထည့်နိုင်ပါတယ်"
                )
            }
        } else {
            items(memories, key = { "memory-${it.id}" }) { memory ->
                MemoryCard(memory, { onToggleMemory(memory) }, { onDeleteMemory(memory) })
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (addDialog) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Add personal memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("ခေါင်းစဉ်") }, singleLine = true)
                    OutlinedTextField(content, { content = it }, label = { Text("AI မှတ်ထားစေချင်တာ") }, minLines = 4)
                }
            },
            confirmButton = {
                Button(onClick = { onAddMemory(title, content); addDialog = false }, enabled = title.isNotBlank() && content.isNotBlank()) {
                    Text("သိမ်းမယ်")
                }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SourceCard(source: KnowledgeSourceEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Mint.copy(alpha = .12f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Description, null, tint = Mint) }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(source.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(source.mimeType, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(source.enabled, onCheckedChange = { onToggle() })
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete") }
            }
            AnimatedVisibility(source.summary.isNotBlank()) {
                Text(
                    source.summary,
                    modifier = Modifier.padding(top = 9.dp),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MemoryCard(memory: MemoryEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(memory.title, fontWeight = FontWeight.SemiBold)
                Text(memory.content, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Switch(memory.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete") }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    hasGeminiKey: Boolean,
    hasNvidiaKey: Boolean,
    work: WorkState,
    onSave: (AppSettings, String?, String?) -> Unit,
    onPreviewVoice: (String, String, String) -> Unit
) {
    var displayName by remember(settings.displayName) { mutableStateOf(settings.displayName) }
    var geminiKey by remember { mutableStateOf("") }
    var nvidiaKey by remember { mutableStateOf("") }
    var brainMode by remember(settings.brainMode) { mutableStateOf(settings.brainMode) }
    var autoSpeak by remember(settings.autoSpeak) { mutableStateOf(settings.autoSpeak) }
    var darkTheme by remember(settings.darkTheme) { mutableStateOf(settings.darkTheme) }
    var geminiModel by remember(settings.geminiModel) { mutableStateOf(settings.geminiModel) }
    var ttsModel by remember(settings.geminiTtsModel) { mutableStateOf(settings.geminiTtsModel) }
    var nvidiaModel by remember(settings.nvidiaModel) { mutableStateOf(settings.nvidiaModel) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("API၊ brain mode နဲ့ Gemini voice ကိုထိန်းချုပ်ပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsCard("Profile") {
                OutlinedTextField(
                    displayName, { displayName = it }, label = { Text("နာမည်") },
                    leadingIcon = { Icon(Icons.Default.Person, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
        }
        item {
            SettingsCard("API keys") {
                KeyField(
                    label = "Gemini API key",
                    value = geminiKey,
                    onValue = { geminiKey = it },
                    configured = hasGeminiKey,
                    required = true
                )
                Spacer(Modifier.height(12.dp))
                KeyField(
                    label = "NVIDIA API key",
                    value = nvidiaKey,
                    onValue = { nvidiaKey = it },
                    configured = hasNvidiaKey,
                    required = false
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Key အသစ်မရိုက်ဘဲ Save လုပ်လျှင် သိမ်းထားသော key ကိုမပြောင်းပါ။ Keys ကို Android Keystore ဖြင့် encrypted သိမ်းထားသည်။",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SettingsCard("Brain mode") {
                BrainMode.entries.forEach { mode ->
                    SelectableCard(mode.label, mode.description, brainMode == mode) { brainMode = mode }
                    Spacer(Modifier.height(7.dp))
                }
                if (brainMode != BrainMode.GEMINI_ONLY && !hasNvidiaKey && nvidiaKey.isBlank()) {
                    Text("ဒီ mode အတွက် NVIDIA API key လိုအပ်သည်", color = Coral, fontSize = 12.sp)
                }
            }
        }
        item {
            SettingsCard("Gemini voices") {
                DefaultVoicePresets.forEach { preset ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onPreviewVoice(preset.voice, preset.style, preset.previewText)
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Violet.copy(alpha = .16f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.VolumeUp, null, tint = VioletLight) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, fontWeight = FontWeight.Medium)
                            Text(preset.voice, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.PlayArrow, "Preview", tint = Mint)
                    }
                }
                if (work.isSpeaking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(work.status, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            SettingsCard("Behavior") {
                ToggleRow("အဖြေရတာနဲ့ အသံပြောမယ်", "Gemini TTS ကိုအလိုအလျောက်ဖွင့်မည်", autoSpeak) { autoSpeak = it }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .15f))
                ToggleRow("Dark theme", "Premium dark interface", darkTheme) { darkTheme = it }
            }
        }
        item {
            SettingsCard("Advanced models") {
                OutlinedTextField(geminiModel, { geminiModel = it }, label = { Text("Gemini brain model") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(ttsModel, { ttsModel = it }, label = { Text("Gemini TTS model") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(nvidiaModel, { nvidiaModel = it }, label = { Text("NVIDIA reasoning model") }, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            displayName = displayName,
                            brainMode = brainMode,
                            autoSpeak = autoSpeak,
                            darkTheme = darkTheme,
                            geminiModel = geminiModel.trim(),
                            geminiTtsModel = ttsModel.trim(),
                            nvidiaModel = nvidiaModel.trim()
                        ),
                        geminiKey.trim().takeIf { it.isNotEmpty() },
                        nvidiaKey.trim().takeIf { it.isNotEmpty() }
                    )
                    geminiKey = ""
                    nvidiaKey = ""
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Save settings", fontWeight = FontWeight.Bold)
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun KeyField(label: String, value: String, onValue: (String) -> Unit, configured: Boolean, required: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(if (configured) "သိမ်းထားပြီးသား ••••••••" else if (required) "Required" else "Optional") },
        visualTransformation = PasswordVisualTransformation(),
        trailingIcon = {
            if (configured) Icon(Icons.Default.Check, "Configured", tint = Mint)
        },
        singleLine = true
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(13.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String = "") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (trailing.isNotBlank()) Text(trailing, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 35.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun AppTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun LoadingPane(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Mint)
            Spacer(Modifier.height(12.dp))
            Text(text)
        }
    }
}

private fun formatDate(time: Long): String = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(time))
