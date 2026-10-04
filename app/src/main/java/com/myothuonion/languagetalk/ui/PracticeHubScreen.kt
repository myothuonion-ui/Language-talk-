package com.myothuonion.languagetalk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myothuonion.languagetalk.data.AppSettings
import com.myothuonion.languagetalk.data.ChatEntity
import com.myothuonion.languagetalk.model.*

@Composable
internal fun PracticeHubScreen(settings: AppSettings, onBooks: () -> Unit, onLive: () -> Unit,
    onDaily: () -> Unit, onSettings: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("practice-hub"), contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR KOREAN", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("စကားပြောတတ်ဖို့၊\nတစ်ဆင့်ချင်း", fontSize = 28.sp, lineHeight = 43.sp, fontWeight = FontWeight.SemiBold)
                    Text(settings.displayName + " · ဒီနေ့ ဘယ်လိုလေ့လာမလဲ", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onSettings) { Icon(Icons.Default.Settings, "Settings") }
            }
        }
        item { PracticeEntry("စာအုပ်သင်ခန်းစာ", "မူရင်းစာအုပ်အစဉ်အတိုင်း", "Beginner ၄၀ + Intermediate ၃၀ ခန်း\nနှစ်ယောက် အလှည့်ကျပြောမယ်", Icons.Default.MenuBook, "practice-books", onBooks) }
        item { PracticeEntry("Live စကားပြော", "ကိုယ့်ဘဝနဲ့ ကိုက်ညီတဲ့ Korean", "ဖြေဖို့ ပုံစံပြပေးတဲ့ Guided\nလွတ်လွတ်လပ်လပ် Open conversation", Icons.Default.GraphicEq, "practice-live", onLive) }
        item { PracticeEntry("ဒီနေ့ နည်းနည်းစီ", "၅–၁၀ မိနစ် · စကားပုံစံတစ်ခု", "အရင်သင် → အတူပြော → ကိုယ်တိုင်သုံး\nနောက်နေ့မှာ ပြန်လေ့ကျင့်", Icons.Default.WbSunny, "practice-daily", onDaily) }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(Icons.Default.VolumeUp, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("ကိုရီးယားအသံနဲ့ စကားပြောမယ်။ အဓိပ္ပာယ်နဲ့ grammar ကို မြန်မာစာနဲ့ ရှင်းပြမယ်။",
                    fontSize = 12.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PracticeEntry(title: String, subtitle: String, detail: String, icon: ImageVector, tag: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag), shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 13.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LiveSetupScreen(settings: AppSettings, chats: List<ChatEntity>, viewModel: AppViewModel,
    onBack: () -> Unit, onStart: (TutorConfig, Boolean) -> Unit, onResume: (Long, Boolean) -> Unit, onAdvanced: () -> Unit) {
    var guided by rememberSaveable { mutableStateOf(true) }
    var topic by rememberSaveable { mutableStateOf("နေ့စဉ်ဘဝနဲ့ အလုပ်ဖော် စကားပြော") }
    var typing by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().testTag("live-setup"), contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Text("Live စကားပြော", Modifier.weight(1f), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                AiTaskPicker(AiTask.CONVERSATION, viewModel)
            }
        }
        item {
            Text("ပြောချင်ပေမယ့် ဘယ်လိုစရမလဲ", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("Guided နဲ့ စပြီး အဖြေပုံစံကိုကြည့်ပြောနိုင်တယ်။ ကိုယ့်စကားနဲ့ သုံးတတ်လာရင် Open ကို ရွေးပါ။",
                fontSize = 13.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(guided, { guided = true }, label = { Text("Guided") }, modifier = Modifier.testTag("guided-mode"))
                FilterChip(!guided, { guided = false }, label = { Text("Open") }, modifier = Modifier.testTag("open-mode"))
            }
            Card(shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if (guided) "AI မေးမယ်၊ မင်းက ပြန်ဖြေမယ်" else "ကိုယ်စိတ်ဝင်စားတာကို ပြောမယ်", fontWeight = FontWeight.SemiBold)
                    Text(if (guided) "AI: 뭘 드릴까요?\nမင်းဖြေရမယ့်ပုံစံ: [လိုချင်တဲ့အရာ] 주세요."
                        else "ကိုယ့် topic ကိုပဲ ဆက်ပြောမယ်။ အခက်အခဲရှိရင် အဖြေပုံစံအကူအညီကို ဖွင့်နိုင်တယ်။", lineHeight = 27.sp, fontSize = 14.sp)
                }
            }
        }
        item {
            OutlinedTextField(topic, { topic = it.take(500) }, label = { Text("ဒီနေ့ ပြောချင်တဲ့အကြောင်း") },
                minLines = 2, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("အလုပ်ဖော်", "အစားအစာမှာ", "နေ့စဉ်ဘဝ").forEach { label ->
                    SuggestionChip({ topic = label }, label = { Text(label, fontSize = 12.sp) })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("စာနဲ့ လေ့ကျင့်မယ်", Modifier.weight(1f), fontSize = 13.sp)
                Switch(typing, { typing = it })
            }
            Button({
                onStart(TutorConfig(topic = topic.trim(), practiceMode = if (guided) PracticeMode.GUIDED else PracticeMode.FREE_TALK,
                    voiceName = settings.defaultVoiceName, voiceStyle = settings.defaultVoiceStyle,
                    speakingPace = settings.defaultPace, silenceMs = settings.defaultSilenceMs,
                    speakCorrections = settings.defaultSpeakCorrections, brainMode = BrainMode.GEMINI_ONLY), typing)
            }, enabled = topic.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp).testTag("start-live"), shape = RoundedCornerShape(18.dp)) {
                Icon(if (typing) Icons.Default.Keyboard else Icons.Default.Mic, null); Spacer(Modifier.width(8.dp))
                Text(if (typing) "စကားပြော စမယ်" else "Live စမယ်")
            }
        }
        if (chats.isNotEmpty()) {
            item { Text("အရင်စကားဝိုင်းကို ဆက်မယ်", fontWeight = FontWeight.SemiBold) }
            items(chats.take(5), key = { it.id }) { chat ->
                OutlinedCard(onClick = { onResume(chat.id, typing) }, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(chat.title, fontWeight = FontWeight.Medium)
                        Text(if (chat.practiceMode == "FREE_TALK") "Open conversation" else "Guided · အဖြေပုံစံအကူအညီ", fontSize = 12.sp)
                    }
                }
            }
        }
        item { TextButton(onAdvanced) { Text("အသေးစိတ် voice / role settings") } }
    }
}

