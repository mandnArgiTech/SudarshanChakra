package com.sudarshanchakra.ui.screens.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.sudarshanchakra.service.MqttForegroundService
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sudarshanchakra.domain.model.AlertPriority
import com.sudarshanchakra.ui.components.AlertCard
import com.sudarshanchakra.ui.components.ConnectionBanner
import com.sudarshanchakra.ui.components.OfflineBanner
import com.sudarshanchakra.ui.components.WaterTankCard
import com.sudarshanchakra.ui.theme.CreamBackground
import com.sudarshanchakra.ui.components.WaterStripCard
import com.sudarshanchakra.ui.screens.water.WaterViewModel
import com.sudarshanchakra.ui.theme.CriticalRed
import com.sudarshanchakra.ui.theme.GeorgiaFamily
import com.sudarshanchakra.ui.theme.HighPriority
import com.sudarshanchakra.ui.theme.Terracotta
import com.sudarshanchakra.ui.theme.TextMuted
import com.sudarshanchakra.ui.theme.TextPrimary
import com.sudarshanchakra.ui.theme.TextSecondary
import com.sudarshanchakra.ui.theme.WarningPriority

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AlertFeedScreen(
    onAlertClick: (String) -> Unit,
    onSirenClick: () -> Unit,
    onWaterCardClick: () -> Unit = {},
    viewModel: AlertViewModel = hiltViewModel(),
    waterViewModel: WaterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val waterUiState by waterViewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? ComponentActivity
    BackHandler {
        activity?.moveTaskToBack(true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground),
    ) {
        ConnectionBanner()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alert Feed",
                            fontFamily = GeorgiaFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = TextPrimary,
                        )
                        Text(
                            text = "${uiState.filteredAlerts.size} active alerts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh alerts")
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CriticalRed)
                            .clickable(onClick = onSirenClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "🚨", fontSize = 20.sp)
                    }
                }
            }

        WaterTankCard(name = "Water tank", levelPct = 72f)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                label = "All",
                isSelected = uiState.selectedPriority == null,
                color = Terracotta,
                onClick = { viewModel.filterByPriority(null) }
            )
            FilterChip(
                label = "Critical",
                isSelected = uiState.selectedPriority == AlertPriority.CRITICAL,
                color = CriticalRed,
                onClick = { viewModel.filterByPriority(AlertPriority.CRITICAL) }
            )
            FilterChip(
                label = "High",
                isSelected = uiState.selectedPriority == AlertPriority.HIGH,
                color = HighPriority,
                onClick = { viewModel.filterByPriority(AlertPriority.HIGH) }
            )
            FilterChip(
                label = "Warning",
                isSelected = uiState.selectedPriority == AlertPriority.WARNING,
                color = WarningPriority,
                onClick = { viewModel.filterByPriority(AlertPriority.WARNING) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Terracotta)
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "⚠️",
                            fontSize = 40.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "You may be offline — pull saved data or retry after checking connection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                }
            }
            uiState.filteredAlerts.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "✅", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No alerts",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "All clear on the farm",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            }
            else -> {
                val pullRefreshState = rememberPullRefreshState(
                    refreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    // Water tank strip — tap to navigate to WaterTanksScreen
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (waterUiState.tanks.isNotEmpty()) {
                            WaterStripCard(
                                tanks = waterUiState.tanks,
                                motors = waterUiState.motors,
                                onClick = onWaterCardClick,
                                modifier = androidx.compose.ui.Modifier.padding(horizontal = 0.dp, vertical = 4.dp),
                            )
                        }

                        val mqttConnected by MqttForegroundService.mqttConnected.collectAsStateWithLifecycle()

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pullRefresh(pullRefreshState),
                        ) {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (!mqttConnected && uiState.filteredAlerts.isNotEmpty()) {
                                    item {
                                        OfflineBanner(modifier = Modifier.padding(bottom = 8.dp))
                                    }
                                }
                                items(uiState.filteredAlerts, key = { it.id }) { alert ->
                                    AlertCard(
                                        alert = alert,
                                        onClick = { onAlertClick(alert.id) },
                                        onAcknowledge = { viewModel.acknowledgeAlert(alert.id) },
                                        onFalsePositive = { viewModel.markFalsePositive(alert.id) },
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(16.dp)) }
                            }
                            PullRefreshIndicator(
                                refreshing = uiState.isRefreshing,
                                state = pullRefreshState,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) color else TextMuted,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
