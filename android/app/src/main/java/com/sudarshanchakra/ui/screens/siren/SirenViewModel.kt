package com.sudarshanchakra.ui.screens.siren

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudarshanchakra.data.repository.DeviceRepository
import com.sudarshanchakra.data.repository.SirenRepository
import com.sudarshanchakra.domain.model.EdgeNode
import com.sudarshanchakra.domain.model.SirenAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SirenUiState(
    val nodes: List<EdgeNode> = emptyList(),
    val history: List<SirenAction> = emptyList(),
    val activeNodeIds: Set<String> = emptySet(),
    val isAllSirenActive: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null
)

@HiltViewModel
class SirenViewModel @Inject constructor(
    private val sirenRepository: SirenRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SirenUiState())
    val uiState: StateFlow<SirenUiState> = _uiState.asStateFlow()

    init {
        loadData(isPullRefresh = false)
    }

    fun refresh() = loadData(isPullRefresh = true)

    private fun loadData(isPullRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                if (isPullRefresh) {
                    it.copy(isRefreshing = true)
                } else {
                    it.copy(isLoading = true)
                }
            }
            val nodesResult = deviceRepository.getNodes()
            val historyResult = sirenRepository.getHistory()

            _uiState.update {
                it.copy(
                    nodes = nodesResult.getOrDefault(emptyList()),
                    history = historyResult.getOrDefault(emptyList()),
                    isLoading = false,
                    isRefreshing = false,
                )
            }
        }
    }

    fun triggerAllSirens() {
        viewModelScope.launch {
            val nodes = _uiState.value.nodes
            _uiState.update { it.copy(isAllSirenActive = true) }
            nodes.forEach { node ->
                sirenRepository.triggerSiren(node.id, "Manual trigger - all nodes")
            }
            _uiState.update {
                it.copy(
                    activeNodeIds = nodes.map { n -> n.id }.toSet(),
                    actionMessage = "All sirens triggered"
                )
            }
            loadHistory()
        }
    }

    fun stopAllSirens() {
        viewModelScope.launch {
            val nodes = _uiState.value.nodes
            nodes.forEach { node ->
                sirenRepository.stopSiren(node.id)
            }
            _uiState.update {
                it.copy(
                    isAllSirenActive = false,
                    activeNodeIds = emptySet(),
                    actionMessage = "All sirens stopped"
                )
            }
            loadHistory()
        }
    }

    fun toggleNodeSiren(nodeId: String) {
        viewModelScope.launch {
            val isActive = _uiState.value.activeNodeIds.contains(nodeId)
            if (isActive) {
                sirenRepository.stopSiren(nodeId)
                _uiState.update {
                    it.copy(
                        activeNodeIds = it.activeNodeIds - nodeId,
                        actionMessage = "Siren stopped for node"
                    )
                }
            } else {
                sirenRepository.triggerSiren(nodeId)
                _uiState.update {
                    it.copy(
                        activeNodeIds = it.activeNodeIds + nodeId,
                        actionMessage = "Siren triggered for node"
                    )
                }
            }
            loadHistory()
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val result = sirenRepository.getHistory()
            result.onSuccess { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}
