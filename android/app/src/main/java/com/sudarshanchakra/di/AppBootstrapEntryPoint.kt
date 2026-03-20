package com.sudarshanchakra.di

import com.sudarshanchakra.data.repository.AuthRepository
import com.sudarshanchakra.data.repository.ServerSettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Process startup: hydrate runtime config + auth token cache (no DI in [android.app.Application]). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppBootstrapEntryPoint {
    fun serverSettingsRepository(): ServerSettingsRepository
    fun authRepository(): AuthRepository
}
