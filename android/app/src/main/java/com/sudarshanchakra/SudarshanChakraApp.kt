package com.sudarshanchakra

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sudarshanchakra.di.AppBootstrapEntryPoint
import com.sudarshanchakra.mdm.MdmConfig
import com.sudarshanchakra.mdm.MdmWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class SudarshanChakraApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

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
        if (MdmConfig.isEnabled(this)) {
            MdmWorkScheduler.schedule(this)
        }
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private const val TAG = "SudarshanChakraApp"
    }
}
