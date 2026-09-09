@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myothuonion.languagetalk.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.myothuonion.languagetalk.data.ChatEntity
import com.myothuonion.languagetalk.data.MemoryEntity
import com.myothuonion.languagetalk.network.LiveLine
import com.myothuonion.languagetalk.network.LivePhase
import com.myothuonion.languagetalk.network.LiveSpeaker
import com.myothuonion.languagetalk.network.LiveState

@Composable
internal fun ChatHubScreen(
    chats: List<ChatEntity>,
    onNewChat: () -> Unit,
    onMessageChat: (Long?) -> Unit,
    onLiveChat: (Long?) -> Unit,
    onCustomize: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Text("Chat", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text(
                    "စာပို့ပြီးပြောမလား၊ hands-free Live နဲ့ တိုက်ရိုက်ပြောမလား",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            ChatModeCard(
                title = "Message Chat",
                subtitle = "စာရိုက်၊ voice message ပို့ပြီး သင်ယူမယ်",
                badge = "SEND MODE",
                icon = Icons.Default.Keyboard,
                colors = listOf(Color(0xFF39225F), Color(0xFF182A46)),
                accent = VioletLight,
                onClick = { onMessageChat(chats.firstOrNull()?.id) }
            )
        }
        item {
            ChatModeCard(
                title = "Gemini Live",
                subtitle = "ခလုတ်နှိပ်ပို့စရာမလိုဘဲ အပြန်အလှန်ပြောမယ်",
                badge = "HANDS-FREE",
                icon = Icons.Default.GraphicEq,
                colors = listOf(Color(0xFF0D4A45), Color(0xFF252052), Color(0xFF181526)),
                accent = Mint,
                onClick = { onLiveChat(chats.firstOrNull()?.id) }
            )
        }
        item {
            OutlinedButton(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("New custom chat")
            }
        }
        if (chats.isNotEmpty()) {
            item {
                Text("Choose a conversation", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            items(chats, key = { it.id }) { chat ->
                ConversationChoice(chat, onMessageChat, onLiveChat, onCustomize)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ChatModeCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    colors: List<Color>,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            Modifier.background(Brush.linearGradient(colors)).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(68.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(accent, accent.copy(alpha = .25f), Color.Transparent)))
                    .border(1.dp, Color.White.copy(alpha = .2f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(31.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(badge, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.White.copy(alpha = .72f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ConversationChoice(
    chat: ChatEntity,
    onMessageChat: (Long?) -> Unit,
    onLiveChat: (Long?) -> Unit,
    onCustomize: (Long) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(Violet.copy(alpha = .16f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.ChatBubbleOutline, null, tint = VioletLight) }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(chat.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${chat.language.lowercase().replaceFirstChar { it.uppercase() }} · ${chat.level}",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onCustomize(chat.id) }) { Icon(Icons.Default.Tune, "Chat behavior and memory") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = { onMessageChat(chat.id) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Keyboard, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Message")
                }
                Button(onClick = { onLiveChat(chat.id) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Mic, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Live")
                }
            }
        }
    }
}

@Composable
internal fun ChatProfileDialog(
    chat: ChatEntity,
    memories: List<MemoryEntity>,
    onDismiss: () -> Unit,
    onSaveBehavior: (String) -> Unit,
    onAddMemory: (String, String) -> Unit,
    onToggleMemory: (MemoryEntity) -> Unit,
    onDeleteMemory: (MemoryEntity) -> Unit
) {
    var behavior by remember(chat.id, chat.customPrompt) { mutableStateOf(chat.customPrompt) }
    var memoryTitle by remember(chat.id) { mutableStateOf("") }
    var memoryContent by remember(chat.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Settings, null, tint = Mint) },
        title = { Text("${chat.title} · Customize") },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "ဒီနေရာက setting တွေဟာ ဒီ chat တစ်ခုတည်းအတွက်သာ သက်ရောက်မယ်။",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    behavior,
                    { behavior = it },
                    label = { Text("ဒီ Chat ရဲ့ Behaviour") },
                    placeholder = { Text("ဥပမာ—ငါ့ကို မန်နေဂျာလို Korean နဲ့မေးပါ") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { onSaveBehavior(behavior) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save behaviour")
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("ဒီ Chat ရဲ့ Memory", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    memoryTitle,
                    { memoryTitle = it },
                    label = { Text("Memory ခေါင်းစဉ်") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    memoryContent,
                    { memoryContent = it },
                    label = { Text("မှတ်ထားစေချင်တဲ့ အကြောင်းအရာ") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        if (memoryTitle.isNotBlank() && memoryContent.isNotBlank()) {
                            onAddMemory(memoryTitle, memoryContent)
                            memoryTitle = ""
                            memoryContent = ""
                        }
                    },
                    enabled = memoryTitle.isNotBlank() && memoryContent.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add chat memory")
                }
                memories.forEach { memory ->
                    OutlinedCard(shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(memory.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    memory.content,
                                    fontSize = 12.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { onDeleteMemory(memory) }, contentPadding = PaddingValues(0.dp)) {
                                    Text("Delete", color = Coral, fontSize = 11.sp)
                                }
                            }
                            Switch(memory.enabled, onCheckedChange = { onToggleMemory(memory) })
                        }
                    }
                }
                if (memories.isEmpty()) {
                    Text("ဒီ chat အတွက် memory မရှိသေးပါ", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
internal fun LiveChatScreen(
    chat: ChatEntity,
    state: LiveState,
    onStart: () -> Unit,
    onToggleMic: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onCustomize: () -> Unit
) {
    val context = LocalContext.current
    var permissionDenied by rememberSaveable(chat.id) { mutableStateOf(false) }
    var captionsVisible by rememberSaveable(chat.id) { mutableStateOf(true) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) onStart()
    }

    LaunchedEffect(chat.id) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onStart()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    DisposableEffect(chat.id) { onDispose { onStop() } }
    BackHandler {
        onStop()
        onBack()
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF080610), Color(0xFF111026), Color(0xFF071C1C)))
        )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onStop(); onBack() }) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(chat.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(phaseColor(state.phase)))
                        Spacer(Modifier.width(6.dp))
                        Text(phaseLabel(state), color = Color.White.copy(alpha = .65f), fontSize = 11.sp)
                    }
                }
                IconButton(onClick = { captionsVisible = !captionsVisible }) {
                    Icon(if (captionsVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show or hide transcript", tint = Color.White)
                }
                IconButton(onClick = onCustomize) { Icon(Icons.Default.Tune, "Customize", tint = Color.White) }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CinematicOrb(state)
                Spacer(Modifier.height(28.dp))
                Text(phaseLabel(state), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(
                    phaseHint(state),
                    color = Color.White.copy(alpha = .58f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                AnimatedVisibility(captionsVisible && (state.aiCaption.isNotBlank() || state.userCaption.isNotBlank())) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (state.aiCaption.isNotBlank()) {
                            Text("AI", color = Mint, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp)
                            Text(
                                state.aiCaption,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                fontSize = 19.sp,
                                lineHeight = 27.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (state.userCaption.isNotBlank()) {
                            Text("YOU", color = VioletLight, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp)
                            Text(
                                state.userCaption,
                                color = Color.White.copy(alpha = .82f),
                                textAlign = TextAlign.Center,
                                fontSize = 17.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (permissionDenied || state.phase == LivePhase.ERROR) {
                    Text(
                        state.error ?: "Live Chat အတွက် microphone permission လိုအပ်ပါတယ်",
                        color = Coral,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                onStop()
                                onStart()
                            } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Try again") }
                }
            }

            AnimatedVisibility(captionsVisible && state.lines.isNotEmpty()) {
                LiveTranscript(state.lines)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 42.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onToggleMic,
                    enabled = state.phase != LivePhase.DISCONNECTED && state.phase != LivePhase.CONNECTING,
                    modifier = Modifier.size(58.dp),
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (state.micEnabled) Color.White.copy(alpha = .14f) else Color.White,
                        contentColor = if (state.micEnabled) Color.White else Ink
                    )
                ) { Icon(if (state.micEnabled) Icons.Default.Mic else Icons.Default.MicOff, "Mute microphone") }
                FilledIconButton(
                    onClick = { onStop(); onBack() },
                    modifier = Modifier.size(68.dp),
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = Coral, contentColor = Color.White)
                ) { Icon(Icons.Default.CallEnd, "End live chat", Modifier.size(29.dp)) }
            }
        }
    }
}

@Composable
private fun CinematicOrb(state: LiveState) {
    val infinite = rememberInfiniteTransition(label = "live-orb")
    val pulse by infinite.animateFloat(
        initialValue = .96f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse"
    )
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8500), RepeatMode.Restart),
        label = "rotation"
    )
    val rawEnergy = when (state.phase) {
        LivePhase.SPEAKING -> state.outputLevel
        LivePhase.LISTENING -> state.inputLevel
        LivePhase.THINKING -> .28f
        else -> .08f
    }
    val energy by animateFloatAsState(rawEnergy.coerceIn(.05f, 1f), tween(110), label = "energy")
    val color = phaseColor(state.phase)
    Box(Modifier.size(238.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(230.dp).graphicsLayer {
                scaleX = pulse + energy * .09f
                scaleY = pulse + energy * .09f
                alpha = .25f + energy * .28f
            }.clip(CircleShape).background(Brush.radialGradient(listOf(color.copy(alpha = .8f), Violet.copy(alpha = .25f), Color.Transparent)))
        )
        Box(
            Modifier.size(190.dp).graphicsLayer { rotationZ = rotation }
                .clip(CircleShape)
                .background(Brush.sweepGradient(listOf(color.copy(alpha = .12f), color, Violet, color.copy(alpha = .1f))))
                .padding(3.dp)
                .clip(CircleShape)
                .background(Color(0xFF0E0B1B))
        )
        Box(
            Modifier.size(151.dp).graphicsLayer {
                scaleX = 1f + energy * .12f
                scaleY = 1f + energy * .12f
            }.clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = .2f), color.copy(alpha = .8f), Violet.copy(alpha = .64f), Color(0xFF211137))))
                .border(1.dp, Color.White.copy(alpha = .25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (state.phase == LivePhase.THINKING) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(48.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(.58f, .92f, 1.2f, .92f, .58f).forEachIndexed { index, size ->
                        Box(
                            Modifier.width(6.dp).height((29f * size * (.55f + energy * .6f)).dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (index == 2) 1f else .78f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTranscript(lines: List<LiveLine>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171423).copy(alpha = .94f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text("LIVE TRANSCRIPT", color = Color.White.copy(alpha = .45f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                contentPadding = PaddingValues(top = 7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(lines.takeLast(8)) { line ->
                    Row {
                        Text(
                            if (line.speaker == LiveSpeaker.AI) "AI" else "YOU",
                            color = if (line.speaker == LiveSpeaker.AI) Mint else VioletLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(34.dp)
                        )
                        Text(line.text, color = Color.White.copy(alpha = .84f), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

private fun phaseColor(phase: LivePhase): Color = when (phase) {
    LivePhase.LISTENING -> Mint
    LivePhase.THINKING -> VioletLight
    LivePhase.SPEAKING -> Color(0xFF62A9FF)
    LivePhase.ERROR -> Coral
    LivePhase.RECONNECTING -> Color(0xFFFFC857)
    else -> Violet
}

private fun phaseLabel(state: LiveState): String = when (state.phase) {
    LivePhase.CONNECTING -> "Connecting…"
    LivePhase.RECONNECTING -> "Reconnecting…"
    LivePhase.LISTENING -> if (state.micEnabled) "Listening" else "Microphone muted"
    LivePhase.THINKING -> "Thinking"
    LivePhase.SPEAKING -> "Speaking"
    LivePhase.ERROR -> "Live paused"
    LivePhase.DISCONNECTED -> "Ready for Live"
}

private fun phaseHint(state: LiveState): String = when (state.phase) {
    LivePhase.LISTENING -> if (state.micEnabled) "စကားပြောလိုက်ပါ · AI ပြောနေချိန်မှာလည်း ဝင်ပြောဖြတ်နိုင်ပါတယ်" else "Mic ကိုဖွင့်ပြီး ဆက်ပြောပါ"
    LivePhase.THINKING -> "မင်းပြောတာကို နားလည်ပြီး အဖြေစဉ်းစားနေပါတယ်"
    LivePhase.SPEAKING -> "Gemini က တိုက်ရိုက်ပြန်ပြောနေပါတယ်"
    LivePhase.CONNECTING, LivePhase.RECONNECTING -> "Gemini Live secure session ကိုချိတ်ဆက်နေပါတယ်"
    LivePhase.ERROR -> "Connection နဲ့ API settings ကိုစစ်ပြီး ပြန်စမ်းပါ"
    LivePhase.DISCONNECTED -> "Hands-free conversation စတင်ရန် အဆင်သင့်ပါ"
}
