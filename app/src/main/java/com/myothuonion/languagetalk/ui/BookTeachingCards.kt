package com.myothuonion.languagetalk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myothuonion.languagetalk.model.*

@Composable
internal fun BookTeachingCards(value: PageTeaching, onListen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(value.overview, lineHeight = 28.sp)
        value.sentences.forEach { sentence ->
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookTeachingLine(sentence.korean, onListen)
                    Text(sentence.meaning, lineHeight = 28.sp)
                }
            }
        }
        value.vocabulary.forEach { word ->
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookTeachingLine(word.word, onListen)
                    Text(word.meaning)
                    if (word.usage.isNotBlank()) Text(word.usage, lineHeight = 27.sp)
                }
            }
        }
        value.grammar.forEach { rule ->
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(rule.pattern, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text("အဓိပ္ပာယ် · " + rule.meaning, lineHeight = 28.sp)
                    Text("ပေါင်းစပ်ပုံ · " + rule.construction, lineHeight = 28.sp)
                    if (rule.originalExample.isNotBlank()) BookTeachingLine(rule.originalExample, onListen)
                    if (rule.explanation.isNotBlank()) Text(rule.explanation, lineHeight = 28.sp)
                    if (rule.commonError.isNotBlank()) Text("မှားလွယ်သောအချက် · " + rule.commonError, lineHeight = 28.sp)
                    if (rule.practice.isNotBlank()) Text("AI ထပ်လေ့ကျင့်ရန် · " + rule.practice, lineHeight = 28.sp)
                }
            }
        }
        value.notes.forEach { Text(it, lineHeight = 28.sp) }
        value.exercises.forEach { BookTeachingExercise(it.question, it.hint, it.answer, false) }
        value.addedPractice.forEach { BookTeachingExercise(it.question, it.hint, it.answer, true) }
        value.unreadable.forEach { Text("ဖတ်မရသေးသောစာသား · " + it, color = MaterialTheme.colorScheme.error) }
        Text("မူရင်းစာသားကို အခြေခံထားသော AI ရှင်းပြချက် · အသံသည် AI အသံ", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BookTeachingLine(text: String, onListen: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontSize = 21.sp, lineHeight = 31.sp)
        if (text.any { it in '\uAC00'..'\uD7A3' }) IconButton({ onListen(text) }) {
            Icon(Icons.Default.VolumeUp, "Listen to Korean example")
        }
    }
}

@Composable
private fun BookTeachingExercise(question: String, hint: String, answer: String, added: Boolean) {
    var hintOpen by remember(question) { mutableStateOf(false) }
    var answerOpen by remember(question) { mutableStateOf(false) }
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (added) "AI ထပ်ဖန်တီးသော လေ့ကျင့်ခန်း" else "စာအုပ်လေ့ကျင့်ခန်း", color = MaterialTheme.colorScheme.primary)
            Text(question, lineHeight = 28.sp)
            TextButton({ hintOpen = !hintOpen }) { Text("အကူအညီ") }
            if (hintOpen) Text(hint)
            TextButton({ answerOpen = !answerOpen }) { Text(if (answerOpen) "အဖြေဖျောက်" else "အဖြေကြည့်") }
            if (answerOpen) Text(answer, lineHeight = 28.sp)
        }
    }
}
