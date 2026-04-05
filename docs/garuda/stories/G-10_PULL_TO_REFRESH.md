# G-10: Android pull-to-refresh on list tabs

## Status

**COMPLETE (baseline)** — The four main **tab** screens use **`androidx.compose.material.pullrefresh`**: `Modifier.pullRefresh`, `rememberPullRefreshState`, and `PullRefreshIndicator`, with **`@OptIn(ExperimentalMaterialApi::class)`**. ViewModels expose **`isRefreshing`** and reuse existing **load** / **refresh** entry points.

**Garuda checklist scope:** G-10 refers to these **Alerts, Cameras, Water Tanks, Edge Nodes** list experiences. Other screens (settings, login, zone drawer, video recordings list, etc.) are **out of scope** unless a follow-up story expands coverage.

## Implementation (authoritative)

| Screen | UI | ViewModel / load |
|--------|-----|------------------|
| Alerts | [`AlertFeedScreen.kt`](android/app/src/main/java/com/sudarshanchakra/ui/screens/alerts/AlertFeedScreen.kt) | [`AlertViewModel`](android/app/src/main/java/com/sudarshanchakra/ui/screens/alerts/AlertViewModel.kt) — `AlertFeedUiState.isRefreshing`, `refresh()` → `loadAlerts(fullScreenLoading = false)` |
| Cameras | [`CameraGridScreen.kt`](android/app/src/main/java/com/sudarshanchakra/ui/screens/cameras/CameraGridScreen.kt) | [`CameraViewModel`](android/app/src/main/java/com/sudarshanchakra/ui/screens/cameras/CameraGridScreen.kt) — `CameraGridUiState.isRefreshing`, `loadCameras()` |
| Water | [`WaterTanksScreen.kt`](android/app/src/main/java/com/sudarshanchakra/ui/screens/water/WaterTanksScreen.kt) | [`WaterViewModel`](android/app/src/main/java/com/sudarshanchakra/ui/screens/water/WaterViewModel.kt) — `WaterUiState.isRefreshing`, `refresh()` |
| Edge nodes | [`DeviceStatusScreen.kt`](android/app/src/main/java/com/sudarshanchakra/ui/screens/devices/DeviceStatusScreen.kt) | **`DeviceViewModel`** in the same file — `DeviceUiState.isRefreshing`, `loadNodes()` |

**Also implemented (not in the original four-file list):** [`SirenControlScreen.kt`](android/app/src/main/java/com/sudarshanchakra/ui/screens/siren/SirenControlScreen.kt), [`MotorControlScreen.kt`](android/app/src/main/java/com/sudarshanchakra/ui/screens/water/MotorControlScreen.kt).

## API note (story vs code)

An older draft used Material3 **`PullToRefreshBox`** and `material3.pulltorefresh`. This app intentionally uses **Material (M2) `pullrefresh`** alongside Material3 widgets — see [`android/app/build.gradle.kts`](android/app/build.gradle.kts) comment. That draft snippet is **not** the source of truth.

## Verification

```bash
cd android && ./gradlew assembleDebug
# On device/emulator: Alerts, Cameras, Water, Edge Nodes — swipe down when content or error/empty is shown; indicator appears and data reloads.
```

## UX note

Pull-to-refresh is available on **loading, error, and empty** states as well as populated lists (where a vertically scrollable host is required for the gesture), so users can **retry** without only using the toolbar refresh icon.

## Follow-ups (optional product work)

- Migrate to Material3 pull-to-refresh only if the team standardizes and BOM alignment allows it without extra artifact/version skew.
- “Pull-to-refresh on **every** scrollable screen” is a wider scope than this story.
