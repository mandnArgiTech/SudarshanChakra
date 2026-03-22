package com.sudarshanchakra.ui.screens.siren

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sudarshanchakra.ui.components.SirenButton
import com.sudarshanchakra.ui.theme.CardWhite
import com.sudarshanchakra.ui.theme.CreamBackground
import com.sudarshanchakra.ui.theme.CriticalRed
import com.sudarshanchakra.ui.theme.GeorgiaFamily
import com.sudarshanchakra.ui.theme.StatusOnline
import com.sudarshanchakra.ui.theme.SurfaceLight
import com.sudarshanchakra.ui.theme.Terracotta
import com.sudarshanchakra.ui.theme.TextMuted
import com.sudarshanchakra.ui.theme.TextPrimary
import com.sudarshanchakra.ui.theme.TextSecondary

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SirenControlScreen(
    viewModel: SirenViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSirenConfirm by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        Text(
            text = "Siren Control",
            fontFamily = GeorgiaFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Terracotta)
            }
        } else {
            val pullState = rememberPullRefreshState(
                refreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullState),
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))

                    SirenButton(
                        isActive = uiState.isAllSirenActive,
                        onClick = {
                            if (uiState.isAllSirenActive) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.stopAllSirens()
                            } else {
                                showSirenConfirm = true
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (uiState.isAllSirenActive) "Tap to stop all sirens"
                        else "Tap to trigger all sirens",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "Per-Node Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(uiState.nodes, key = { it.id }) { node ->
                    val isActive = uiState.activeNodeIds.contains(node.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isActive) CriticalRed else StatusOnline
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = node.name.ifBlank { "Unnamed node" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = node.location.ifBlank { "—" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Switch(
                                checked = isActive,
                                onCheckedChange = { viewModel.toggleNodeSiren(node.id) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CardWhite,
                                    checkedTrackColor = CriticalRed,
                                    uncheckedThumbColor = CardWhite,
                                    uncheckedTrackColor = SurfaceLight
                                )
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))

                    if (uiState.history.isNotEmpty()) {
                        Text(
                            text = "Recent Activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                items(uiState.history.take(10), key = { it.id }) { action ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 3.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (action.action == "trigger") CriticalRed else SurfaceLight),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${action.action.replaceFirstChar { it.uppercase() }} - Node ${action.nodeId.takeLast(4).ifEmpty { "?" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "by ${action.triggeredBy ?: "—"} · ${action.timestamp.take(16).replace("T", " ").ifEmpty { "—" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
                PullRefreshIndicator(
                    refreshing = uiState.isRefreshing,
                    state = pullState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }

        if (showSirenConfirm) {
            AlertDialog(
                onDismissRequest = { showSirenConfirm = false },
                title = { Text("Trigger siren?") },
                text = {
                    Text("This will activate the farm PA / siren system. Only confirm in a real emergency.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSirenConfirm = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.triggerAllSirens()
                        },
                    ) {
                        Text("Trigger", color = CriticalRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSirenConfirm = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}
