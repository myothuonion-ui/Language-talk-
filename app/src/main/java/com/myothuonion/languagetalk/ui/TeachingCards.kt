package com.myothuonion.languagetalk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myothuonion.languagetalk.model.*

@Composable
internal fun PageTeachingCards(value: PageTeaching, onListen: (String) -> Unit,
    onSaveWord: ((TeachingWord) -> Unit)? = null, onSource: ((TeachingBlock) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TeachingHeading("ဒီစာမျက်နှာမှာ သင်ရမှာ", Icons.Default.AutoAwesome)
        Text(value.overview, style = MaterialTheme.typography.bodyLarge)
        if (value.blocks.isNotEmpty()) {
            TeachingHeading("ဝါကျအလိုက် အဓိပ္ပာယ်", Icons.Default.Translate)
            value.blocks.forEach { block ->
                OutlinedCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(block.source, Modifier.weight(1f), fontSize = 20.sp)
                            IconButton({ onListen(block.source) }) { Icon(Icons.Default.VolumeUp,"Listen to Korean sentence") }
                        }
                        Text(block.meaning, style = MaterialTheme.typography.bodyLarge)
                        if (block.note.isNotBlank()) Text(block.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (onSource != null) TextButton({ onSource(block) }) { Text("မူရင်းနေရာ ကြည့်မယ်") }
                    }
                }
            }
        }
        if (value.vocabulary.isNotEmpty()) {
            TeachingHeading("ဝေါဟာရ", Icons.Default.BookmarkBorder)
            value.vocabulary.forEach { word ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(word.korean,fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Text(word.meaning)
                        if (word.usage.isNotBlank()) Text(word.usage,style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton({ onListen(word.korean) }) { Icon(Icons.Default.VolumeUp,"Listen to " + word.korean) }
                    if (onSaveWord != null) IconButton({ onSaveWord(word) }) { Icon(Icons.Default.BookmarkAdd,"Save " + word.korean) }
                }
                HorizontalDivider()
            }
        }
        if (value.grammar.isNotEmpty()) {
            TeachingHeading("Grammar ကို နားလည်အောင်", Icons.Default.School)
            value.grammar.forEach { grammar ->
                OutlinedCard {
                    Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(grammar.pattern,fontSize = 21.sp,fontWeight = FontWeight.SemiBold,color = MaterialTheme.colorScheme.primary)
                        Text(grammar.meaning)
                        if (grammar.formation.isNotBlank()) { Text("တည်ဆောက်ပုံ",fontWeight = FontWeight.Medium); Text(grammar.formation) }
                        if (grammar.example.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(grammar.example,Modifier.weight(1f),fontSize = 20.sp)
                                IconButton({ onListen(grammar.example) }) { Icon(Icons.Default.VolumeUp,"Listen to grammar example") }
                            }
                            Text(grammar.exampleMeaning)
                        }
                        if (grammar.commonMistake.isNotBlank()) {
                            Text("မှားလွယ်တဲ့အချက်",fontWeight = FontWeight.Medium)
                            Text(grammar.commonMistake,color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (value.culture.isNotBlank()) { TeachingHeading("ယဉ်ကျေးမှုမှတ်ချက်",Icons.Default.Public); Text(value.culture) }
        if (value.exercises.isNotEmpty()) {
            TeachingHeading("စာအုပ်လေ့ကျင့်ခန်း",Icons.Default.EditNote)
            value.exercises.forEachIndexed { index, exercise ->
                key(index,exercise.question) {
                    var hint by remember { mutableStateOf(false) }
                    var answer by remember { mutableStateOf(false) }
                    OutlinedCard {
                        Column(Modifier.padding(14.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(exercise.question,fontSize = 18.sp)
                            Row {
                                TextButton({ hint = !hint }) { Text(if (hint) "Hint ဖျောက်" else "Hint") }
                                TextButton({ answer = !answer }) { Text(if (answer) "အဖြေဖျောက်" else "အဖြေကြည့်") }
                            }
                            if (hint) Text(exercise.hint)
                            if (answer) Text(exercise.answer.ifBlank { "အဖြေကို သေချာမသတ်မှတ်နိုင်သေးပါ။" })
                        }
                    }
                }
            }
        }
        if (value.addedPractice.isNotBlank()) {
            TeachingHeading("ထပ်လေ့ကျင့်ရန်",Icons.Default.Forum)
            Text("AI ထပ်ဖန်တီးထားသော လေ့ကျင့်ခန်း",style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(value.addedPractice)
        }
        if (value.unreadable.isNotEmpty()) {
            Text("မရှင်းလင်းသော မူရင်းနေရာများ",fontWeight = FontWeight.Medium)
            value.unreadable.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun TeachingHeading(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassIconTile(icon,Modifier.size(34.dp))
        Text(text,fontWeight = FontWeight.SemiBold,style = MaterialTheme.typography.titleMedium)
    }
}
