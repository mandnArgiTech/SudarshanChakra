package com.sudarshanchakra.ui.screens.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudarshanchakra.data.repository.AlertBadgeRepository
import com.sudarshanchakra.data.repository.AlertRepository
import com.sudarshanchakra.data.repository.ServerSettingsRepository
import com.sudarshanchakra.domain.model.Alert
import com.sudarshanchakra.domain.model.AlertPriority
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertFeedUiState(
    val alerts: List<Alert> = emptyList(),
    val filteredAlerts: List<Alert> = emptyList(),
    val selectedPriority: AlertPriority? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedAlert: Alert? = null,
    val isDetailLoading: Boolean = false,
    val actionMessage: String? = null
)

@HiltViewModel
class AlertViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val alertBadgeRepository: AlertBadgeRepository,
    serverSettingsRepository: ServerSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertFeedUiState())
    val uiState: StateFlow<AlertFeedUiState> = _uiState.asStateFlow()

    /** Edge Flask base (no trailing slash) for `/api/clips/{alertId}.mp4`. */
    val edgeGuiBaseUrl: StateFlow<String> = serverSettingsRepository.settings
        .map { it.edgeGuiBaseUrl.trim().trimEnd('/') }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init {
        loadAlerts(fullScreenLoading = true)
    }

    fun refresh() = loadAlerts(fullScreenLoading = false)

    fun loadAlerts(fullScreenLoading: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                if (fullScreenLoading) {
                    it.copy(isLoading = true, error = null)
                } else {
                    it.copy(isRefreshing = true, error = null)
                }
            }
            val result = alertRepository.getAlerts()
            result.fold(
                onSuccess = { alerts ->
                    alertBadgeRepository.clear()
                    _uiState.update {
                        it.copy(
                            alerts = alerts,
                            filteredAlerts = filterAlerts(alerts, it.selectedPriority),
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "Failed to load alerts",
                        )
                    }
                },
            )
        }
    }

    fun filterByPriority(priority: AlertPriority?) {
        _uiState.update {
            it.copy(
                selectedPriority = priority,
                filteredAlerts = filterAlerts(it.alerts, priority)
            )
        }
    }

    fun loadAlertDetail(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true) }
            val result = alertRepository.getAlert(id)
            result.fold(
                onSuccess = { alert ->
                    _uiState.update { it.copy(selectedAlert = alert, isDetailLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isDetailLoading = false,
                            error = error.message ?: "Failed to load alert"
                        )
                    }
                }
            )
        }
    }

    fun acknowledgeAlert(id: String) {
        viewModelScope.launch {
            val result = alertRepository.acknowledgeAlert(id)
            result.fold(
                onSuccess = { alert ->
                    _uiState.update { it.copy(selectedAlert = alert, actionMessage = "Alert acknowledged") }
                    refresh()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(actionMessage = "Failed to acknowledge: ${error.message}") }
                }
            )
        }
    }

    fun resolveAlert(id: String) {
        viewModelScope.launch {
            val result = alertRepository.resolveAlert(id)
            result.fold(
                onSuccess = { alert ->
                    _uiState.update { it.copy(selectedAlert = alert, actionMessage = "Alert resolved") }
                    refresh()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(actionMessage = "Failed to resolve: ${error.message}") }
                }
            )
        }
    }

    fun markFalsePositive(id: String) {
        viewModelScope.launch {
            val result = alertRepository.markFalsePositive(id)
            result.fold(
                onSuccess = { alert ->
                    _uiState.update { it.copy(selectedAlert = alert, actionMessage = "Marked as false positive") }
                    refresh()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(actionMessage = "Failed: ${error.message}") }
                }
            )
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    private fun filterAlerts(alerts: List<Alert>, priority: AlertPriority?): List<Alert> {
        return if (priority == null) alerts
        else alerts.filter { it.priority == priority }
    }
}
