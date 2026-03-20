package com.sudarshanchakra

import android.app.Application
import com.sudarshanchakra.di.AppBootstrapEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class SudarshanChakraApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(this, AppBootstrapEntryPoint::class.java)
        runBlocking {
            entryPoint.serverSettingsRepository().hydrateRuntimeFromStore()
            entryPoint.authRepository().syncTokenCacheFromDataStore()
        }
    }
}
