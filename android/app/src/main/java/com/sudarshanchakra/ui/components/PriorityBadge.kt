package com.sudarshanchakra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudarshanchakra.domain.model.AlertPriority
import com.sudarshanchakra.ui.theme.CriticalRed
import com.sudarshanchakra.ui.theme.HighPriority
import com.sudarshanchakra.ui.theme.LowPriority
import com.sudarshanchakra.ui.theme.WarningPriority

@Composable
fun PriorityBadge(
    priority: AlertPriority,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, label) = when (priority) {
        AlertPriority.CRITICAL -> Triple(CriticalRed.copy(alpha = 0.15f), CriticalRed, "CRITICAL")
        AlertPriority.HIGH -> Triple(HighPriority.copy(alpha = 0.15f), HighPriority, "HIGH")
        AlertPriority.WARNING -> Triple(WarningPriority.copy(alpha = 0.15f), WarningPriority, "WARNING")
        AlertPriority.LOW -> Triple(LowPriority.copy(alpha = 0.15f), LowPriority, "LOW")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun PriorityFilterPill(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent
    val borderColor = if (isSelected) color else Color(0xFFE0DCD6)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) color else Color(0xFF9E9891),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
