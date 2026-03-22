package com.sudarshanchakra.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudarshanchakra.data.repository.AppLockPreferencesRepository
import com.sudarshanchakra.data.repository.AuthRepository
import com.sudarshanchakra.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val appLockPreferencesRepository: AppLockPreferencesRepository,
) : ViewModel() {

    val currentUser: StateFlow<User?> = authRepository.currentUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null,
    )

    val lockOnResumeEnabled: StateFlow<Boolean> = appLockPreferencesRepository.lockOnResumeEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false,
    )

    fun setLockOnResume(enabled: Boolean) {
        viewModelScope.launch {
            appLockPreferencesRepository.setLockOnResume(enabled)
        }
    }
}
