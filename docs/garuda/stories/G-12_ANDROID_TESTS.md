# G-12: Android test coverage (ViewModels)

## Status: COMPLETE (ViewModel unit tests)

This story delivers **unit tests for four screens’ ViewModels** under `android/app/src/test/...`, with **`kotlinx-coroutines-test`** and **MockK**, and a shared **`MainDispatcherRule`** (`StandardTestDispatcher` + `Dispatchers.setMain`).

**Out of scope here:** Compose **UI** tests (`createComposeRule`, etc.) — see the separate Garuda checklist row *UI component tests (G-12)*.

## Reality vs early draft

| Area | Note |
|------|------|
| **WaterViewModel** | Depends on **`ApiService`** (Retrofit `Response.success` / exceptions), not a dedicated repository type. |
| **AlertViewModel** | Needs **`AlertRepository`**, **`AlertBadgeRepository`**, and **`ServerSettingsRepository.settings`** as a **`Flow<ServerSettings>`** for `edgeGuiBaseUrl`. **`init`** runs **`loadAlerts(true)`** — assert **steady state after `advanceUntilIdle()`** (e.g. `isLoading == false`), not the first pre-`init` `StateFlow` value. |
| **SirenViewModel** | **`SirenRepository`** + **`DeviceRepository`**; **`init`** calls **`loadData`**. Tests use **`coVerify`** on **`triggerSiren` / `stopSiren`** (and mock **`getNodes` / `getHistory`** for load). |
| **LoginViewModel** | **`init`** loads **`getRememberedLoginForm()`** — **`advanceUntilIdle()`** before asserting username/password. |
| **Mocking repositories** | Production repositories are **classes**; they are marked **`open`** so MockK can subclass them on the JVM. **`ApiService`** remains an **interface** (no change). |

## Files

| File | Role |
|------|------|
| `android/app/build.gradle.kts` | `testImplementation` **kotlinx-coroutines-test:1.7.3**, **mockk:1.13.8** |
| `android/app/src/test/java/com/sudarshanchakra/MainDispatcherRule.kt` | Shared main dispatcher for VM tests |
| `android/app/src/test/java/com/sudarshanchakra/ui/screens/alerts/AlertViewModelTest.kt` | Load success/failure, refresh → extra `getAlerts` |
| `android/app/src/test/java/com/sudarshanchakra/ui/screens/water/WaterViewModelTest.kt` | ApiService mocks, error path, refresh settles |
| `android/app/src/test/java/com/sudarshanchakra/ui/screens/siren/SirenViewModelTest.kt` | `triggerAllSirens` / `stopAllSirens` / `toggleNodeSiren` → repository calls |
| `android/app/src/test/java/com/sudarshanchakra/ui/screens/login/LoginViewModelTest.kt` | Remembered form, blank validation, login success/failure |

## Verification

From repo root (with Android SDK configured, e.g. `ANDROID_HOME` or `android/local.properties` **`sdk.dir`**):

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

Optional smoke:

```bash
./gradlew :app:testDebugUnitTest 2>&1 | grep -E "PASSED|FAILED|tests"
```

`connectedAndroidTest` / emulator tests are **not** required for this story.

## Garuda checklist

- **[x]** ViewModel unit tests (G-12) — this document.
- **[ ]** UI component tests (G-12) — follow-up (Compose UI test harness).
