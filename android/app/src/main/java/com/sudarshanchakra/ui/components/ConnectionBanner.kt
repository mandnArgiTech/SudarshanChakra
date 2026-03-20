package com.sudarshanchakra.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sudarshanchakra.service.MqttForegroundService
import com.sudarshanchakra.ui.theme.StatusOnline
import com.sudarshanchakra.ui.theme.StatusOffline
import com.sudarshanchakra.ui.theme.WarningPriority

@Composable
fun ConnectionBanner(
    apiReachable: Boolean? = null,
    modifier: Modifier = Modifier,
) {
    val mqttConnected by MqttForegroundService.mqttConnected.collectAsStateWithLifecycle()
    val label: String
    val dotColor = when {
        mqttConnected && (apiReachable != false) -> StatusOnline
        mqttConnected -> WarningPriority
        else -> StatusOffline
    }
    label = when {
        mqttConnected && (apiReachable != false) -> "Connected — alerts streaming"
        mqttConnected -> "MQTT OK — API check unavailable"
        else -> "Reconnecting to alert broker…"
    }

    val pulse = if (!mqttConnected) {
        val t = rememberInfiniteTransition(label = "conn")
        t.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "a",
        ).value
    } else {
        1f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(pulse)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
