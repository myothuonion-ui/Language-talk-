@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myothuonion.languagetalk.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myothuonion.languagetalk.model.ReaderBook
import com.myothuonion.languagetalk.util.PdfWord
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun BookshelfScreen(viewModel: ReaderViewModel, onRead: (String) -> Unit, onStudy: (String) -> Unit) {
    val learning by viewModel.learning.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.import(it) } }
    var remove by remember { mutableStateOf<ReaderBook?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("bookshelf"), contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR LIBRARY", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Text("My Books", fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                    Text("ကိုယ့်အရှိန်နဲ့ ဖတ်ပြီး လေ့လာမယ်", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton({ picker.launch(arrayOf("application/pdf")) }, enabled = !ui.loading,
                    modifier = Modifier.testTag("import-pdf")) { Icon(Icons.Default.Add, "Import PDF") }
            }
        }
        if (ui.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            OutlinedButton({ picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth(), enabled = !ui.loading) {
                Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Import PDF")
            }
        }
        items(learning.books, key = { it.id }) { book ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(86.dp).height(126.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer).testTag("read-" + book.id).clickable { onRead(book.id) }) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(if (book.assetId.isNotBlank()) if (book.id.contains("beginner")) "BEGINNER" else "INTERMEDIATE" else "PDF",
                            fontSize = 9.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(book.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("စာမျက်နှာ " + (book.page + 1) + " / " + book.pageCount, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { (book.page + 1).toFloat() / book.pageCount },
                        modifier = Modifier.fillMaxWidth().height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ onRead(book.id) }) { Text(if (book.page == 0) "ဖတ်မယ်" else "ဆက်ဖတ်မယ်") }
                        if (book.assetId.isNotBlank()) TextButton({ onStudy(book.id) },
                            modifier = Modifier.testTag(if (book.id == "ttmik-beginner") "open-books" else "open-intermediate")) { Text("သင်ခန်းစာ") }
                        else IconButton({ remove = book }) { Icon(Icons.Default.DeleteOutline, "Remove book") }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        }
        item { Text("စာလုံးဖိရွေးပြီး မြန်မာလို အဓိပ္ပာယ်ရှာနိုင်မယ်။ ဖတ်နေရာနဲ့ Bookmark ကို အလိုအလျောက်သိမ်းမယ်။",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    remove?.let { book -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("စာအုပ်ဖယ်မလား?") },
        text = { Text("App ထဲက PDF မိတ္တူကို ဖယ်မယ်။ သိမ်းထားတဲ့ဝေါဟာရတွေ ဆက်ရှိမယ်။") },
        confirmButton = { TextButton({ viewModel.remove(book); remove = null }) { Text("ဖယ်မယ်") } },
        dismissButton = { TextButton({ remove = null }) { Text("မဖယ်တော့ပါ") } }) }
}
