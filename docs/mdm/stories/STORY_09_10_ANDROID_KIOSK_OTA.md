# Story 09: Android Device Owner + Kiosk Mode

## Prerequisites
- Story 06-08 complete

## Goal
Implement DeviceAdminReceiver, KioskManager (policy enforcement), KioskLauncherActivity (home screen with whitelisted app grid), and hidden developer escape hatch.

## Reference
- `docs/mdm/MDM_KIOSK_PLAN.md` — sections 4c, 4h, 4i, 4j for exact Kotlin code

## Files to CREATE

### 1. `android/app/src/main/res/xml/device_admin_policies.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <limit-password />
        <watch-login />
        <reset-password />
        <force-lock />
        <wipe-data />
        <expire-password />
        <encrypted-storage />
        <disable-camera />
        <disable-keyguard-features />
    </uses-policies>
</device-admin>
```

### 2. `android/app/src/main/java/com/sudarshanchakra/mdm/SudarshanDeviceAdminReceiver.kt`
```kotlin
package com.sudarshanchakra.mdm

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class SudarshanDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun getComponentName(context: Context): ComponentName =
            ComponentName(context, SudarshanDeviceAdminReceiver::class.java)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i("MDM", "Device admin enabled")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.i("MDM", "Profile provisioning complete — applying kiosk policies")
        KioskManager(context).enforceKioskPolicies()
    }
}
```

### 3. `android/app/src/main/java/com/sudarshanchakra/mdm/KioskManager.kt`
Full implementation as specified in `MDM_KIOSK_PLAN.md` section 4c.
Key methods:
- `isDeviceOwner: Boolean`
- `enforceKioskPolicies()` — all 8 policy items
- `exitKiosk(pin: String): Boolean`
- `decommission(pin: String): Boolean`
- `applyPolicies(policiesJson: JsonObject)` — for remote SET_POLICY command

### 4. `android/app/src/main/java/com/sudarshanchakra/mdm/KioskLauncherActivity.kt`
Compose activity that:
- Shows the main SudarshanChakra NavGraph (existing dashboard)
- Adds a bottom row of whitelisted app icons (WhatsApp, YouTube, Maps, Camera, Phone)
- Calls `startLockTask()` in `onResume()` if Device Owner

### 5. `android/app/src/main/java/com/sudarshanchakra/mdm/DevEscapeDialog.kt`
Composable dialog:
- PIN input (4-6 digits)
- Two buttons: "Exit Kiosk Mode" and "Decommission Device"
- Triggered from SettingsScreen when version text tapped 7 times

## Files to MODIFY

### 1. `android/app/src/main/AndroidManifest.xml`
Add BEFORE the `<application>` closing tag:
```xml
<!-- MDM Device Admin Receiver -->
<receiver
    android:name=".mdm.SudarshanDeviceAdminReceiver"
    android:permission="android.permission.BIND_DEVICE_ADMIN"
    android:exported="true">
    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/device_admin_policies" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
        <action android:name="android.app.action.PROFILE_PROVISIONING_COMPLETE" />
    </intent-filter>
</receiver>

<!-- Kiosk Launcher (becomes HOME when Device Owner) -->
<activity
    android:name=".mdm.KioskLauncherActivity"
    android:exported="true"
    android:launchMode="singleInstance"
    android:theme="@style/Theme.SudarshanChakra">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

Add permissions:
```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

### 2. `android/app/src/main/java/com/sudarshanchakra/ui/screens/settings/SettingsScreen.kt`
Add the 7-tap escape hatch on the version text (see MDM_KIOSK_PLAN.md section 4j).

## Verification
```bash
cd android && ./gradlew assembleDebug
# Install on emulator:
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Provision as Device Owner (factory reset emulator first):
adb shell dpm set-device-owner com.sudarshanchakra/.mdm.SudarshanDeviceAdminReceiver
# App should become home launcher with kiosk mode active
```

---

# Story 10: Android MQTT Command Handler + Silent OTA

## Prerequisites
- Story 09 complete (KioskManager exists)

## Goal
Extend the existing MqttForegroundService to subscribe to MDM command topics. Implement SilentInstaller for OTA updates.

## Files to CREATE

### 1. `android/app/src/main/java/com/sudarshanchakra/mdm/MdmCommandHandler.kt`
```kotlin
package com.sudarshanchakra.mdm

