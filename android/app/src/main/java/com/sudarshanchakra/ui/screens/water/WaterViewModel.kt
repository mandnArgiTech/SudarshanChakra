package com.sudarshanchakra.ui.screens.water

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.domain.model.water.LevelState
import com.sudarshanchakra.domain.model.water.WaterMotor
import com.sudarshanchakra.domain.model.water.WaterTank
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WaterUiState(
    val loading: Boolean           = true,
    val tanks: List<WaterTank>     = emptyList(),
    val motors: List<WaterMotor>   = emptyList(),
    val selectedMotor: WaterMotor? = null,
    val commandSent: String?       = null,  // transient feedback
    val error: String?             = null,
) {
    val farmTanks get() = tanks.filter { it.location == "farm" }
    val homeTanks get() = tanks.filter { it.location == "home" }
    val hasLowAlert get() = tanks.any { it.levelState == LevelState.CRITICAL || it.levelState == LevelState.LOW }
}

@HiltViewModel
class WaterViewModel @Inject constructor(private val api: ApiService) : ViewModel() {

    private val _ui = MutableStateFlow(WaterUiState())
    val uiState: StateFlow<WaterUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            try {
                val tanks  = api.getWaterTanks().body() ?: emptyList()
                val motors = api.getMotors().body() ?: emptyList()
                _ui.update { it.copy(loading = false, tanks = tanks, motors = motors, error = null) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun selectMotor(motor: WaterMotor) = _ui.update { it.copy(selectedMotor = motor) }

    fun sendCommand(motorId: String, command: String) {
        viewModelScope.launch {
            try {
                api.sendMotorCommand(motorId, mapOf("command" to command))
                _ui.update { it.copy(commandSent = command) }
                refresh()
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateSmsConfig(motorId: String, phone: String, onMsg: String, offMsg: String) {
        viewModelScope.launch {
            try {
                api.updateMotor(motorId, mapOf(
                    "gsmTargetPhone" to phone,
                    "gsmOnMessage"   to onMsg,
                    "gsmOffMessage"  to offMsg,
                ))
                refresh()
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearFeedback() = _ui.update { it.copy(commandSent = null, error = null) }
}
