package com.sudarshanchakra.mdm

import android.content.Context

/**
 * MDM feature flag. When false (default), telemetry WorkManager jobs and fused location
 * tracking in [com.sudarshanchakra.service.MqttForegroundService] are skipped.
 * Set [KEY_ENABLED] to true after enrollment (future Story) or via ADB for testing.
 */
object MdmConfig {

    const val PREFS_NAME = "mdm_prefs"
    const val KEY_ENABLED = "mdm_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
