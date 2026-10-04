@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myothuonion.languagetalk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myothuonion.languagetalk.data.AppSettings
import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.network.validateAiProfile
import java.util.UUID

@Composable
internal fun AiTaskPicker(task: AiTask, viewModel: AppViewModel, chatId: Long? = null) {
    val settings by viewModel.settings.collectAsState()
    val revision by viewModel.routeRevision.collectAsState()
    val route = remember(settings.ai, revision, chatId) { viewModel.effectiveRoute(task, chatId) }
    var show by rememberSaveable(task, chatId) { mutableStateOf(false) }
    Box {
        TextButton({ show = true }, modifier = Modifier.testTag("ai-picker-" + task.name)) {
            Icon(Icons.Default.AutoAwesome, null, Modifier.size(15.dp)); Spacer(Modifier.width(5.dp))
            Text(if (route.mode == AiMode.AUTO && route.primary == null) "Auto" else
                if (route.mode == AiMode.REVIEW) "AI နှစ်ခု" else
                    settings.ai.profiles.firstOrNull { it.id == route.primary?.profileId }?.label ?: "Custom", fontSize = 12.sp)
            Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
        }
    }
    if (show) AiRouteDialog(task, route, settings.ai, viewModel, chatId, { show = false })
}

@Composable
internal fun AiSettingsScreen(settings: AppSettings, viewModel: AppViewModel, onBack: () -> Unit) {
    val statuses by viewModel.apiStatus.collectAsState()
    val revision by viewModel.routeRevision.collectAsState()
    var edit by remember { mutableStateOf<AiProfile?>(null) }
    var routeTask by remember { mutableStateOf<AiTask?>(null) }
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().testTag("ai-settings"), contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column {
                    Text("AI & APIs", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                    Text("ကိုယ်နှစ်သက်တဲ့ AI ကို နေရာအလိုက်ရွေးပါ", fontSize = 12.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Gemini + NVIDIA", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Auto မှာ အလုပ်မလုပ်တဲ့ model ကနေ နောက် model၊ နောက် API ကို ဆက်စမ်းမယ်။ AI နှစ်ခုနဲ့စစ် ကို ရွေးရင် အဖြေကို ဒုတိယ AI နဲ့ ပြန်စစ်မယ်။", fontSize = 13.sp, lineHeight = 23.sp)
                }
            }
        }
        items(settings.ai.profiles, key = { it.id }) { profile ->
            val ready = remember(revision, profile.id) { viewModel.apiConfigured(profile.id) }
            OutlinedCard(onClick = { edit = profile }, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Key, null,
                        tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(profile.label, fontWeight = FontWeight.SemiBold)
                        Text(if (ready) if (profile.useAsFallback) "Key ရှိတယ် · fallback ဖွင့်ထားတယ်" else "Key ရှိတယ် · ကိုယ်ရွေးမှသုံးမယ်"
                            else "API key ထည့်ရန်", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        statuses[profile.id]?.let { Text(it, fontSize = 11.sp, lineHeight = 18.sp) }
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
        item {
            OutlinedButton({
                edit = AiProfile("api_" + UUID.randomUUID().toString().take(12), "My API", AiProviderKind.CUSTOM,
                    "https://example.com/v1/")
            }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Add API / နောက် key ထည့်မယ်") }
        }
        item { Text("လုပ်ဆောင်ချက်အလိုက် AI", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
        items(AiTask.entries) { task ->
            val route = settings.ai.routes[task] ?: AiRoutePrefs()
            ListItem(headlineContent = { Text(task.label) },
                supportingContent = { Text(if (route.primary == null) route.mode.label else
                    (settings.ai.profiles.firstOrNull { it.id == route.primary.profileId }?.label ?: "Custom") +
                        " · " + route.mode.label, fontSize = 12.sp) },
                trailingContent = { TextButton({ routeTask = task }) { Text("ရွေးမယ်") } })
        }
        item {
            Text("NVIDIA / Claude / DeepSeek text models ကို အသံ AI အဖြစ်သုံးရင် စာဖမ်းခြင်းနဲ့ အသံပြန်ပြောခြင်းကို Gemini သို့မဟုတ် OpenAI က လုပ်ပေးမယ်။ Native Live က Gemini သီးခြားဖြစ်တယ်။",
                fontSize = 12.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    edit?.let { profile -> ApiProfileDialog(profile, settings.ai, viewModel, { edit = null }) }
    routeTask?.let { task -> AiRouteDialog(task, settings.ai.routes[task] ?: AiRoutePrefs(), settings.ai,
        viewModel, null, { routeTask = null }, settingsOnly = true) }
}

@Composable
private fun ApiProfileDialog(initial: AiProfile, config: AiConfiguration, viewModel: AppViewModel, dismiss: () -> Unit) {
    var profile by remember(initial) { mutableStateOf(initial) }
    var key by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(initial.kind == AiProviderKind.CUSTOM) }
    var validation by remember { mutableStateOf("") }
    val busy by viewModel.apiBusy.collectAsState()
    val statuses by viewModel.apiStatus.collectAsState()
    val catalogs by viewModel.apiModels.collectAsState()
    val builtIn = initial.id in setOf("gemini", "nvidia", "openai", "claude", "deepseek")
    AlertDialog(onDismissRequest = dismiss, title = { Text(profile.label) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()).testTag("api-profile"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!builtIn) {
                ChoiceField("Provider", profile.kind.label, AiProviderKind.entries.map { it to it.label }) { kind ->
                    key = ""
                    profile = if (kind == AiProviderKind.CUSTOM) profile.copy(kind = kind, format = ApiFormat.OPENAI_CHAT, baseUrl = "https://example.com/v1/",
                        speechModel = "", speechBackups = emptyList(), transcribeModel = "", liveModel = "", audioEndpoints = false)
                    else builtInAiProfiles().first { it.kind == kind }.copy(id = initial.id, label = profile.label, useAsFallback = false)
                }
                OutlinedTextField(profile.label, { profile = profile.copy(label = it) }, label = { Text("API နာမည် / key alias") }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(key, { key = it }, label = { Text(if (viewModel.apiConfigured(profile.id)) "အသစ်လဲမယ့် API key (မဖြစ်မနေမဟုတ်)" else "API key") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Key ကို ဒီဖုန်းမှာ encrypt လုပ်သိမ်းမယ်။ Backup ထဲ မပါဘူး။", fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("အလိုအလျောက် fallback သုံးမယ်", Modifier.weight(1f), fontSize = 13.sp)
                Switch(profile.useAsFallback, { profile = profile.copy(useAsFallback = it) })
            }
            if (profile.kind in setOf(AiProviderKind.OPENAI, AiProviderKind.CLAUDE, AiProviderKind.DEEPSEEK))
                Text("ဒီ provider သုံးရင် ကိုယ့် API account ရဲ့ usage billing အတိုင်း ကုန်ကျနိုင်တယ်။", fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("သုံးမယ်", Modifier.weight(1f), fontSize = 13.sp)
                Switch(profile.enabled, { profile = profile.copy(enabled = it) })
            }
            val textCatalog = catalogs[profile.id].orEmpty().filterNot {
                profile.format == ApiFormat.GEMINI && (it.contains("tts") || it.contains("live") || it.contains("embedding"))
            }
            ModelField("Text model", profile.textModel, AiPlans.models(profile, AiTask.CONVERSATION) + textCatalog) {
                profile = profile.copy(textModel = it, taskDefaults = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton({ viewModel.testApi(profile, key) }, enabled = profile.id !in busy && (key.isNotBlank() || viewModel.apiConfigured(profile.id))) { Text("Test text") }
                TextButton({ viewModel.loadApiModels(profile) }, enabled = viewModel.apiConfigured(profile.id)) { Text("Models") }
            }
            if (profile.id in busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            statuses[profile.id]?.let { Text(it, fontSize = 12.sp, lineHeight = 21.sp) }
            TextButton({ advanced = !advanced }) { Text(if (advanced) "အသေးစိတ်ပိတ်မယ်" else "အသေးစိတ် · models / endpoint") }
            if (advanced) {
                if (profile.kind == AiProviderKind.CUSTOM) {
                    ChoiceField("API format", profile.format.name, ApiFormat.entries.map { it to it.name }) { profile = profile.copy(format = it) }
                    OutlinedTextField(profile.baseUrl, { profile = profile.copy(baseUrl = it.trim()) }, label = { Text("Base URL") },
                        supportingText = { Text("ဥပမာ https://your-provider.com/v1/") }, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(profile.textBackups.joinToString("\n"), { profile = profile.copy(textBackups = lines(it)) },
                    label = { Text("Text fallback models · အစဉ်လိုက် တစ်ကြောင်းတစ်ခု") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (profile.format == ApiFormat.GEMINI) {
                    ModelField("Gemini TTS model", profile.speechModel, profile.speechBackups + catalogs[profile.id].orEmpty().filter { it.contains("tts") }) {
                        profile = profile.copy(speechModel = it)
                    }
                    OutlinedTextField(profile.speechBackups.joinToString("\n"), { profile = profile.copy(speechBackups = lines(it)) },
                        label = { Text("TTS fallback models") }, modifier = Modifier.fillMaxWidth())
                    ModelField("Native Live model", profile.liveModel, catalogs[profile.id].orEmpty().filter { it.contains("live") }) {
                        profile = profile.copy(liveModel = it)
                    }
                } else if (profile.format == ApiFormat.OPENAI_CHAT && profile.kind in setOf(AiProviderKind.OPENAI, AiProviderKind.CUSTOM)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("STT / TTS endpoints ရှိတယ်", Modifier.weight(1f), fontSize = 12.sp)
                        Switch(profile.audioEndpoints, { profile = profile.copy(audioEndpoints = it) })
                    }
                    if (profile.audioEndpoints) {
                        OutlinedTextField(profile.transcribeModel, { profile = profile.copy(transcribeModel = it) }, label = { Text("Transcription model") })
                        OutlinedTextField(profile.speechModel, { profile = profile.copy(speechModel = it) }, label = { Text("Speech model") })
                        OutlinedTextField(profile.voice, { profile = profile.copy(voice = it) }, label = { Text("Speech voice name") })
                    }
                }
            }
            if (validation.isNotBlank()) Text(validation, color = MaterialTheme.colorScheme.error)
            if (config.profiles.any { it.id == initial.id })
                TextButton({ viewModel.removeApi(profile.id); dismiss() }) { Text(if (builtIn) "API key ဖယ်မယ်" else "ဒီ API ကိုဖယ်မယ်", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton({
            try { validateAiProfile(profile); viewModel.saveApi(profile, key); dismiss() }
            catch (failure: Exception) { validation = failure.message.orEmpty() }
        }, enabled = profile.id !in busy) { Text("သိမ်းမယ်") }
    }, dismissButton = { TextButton(dismiss) { Text("ပိတ်မယ်") } })
}

@Composable
private fun AiRouteDialog(task: AiTask, initial: AiRoutePrefs, config: AiConfiguration, viewModel: AppViewModel,
    chatId: Long?, dismiss: () -> Unit, settingsOnly: Boolean = false) {
    var route by remember { mutableStateOf(initial) }
    var asDefault by remember { mutableStateOf(settingsOnly) }
    val profiles = config.profiles.filter { it.enabled && it.supports(task) }
    val primary = profiles.firstOrNull { it.id == route.primary?.profileId }
    var fallbackId by remember { mutableStateOf(profiles.firstOrNull { it.id != primary?.id && it.useAsFallback }?.id.orEmpty()) }
    var fallbackModel by remember { mutableStateOf("") }
    var reviewerId by remember { mutableStateOf(route.reviewer?.profileId.orEmpty()) }
    val textTask = task !in setOf(AiTask.SPEECH, AiTask.TRANSCRIBE, AiTask.LIVE)
    AlertDialog(onDismissRequest = dismiss, title = { Text(task.label) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (textTask) {
                ChoiceField("Mode", route.mode.label, AiMode.entries.map { it to it.label }) { value ->
                    route = if (value == AiMode.AUTO) AiRoutePrefs() else route.copy(mode = value)
                }
            }
            ChoiceField("ပင်မ API", primary?.label ?: "Auto",
                listOf("" to "Auto") + profiles.map { it.id to it.label }) { id ->
                route = route.copy(primary = id.takeIf(String::isNotBlank)?.let { AiModelRef(it) })
            }
            primary?.let { profile ->
                ModelField("ပင်မ model", route.primary?.model.orEmpty(), AiPlans.models(profile, task)) {
                    route = route.copy(primary = AiModelRef(profile.id, it))
                }
                OutlinedTextField(route.modelBackups.joinToString("\n"), { route = route.copy(modelBackups = lines(it).take(8)) },
                    label = { Text("အဲဒီ API ရဲ့ fallback models · အစဉ်လိုက်") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
            if (route.mode == AiMode.REVIEW && textTask) {
                ChoiceField("ဒုတိယစစ်မယ့် AI", config.profiles.firstOrNull { it.id == reviewerId }?.label ?: "Auto",
                    listOf("" to "Auto") + profiles.filter { it.id != primary?.id }.map { it.id to it.label }) { id ->
                    reviewerId = id
                    route = route.copy(reviewer = id.takeIf(String::isNotBlank)?.let { AiModelRef(it) })
                }
                profiles.firstOrNull { it.id == reviewerId }?.let { profile ->
                    ModelField("Review model", route.reviewer?.model.orEmpty(), AiPlans.models(profile, task)) {
                        route = route.copy(reviewer = AiModelRef(profile.id, it))
                    }
                }
                Text("ဒုတိယ AI မရရင် မူလအဖြေကို ပြမယ်။ မစစ်ရသေးတဲ့အဖြစ် အမှန်အတိုင်းဖော်ပြမယ်။", fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("အလုပ်မလုပ်ရင် fallback သုံးမယ်", Modifier.weight(1f), fontSize = 13.sp)
                Switch(route.allowFallback, { route = route.copy(allowFallback = it) })
            }
            if (route.allowFallback) {
                Text("နောက် API အစဉ်", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                route.fallbacks.forEachIndexed { index, ref ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((index + 1).toString() + ". " + (config.profiles.firstOrNull { it.id == ref.profileId }?.label ?: ref.profileId) +
                            if (ref.model.isBlank()) "" else " · " + ref.model, Modifier.weight(1f), fontSize = 12.sp)
                        IconButton({
                            val list = route.fallbacks.toMutableList()
                            val item = list.removeAt(index); list.add(index - 1, item)
                            route = route.copy(fallbacks = list)
                        }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, "Move earlier", Modifier.size(18.dp)) }
                        IconButton({ route = route.copy(fallbacks = route.fallbacks.filterIndexed { i, _ -> i != index }) }) {
                            Icon(Icons.Default.Close, "Remove fallback", Modifier.size(18.dp))
                        }
                    }
                }
                val backups = profiles.filter { it.id != primary?.id && it.useAsFallback }
                if (backups.isNotEmpty()) {
                    ChoiceField("Fallback API ထပ်ထည့်", profiles.firstOrNull { it.id == fallbackId }?.label ?: "ရွေးပါ",
                        backups.map { it.id to it.label }) { fallbackId = it; fallbackModel = "" }
                    profiles.firstOrNull { it.id == fallbackId }?.let { profile ->
                        ModelField("Fallback ပထမ model", fallbackModel, AiPlans.models(profile, task)) { fallbackModel = it }
                        TextButton({
                            route = route.copy(fallbacks = (route.fallbacks.filterNot { it.profileId == fallbackId } +
                                AiModelRef(fallbackId, fallbackModel)).take(12))
                        }) { Text("အစဉ်ထဲ ထည့်မယ်") }
                    }
                }
                Text("ကျန်တဲ့ enabled fallback API တွေက ဒီအစဉ်နောက်မှာ ဆက်ပါမယ်။ Paid API အတွက် AI & APIs မှာ fallback ကို အရင်ဖွင့်ပါ။",
                    fontSize = 11.sp, lineHeight = 20.sp)
            }
            if (!settingsOnly) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(asDefault, { asDefault = it })
                Text(if (chatId != null) "ဒီလုပ်ဆောင်ချက်ရဲ့ default အဖြစ်သိမ်း\nမရွေးရင် ဒီ chat အတွက်ပဲ" else
                    "ဒီလုပ်ဆောင်ချက်ရဲ့ default အဖြစ်သိမ်း\nမရွေးရင် လက်ရှိ app session အတွက်ပဲ", fontSize = 12.sp)
            }
        }
    }, confirmButton = { TextButton({ viewModel.saveRoute(task, route, asDefault, chatId); dismiss() }) { Text("သုံးမယ်") } },
        dismissButton = { TextButton(dismiss) { Text("ပိတ်မယ်") } })
}

private fun lines(value: String) = value.lines().map(String::trim).filter(String::isNotBlank).distinct()

@Composable
private fun <T> ChoiceField(label: String, selected: String, options: List<Pair<T, String>>, select: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Box {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) {
            Text(selected, Modifier.weight(1f)); Icon(Icons.Default.ExpandMore, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (value, text) -> DropdownMenuItem(text = { Text(text) },
                onClick = { select(value); expanded = false }) }
        }
    }
}

@Composable
private fun ModelField(label: String, value: String, models: List<String>, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(value, select, label = { Text(label) }, supportingText = {
            if (value.isBlank()) Text("လွတ်ထားရင် built-in model သုံးမယ်", fontSize = 10.sp)
        }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = {
            IconButton({ expanded = true }) { Icon(Icons.Default.ExpandMore, "Model choices") }
        })
        DropdownMenu(expanded, { expanded = false }) {
            models.distinct().filter(String::isNotBlank).take(80).forEach { model -> DropdownMenuItem(text = { Text(model, fontSize = 12.sp) },
                onClick = { select(model); expanded = false }) }
        }
    }
}
