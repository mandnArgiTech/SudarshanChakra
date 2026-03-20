package com.sudarshanchakra.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sudarshanchakra.data.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                authRepository.syncTokenCacheFromDataStore()
                if (authRepository.hasAuthSession()) {
                    MqttForegroundService.start(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
