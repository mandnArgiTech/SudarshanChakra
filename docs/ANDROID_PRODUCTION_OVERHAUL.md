# Android App Production Overhaul Plan

## The Problem

The current Android app is a POC, not a production security app. For a farm manager rushing to check a snake alert at 2 AM, this app fails them. Here's every issue found:

---

## Critical Issues (App Unusable Without These)

### C1: Back Press Kills the App

**Current:** No `BackHandler` anywhere. Pressing back on the main screen destroys the Activity and kills the MQTT foreground service connection. The user loses real-time alerts.

**Fix:** Add `BackHandler` to the main screen that minimizes to background instead of closing:

```kotlin
// In AlertFeedScreen or NavGraph scaffold
BackHandler {
    // Minimize to background, DON'T finish
    (context as? ComponentActivity)?.moveTaskToBack(true)
}
```

For inner screens (AlertDetail, MotorControl), back should navigate up, not kill the app. This already works via `navController.popBackStack()` — but the ROOT screen needs the override.

---

### C2: No Logout

**Current:** `AuthRepository.logout()` exists but is never called from any screen. No logout button in the UI. Users cannot switch accounts or log out.

**Fix:**
- Add a **Profile/Settings screen** (currently routes to DeviceStatusScreen as a placeholder)
- Add logout button that: clears JWT token, stops MQTT service, navigates to LoginScreen, clears the back stack
- Add logout confirmation dialog ("Logging out will stop alert notifications. Continue?")

---

### C3: Notification Tap Goes Nowhere

**Current:** Notification `PendingIntent` opens `MainActivity` generically. No alert ID is passed. User sees the app but not the specific alert that fired.

**Fix:** Pass alert data via Intent extras → deep link to AlertDetailScreen:

```kotlin
// In MqttForegroundService.showAlertNotification()
val intent = Intent(this, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    putExtra("NAVIGATE_TO", "alertDetail")
    putExtra("ALERT_ID", alertId)
    putExtra("ALERT_PRIORITY", priority)
}
```

MainActivity reads the extras and navigates:
```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val route = intent.getStringExtra("NAVIGATE_TO")
    val alertId = intent.getStringExtra("ALERT_ID")
    if (route == "alertDetail" && alertId != null) {
        navController.navigate(Routes.alertDetail(alertId))
    }
}
```

---

### C4: No Critical Alert Sound/Vibration

**Current:** Alert notifications use default `IMPORTANCE_HIGH` but no custom sound or vibration pattern. A snake alert at night sounds the same as a WhatsApp message.

**Fix:** For CRITICAL alerts, use alarm-level notification:

```kotlin
// Create the critical channel with alarm sound
val criticalChannel = NotificationChannel(
    "sc_critical_alerts",
    "Critical Farm Alerts",
    NotificationManager.IMPORTANCE_HIGH
).apply {
    description = "Snake, fire, child near pond — immediate danger"
    enableVibration(true)
    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500) // Aggressive pattern
    setSound(
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    )
    enableLights(true)
    lightColor = Color.RED
    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
}
```

Three notification channels:
- `sc_critical_alerts` — ALARM sound, aggressive vibration, wake screen
- `sc_high_alerts` — default sound, normal vibration
- `sc_service` — silent, low priority (MQTT service notification)

---

### C5: Service Doesn't Survive Reboot

**Current:** No `BOOT_COMPLETED` receiver. After phone restarts, MQTT service doesn't start → no alerts until user manually opens the app.

**Fix:** Add a `BootReceiver`:

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Only start if user was logged in
            if (AuthTokenStore.hasToken(context)) {
                MqttForegroundService.start(context)
            }
        }
    }
}
```

AndroidManifest:
```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<receiver android:name=".service.BootReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

---

## High Priority Issues (Bad UX)

### H1: Emoji Icons in Bottom Nav

**Current:** Uses emojis (🔔📷🚨📡💧👤) as navigation icons. Looks amateurish and renders inconsistently across Android versions/manufacturers.

**Fix:** Replace with Material Icons:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*

val items = listOf(
    NavItem(Routes.ALERTS,      "Alerts",  Icons.Filled.NotificationsActive, Icons.Outlined.Notifications),
    NavItem(Routes.CAMERAS,     "Cameras", Icons.Filled.Videocam,            Icons.Outlined.Videocam),
    NavItem(Routes.SIREN,       "Siren",   Icons.Filled.Campaign,            Icons.Outlined.Campaign),
    NavItem(Routes.DEVICES,     "Devices", Icons.Filled.Sensors,             Icons.Outlined.Sensors),
    NavItem(Routes.WATER_TANKS, "Water",   Icons.Filled.WaterDrop,           Icons.Outlined.WaterDrop),
    NavItem(Routes.PROFILE,     "Settings",Icons.Filled.Settings,            Icons.Outlined.Settings),
)
```

Use filled icon for selected state, outlined for unselected. Add `NavigationBar` from Material 3 (not custom Row).

---

### H2: Profile Screen Is a Placeholder

**Current:** `Routes.PROFILE` routes to `DeviceStatusScreen()` — shows device info when user expects profile/settings.

**Fix:** Create a proper `SettingsScreen.kt` with:
- User info (name, role, farm)
- Server connection status (MQTT connected/disconnected, API reachable)
- Notification preferences (toggle critical/high/warning)
- Logout button (with confirmation)
- App version
- About / Support link

---

### H3: No Pull-to-Refresh

**Current:** Zero pull-to-refresh on any screen. User has no way to manually refresh alerts, cameras, or water levels.

**Fix:** Add `pullToRefresh` modifier on all list screens:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
val pullRefreshState = rememberPullToRefreshState()

PullToRefreshBox(
    isRefreshing = uiState.isRefreshing,
    onRefresh = { viewModel.refresh() },
    state = pullRefreshState,
) {
    LazyColumn { ... }
}
```

