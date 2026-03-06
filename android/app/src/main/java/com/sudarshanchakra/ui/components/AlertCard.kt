package com.sudarshanchakra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudarshanchakra.domain.model.Alert
import com.sudarshanchakra.domain.model.AlertPriority
import com.sudarshanchakra.ui.theme.CardWhite
import com.sudarshanchakra.ui.theme.CriticalRed
import com.sudarshanchakra.ui.theme.HighPriority
import com.sudarshanchakra.ui.theme.TextMuted
import com.sudarshanchakra.ui.theme.TextPrimary
import com.sudarshanchakra.ui.theme.TextSecondary
import com.sudarshanchakra.ui.theme.WarningPriority

@Composable
fun AlertCard(
    alert: Alert,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val priorityColor = when (alert.priority) {
        AlertPriority.CRITICAL -> CriticalRed
        AlertPriority.HIGH -> HighPriority
        AlertPriority.WARNING -> WarningPriority
        AlertPriority.LOW -> TextMuted
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(priorityColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (alert.priority) {
                        AlertPriority.CRITICAL -> "!!"
                        AlertPriority.HIGH -> "!"
                        AlertPriority.WARNING -> "⚠"
                        AlertPriority.LOW -> "i"
                    },
                    color = priorityColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = alert.detectionClass.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    PriorityBadge(priority = alert.priority)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${alert.zoneName} · ${alert.zoneType}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Confidence: ${(alert.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Text(
                        text = alert.createdAt.take(16).replace("T", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
