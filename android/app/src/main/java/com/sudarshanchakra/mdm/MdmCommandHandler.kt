package com.sudarshanchakra.mdm

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sudarshanchakra.service.MqttForegroundService

class MdmCommandHandler(
    private val context: Context,
    private val kioskManager: KioskManager,
    private val silentInstaller: SilentInstaller,
    private val ackCallback: ((String, String, Boolean) -> Unit)? = null,
) {

    private val gson = Gson()

    fun handle(topic: String, payload: String) {
        val json = try {
            gson.fromJson(payload, JsonObject::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse command payload", e)
            return
        }

        val command = json.get("command")?.asString ?: run {
            Log.w(TAG, "Command payload missing 'command' field")
            return
        }
        val commandId = json.get("command_id")?.asString ?: ""

        Log.i(TAG, "Received command: $command (id=$commandId) on topic=$topic")

        try {
            when (command) {
                "UPDATE_APP" -> handleUpdateApp(json, commandId)
                "LOCK_SCREEN" -> handleLockScreen(commandId)
                "WIPE_DEVICE" -> handleWipeDevice(commandId)
                "SYNC_TELEMETRY" -> handleSyncTelemetry(commandId)
                "SET_POLICY" -> handleSetPolicy(json, commandId)
                else -> {
                    Log.w(TAG, "Unknown command: $command")
                    ack(commandId, command, false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling command $command", e)
            ack(commandId, command, false)
        }
    }

    private fun handleUpdateApp(json: JsonObject, commandId: String) {
        val apkUrl = json.get("apk_url")?.asString
        if (apkUrl.isNullOrBlank()) {
            Log.w(TAG, "UPDATE_APP missing apk_url")
            ack(commandId, "UPDATE_APP", false)
            return
        }
        ack(commandId, "UPDATE_APP", true)
        silentInstaller.downloadAndInstall(apkUrl)
    }

    private fun handleLockScreen(commandId: String) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.lockNow()
        ack(commandId, "LOCK_SCREEN", true)
    }

    private fun handleWipeDevice(commandId: String) {
        if (!kioskManager.isDeviceOwner) {
            Log.w(TAG, "WIPE_DEVICE rejected — not device owner")
            ack(commandId, "WIPE_DEVICE", false)
            return
        }
        ack(commandId, "WIPE_DEVICE", true)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.wipeData(0)
    }

    private fun handleSyncTelemetry(commandId: String) {
        MdmWorkScheduler.scheduleSyncNow(context)
        ack(commandId, "SYNC_TELEMETRY", true)
    }

    private fun handleSetPolicy(json: JsonObject, commandId: String) {
        val policies = json.getAsJsonObject("policies")
        if (policies == null) {
            Log.w(TAG, "SET_POLICY missing 'policies' object")
            ack(commandId, "SET_POLICY", false)
            return
        }

        kioskManager.applyPolicies(policies)

        if (policies.has("location_interval_sec")) {
            val intervalSec = policies.get("location_interval_sec").asLong
            val intervalMs = (intervalSec * 1000).coerceIn(15_000L, 3_600_000L)
            context.getSharedPreferences(MdmConfig.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong("mdm_location_interval_ms", intervalMs)
                .apply()

            val intent = Intent(context, MqttForegroundService::class.java).apply {
                action = MqttForegroundService.ACTION_UPDATE_LOCATION_INTERVAL
            }
            context.startService(intent)
        }

        ack(commandId, "SET_POLICY", true)
    }

    private fun ack(commandId: String, command: String, success: Boolean) {
        if (commandId.isNotBlank()) {
            ackCallback?.invoke(commandId, command, success)
        }
    }

    companion object {
        private const val TAG = "MdmCommandHandler"
    }
}
