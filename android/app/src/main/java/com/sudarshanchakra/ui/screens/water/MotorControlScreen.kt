package com.sudarshanchakra.ui.screens.water

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sudarshanchakra.domain.model.water.WaterMotor
import com.sudarshanchakra.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotorControlScreen(
    motorId: String,
    onBack: () -> Unit,
    viewModel: WaterViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val motor = ui.motors.find { it.id == motorId }

    LaunchedEffect(motorId) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(motor?.displayName ?: "Motor Control") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
                },
            )
        }
    ) { padding ->
        if (motor == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (ui.loading) CircularProgressIndicator()
                else Text("Motor not found", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Status card
            MotorStatusCard(motor = motor, onCommand = { cmd -> viewModel.sendCommand(motorId, cmd) })

            // Control type badge
            ControlTypeBadge(motor)

            // Mode selector
            ModeSelector(current = motor.mode, onSelect = { mode ->
                val cmd = when (mode) { "on" -> "pump_on"; "off" -> "pump_off"; else -> "pump_auto" }
                viewModel.sendCommand(motorId, cmd)
            })

            // Auto thresholds
            AnimatedVisibility(motor.mode == "auto" || motor.autoMode) {
                AutoThresholdsCard(motor)
            }

            // SMS config — only for Taro Smart Panel
            if (motor.isSms) {
                SmsConfigCard(motor = motor, onSave = { phone, onMsg, offMsg ->
                    viewModel.updateSmsConfig(motorId, phone, onMsg, offMsg)
                })
            }

            // Safety info
            SafetyCard(motor)

            // Feedback snackbar
            ui.commandSent?.let { cmd ->
                LaunchedEffect(cmd) {
                    kotlinx.coroutines.delay(2000)
                    viewModel.clearFeedback()
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(.15f)),
                    border = BorderStroke(1.dp, SuccessGreen.copy(.4f)),
                    shape  = RoundedCornerShape(12.dp),
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                        Text("Command sent: $cmd", color = SuccessGreen, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ─── Status card ─────────────────────────────────────────────────────────────

@Composable
private fun MotorStatusCard(motor: WaterMotor, onCommand: (String) -> Unit) {
    val isRunning = motor.isRunning
    val stateColor = if (isRunning) SuccessGreen else TextMuted
    val bgColor    = if (isRunning) Color(0xFFEDF7F0) else SurfaceLight

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.5.dp, stateColor.copy(.3f)),
        shape  = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // State indicator circle
            Box(
                Modifier.size(72.dp)
                    .background(stateColor.copy(.15f), CircleShape)
                    .border(2.dp, stateColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isRunning) Icons.Default.WaterDrop else Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = stateColor,
                    modifier = Modifier.size(32.dp),
                )
            }

            Text(
                if (isRunning) "RUNNING" else motor.state.uppercase(),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = stateColor,
            )

            if (isRunning && motor.runSeconds > 0) {
                val m = motor.runSeconds / 60; val s = motor.runSeconds % 60
                Text("Runtime: %02d:%02d".format(m, s), fontSize = 13.sp, color = TextSecondary)
            }

            // Big toggle button
            Button(
                onClick = { onCommand(if (isRunning) "pump_off" else "pump_on") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) CriticalRed else SuccessGreen,
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(.6f).height(48.dp),
            ) {
                Text(
                    if (isRunning) "Stop Motor" else "Start Motor",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                )
            }
        }
    }
}

// ─── Control type badge ───────────────────────────────────────────────────────

@Composable
private fun ControlTypeBadge(motor: WaterMotor) {
    val (icon, label, desc) = if (motor.isSms)
        Triple("📱", "SMS Control", "Commands Taro Smart Panel via GSM")
    else
        Triple("⚡", "Relay Control", "Direct GPIO relay switching")

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        shape  = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp)
            Column {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(desc,  fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

// ─── Mode selector ────────────────────────────────────────────────────────────

@Composable
private fun ModeSelector(current: String, onSelect: (String) -> Unit) {
    Text("Operating mode", fontSize = 11.sp, color = TextMuted,
        modifier = Modifier.padding(start = 4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("off" to "Always Off", "auto" to "Auto", "on" to "Always On")
            .forEach { (mode, label) ->
                val selected = current == mode
                val selColor = when (mode) { "off" -> CriticalRed; "auto" -> Terracotta; else -> SuccessGreen }
                Card(
                    modifier = Modifier.weight(1f).clickable { onSelect(mode) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) selColor.copy(.12f) else SurfaceLight
                    ),
                    border = BorderStroke(1.5.dp, if (selected) selColor else DividerColor),
                    shape  = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(when(mode) { "off" -> "🔴"; "auto" -> "🔵"; else -> "🟢" }, fontSize = 18.sp)
                        Text(label, fontSize = 10.sp,
                            color = if (selected) selColor else TextMuted,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
    }
}

// ─── Auto thresholds ──────────────────────────────────────────────────────────

@Composable
private fun AutoThresholdsCard(motor: WaterMotor) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, DividerColor),
        shape  = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Auto thresholds", fontSize = 11.sp, color = TextMuted)
            ThresholdRow("Start motor below", "${motor.pumpOnPercent.toInt()}%", Terracotta)
            ThresholdRow("Stop motor above",  "${motor.pumpOffPercent.toInt()}%", SuccessGreen)
            ThresholdRow("Max run time",      "${motor.maxRunMinutes} min",       HighPriority)
        }
    }
}

@Composable
private fun ThresholdRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(value,  fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

// ─── SMS config (Taro Smart Panel) ───────────────────────────────────────────

@Composable
private fun SmsConfigCard(motor: WaterMotor, onSave: (String, String, String) -> Unit) {
    var phone  by remember(motor.id) { mutableStateOf(motor.gsmTargetPhone ?: "") }
    var onMsg  by remember(motor.id) { mutableStateOf(motor.gsmOnMessage  ?: "START PUMP") }
    var offMsg by remember(motor.id) { mutableStateOf(motor.gsmOffMessage ?: "STOP PUMP") }
    var editing by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, DividerColor),
        shape  = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Taro Smart Panel — SMS Config", fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("Configure SMS messages to control the Taro panel",
                        fontSize = 11.sp, color = TextMuted)
                }
                TextButton(onClick = { editing = !editing }) {
                    Text(if (editing) "Cancel" else "Edit", color = Terracotta)
                }
            }

            if (editing) {
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Target phone number") },
                    placeholder = { Text("+91XXXXXXXXXX") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = onMsg, onValueChange = { onMsg = it },
                    label = { Text("Motor ON message (sent to Taro panel)") },
                    placeholder = { Text("e.g. START PUMP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = offMsg, onValueChange = { offMsg = it },
                    label = { Text("Motor OFF message (sent to Taro panel)") },
                    placeholder = { Text("e.g. STOP PUMP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(
                    onClick = { onSave(phone, onMsg, offMsg); editing = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
                    shape  = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save SMS Config", color = Color.White) }
            } else {
                SmsRow("Phone",    phone.ifEmpty { "Not set" })
                SmsRow("ON  SMS",  onMsg.ifEmpty { "Not set" })
                SmsRow("OFF SMS",  offMsg.ifEmpty { "Not set" })
            }
        }
    }
}

@Composable
private fun SmsRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = TextMuted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}

// ─── Safety card ──────────────────────────────────────────────────────────────

@Composable
private fun SafetyCard(motor: WaterMotor) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8EE)),
        border = BorderStroke(1.dp, HighPriority.copy(.3f)),
        shape  = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Safety limits (firmware-enforced)", fontSize = 11.sp, color = HighPriority)
            ThresholdRow("Max run time",    "${motor.maxRunMinutes} min",   HighPriority)
            ThresholdRow("Dry-run guard",   "Stops if level < 5%",          HighPriority)
            ThresholdRow("Control type",    if (motor.isSms) "SMS → Taro" else "GPIO relay", TextSecondary)
            Text(
                "⚠  Safety cutoffs are enforced by the ESP8266 firmware independently of the app.",
                fontSize = 11.sp, color = HighPriority.copy(.7f), lineHeight = 16.sp,
            )
        }
    }
}
