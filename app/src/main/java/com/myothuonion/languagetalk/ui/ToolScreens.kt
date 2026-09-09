@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myothuonion.languagetalk.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.myothuonion.languagetalk.model.KoreanNameCandidate

@Composable
internal fun NameStudioScreen(
    state: NameStudioState,
    onBack: () -> Unit,
    onGenerate: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item { ToolTopBar("Korean Name Studio", onBack) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(
                    Modifier.background(Brush.linearGradient(listOf(Color(0xFF352064), Color(0xFF123F46)))).padding(19.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Mint.copy(alpha = .2f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Palette, null, tint = Mint) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("မြန်မာနာမည် → 한국 이름", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("အသံအနီးဆုံး Hangul နာမည် ၃ ခုနဲ့ unique identity cards", color = Color.White.copy(alpha = .68f), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("မြန်မာနာမည်") },
                        placeholder = { Text("ဥပမာ—မျိုးမင်းသူ") },
                        singleLine = true,
                        shape = RoundedCornerShape(17.dp)
                    )
                    Button(
                        onClick = { onGenerate(name) },
                        enabled = name.isNotBlank() && !state.isLoading,
                        modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (state.isLoading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isLoading) "နာမည်နဲ့ design စဉ်းစားနေသည်…" else "Create Korean name cards")
                    }
                }
            }
        }
        state.error?.let { error ->
            item { ToolError(error) }
        }
        state.result?.let { result ->
            item {
                Column {
                    Text("${result.originalName} အတွက် ရွေးချယ်စရာ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "အသံတူမှုကိုဦးစားပေးစီထားပါတယ် · ${result.activeModel}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
            itemsIndexed(result.candidates) { index, candidate ->
                KoreanNameCard(candidate, index)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun KoreanNameCard(candidate: KoreanNameCandidate, index: Int) {
    val primary = colorFromHex(candidate.primaryColor, listOf(Mint, Coral, Color(0xFF66A6FF))[index % 3])
    val secondary = colorFromHex(candidate.secondaryColor, listOf(Violet, Color(0xFFFFB547), Color(0xFFC471ED))[index % 3])
    var details by rememberSaveable(candidate.hangul) { mutableStateOf(false) }
    var promptVisible by rememberSaveable(candidate.hangul) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(330.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF090B16), secondary.copy(alpha = .42f), primary.copy(alpha = .27f))))
        ) {
            CardLightPattern(candidate.lightPattern, candidate.layoutStyle, primary, secondary)
            when (candidate.layoutStyle) {
                "crest" -> {
                    Column(Modifier.fillMaxSize().padding(22.dp)) {
                        Text("KOREAN IDENTITY // 0${index + 1}", color = primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                        Spacer(Modifier.height(19.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AnimalEmblem(candidate.animal, primary, Modifier.size(112.dp))
                            Spacer(Modifier.width(17.dp))
                            Column {
                                Text(candidate.hangul, color = Color.White, fontSize = 43.sp, fontWeight = FontWeight.Black)
                                Text(candidate.romanization.uppercase(), color = Color.White.copy(alpha = .58f), fontSize = 12.sp, letterSpacing = 1.4.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        NameCardFooter(candidate, primary)
                    }
                }
                "diagonal" -> {
                    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.End) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("0${index + 1}", color = secondary, fontSize = 52.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.weight(1f))
                            AnimalEmblem(candidate.animal, primary, Modifier.size(108.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Text(candidate.hangul, color = Color.White, fontSize = 47.sp, fontWeight = FontWeight.Black)
                        Text(candidate.vibe, color = primary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(13.dp))
                        NameCardFooter(candidate, primary)
                    }
                }
                else -> {
                    Column(
                        Modifier.fillMaxSize().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("NAME ORBIT // 0${index + 1}", color = primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        AnimalEmblem(candidate.animal, primary, Modifier.size(126.dp))
                        Text(candidate.hangul, color = Color.White, fontSize = 45.sp, fontWeight = FontWeight.Black)
                        Text(candidate.romanization, color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        NameCardFooter(candidate, primary)
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(candidate.myanmarPronunciation, fontWeight = FontWeight.Bold)
                    Text("Naturalness ${candidate.naturalnessScore}/100 · ${candidate.animal.replaceFirstChar { it.uppercase() }}", color = primary, fontSize = 12.sp)
                }
                AssistChip(onClick = { details = !details }, label = { Text(if (details) "Hide details" else "Meaning & sound") })
            }
            AnimatedVisibility(details) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IdentityDetail("အသံကွာခြားချက်", candidate.soundNotes, VioletLight)
                    IdentityDetail("Possible Hanja-inspired meaning", candidate.hanjaInspiredMeaning, Mint)
                    IdentityDetail("Animal symbolism", candidate.animalSymbolism, primary)
                    Text(
                        "မှတ်ချက်—Hangul အသံရေးပုံတစ်ခုတည်းနဲ့ တိကျတဲ့နာမည်အဓိပ္ပာယ် မသတ်မှတ်နိုင်ပါ။ အဓိပ္ပာယ်ပိုင်းက creative Hanja-inspired interpretation ဖြစ်ပါတယ်။",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .15f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("External AI image prompt", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                TextButton(onClick = { promptVisible = !promptVisible }) { Text(if (promptVisible) "Hide" else "Show") }
                IconButton(onClick = { clipboard.setText(AnnotatedString(candidate.externalImagePrompt)) }) {
                    Icon(Icons.Default.ContentCopy, "Copy prompt", tint = primary)
                }
            }
            AnimatedVisibility(promptVisible) {
                Text(
                    candidate.externalImagePrompt,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NameCardFooter(candidate: KoreanNameCandidate, accent: Color) {
    Column(Modifier.fillMaxWidth()) {
        Text(candidate.vibe.uppercase(), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text(candidate.motto, color = Color.White.copy(alpha = .86f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CardLightPattern(pattern: String, layout: String, primary: Color, secondary: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val step = size.width / 8f
        if (layout == "diagonal") {
            repeat(10) { i ->
                drawLine(primary.copy(alpha = .1f), Offset(-size.height + i * step * 2, size.height), Offset(i * step * 2, 0f), 2f)
            }
        } else if (layout == "crest") {
            repeat(5) { i ->
                drawCircle(secondary.copy(alpha = .09f), radius = size.minDimension * (.15f + i * .09f), center = Offset(size.width * .24f, size.height * .46f), style = Stroke(2f))
            }
        } else {
            repeat(4) { i ->
                drawCircle(primary.copy(alpha = .1f), radius = size.minDimension * (.18f + i * .1f), center = center, style = Stroke(2f))
            }
        }
        val seed = pattern.hashCode().toUInt().toLong()
        repeat(22) { i ->
            val x = ((seed + i * 83L) % 997L).toFloat() / 997f * size.width
            val y = ((seed + i * 137L) % 991L).toFloat() / 991f * size.height
            drawCircle(if (i % 2 == 0) primary.copy(alpha = .28f) else secondary.copy(alpha = .22f), 1.5f + (i % 3), Offset(x, y))
        }
    }
}

@Composable
private fun AnimalEmblem(animal: String, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * .045f, cap = StrokeCap.Round)
        val glow = accent.copy(alpha = .2f)
        drawCircle(glow, size.minDimension * .47f)
        drawCircle(accent.copy(alpha = .42f), size.minDimension * .39f, style = Stroke(size.minDimension * .018f))
        when (animal.lowercase()) {
            "fox" -> {
                val face = Path().apply {
                    moveTo(size.width * .2f, size.height * .22f)
                    lineTo(size.width * .38f, size.height * .35f)
                    lineTo(size.width * .5f, size.height * .76f)
                    lineTo(size.width * .62f, size.height * .35f)
                    lineTo(size.width * .8f, size.height * .22f)
                    lineTo(size.width * .7f, size.height * .68f)
                    lineTo(size.width * .5f, size.height * .84f)
                    lineTo(size.width * .3f, size.height * .68f)
                    close()
                }
                drawPath(face, accent.copy(alpha = .18f))
                drawPath(face, accent, style = stroke)
                drawCircle(Color.White, size.minDimension * .025f, Offset(size.width * .39f, size.height * .56f))
                drawCircle(Color.White, size.minDimension * .025f, Offset(size.width * .61f, size.height * .56f))
            }
            "wolf" -> {
                val head = Path().apply {
                    moveTo(size.width * .22f, size.height * .27f)
                    lineTo(size.width * .36f, size.height * .13f)
                    lineTo(size.width * .43f, size.height * .4f)
                    lineTo(size.width * .5f, size.height * .28f)
                    lineTo(size.width * .57f, size.height * .4f)
                    lineTo(size.width * .64f, size.height * .13f)
                    lineTo(size.width * .78f, size.height * .27f)
                    lineTo(size.width * .67f, size.height * .72f)
                    lineTo(size.width * .5f, size.height * .86f)
                    lineTo(size.width * .33f, size.height * .72f)
                    close()
                }
                drawPath(head, accent.copy(alpha = .16f))
                drawPath(head, accent, style = stroke)
                drawArc(accent.copy(alpha = .55f), 205f, 130f, false, Offset(size.width * .66f, size.height * .07f), androidx.compose.ui.geometry.Size(size.width * .2f, size.height * .2f), style = stroke)
                drawCircle(Color.White, size.minDimension * .024f, Offset(size.width * .4f, size.height * .57f))
                drawCircle(Color.White, size.minDimension * .024f, Offset(size.width * .6f, size.height * .57f))
            }
            "crane" -> {
                val neck = Path().apply {
                    moveTo(size.width * .24f, size.height * .75f)
                    quadraticBezierTo(size.width * .55f, size.height * .72f, size.width * .55f, size.height * .34f)
                    quadraticBezierTo(size.width * .57f, size.height * .17f, size.width * .72f, size.height * .25f)
                }
                drawPath(neck, accent, style = stroke)
                drawCircle(accent, size.minDimension * .055f, Offset(size.width * .72f, size.height * .25f))
                drawLine(accent, Offset(size.width * .76f, size.height * .25f), Offset(size.width * .91f, size.height * .2f), stroke.width * .65f)
                drawLine(accent, Offset(size.width * .43f, size.height * .7f), Offset(size.width * .29f, size.height * .9f), stroke.width * .7f)
                drawLine(accent, Offset(size.width * .48f, size.height * .7f), Offset(size.width * .56f, size.height * .9f), stroke.width * .7f)
            }
            "falcon" -> {
                val wing = Path().apply {
                    moveTo(size.width * .16f, size.height * .62f)
                    quadraticBezierTo(size.width * .45f, size.height * .14f, size.width * .82f, size.height * .4f)
                    quadraticBezierTo(size.width * .55f, size.height * .42f, size.width * .36f, size.height * .75f)
                }
                drawPath(wing, accent, style = stroke)
                drawCircle(accent, size.minDimension * .055f, Offset(size.width * .76f, size.height * .36f))
                drawLine(accent, Offset(size.width * .78f, size.height * .36f), Offset(size.width * .9f, size.height * .31f), stroke.width)
            }
            "dragon" -> {
                drawArc(accent, 25f, 290f, false, topLeft = Offset(size.width * .18f, size.height * .18f), size = androidx.compose.ui.geometry.Size(size.width * .64f, size.height * .64f), style = stroke)
                drawCircle(accent, size.minDimension * .075f, Offset(size.width * .72f, size.height * .29f))
                repeat(4) { i ->
                    drawLine(accent, Offset(size.width * (.32f + i * .09f), size.height * (.69f + (i % 2) * .04f)), Offset(size.width * (.36f + i * .09f), size.height * .8f), stroke.width * .7f)
                }
            }
            "deer" -> {
                drawCircle(accent.copy(alpha = .18f), size.minDimension * .23f, Offset(size.width * .5f, size.height * .58f))
                drawCircle(accent, size.minDimension * .23f, Offset(size.width * .5f, size.height * .58f), style = stroke)
                drawLine(accent, Offset(size.width * .39f, size.height * .4f), Offset(size.width * .28f, size.height * .17f), stroke.width)
                drawLine(accent, Offset(size.width * .61f, size.height * .4f), Offset(size.width * .72f, size.height * .17f), stroke.width)
                drawLine(accent, Offset(size.width * .28f, size.height * .2f), Offset(size.width * .18f, size.height * .29f), stroke.width * .65f)
                drawLine(accent, Offset(size.width * .72f, size.height * .2f), Offset(size.width * .82f, size.height * .29f), stroke.width * .65f)
                drawCircle(Color.White, size.minDimension * .022f, Offset(size.width * .42f, size.height * .56f))
                drawCircle(Color.White, size.minDimension * .022f, Offset(size.width * .58f, size.height * .56f))
            }
            "rabbit" -> {
                drawCircle(accent.copy(alpha = .18f), size.minDimension * .23f, Offset(size.width * .5f, size.height * .62f))
                drawCircle(accent, size.minDimension * .23f, Offset(size.width * .5f, size.height * .62f), style = stroke)
                drawOval(accent.copy(alpha = .14f), Offset(size.width * .31f, size.height * .1f), androidx.compose.ui.geometry.Size(size.width * .16f, size.height * .42f))
                drawOval(accent, Offset(size.width * .31f, size.height * .1f), androidx.compose.ui.geometry.Size(size.width * .16f, size.height * .42f), style = stroke)
                drawOval(accent.copy(alpha = .14f), Offset(size.width * .53f, size.height * .1f), androidx.compose.ui.geometry.Size(size.width * .16f, size.height * .42f))
                drawOval(accent, Offset(size.width * .53f, size.height * .1f), androidx.compose.ui.geometry.Size(size.width * .16f, size.height * .42f), style = stroke)
                drawCircle(Color.White, size.minDimension * .024f, Offset(size.width * .42f, size.height * .61f))
                drawCircle(Color.White, size.minDimension * .024f, Offset(size.width * .58f, size.height * .61f))
            }
            "turtle" -> {
                drawOval(accent.copy(alpha = .17f), Offset(size.width * .18f, size.height * .3f), androidx.compose.ui.geometry.Size(size.width * .64f, size.height * .45f))
                drawOval(accent, Offset(size.width * .18f, size.height * .3f), androidx.compose.ui.geometry.Size(size.width * .64f, size.height * .45f), style = stroke)
                drawCircle(accent, size.minDimension * .085f, Offset(size.width * .82f, size.height * .52f))
                repeat(4) { i ->
                    val px = if (i % 2 == 0) .31f else .64f
                    val py = if (i < 2) .31f else .75f
                    drawCircle(accent, size.minDimension * .055f, Offset(size.width * px, size.height * py))
                }
                drawLine(accent.copy(alpha = .7f), Offset(size.width * .5f, size.height * .34f), Offset(size.width * .5f, size.height * .72f), stroke.width * .55f)
                drawLine(accent.copy(alpha = .7f), Offset(size.width * .26f, size.height * .52f), Offset(size.width * .74f, size.height * .52f), stroke.width * .55f)
            }
            "lion" -> {
                repeat(12) { i ->
                    val angle = Math.toRadians(i * 30.0)
                    drawLine(
                        accent,
                        Offset(size.width * .5f + kotlin.math.cos(angle).toFloat() * size.width * .25f, size.height * .54f + kotlin.math.sin(angle).toFloat() * size.height * .25f),
                        Offset(size.width * .5f + kotlin.math.cos(angle).toFloat() * size.width * .36f, size.height * .54f + kotlin.math.sin(angle).toFloat() * size.height * .36f),
                        stroke.width
                    )
                }
                drawCircle(accent.copy(alpha = .18f), size.minDimension * .25f, Offset(size.width * .5f, size.height * .54f))
                drawCircle(accent, size.minDimension * .25f, Offset(size.width * .5f, size.height * .54f), style = stroke)
                drawCircle(Color.White, size.minDimension * .024f, Offset(size.width * .42f, size.height * .52f))
                drawCircle(Color.White, size.minDimension * .024f, Offset(size.width * .58f, size.height * .52f))
            }
            else -> { // tiger
                val head = Path().apply {
                    moveTo(size.width * .25f, size.height * .27f)
                    lineTo(size.width * .39f, size.height * .36f)
                    quadraticBezierTo(size.width * .5f, size.height * .2f, size.width * .61f, size.height * .36f)
                    lineTo(size.width * .75f, size.height * .27f)
                    lineTo(size.width * .68f, size.height * .72f)
                    lineTo(size.width * .5f, size.height * .84f)
                    lineTo(size.width * .32f, size.height * .72f)
                    close()
                }
                drawPath(head, accent.copy(alpha = .17f))
                drawPath(head, accent, style = stroke)
                repeat(3) { i ->
                    drawLine(accent, Offset(size.width * (.38f + i * .12f), size.height * .38f), Offset(size.width * (.35f + i * .15f), size.height * .53f), stroke.width * .55f)
                }
                drawCircle(Color.White, size.minDimension * .024f, Offset(size.width * .4f, size.height * .59f))
                drawCircle(Color.White, size.minDimension * .024f, Offset(size.width * .6f, size.height * .59f))
            }
        }
    }
}

@Composable
private fun IdentityDetail(title: String, value: String, accent: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = .08f)).padding(11.dp)) {
        Text(title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun QuickTranslateScreen(
    state: QuickTranslateState,
    onBack: () -> Unit,
    onTranslate: (String) -> Unit,
    onMic: () -> Unit,
    onListen: (String) -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onMic()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ToolTopBar("Quick Translate", onBack, applyStatusBars = false)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(
                    Modifier.background(Brush.linearGradient(listOf(Color(0xFF103B43), Color(0xFF2B2053)))).padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Translate, null, tint = Mint, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Korean · English · Myanmar", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("မြန်မာအဓိပ္ပာယ်ကို မြန်မြန်ထုတ်ပေးမယ်", color = Color.White.copy(alpha = .65f), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    OutlinedTextField(
                        input,
                        { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("စာရိုက်ပါ") },
                        placeholder = { Text("한국어 또는 English…") },
                        minLines = 4,
                        maxLines = 8,
                        enabled = !state.isRecording,
                        shape = RoundedCornerShape(17.dp)
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) onMic()
                                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            enabled = !state.isLoading,
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Icon(if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.isRecording) "Stop & translate" else "ပြောမယ်")
                        }
                        Button(
                            onClick = { onTranslate(input) },
                            enabled = input.isNotBlank() && !state.isLoading && !state.isRecording,
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            if (state.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Translate")
                        }
                    }
                }
            }
            if (state.isRecording) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Coral.copy(alpha = .12f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GraphicEq, null, tint = Coral)
                    Spacer(Modifier.width(9.dp))
                    Text("နားထောင်နေပါတယ်… ပြီးရင် Stop & translate ကိုနှိပ်ပါ")
                }
            }
            state.error?.let { ToolError(it) }
            state.result?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(23.dp)
                ) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("မြန်မာအဓိပ္ပာယ်", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(result.myanmarMeaning, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
                            }
                            FilledIconButton(onClick = { onListen(result.originalText) }) {
                                Icon(Icons.Default.VolumeUp, "Listen")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .14f))
                        Text(result.originalText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        if (result.pronunciation.isNotBlank()) {
                            IdentityDetail("အသံထွက်", result.pronunciation, VioletLight)
                        }
                        if (result.naturalTranslation.isNotBlank() && result.naturalTranslation != result.myanmarMeaning) {
                            IdentityDetail("Natural translation", result.naturalTranslation, Mint)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Detected: ${result.detectedLanguage}", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Words & grammar") }
                        }
                        AnimatedVisibility(details) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (result.wordBreakdown.isNotBlank()) IdentityDetail("စကားလုံးခွဲခြမ်း", result.wordBreakdown, Color(0xFF66A6FF))
                                if (result.grammarNote.isNotBlank()) IdentityDetail("Grammar / particles", result.grammarNote, Coral)
                            }
                        }
                        Text("Model: ${result.activeModel}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolTopBar(title: String, onBack: () -> Unit, applyStatusBars: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().then(if (applyStatusBars) Modifier.statusBarsPadding() else Modifier).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 21.sp)
    }
}

@Composable
private fun ToolError(error: String) {
    OutlinedCard(border = androidx.compose.foundation.BorderStroke(1.dp, Coral.copy(alpha = .6f))) {
        Text(error, color = Coral, modifier = Modifier.fillMaxWidth().padding(14.dp), textAlign = TextAlign.Center)
    }
}

private fun colorFromHex(value: String, fallback: Color): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(fallback)
