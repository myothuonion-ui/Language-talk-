@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.myothuonion.languagetalk.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myothuonion.languagetalk.data.ReplaySession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun audioTime(ms: Long) = "%02d:%02d".format(ms / 60000, (ms / 1000) % 60)

@Composable
internal fun ReplayScreen(viewModel: ReplayViewModel, onLegacy: () -> Unit, onRead: (String, Int) -> Unit) {
    val sessions by viewModel.sessions.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val clips by viewModel.clips.collectAsState()
    val speaker by viewModel.speaker.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val notice by viewModel.notice.collectAsState()
    var removing by remember { mutableStateOf<ReplaySession?>(null) }
    val session = sessions.firstOrNull { it.id == selected }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && selected != null) viewModel.export(uri, selected!!)
    }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }
    BackHandler(enabled = selected != null) { viewModel.open(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("replays-screen"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                if (selected != null) IconButton({ viewModel.open(null) }) { Icon(Icons.Default.ArrowBack, "All recordings") }
                Column(Modifier.weight(1f)) {
                    Text("VOICE JOURNAL", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Text(if (session == null) "ပြန်နားထောင်မယ်" else session.title, fontSize = if (session == null) 27.sp else 19.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (session == null) "မင်းအသံနဲ့ AI အသံ · စကားဝိုင်းအလိုက်" else
                        SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(session.createdAt)),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (session == null) {
            item { TextButton(onLegacy) { Icon(Icons.Default.History, null); Spacer(Modifier.width(6.dp)); Text("အရင်အသံနဲ့ Review များ") } }
            if (sessions.isEmpty()) item {
                OutlinedCard(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Headphones, null, Modifier.size(28.dp))
                    Text("စကားဝိုင်းအသံတွေ ဒီမှာ ရှိမယ်", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("Me → Settings မှာ အသံမှတ်တမ်းဖွင့်ထားရင် Practice နဲ့ Live မှ မင်းအသံနဲ့ AI အသံကို အလိုအလျောက် သိမ်းမယ်။")
                    Text("အရင်က မသိမ်းခဲ့တဲ့ AI အသံကို မူရင်းအတိုင်း ပြန်ယူလို့မရပါ။", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
            }
            items(sessions, key = { it.id }) { saved ->
                OutlinedCard(onClick = { viewModel.open(saved.id) }, shape = RoundedCornerShape(20.dp), modifier = Modifier.testTag("replay-session")) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Headphones, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(saved.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(saved.updatedAt)) +
                                if (saved.page >= 0) " · စာမျက်နှာ ${saved.page + 1}" else "",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        } else {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("BOTH" to "နှစ်ယောက်စလုံး", "USER" to "ငါ့အသံ", "AI" to "AI အသံ").forEach { (id, title) ->
                        FilterChip(speaker == id, { viewModel.speaker(id) }, label = { Text(title) }, modifier = Modifier.testTag("replay-filter-$id"))
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Slider(playback.positionMs.toFloat().coerceAtMost(playback.durationMs.toFloat()),
                            { viewModel.seek(it.toLong()) }, valueRange = 0f..maxOf(1f, playback.durationMs.toFloat()),
                            enabled = playback.durationMs > 0, modifier = Modifier.testTag("replay-seek"))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(audioTime(playback.positionMs), fontSize = 12.sp)
                            Text(audioTime(playback.durationMs), fontSize = 12.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            IconButton({ viewModel.seek(playback.positionMs - 5000) }) { Icon(Icons.Default.Replay5, "Back 5 seconds") }
                            FilledIconButton(viewModel::playPause, enabled = playback.durationMs > 0 && !playback.preparing,
                                modifier = Modifier.size(56.dp).testTag("replay-play")) {
                                if (playback.preparing) CircularProgressIndicator(Modifier.size(22.dp))
                                else Icon(if (playback.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play / pause")
                            }
                            IconButton({ viewModel.seek(playback.positionMs + 5000) }) { Icon(Icons.Default.Forward5, "Forward 5 seconds") }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(.5f, .75f, 1f, 1.25f).forEach { speed -> FilterChip(playback.speed == speed,
                                { viewModel.speed(speed) }, label = { Text("${speed}×") }, modifier = Modifier.testTag("replay-speed-$speed")) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(viewModel::markA, enabled = playback.durationMs > 0, modifier = Modifier.testTag("replay-mark-A")) { Text("A · " + (playback.loopStart?.let(::audioTime) ?: "ရွေး")) }
                            OutlinedButton(viewModel::markB, enabled = playback.loopStart != null, modifier = Modifier.testTag("replay-mark-B")) { Text("B · " + (playback.loopEnd?.let(::audioTime) ?: "ရွေး")) }
                            if (playback.loopStart != null) TextButton(viewModel::clearLoop) { Text("Loop ပိတ်") }
                        }
                        if (playback.loopEnd != null) Text("A–B ကို ထပ်တလဲလဲ နားထောင်နေမယ်", fontSize = 12.sp)
                        if (clips.any { it.audioName.isBlank() }) Text("အချို့စာကြောင်းအတွက် မူရင်းအသံ မသိမ်းထားပါ။", fontSize = 12.sp)
                        playback.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                }
            }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ export.launch("language-talk-session-${session.createdAt}.zip") }) { Icon(Icons.Default.IosShare, null); Spacer(Modifier.width(6.dp)); Text("အသံနဲ့ စာ ထုတ်မယ်") }
                if (session.bookId.isNotBlank() && session.page >= 0) TextButton({ viewModel.stop(); onRead(session.bookId, session.page) }) { Text("မူရင်းစာမျက်နှာ") }
                TextButton({ removing = session }) { Icon(Icons.Default.DeleteOutline, null); Text("ဖျက်မယ်") }
            } }
            if (notice.isNotBlank()) item { Text(notice, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
            items(clips.filter { speaker == "BOTH" || it.speaker == speaker }, key = { it.id }) { clip ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor =
                    if (playback.clipId == clip.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().testTag("replay-clip-${clip.speaker}").clickable { viewModel.seekClip(clip) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (clip.speaker == "USER") "မင်း" else "AI", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            Text(if (clip.audioName.isBlank()) "အသံမရှိ" else audioTime(clip.durationMs), fontSize = 12.sp)
                        }
                        Text(clip.text, fontSize = 17.sp, lineHeight = 28.sp)
                        if (clip.speaker == "AI" && clip.origin == "GENERATED") Text("စာသားကို AI ဖတ်ပြထားတဲ့အသံ", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (clip.audioName.isNotBlank()) Text("နှိပ်ပြီး ဒီစာကြောင်းကို နားထောင်မယ်", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    removing?.let { saved -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("အသံမှတ်တမ်း ဖျက်မလား?") },
        text = { Text("ဒီစကားဝိုင်းရဲ့ အသံဖိုင်နဲ့ စာမှတ်တမ်းတွေကို ဖျက်မယ်။") },
        confirmButton = { TextButton({ viewModel.delete(saved.id); removing = null }) { Text("ဖျက်မယ်") } },
        dismissButton = { TextButton({ removing = null }) { Text("မဖျက်တော့ပါ") } }) }
}
