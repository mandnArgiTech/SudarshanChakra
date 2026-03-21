package com.sudarshanchakra

import android.app.Application
import android.util.Log
import com.sudarshanchakra.di.AppBootstrapEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class SudarshanChakraApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            val entryPoint =
                EntryPointAccessors.fromApplication(this, AppBootstrapEntryPoint::class.java)
            runBlocking {
                entryPoint.serverSettingsRepository().hydrateRuntimeFromStore()
                entryPoint.authRepository().syncTokenCacheFromDataStore()
            }
        } catch (t: Throwable) {
            // Corrupt prefs / DataStore / direct-boot: avoid process death; app uses build defaults.
            Log.e(TAG, "Startup bootstrap failed (continuing with defaults)", t)
        }
    }

    companion object {
        private const val TAG = "SudarshanChakraApp"
    }
}
