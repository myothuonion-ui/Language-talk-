package com.myothuonion.languagetalk.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.myothuonion.languagetalk.data.*
import com.myothuonion.languagetalk.network.*
import kotlinx.coroutines.delay

@Composable
internal fun LiveChatScreen(chat: ChatEntity,state: LiveState,progress: LearningProgressEntity?,
    onStart: () -> Unit,onToggleMic: () -> Unit,onStop: () -> Unit,onBack: () -> Unit,onCustomize: () -> Unit,
    onHelp: () -> Unit = {},onTextChat: () -> Unit = {},aiChooser: @Composable () -> Unit = {},
    aiStatus: AiRouteStatus = AiRouteStatus(),onContinuePractice: () -> Unit = {},
    onSpeak: (String) -> Unit = {},isPlaying: Boolean = false,onStopPlayback: () -> Unit = {}) {
    val context = LocalContext.current
    var denied by rememberSaveable(chat.id) { mutableStateOf(false) }
    var advance by remember { mutableStateOf(false) }
    var hint by rememberSaveable(chat.id) { mutableStateOf(false) }
    var controls by remember { mutableStateOf(false) }
    var elapsed by rememberSaveable(chat.id) { mutableIntStateOf(0) }
    var heard by rememberSaveable(chat.id) { mutableStateOf("") }
    var aiLine by rememberSaveable(chat.id) { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) { if (advance) onContinuePractice() else onStart() }
    }
    fun begin(next: Boolean = false) {
        advance = next
        if (ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (next) onContinuePractice() else onStart()
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(chat.id) { begin() }
    LaunchedEffect(state.userCaption,state.aiCaption) {
        if (state.userCaption.isNotBlank()) heard = state.userCaption
        if (state.aiCaption.isNotBlank()) aiLine = state.aiCaption
    }
    LaunchedEffect(state.connected) {
        while (state.connected && elapsed < 1800) { delay(1000); elapsed++ }
        if (elapsed >= 1800) onStop()
    }
    LaunchedEffect(progress?.readyNext,state.phase) {
        if (progress?.readyNext == true && state.connected && state.phase == LivePhase.LISTENING) onStop()
    }
    DisposableEffect(chat.id) { onDispose { onStop() } }
    androidx.lifecycle.compose.LifecycleResumeEffect(chat.id) { onPauseOrDispose { onStop() } }
    BackHandler { onStop(); onBack() }
    LazyColumn(Modifier.fillMaxSize().testTag("live-practice"),contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Row(Modifier.statusBarsPadding(),verticalAlignment = Alignment.CenterVertically) {
                IconButton({ onStop(); onBack() }) { Icon(Icons.Default.ArrowBack,"Back") }
                Column(Modifier.weight(1f)) {
                    Text(chat.topic,maxLines = 2,style = MaterialTheme.typography.titleLarge)
                    Text(if (chat.practiceMode == "GUIDED") "အတူလေ့ကျင့် · ကိုယ်တိုင်ဆက်မယ်" else "ကိုယ်ရွေးထားတဲ့အကြောင်း ပြောမယ်",
                        style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ controls = !controls }) { GlassIconTile(Icons.Default.Tune,label = "Voice settings") }
            }
        }
        if (controls) item {
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    TextButton(onCustomize) { Text("အသံနှင့် လေ့ကျင့်ပုံ") }
                    aiChooser()
                    TextButton(onTextChat) { Icon(Icons.Default.Keyboard,null); Text("စာရိုက်စကားပြော") }
                }
            }
        }
        if (state.error != null) item {
            Text(state.error.orEmpty(),color = MaterialTheme.colorScheme.error,style = MaterialTheme.typography.bodyMedium)
            Button({ onStop(); begin() },Modifier.fillMaxWidth()) { Text("ပြန်ချိတ်မယ်") }
            TextButton(onTextChat) { Text("စာရိုက်ပြီး ဆက်မယ်") }
        }
        item {
            Text(when (progress?.stage) { "REPEAT" -> "နမူနာကို လိုက်ပြော"; "APPLY" -> "ကိုယ်တိုင် အသုံးချပြော"; "REVIEW" -> "ပြန်လေ့လာ"; else -> "စလေ့လာမယ်" },
                color = MaterialTheme.colorScheme.primary,fontWeight = FontWeight.SemiBold)
            if (progress != null) Text("သုံးနိုင်ခဲ့သော စကားပုံစံ " + progress.completed + " / 5",
                style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (aiLine.isNotBlank() || progress?.targetSentence?.isNotBlank() == true) item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI အလှည့်",Modifier.weight(1f),color = MaterialTheme.colorScheme.primary)
                        IconButton({ onSpeak(aiLine.ifBlank { progress?.targetSentence.orEmpty() }) },enabled = !isPlaying) {
                            GlassIconTile(Icons.Default.VolumeUp,Modifier.size(33.dp),"Replay AI sentence")
                        }
                    }
                    Text(aiLine.ifBlank { progress?.targetSentence.orEmpty() },fontSize = 22.sp,lineHeight = 34.sp)
                    if (progress?.lastCorrection?.isNotBlank() == true) Text(progress.lastCorrection,style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("မင်းအလှည့်",fontWeight = FontWeight.SemiBold,color = MaterialTheme.colorScheme.primary)
                    Text(if (progress?.readyNext == true) "ဒီအပိုင်းကို လေ့ကျင့်ပြီးပြီ။ ထပ်လေ့ကျင့်နိုင်တယ်။" else "ကိုရီးယားလို ပြန်ပြောကြည့်ပါ။")
                    TextButton({ hint = !hint; if (hint) onHelp() }) { Text(if (hint) "အကူအညီဖျောက်" else "အဖြေပုံစံ အကူအညီ") }
                    if (hint && progress != null) AnswerPatternCard(progress.answerPattern,progress.answerExample,progress.answerHint)
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledIconButton({ if (state.connected) onToggleMic() else begin() },modifier = Modifier.size(64.dp),
                        enabled = !isPlaying && state.phase != LivePhase.CONNECTING && progress?.readyNext != true) {
                        Icon(if (state.connected && !state.micEnabled) Icons.Default.MicOff else Icons.Default.Mic,"Microphone",Modifier.size(29.dp))
                    }
                    MicrophoneStatus(false,0f,processing = state.phase == LivePhase.THINKING,
                        live = state,heard = heard,modifier = Modifier.fillMaxWidth(),playback = isPlaying)
                    Text((elapsed/60).toString().padStart(2,'0') + ":" + (elapsed%60).toString().padStart(2,'0') + " / 30:00",
                        style = MaterialTheme.typography.labelMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isPlaying) TextButton(onStopPlayback) { Text("အသံရပ်မယ်") }
                    else if (state.connected) TextButton(onStop) { Text("အသံရပ်မယ်") }
                }
            }
        }
        if (progress?.readyNext == true) item {
            Button({ begin(next = true) },Modifier.fillMaxWidth().testTag("live-continue-step")) { Text("ကိုယ်တိုင် ဆက်လေ့ကျင့်မယ်") }
        }
        if (state.phase == LivePhase.CONNECTING) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("အသံချိတ်ဆက်နေပါတယ်…") }
        if (denied) item {
            Text("အသံလေ့ကျင့်ဖို့ Microphone permission ဖွင့်ပေးပါ။",color = MaterialTheme.colorScheme.error)
            OutlinedButton({ begin() }) { Text("Mic ခွင့်ပြုမယ်") }
        }
        if (aiStatus.review.isNotBlank()) item { Text(aiStatus.review,style = MaterialTheme.typography.bodySmall) }
    }
}
