package com.sudarshanchakra.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sudarshanchakra.di.BootReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restarts MQTT after reboot if the user had a session.
 * Uses [EntryPointAccessors] instead of @AndroidEntryPoint + @Inject (unreliable for manifest receivers).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val entry = EntryPointAccessors.fromApplication(
                    appContext,
                    BootReceiverEntryPoint::class.java,
                )
                val auth = entry.authRepository()
                auth.syncTokenCacheFromDataStore()
                if (auth.hasAuthSession()) {
                    MqttForegroundService.start(appContext)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "BootReceiver: failed to restore MQTT session", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