import android.content.Context
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.JsonObject

class MdmCommandHandler(
    private val context: Context,
    private val kioskManager: KioskManager,
    private val silentInstaller: SilentInstaller,
) {
    private val gson = Gson()

    fun handle(topic: String, payload: String) {
        val cmd = gson.fromJson(payload, JsonObject::class.java)
        val command = cmd.get("command")?.asString ?: return
        val commandId = cmd.get("command_id")?.asString ?: ""

        when (command) {
            "UPDATE_APP" -> {
                val apkUrl = cmd.getAsJsonObject("payload")?.get("apk_url")?.asString ?: return
                silentInstaller.downloadAndInstall(apkUrl)
            }
            "LOCK_SCREEN" -> {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as android.app.admin.DevicePolicyManager
                dpm.lockNow()
            }
            "WIPE_DEVICE" -> {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as android.app.admin.DevicePolicyManager
                dpm.wipeData(0)
            }
            "SYNC_TELEMETRY" -> {
                WorkManager.getInstance(context).enqueue(
                    OneTimeWorkRequest.from(TelemetryUploadWorker::class.java)
                )
            }
            "SET_POLICY" -> {
                cmd.getAsJsonObject("payload")?.let { kioskManager.applyPolicies(it) }
            }
        }
    }
}
```

### 2. `android/app/src/main/java/com/sudarshanchakra/mdm/SilentInstaller.kt`
```kotlin
package com.sudarshanchakra.mdm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import java.io.InputStream
import java.net.URL

class SilentInstaller(private val context: Context) {

    fun downloadAndInstall(apkUrl: String) {
        Thread {
            try {
                val input: InputStream = URL(apkUrl).openStream()
                installFromStream(input)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun installFromStream(input: InputStream) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        session.openWrite("update.apk", 0, -1).use { out ->
            input.copyTo(out)
            session.fsync(out)
        }
        input.close()

        val intent = PendingIntent.getBroadcast(
            context, sessionId,
            Intent("com.sudarshanchakra.INSTALL_COMPLETE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        session.commit(intent.intentSender)
    }
}
```

### 3. `android/app/src/main/java/com/sudarshanchakra/mdm/InstallResultReceiver.kt`
```kotlin
package com.sudarshanchakra.mdm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Log.i("MDM", "OTA install successful")
            else -> Log.e("MDM", "OTA install failed: $status $msg")
        }
    }
}
```

## Files to MODIFY

### 1. `android/app/src/main/java/com/sudarshanchakra/service/MqttForegroundService.kt`
In the `subscribeToTopic` / `connectAndSubscribe` method, add subscription to MDM command topic:
```kotlin
// After existing alert subscriptions, add:
val deviceId = getDeviceId() // from SharedPrefs or Settings.Secure.ANDROID_ID
subscribeToTopic(client, "farm/mdm/$deviceId/command")

// In message handler, route MDM commands:
if (topic.contains("/mdm/") && topic.endsWith("/command")) {
    val handler = MdmCommandHandler(this, KioskManager(this), SilentInstaller(this))
    handler.handle(topic, payload)
    return
}
```

## Verification
```bash
cd android && ./gradlew assembleDebug
# Test on Device Owner emulator:
# 1. Publish MQTT command: mosquitto_pub -t "farm/mdm/<device-id>/command" -m '{"command":"LOCK_SCREEN","command_id":"test"}'
# 2. Device screen should lock
```