Apply to: AlertFeedScreen, CameraGridScreen, DeviceStatusScreen, WaterTanksScreen.

---

### H4: No Dark Theme

**Current:** Only `lightColorScheme` defined. No dark mode. Checking a snake alert at 2 AM blasts the user with a bright white screen.

**Fix:** Add `darkColorScheme` and respect system setting:

```kotlin
@Composable
fun SudarshanChakraTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8A04A),       // Gold on dark
    background = Color(0xFF1A1814),    // Dark brown
    surface = Color(0xFF2A2620),       // Card dark
    onBackground = Color(0xFFE8E2D8), // Light cream text
    onSurface = Color(0xFFE8E2D8),
    error = Color(0xFFE85545),
    // ... complete dark palette
)
```

---

### H5: No Haptic Feedback on Critical Actions

**Current:** Siren trigger button has no haptic feedback. In an emergency, the user can't feel if they pressed the button.

**Fix:** Add haptics to siren trigger, alert acknowledge, and motor start/stop:

```kotlin
val haptic = LocalHapticFeedback.current

Button(onClick = {
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    viewModel.triggerSiren()
}) { ... }
```

---

### H6: No Connection Status Indicator

**Current:** No visible indicator showing whether the app is connected to the backend. User doesn't know if alerts are flowing.

**Fix:** Add a persistent connection banner at the top of AlertFeedScreen:

```
Connected ● (green dot)
Reconnecting... ● (amber, pulsing)
Disconnected ● (red) — tap to retry
```

Check MQTT service status + API reachability.

---

## Medium Priority Issues (Polish)

### M1: No Alert Count Badge on App Icon

Add unread alert count badge using `ShortcutBadger` or the built-in Android `NotificationManager` badge support.

### M2: No Biometric/PIN Lock

For a security app, add optional biometric or PIN lock on app open. Use `BiometricPrompt` from AndroidX.

### M3: No Offline Mode Indicator

When no internet, show a banner and serve cached alerts from Room DB. Currently the Room DB exists (`AlertDao`, `AlertEntity`) but may not be used as a fallback.

### M4: Alert Timestamp Shows Absolute, Not Relative

Show "2 minutes ago" not "2026-03-20T14:32:00Z". Use a relative time formatter.

### M5: No Alert Sound Preview in Settings

Let users preview what the critical alert sounds like before a real emergency.

### M6: Siren Button Needs Confirmation Dialog

**Current:** One tap triggers the siren. Accidental taps in pocket will blast the 250W PA system.

**Fix:** Two-step: first tap shows "TRIGGER SIREN? This will activate the 250W PA system" → Confirm/Cancel.

### M7: No Swipe Actions on Alert Cards

Swipe right to acknowledge, swipe left to mark false positive. Standard mobile pattern for quick triage.

### M8: Camera Grid Shows Static Snapshots

Add auto-refresh every 3-5 seconds (poll edge `/api/snapshot/{cam_id}` endpoint).

---

## File Changes Summary

### New Files

| File | Purpose |
|:-----|:--------|
| `ui/screens/settings/SettingsScreen.kt` | Profile/settings screen with logout, preferences, connection status |
| `ui/screens/settings/SettingsViewModel.kt` | Settings state management |
| `service/BootReceiver.kt` | BOOT_COMPLETED → auto-start MQTT service |
| `util/RelativeTimeFormatter.kt` | "2 minutes ago" time display |
| `ui/components/ConnectionBanner.kt` | MQTT/API connection status bar |
| `ui/components/ConfirmDialog.kt` | Reusable confirmation dialog (siren, logout) |

### Modified Files

| File | Changes |
|:-----|:--------|
| `AndroidManifest.xml` | BOOT_COMPLETED permission + BootReceiver |
| `MqttForegroundService.kt` | 3 notification channels, alert deep link, alarm sound for critical |
| `MainActivity.kt` | Handle deep link intent extras, BackHandler |
| `NavGraph.kt` | SettingsScreen route, deep link handling |
| `BottomNavBar.kt` | Material Icons replace emojis, NavigationBar component |
| `ui/theme/Theme.kt` | Dark theme + isSystemInDarkTheme |
| `ui/theme/Color.kt` | Dark color palette |
| `AlertFeedScreen.kt` | Pull-to-refresh, BackHandler, connection banner |
| `SirenControlScreen.kt` | Confirmation dialog, haptic feedback |
| `CameraGridScreen.kt` | Pull-to-refresh, auto-refresh snapshots |
| `WaterTanksScreen.kt` | Pull-to-refresh |
| `AlertCard.kt` | Relative time, swipe actions |

---

## Priority Implementation Order

**Phase 1 — Stops being broken (1 day):**
C1 (back press), C2 (logout), C3 (notification deep link), C4 (critical sound), C5 (boot receiver), H1 (Material icons), H2 (settings screen)

**Phase 2 — Feels professional (1 day):**
H3 (pull-to-refresh), H4 (dark theme), H5 (haptics), H6 (connection status), M6 (siren confirmation)

**Phase 3 — Production polish (1 day):**
M1 (badge), M2 (biometric), M3 (offline), M4 (relative time), M5 (sound preview), M7 (swipe), M8 (camera refresh)

---

## Non-Breaking Guarantee

All changes are additive — existing screens keep their functionality. The navigation structure stays the same (just Routes.PROFILE → SettingsScreen instead of DeviceStatusScreen). No APIs change. No backend changes needed.
