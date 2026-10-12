@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myothuonion.languagetalk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.myothuonion.languagetalk.model.*

@Composable
internal fun SavedReaderScreen(viewModel: ReaderViewModel,onBack: () -> Unit,onRead: (String,Int) -> Unit,
    onPractice: (String,String,String,Int) -> Unit) {
    BackHandler(onBack = onBack)
    val state by viewModel.learning.collectAsState()
    val ui by viewModel.ui.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var edit by remember { mutableStateOf<ReaderAnnotation?>(null) }
    var note by remember { mutableStateOf("") }
    val annotationMatches = state.annotations.asReversed().filter {
        (filter == "ALL" || filter == "NOTES" && it.kind == "NOTE" || filter == "PASSAGES" && it.kind != "NOTE") &&
            (query.isBlank() || (it.quote + " " + it.note + " " + it.context).contains(query,true))
    }
    val words = state.cards.filter { it.kind == "WORD" && (query.isBlank() || (it.title+" "+it.meaning).contains(query,true)) }
    val bookmarks = state.books.flatMap { book -> book.bookmarks.map { page -> book to page } }
        .filter { query.isBlank() || (it.first.title+" "+(it.second+1)).contains(query,true) }
    LazyColumn(Modifier.fillMaxSize().testTag("reader-collection"),contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.statusBarsPadding(),verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Default.ArrowBack,"Back") }
                GlassIconTile(Icons.Default.Bookmarks)
                Text("သိမ်းထားတာတွေ",Modifier.padding(start = 12.dp),style = MaterialTheme.typography.headlineSmall)
            }
        }
        item { OutlinedTextField(query,{ query = it },Modifier.fillMaxWidth(),label = { Text("ဝေါဟာရ၊ ဝါကျ၊ မှတ်စု ရှာမယ်") },singleLine = true) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("ALL" to "အားလုံး","WORDS" to "ဝေါဟာရ","PASSAGES" to "စာသား","PAGES" to "စာမျက်နှာ","NOTES" to "မှတ်စု").forEach { (id,label) ->
                    FilterChip(filter == id,{ filter = id },label = { Text(label) })
                }
            }
        }
        if (ui.status.isNotBlank()) item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ui.status,Modifier.weight(1f),style = MaterialTheme.typography.bodySmall)
            TextButton({ viewModel.undo() }) { Text("Undo") }
        } }
        if (filter in setOf("ALL","PASSAGES","NOTES")) items(annotationMatches,key = { it.id }) { value ->
            val book = state.books.firstOrNull { it.id == value.bookId }
            OutlinedCard {
                Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text((book?.title ?: value.bookId) + " · စာမျက်နှာ " + (value.page+1),
                        style = MaterialTheme.typography.labelMedium,color = MaterialTheme.colorScheme.primary)
                    Text(value.quote,fontWeight = if (value.kind == "BOLD") FontWeight.Bold else FontWeight.Normal)
                    if (value.note.isNotBlank()) Text(value.note)
                    Row {
                        TextButton({ onRead(value.bookId,value.page) },enabled = book != null) { Text("မူရင်း") }
                        TextButton({ onPractice(value.quote,value.context,book?.title.orEmpty(),value.page) }) { Text("လေ့ကျင့်မယ်") }
                        IconButton({ edit = value; note = value.note }) { Icon(Icons.Default.EditNote,"Edit note") }
                        IconButton({ viewModel.removeAnnotation(value) }) { Icon(Icons.Default.DeleteOutline,"Remove saved passage") }
                    }
                }
            }
        }
        if (filter in setOf("ALL","WORDS")) items(words,key = { it.id }) { word ->
            OutlinedCard {
                Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(word.title,style = MaterialTheme.typography.titleLarge)
                    Text(word.meaning)
                    Text(word.bookTitle + " · စာမျက်နှာ " + (word.page+1),style = MaterialTheme.typography.labelMedium)
                    Row {
                        TextButton({ onRead(word.bookId,word.page) }) { Text("မူရင်း") }
                        TextButton({ onPractice(word.title,word.context,word.bookTitle,word.page) }) { Text("လေ့ကျင့်မယ်") }
                    }
                }
            }
        }
        if (filter in setOf("ALL","PAGES")) items(bookmarks,key = { it.first.id+"-"+it.second }) { (book,page) ->
            OutlinedCard {
                Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bookmark,null,tint = MaterialTheme.colorScheme.primary)
                    Text(book.title + "\nစာမျက်နှာ " + (page+1),Modifier.weight(1f).padding(horizontal = 12.dp))
                    TextButton({ onRead(book.id,page) }) { Text("ဖတ်မယ်") }
                }
            }
        }
        if (annotationMatches.isEmpty() && words.isEmpty() && bookmarks.isEmpty()) item {
            Text("Reader မှာ စာသားကို ဖိရွေးပြီး သိမ်းပါ။ သိမ်းထားတာတွေ ဒီမှာပြမယ်။",Modifier.padding(vertical = 24.dp))
        }
    }
    edit?.let { value ->
        AlertDialog(onDismissRequest = { edit = null },title = { Text("မှတ်စု ပြင်မယ်") },
            text = { OutlinedTextField(note,{ note = it.take(4000) },Modifier.fillMaxWidth(),label = { Text("မှတ်စု") }) },
            confirmButton = { TextButton({ viewModel.updateNote(value.id,note); edit = null }) { Text("သိမ်းမယ်") } },
            dismissButton = { TextButton({ edit = null }) { Text("ပိတ်") } })
    }
}
