package com.sudarshanchakra.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudarshanchakra.data.config.ConnectionUrlNormalizer
import com.sudarshanchakra.data.repository.ServerSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerSettingsUiState(
    val apiBaseUrl: String = "",
    val mqttBrokerUrl: String = "",
    val edgeGuiBaseUrl: String = "",
    val buildDefaultApiHint: String = "",
    val buildDefaultMqttHint: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
)

@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    private val repository: ServerSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ServerSettingsUiState(
            buildDefaultApiHint = ConnectionUrlNormalizer.defaultApiBaseUrl(),
            buildDefaultMqttHint = ConnectionUrlNormalizer.defaultMqttBrokerUrl(),
        ),
    )
    val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

    init {
        // Single snapshot — continuous collect raced with user edits and overwrote fields mid-typing.
        viewModelScope.launch {
            val s = repository.settings.first()
            _uiState.update {
                it.copy(
                    apiBaseUrl = s.apiBaseUrl.trimEnd('/'),
                    mqttBrokerUrl = s.mqttBrokerUrl,
                    edgeGuiBaseUrl = s.edgeGuiBaseUrl,
                )
            }
        }
    }

    fun onApiChange(value: String) {
        _uiState.update { it.copy(apiBaseUrl = value, error = null, savedMessage = null) }
    }

    fun onMqttChange(value: String) {
        _uiState.update { it.copy(mqttBrokerUrl = value, error = null, savedMessage = null) }
    }

    fun onEdgeGuiChange(value: String) {
        _uiState.update { it.copy(edgeGuiBaseUrl = value, error = null, savedMessage = null) }
    }

    fun save() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, savedMessage = null) }
            val s = _uiState.value
            val result = repository.save(s.apiBaseUrl, s.mqttBrokerUrl, s.edgeGuiBaseUrl)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    error = result.exceptionOrNull()?.message,
                    savedMessage = if (result.isSuccess) {
                        "Saved. Restart the app or log in again for MQTT to use the new broker. Camera snapshots use the edge URL immediately."
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, savedMessage = null) }
            val result = repository.resetToBuildDefaults()
            _uiState.update {
                it.copy(
                    isSaving = false,
                    error = result.exceptionOrNull()?.message,
                    savedMessage = if (result.isSuccess) "Reset to build defaults." else null,
                    apiBaseUrl = ConnectionUrlNormalizer.defaultApiBaseUrl().trimEnd('/'),
                    mqttBrokerUrl = ConnectionUrlNormalizer.defaultMqttBrokerUrl(),
                    edgeGuiBaseUrl = "",
                )
            }
        }
    }
}
