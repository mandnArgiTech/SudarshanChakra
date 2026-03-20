package com.sudarshanchakra.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sudarshanchakra.ui.theme.CreamBackground
import com.sudarshanchakra.ui.theme.DividerColor
import com.sudarshanchakra.ui.theme.Terracotta
import com.sudarshanchakra.ui.theme.TextMuted
import com.sudarshanchakra.ui.theme.TextPrimary
import com.sudarshanchakra.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: ServerSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = CreamBackground,
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text("Server connection") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CreamBackground),
                )
            }
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
            if (onBack == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Server connection",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    text = "REST API (gateway) and MQTT broker. Values are saved on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "API base (gateway)",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
            )
            OutlinedTextField(
                value = state.apiBaseUrl,
                onValueChange = viewModel::onApiChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. http://192.168.1.5:8080") },
                supportingText = {
                    Text(
                        "Build default: ${state.buildDefaultApiHint}",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                singleLine = false,
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Terracotta,
                    unfocusedBorderColor = DividerColor,
                    focusedLabelColor = Terracotta,
                    cursorColor = Terracotta,
                ),
            )

            Text(
                text = "MQTT broker",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
            )
            OutlinedTextField(
                value = state.mqttBrokerUrl,
                onValueChange = viewModel::onMqttChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. tcp://192.168.1.5:1883") },
                supportingText = {
                    Text(
                        "Leave blank to use build default: ${state.buildDefaultMqttHint}",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Terracotta,
                    unfocusedBorderColor = DividerColor,
                    focusedLabelColor = Terracotta,
                    cursorColor = Terracotta,
                ),
            )

            if (state.error != null) {
                Text(text = state.error!!, color = CriticalRed, style = MaterialTheme.typography.bodySmall)
            }
            if (state.savedMessage != null) {
                Text(text = state.savedMessage!!, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
            ) {
                Text(if (state.isSaving) "Saving…" else "Save")
            }

            OutlinedButton(
                onClick = viewModel::resetToDefaults,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Reset to build defaults")
            }
        }
    }
}