@Composable
internal fun AnswerPatternCard(pattern: String, example: String, hint: String, dark: Boolean = false) {
    var showExample by remember(pattern, example) { mutableStateOf(false) }
    if (pattern.isBlank() && example.isBlank()) return
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(
        containerColor = if (dark) androidx.compose.ui.graphics.Color(0xFF20382E) else MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().testTag("answer-pattern")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("မင်းဖြေရမယ့်ပုံစံ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = if (dark) Mint else MaterialTheme.colorScheme.primary)
            Text(pattern, fontSize = 20.sp, lineHeight = 30.sp, color = if (dark) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onPrimaryContainer)
            if (hint.isNotBlank()) Text(hint, fontSize = 13.sp, lineHeight = 24.sp,
                color = if (dark) androidx.compose.ui.graphics.Color.White.copy(alpha = .8f) else MaterialTheme.colorScheme.onPrimaryContainer)
            if (example.isNotBlank()) {
                TextButton({ showExample = !showExample }, modifier = Modifier.testTag("show-answer-example")) {
                    Text(if (showExample) "နမူနာဖျောက်မယ်" else "နမူနာအဖြေကြည့်မယ်", color = if (dark) Mint else MaterialTheme.colorScheme.primary)
                }
                if (showExample) Text(example, fontSize = 18.sp,
                    color = if (dark) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
