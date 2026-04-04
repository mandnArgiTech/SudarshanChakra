package com.sudarshanchakra.mdm

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.google.gson.JsonObject
import java.security.MessageDigest

class KioskManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val componentName = SudarshanDeviceAdminReceiver.getComponentName(context)
    val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(context.packageName)

    fun enforceKioskPolicies() {
        if (!isDeviceOwner) {
            Log.w(TAG, "Not device owner — skipping kiosk policy enforcement")
            return
        }

        val kioskLauncherComponent = ComponentName(context, "com.sudarshanchakra.mdm.KioskLauncherActivity")
        val homeFilter = IntentFilter(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            addCategory(android.content.Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(componentName, homeFilter, kioskLauncherComponent)

        val allowedPackages = arrayOf(
            context.packageName,
            "com.whatsapp",
            "com.google.android.youtube",
            "com.google.android.apps.maps",
            "com.android.camera2",
            "com.google.android.dialer",
            "com.android.dialer",
        )
        dpm.setLockTaskPackages(componentName, allowedPackages)

        dpm.setStatusBarDisabled(componentName, true)

        val restrictions = arrayOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_CONFIG_WIFI,
            UserManager.DISALLOW_CONFIG_LOCATION,
            UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
        )
        for (r in restrictions) {
            dpm.addUserRestriction(componentName, r)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setLocationEnabled(componentName, true)
        }

        @Suppress("DEPRECATION")
        val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiMgr?.isWifiEnabled = true

        val autoGrantPermissions = arrayOf(
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )
        for (perm in autoGrantPermissions) {
            dpm.setPermissionGrantState(
                componentName,
                context.packageName,
                perm,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
            )
        }

        dpm.setPermissionPolicy(componentName, DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT)

        MdmConfig.setEnabled(context, true)
        MdmWorkScheduler.schedule(context)

        initDefaultPinIfAbsent()

        Log.i(TAG, "Kiosk policies enforced successfully")
    }

    fun exitKiosk(pin: String): Boolean {
        if (!verifyEscapePin(pin)) return false
        if (!isDeviceOwner) return false

        dpm.setLockTaskPackages(componentName, emptyArray())

        dpm.setStatusBarDisabled(componentName, false)

        (context as? Activity)?.stopLockTask()

        Log.i(TAG, "Kiosk mode exited")
        return true
    }

    fun decommission(pin: String): Boolean {
        if (!exitKiosk(pin)) return false
        try {
            val restrictions = arrayOf(
                UserManager.DISALLOW_FACTORY_RESET,
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_CONFIG_WIFI,
                UserManager.DISALLOW_CONFIG_LOCATION,
                UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
            )
            for (r in restrictions) {
                dpm.clearUserRestriction(componentName, r)
            }
            dpm.clearDeviceOwnerApp(context.packageName)
            MdmConfig.setEnabled(context, false)
            Log.i(TAG, "Device decommissioned — Device Owner cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Decommission failed", e)
            return false
        }
        return true
    }

    fun applyPolicies(policiesJson: JsonObject) {
        if (!isDeviceOwner) return
        try {
            if (policiesJson.has("location_interval_sec")) {
                val intervalSec = policiesJson.get("location_interval_sec").asLong
                val intervalMs = (intervalSec * 1000).coerceIn(15_000L, 3_600_000L)
                context.getSharedPreferences(MdmConfig.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong("mdm_location_interval_ms", intervalMs)
                    .apply()
                Log.i(TAG, "Location interval updated to ${intervalMs}ms")
            }
            if (policiesJson.has("status_bar_disabled")) {
                dpm.setStatusBarDisabled(componentName, policiesJson.get("status_bar_disabled").asBoolean)
            }
            if (policiesJson.has("camera_disabled")) {
                dpm.setCameraDisabled(componentName, policiesJson.get("camera_disabled").asBoolean)
            }
        } catch (e: Exception) {
            Log.w(TAG, "applyPolicies: ignoring malformed policy JSON", e)
        }
    }

    fun verifyEscapePin(pin: String): Boolean {
        val prefs = context.getSharedPreferences(MdmConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val storedHash = prefs.getString(KEY_ESCAPE_PIN_HASH, null) ?: sha256(DEFAULT_PIN)
        return sha256(pin) == storedHash
    }

    private fun initDefaultPinIfAbsent() {
        val prefs = context.getSharedPreferences(MdmConfig.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_ESCAPE_PIN_HASH)) {
            prefs.edit().putString(KEY_ESCAPE_PIN_HASH, sha256(DEFAULT_PIN)).apply()
        }
    }

    companion object {
        private const val TAG = "KioskManager"
        private const val KEY_ESCAPE_PIN_HASH = "escape_pin_hash"
        private const val DEFAULT_PIN = "1234"

        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
