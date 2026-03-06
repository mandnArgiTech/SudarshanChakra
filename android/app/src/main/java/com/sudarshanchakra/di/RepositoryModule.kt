package com.sudarshanchakra.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    // Repositories (AuthRepository, AlertRepository, SirenRepository, DeviceRepository)
    // are provided via their @Inject constructor + @Singleton annotations.
    // This module exists as an extension point for future interface bindings.
}
