# Android app — code quality & resilience

This document summarizes stability-focused review items and the mitigations applied in the SudarshanChakra Android module.

## Threading & async

| Issue | Risk | Mitigation |
|--------|------|------------|
| **`AuthInterceptor` read JWT via `runBlocking { dataStore }` on every HTTP call** | Blocks OkHttp dispatcher threads; can stall under load; poor coroutine hygiene | **`AuthTokenCache`** (`AtomicReference`) holds the JWT; interceptor is synchronous read-only. **`AuthRepository`** updates the cache on login/register/logout/save. **`SudarshanChakraApp.onCreate`** calls **`AuthRepository.syncTokenCacheFromDataStore()`** (via **`AppBootstrapEntryPoint`**) so cold start requests include the token. |
| **`ServerSettingsViewModel` collected `repository.settings` forever** | DataStore emissions could reset URL fields while the user was typing | **`init` uses `repository.settings.first()`** once; save/reset paths already update UI state explicitly. |

## Logging & release builds

| Issue | Mitigation |
|--------|------------|
| **`HttpLoggingInterceptor.Level.BODY` always on** | **`Level.BODY` only if `BuildConfig.DEBUG`**, else **`Level.NONE`** (avoids noisy logs and accidental credential/body leakage in release). |

## Duplication & error handling

| Area | Mitigation |
|------|------------|
| Repeated Retrofit `try / isSuccessful / body / failure` blocks | Shared **`suspend fun <T> executeApi(block: suspend () -> Response<T>): Result<T>`** in `data/api/ApiCall.kt` (HTTP code + short error-body snippet). Used by **`DeviceRepository`**, **`SirenRepository`**, **`AlertRepository`** (with cache fallback where applicable), **`AuthRepository`** (login/register with post-success suspend side effects via **`fold`**). |

## Production overhaul (implemented highlights)

- **Back** on alert home → `moveTaskToBack`; **Settings** tab with logout (confirm), server link, critical notification channel shortcut, version.
- **Notification deep link** → `MainNavViewModel` + `MainActivity` intent extras; **3 channels** (service / high / critical with alarm+vibration).
- **`BootReceiver`**: restarts MQTT after boot if DataStore has a session token.
- **Bottom nav**: Material icons + `NavigationBar`.
- **UX**: dark theme (system), connection banner (MQTT `StateFlow`), refresh on feeds, camera grid auto-refresh ~4s, siren confirm dialog, haptics on siren/motor/alert actions, relative timestamps.

**Deferred (optional polish):** launcher unread badge (`ShortcutBadger`), biometric app lock, swipe-to-ack on list rows.

## Ongoing / manual review

- **`MqttForegroundService`**: verify coroutine scopes and callback lifecycles when the service is stopped or the process is killed; avoid holding Activity/Fragment references from long-lived components.
- **Navigation**: duplicate paths to the same destination (e.g. login + `LaunchedEffect`) are redundant but low risk; consolidate if routes diverge later.
- **401 handling**: if the backend returns 401, consider a single path to clear **`AuthTokenCache`** + DataStore session and navigate to login (not all call sites may handle this uniformly yet).

## Build verification

```bash
cd android && ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug --no-daemon
```
