package com.sudarshanchakra.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudarshanchakra.domain.model.water.LevelState
import com.sudarshanchakra.domain.model.water.WaterMotor
import com.sudarshanchakra.domain.model.water.WaterTank
import com.sudarshanchakra.ui.theme.*

/**
 * Compact horizontal strip shown at the top of AlertFeedScreen.
 * Shows all tanks grouped by location + motor running state.
 * Tapping navigates to WaterTanksScreen.
 */
@Composable
fun WaterStripCard(
    tanks: List<WaterTank>,
    motors: List<WaterMotor>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tanks.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, DividerColor),
        shape  = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("💧 Water", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Text("View all →", fontSize = 11.sp, color = Terracotta)
            }

            // Tank level pills — scrollable row
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tanks.forEach { tank ->
                    TankPill(tank)
                }
            }

            // Motor status row
            if (motors.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    motors.forEach { motor ->
                        MotorPill(motor)
                    }
                }
            }
        }
    }
}

@Composable
private fun TankPill(tank: WaterTank) {
    val levelColor = when (tank.levelState) {
        LevelState.CRITICAL, LevelState.OVERFLOW -> CriticalRed
        LevelState.LOW     -> HighPriority
        LevelState.OFFLINE -> StatusOffline
        else               -> SuccessGreen
    }
    val pct = tank.levelPercent.toFloat().coerceIn(0f, 100f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Mini progress bar
        Box(
            Modifier
                .width(52.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(DividerColor),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct / 100f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(levelColor),
            )
        }
        Text("${pct.toInt()}%", fontSize = 10.sp, color = levelColor, fontWeight = FontWeight.SemiBold)
        Text(
            tank.displayName.replace("Farm ", "").replace("Home ", "").take(8),
            fontSize = 9.sp, color = TextMuted,
        )
    }
}

@Composable
private fun MotorPill(motor: WaterMotor) {
    val runColor = if (motor.isRunning) SuccessGreen else TextMuted
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(runColor))
        Text(
            "${motor.displayName.take(12)}: ${if (motor.isRunning) "ON" else "OFF"}",
            fontSize = 10.sp, color = runColor,
        )
    }
}
