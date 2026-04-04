package com.sudarshanchakra.ui.screens.settings

import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sudarshanchakra.BuildConfig
import com.sudarshanchakra.mdm.DevEscapeDialog
import com.sudarshanchakra.mdm.KioskManager
import com.sudarshanchakra.service.MqttForegroundService
import com.sudarshanchakra.ui.navigation.SessionViewModel
import kotlinx.coroutines.delay
import com.sudarshanchakra.ui.theme.CreamBackground
import com.sudarshanchakra.ui.theme.CriticalRed
import com.sudarshanchakra.ui.theme.Terracotta
import com.sudarshanchakra.ui.theme.TextMuted
import com.sudarshanchakra.ui.theme.TextPrimary
import com.sudarshanchakra.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionViewModel: SessionViewModel,
    onOpenServerSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val lockOnResume by viewModel.lockOnResumeEnabled.collectAsStateWithLifecycle()
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showEscapeDialog by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val kioskManager = remember { KioskManager(context) }

    LaunchedEffect(tapCount) {
        if (tapCount in 1..6) {
            delay(3000L)
            tapCount = 0
        }
    }
    Scaffold(
        containerColor = CreamBackground,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CreamBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Account",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            user?.let { u ->
                Text(text = u.username, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(text = u.email, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(text = "Role: ${u.role}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            } ?: Text("Not signed in", style = MaterialTheme.typography.bodyMedium, color = TextMuted)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Connection",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            OutlinedButton(
                onClick = onOpenServerSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Server & MQTT", modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Security",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Require unlock after leaving app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Switch(
                    checked = lockOnResume,
                    onCheckedChange = viewModel::setLockOnResume,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Terracotta,
                        checkedTrackColor = Terracotta.copy(alpha = 0.5f),
                    ),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "About",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.clickable {
                    tapCount++
                    if (tapCount >= 7) {
                        tapCount = 0
                        showEscapeDialog = true
                    }
                },
            )

            OutlinedButton(
                onClick = {
                    runCatching {
                        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        val ringtone = RingtoneManager.getRingtone(context, uri)
                        ringtone?.play()
                        Handler(Looper.getMainLooper()).postDelayed(
                            { runCatching { ringtone?.stop() } },
                            2000L,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Preview critical alert sound (2s)")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                OutlinedButton(
                    onClick = {
                        val i = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            putExtra(Settings.EXTRA_CHANNEL_ID, MqttForegroundService.CRITICAL_ALERT_CHANNEL_ID)
                        }
                        runCatching { context.startActivity(i) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Critical alert sound (system settings)")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CriticalRed),
            ) {
                Text("Log out")
            }
        }
    }

    if (showEscapeDialog) {
        DevEscapeDialog(
            onDismiss = { showEscapeDialog = false },
            onExitKiosk = { pin -> kioskManager.exitKiosk(pin) },
            onDecommission = { pin -> kioskManager.decommission(pin) },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log out?") },
            text = {
                Text("Logging out will stop alert notifications until you sign in again. Server URLs on this device are kept.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        sessionViewModel.logout()
                    },
                ) {
                    Text("Log out", color = CriticalRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
