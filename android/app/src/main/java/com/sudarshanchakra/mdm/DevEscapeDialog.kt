package com.sudarshanchakra.mdm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun DevEscapeDialog(
    onDismiss: () -> Unit,
    onExitKiosk: (String) -> Boolean,
    onDecommission: (String) -> Boolean,
) {
    var pin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Developer Escape") },
        text = {
            Column {
                Text(
                    "Enter escape PIN to exit kiosk mode or decommission the device.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { newVal ->
                        if (newVal.length <= 6 && newVal.all { it.isDigit() }) {
                            pin = newVal
                            errorText = null
                        }
                    },
                    label = { Text("PIN (4-6 digits)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorText != null,
                )
                errorText?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            if (pin.length < 4) {
                                errorText = "PIN must be at least 4 digits"
                                return@Button
                            }
                            if (onExitKiosk(pin)) {
                                onDismiss()
                            } else {
                                errorText = "Incorrect PIN"
                                pin = ""
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Exit Kiosk")
                    }
                    Button(
                        onClick = {
                            if (pin.length < 4) {
                                errorText = "PIN must be at least 4 digits"
                                return@Button
                            }
                            if (onDecommission(pin)) {
                                onDismiss()
                            } else {
                                errorText = "Incorrect PIN"
                                pin = ""
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Decommission")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
