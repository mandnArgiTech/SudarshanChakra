# G-10: Android Pull-to-Refresh

## File to MODIFY (4 files)

### Pattern to apply to each screen:

1. Add `isRefreshing` to the ViewModel's UI state:
```kotlin
data class UiState(
    // ... existing fields ...
    val isRefreshing: Boolean = false,
)
```

2. Add `refresh()` to ViewModel:
```kotlin
fun refresh() {
    viewModelScope.launch {
        _uiState.update { it.copy(isRefreshing = true) }
        // Re-fetch data (call existing load function)
        loadData()
        _uiState.update { it.copy(isRefreshing = false) }
    }
}
```

3. Wrap the `LazyColumn` (or `Column`) in the screen Composable:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)

val pullRefreshState = rememberPullToRefreshState()

PullToRefreshBox(
    isRefreshing = uiState.isRefreshing,
    onRefresh = { viewModel.refresh() },
    state = pullRefreshState,
) {
    LazyColumn { /* existing content */ }
}
```

### Apply to these 4 files:

**1. `android/.../ui/screens/alerts/AlertFeedScreen.kt`** + `AlertViewModel.kt`
**2. `android/.../ui/screens/cameras/CameraGridScreen.kt`** (add ViewModel if doesn't have one, or use `rememberCoroutineScope`)
**3. `android/.../ui/screens/water/WaterTanksScreen.kt`** + `WaterViewModel.kt`
**4. `android/.../ui/screens/devices/DeviceStatusScreen.kt`** (add refresh via DeviceRepository)

### Import needed:
```kotlin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
```

## Verification
```bash
cd android && ./gradlew assembleDebug
# Install on emulator, navigate to Alerts tab
# Swipe down → spinner appears → list refreshes
# Repeat on Cameras, Water, Devices tabs
```

---

