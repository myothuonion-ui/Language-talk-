package com.myothuonion.languagetalk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.myothuonion.languagetalk.data.ReaderAnnotation

@Composable
internal fun ReaderCollectionScreen(viewModel: ReaderViewModel, onBack: () -> Unit, onRead: (String, Int) -> Unit,
    onPractice: (String) -> Unit) {
    val learning by viewModel.learning.collectAsState()
    val annotations by viewModel.annotations.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var edit by remember { mutableStateOf<ReaderAnnotation?>(null) }
    var note by remember { mutableStateOf("") }
    var unavailable by remember { mutableStateOf(false) }
    fun read(id: String, page: Int) { if (learning.books.any { it.id == id }) onRead(id, page) else unavailable = true }
    val visible = annotations.filter {
        (filter == "ALL" || filter == "NOTES" && (it.kind == "NOTE" || it.note.isNotBlank()) || filter == "WORDS" && it.savedType == "WORD" ||
            filter == "SENTENCES" && it.savedType != "WORD" || filter == "MARKS" && it.kind in setOf("HIGHLIGHT", "UNDERLINE", "BOLD")) &&
            (query.isBlank() || listOf(it.quote, it.note, it.context, it.bookTitle).any { value -> value.contains(query, true) })
    }
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().testTag("reader-collection"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Text("သိမ်းထားတာများ", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("စာသား၊ မှတ်စု၊ စာအုပ်နာမည် ရှာ") })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to "အားလုံး", "WORDS" to "ဝေါဟာရ", "SENTENCES" to "ဝါကျ", "PAGES" to "စာမျက်နှာ", "NOTES" to "မှတ်စု", "MARKS" to "မျဉ်းသား").forEach { (id, label) ->
                    FilterChip(filter == id, { filter = id }, label = { Text(label) })
                }
            }
        }
        if (filter != "PAGES") items(visible, key = { it.id }) { value ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(value.quote, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = if (value.kind == "BOLD") FontWeight.Bold else FontWeight.Normal)
                    Text("${value.bookTitle} · စာမျက်နှာ ${value.page + 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (value.note.isNotBlank()) Text(value.note, lineHeight = 27.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton({ read(value.bookId, value.page) }) { Text("မူရင်းနေရာ") }
                        TextButton({ viewModel.practiceSaved(value, onPractice) }) { Text("ပြန်လေ့ကျင့်") }
                        TextButton({ edit = value; note = value.note }) { Text("မှတ်စု/အရောင်") }
                        TextButton({ viewModel.deleteAnnotation(value.id) }) { Text("ဖျက်") }
                    }
                }
            }
        }
        if (filter in setOf("ALL", "WORDS")) items(learning.cards.filter { it.kind == "WORD" && (query.isBlank() ||
            (it.title + it.meaning + it.context + it.bookTitle).contains(query, true)) }, key = { "card-" + it.id }) { card ->
            OutlinedCard { Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(card.title, fontSize = 21.sp); Text(card.meaning, lineHeight = 27.sp)
                Text("${card.bookTitle} · စာမျက်နှာ ${card.page + 1}", fontSize = 12.sp)
                Row { TextButton({ read(card.bookId, card.page) }) { Text("မူရင်းနေရာ") }; TextButton({ onPractice(card.id) }) { Text("ပြန်လေ့ကျင့်") } }
            } }
        }
        if (filter in setOf("ALL", "PAGES")) learning.books.forEach { book ->
            items(book.bookmarks.filter { query.isBlank() || book.title.contains(query, true) || (it + 1).toString() == query }, key = { "bookmark-" + book.id + ":" + it }) { page ->
                OutlinedCard { Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("${book.title} · စာမျက်နှာ ${page + 1}")
                    TextButton({ read(book.id, page) }) { Text("စာမျက်နှာဖွင့်") }
                } }
            }
        }
        if (visible.isEmpty() && filter != "PAGES") item { Text("ဖတ်ရင်း စာသားကို ဖိရွေးပြီး သိမ်းနိုင်ပါတယ်။", fontSize = 13.sp) }
    }
    edit?.let { value -> AlertDialog(onDismissRequest = { edit = null }, title = { Text("မှတ်စုနဲ့ အရောင်") },
        text = { Column {
            OutlinedTextField(note, { note = it.take(4000) }, label = { Text("မှတ်စု") })
            Row { listOf(0xFFFFD666L, 0xFF7BDDB1L, 0xFF91C9FFL, 0xFFFFAFCFL).forEach { color ->
                TextButton({ viewModel.editAnnotation(value, note, color); edit = null }) { Text("●", color = androidx.compose.ui.graphics.Color(color)) }
            } }
        } }, confirmButton = { TextButton({ viewModel.editAnnotation(value, note); edit = null }) { Text("သိမ်း") } },
        dismissButton = { TextButton({ edit = null }) { Text("ပိတ်") } }) }
    if (unavailable) AlertDialog(onDismissRequest = { unavailable = false }, title = { Text("စာအုပ်ကို ပြန် Import လုပ်ပါ") },
        text = { Text("အမှတ်သားထားတာတွေ ရှိနေပါတယ်။ မူရင်း PDF ဖိုင်ကို ပြန်တင်ရင် ပြန်ချိတ်ပေးမယ်။") },
        confirmButton = { TextButton({ unavailable = false }) { Text("ပိတ်") } })
}
