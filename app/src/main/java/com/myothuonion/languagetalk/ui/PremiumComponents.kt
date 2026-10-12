package com.myothuonion.languagetalk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.myothuonion.languagetalk.network.LivePhase
import com.myothuonion.languagetalk.network.LiveState

@Composable
internal fun GlassIconTile(icon: ImageVector, modifier: Modifier = Modifier, label: String? = null) {
    val colours = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.size(42.dp).shadow(5.dp, shape).clip(shape)
        .background(Brush.linearGradient(listOf(colours.primaryContainer, colours.primary.copy(alpha = .55f))))
        .border(1.dp, colours.onPrimaryContainer.copy(alpha = .18f), shape), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha = .25f), Color.Transparent))))
        Icon(icon, label, Modifier.size(23.dp), tint = colours.onPrimaryContainer)
    }
}

@Composable
internal fun MicrophoneStatus(recording: Boolean, level: Float, elapsedSeconds: Long = 0,
    processing: Boolean = false, live: LiveState? = null, heard: String = "", modifier: Modifier = Modifier, playback: Boolean = false) {
    val capture = live?.let { it.connected && it.micEnabled && it.phase == LivePhase.LISTENING } ?: recording
    val input = if (capture) (live?.inputLevel ?: level).coerceIn(0f, 1f) else 0f
    val label = when {
        live?.error != null -> "အသံချိတ်ဆက်မှု မအောင်မြင်ပါ"
        live?.connected == true && !live.micEnabled -> "Mic ပိတ်ထားပါတယ်"
        playback || live?.phase == LivePhase.SPEAKING -> "AI ပြောနေပါတယ်"
        processing || live?.phase == LivePhase.THINKING -> "အသံပို့ပြီး ရလဒ်ကို စောင့်နေပါတယ်"
        capture -> if (input > .015f) "ဖုန်းထဲ အသံဝင်နေပါတယ်" else "Mic ဖွင့်ထားပါတယ် · ပြောလို့ရပါပြီ"
        heard.isNotBlank() -> "ဖမ်းမိသောစာသား ရရှိပြီးပြီ"
        else -> "Mic ကိုနှိပ်ပြီး စပြောပါ"
    }
    val ink = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.height(23.dp).weight(1f).semantics { contentDescription = "Real microphone input level" }) {
                val gap = size.width / 20
                repeat(18) { index ->
                    val active = index / 18f < (input * 4f).coerceAtMost(1f)
                    val height = size.height * if (active) .9f else .22f
                    drawRoundRect(if (active) ink else track, Offset(index * gap, (size.height-height)/2),
                        Size(gap * .55f, height))
                }
            }
            if (capture && recording) Text("$elapsedSeconds s", Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.labelMedium)
        }
        if (capture) Text("အသံတန်းက ဖုန်းရဲ့အသံဝင်မှုကို ပြပါတယ်။ AI စာသားဖမ်းမိရင် အောက်မှာပြမယ်။",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (heard.isNotBlank()) {
            Text("ဖမ်းမိသောစာသား", style = MaterialTheme.typography.labelMedium, color = ink)
            Text(heard, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
