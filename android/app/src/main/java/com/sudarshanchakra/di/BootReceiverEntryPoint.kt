package com.sudarshanchakra.di

import com.sudarshanchakra.data.repository.AuthRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Manifest [BroadcastReceiver] cannot rely on @AndroidEntryPoint field injection on all devices. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BootReceiverEntryPoint {
    fun authRepository(): AuthRepository
}
