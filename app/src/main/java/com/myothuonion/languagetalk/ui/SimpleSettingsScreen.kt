@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myothuonion.languagetalk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myothuonion.languagetalk.data.AppSettings
import com.myothuonion.languagetalk.model.DefaultVoicePresets

@Composable
internal fun SimpleSettingsScreen(settings: AppSettings, credentials: CredentialState, viewModel: AppViewModel,
    reader: ReaderViewModel, onBack: () -> Unit, onAdvanced: () -> Unit) {
    val learning by reader.learning.collectAsState()
    var draft by remember(settings) { mutableStateOf(settings) }
    var connection by remember { mutableStateOf(!credentials.geminiConfigured) }
    var key by remember { mutableStateOf("") }
    var voices by remember { mutableStateOf(false) }
    fun save(value: AppSettings) { draft = value; viewModel.saveSettings(value) }
    LazyColumn(Modifier.fillMaxSize().testTag("simple-settings"), contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Text("Settings", fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Text("အသံနဲ့ သင်ယူမှု", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Box {
                OutlinedButton({ voices = true }, Modifier.fillMaxWidth()) {
                    Text(DefaultVoicePresets.firstOrNull { it.voice == draft.defaultVoiceName && it.style == draft.defaultVoiceStyle }?.name ?: draft.defaultVoiceName, Modifier.weight(1f))
                    Icon(Icons.Default.ExpandMore, null)
                }
                DropdownMenu(voices, { voices = false }) {
                    DefaultVoicePresets.forEach { preset -> DropdownMenuItem(text = { Text(preset.name) },
                        onClick = { save(draft.copy(defaultVoiceName = preset.voice, defaultVoiceStyle = preset.style)); voices = false }) }
                }
            }
            TextButton({ viewModel.previewVoice(draft.defaultVoiceName, draft.defaultVoiceStyle, "안녕하세요. 오늘도 같이 한국어를 연습해요.") },
                enabled = credentials.geminiConfigured) { Icon(Icons.Default.VolumeUp, null); Spacer(Modifier.width(7.dp)); Text("နမူနာနားထောင်") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(draft.defaultPace == "SLOW", { save(draft.copy(defaultPace = "SLOW")) }, label = { Text("Slow") })
                FilterChip(draft.defaultPace != "SLOW", { save(draft.copy(defaultPace = "NATURAL")) }, label = { Text("Natural") })
            }
            Text("Grammar နဲ့ အဓိပ္ပာယ်ကို မြန်မာလို ရှင်းပြမယ်။", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ကိုယ့်လေ့ကျင့်အသံ သိမ်းမယ်", Modifier.weight(1f)); Switch(draft.recordPractice, { save(draft.copy(recordPractice = it)) })
            }
        }
        item {
            HorizontalDivider()
            Text("စာဖတ်ခြင်း", Modifier.padding(top = 16.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("DAY" to "Day", "SEPIA" to "Sepia", "NIGHT" to "Night").forEach { (value, label) ->
                    FilterChip(learning.readerTheme == value, { reader.theme(value) }, label = { Text(label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("App dark theme", Modifier.weight(1f)); Switch(draft.darkTheme, { save(draft.copy(darkTheme = it)) })
            }
        }
        item {
            HorizontalDivider()
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AI ချိတ်ဆက်မှု", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (credentials.geminiConfigured) "Gemini key သိမ်းထားပြီးပြီ" else "Gemini key ထည့်ပါ", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton({ connection = !connection }) { Text(if (connection) "ပိတ်" else "ပြင်မယ်") }
            }
            if (connection) {
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("Gemini API key") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton({ viewModel.testGeminiKey(key.trim().takeIf { it.isNotEmpty() }) },
                        enabled = !credentials.checkingGemini && (key.isNotBlank() || credentials.geminiConfigured)) { Text("Test") }
                    Button({ viewModel.replaceGeminiKey(key) }, enabled = !credentials.checkingGemini && key.isNotBlank()) { Text("သိမ်းမယ်") }
                }
            }
            if (credentials.checkingGemini) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (credentials.geminiStatus.isNotBlank()) Text(credentials.geminiStatus, fontSize = 12.sp)
            TextButton(onAdvanced) { Text("Advanced settings"); Icon(Icons.Default.ChevronRight, null) }
        }
        item { Text("Language Talk · v0.7.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
