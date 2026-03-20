package com.sudarshanchakra.ui.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainNavViewModel @Inject constructor() : ViewModel() {

    private val _pendingAlertId = MutableStateFlow<String?>(null)
    val pendingAlertId: StateFlow<String?> = _pendingAlertId.asStateFlow()

    fun offerAlertDeepLink(alertId: String) {
        _pendingAlertId.value = alertId
    }

    fun consumeAlertDeepLink() {
        _pendingAlertId.value = null
    }
}
