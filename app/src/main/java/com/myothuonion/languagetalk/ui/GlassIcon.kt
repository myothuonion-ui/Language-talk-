package com.myothuonion.languagetalk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun GlassIcon(icon: ImageVector, label: String?, active: Boolean = true) {
    val shape = RoundedCornerShape(12.dp)
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).shadow(if (active) 5.dp else 1.dp, shape)
        .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surface.copy(alpha = .94f))), shape)
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .55f), colors.outlineVariant.copy(alpha = .3f))), shape),
        contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(23.dp), tint = if (active) colors.primary else colors.onSurfaceVariant)
    }
}
