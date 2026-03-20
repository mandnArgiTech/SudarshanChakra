package com.sudarshanchakra.ui.screens.water

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sudarshanchakra.domain.model.water.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterTanksScreen(
    onMotorClick: (String) -> Unit,
    viewModel: WaterViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Water Tanks") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (ui.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Low alert banner
            if (ui.hasLowAlert) {
                AlertBanner()
            }

            // Farm section
            if (ui.farmTanks.isNotEmpty()) {
                LocationSection(
                    title = "Sangareddy Farm",
                    icon  = "🌾",
                    tanks = ui.farmTanks,
                    motor = ui.motors.find { it.location == "farm" },
                    onMotorClick = onMotorClick,
                )
            }

            // Home section
            if (ui.homeTanks.isNotEmpty()) {
                LocationSection(
                    title = "Home",
                    icon  = "🏠",
                    tanks = ui.homeTanks,
                    motor = ui.motors.find { it.location == "home" },
                    onMotorClick = onMotorClick,
                )
            }

            ui.error?.let { err ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(err, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun AlertBanner() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1000)),
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(.5f)),
        shape  = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
            Text("One or more tanks are running low", color = Color(0xFFF59E0B), fontSize = 13.sp)
        }
    }
}

@Composable
private fun LocationSection(
    title: String, icon: String,
    tanks: List<WaterTank>,
    motor: WaterMotor?,
    onMotorClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon, fontSize = 18.sp)
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        tanks.forEach { TankCard(it) }
        motor?.let { MotorSummaryCard(it, onMotorClick) }
    }
}

@Composable
private fun TankCard(tank: WaterTank) {
    val pct   = (tank.levelPercent / 100f).coerceIn(0.0, 1.0).toFloat()
    val color = when (tank.levelState) {
        LevelState.CRITICAL, LevelState.OVERFLOW -> MaterialTheme.colorScheme.error
        LevelState.LOW     -> Color(0xFFF59E0B)
        LevelState.OFFLINE -> MaterialTheme.colorScheme.outline
        else               -> Color(0xFF22C55E)
    }
    val animPct by animateFloatAsState(pct, animationSpec = tween(1000), label = "tank")

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape  = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            // Mini arc indicator
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.size(56.dp)) {
                    val stroke = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                    val inset  = 3.dp.toPx()
                    val sz     = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(Color.Gray.copy(.2f), 130f, 280f, false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = sz, style = stroke)
                    if (animPct > 0f)
                        drawArc(color, 130f, 280f * animPct, false,
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = sz, style = stroke)
                }
                Text("${tank.levelPercent.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Column(Modifier.weight(1f)) {
                Text(tank.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                tank.currentLevel?.let { lvl ->
                    Text("${lvl.volumeLiters?.toInt() ?: "—"} / ${tank.capacityLiters?.toInt() ?: "—"} L",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    lvl.temperatureC?.let {
                        Text("${"%.1f".format(it)}°C", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.6f))
                    }
                }
            }

            // Status chip
            val chipColor = if (tank.isOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline
            Box(Modifier.size(8.dp).clip(CircleShape).background(chipColor))
        }
    }
}

@Composable
private fun MotorSummaryCard(motor: WaterMotor, onMotorClick: (String) -> Unit) {
    val runningColor = if (motor.isRunning) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface.copy(.4f)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (motor.isRunning) Color(0xFF0D200D) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (motor.isRunning) Color(0xFF22C55E).copy(.3f)
                                    else MaterialTheme.colorScheme.outline.copy(.3f)),
        shape  = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable { onMotorClick(motor.id) },
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(motor.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (motor.isRunning) "● RUNNING" else "● STOPPED",
                        fontSize = 11.sp, color = runningColor, fontWeight = FontWeight.SemiBold,
                    )
                    Text("·", color = MaterialTheme.colorScheme.onSurface.copy(.3f))
                    Text(
                        if (motor.isSms) "SMS (Taro)" else "Relay",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(.5f),
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(.4f))
        }
    }
}
