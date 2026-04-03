# G-12: Android Test Coverage

## Files to CREATE

### 1. `android/app/src/test/java/com/sudarshanchakra/ui/screens/alerts/AlertViewModelTest.kt`
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class AlertViewModelTest {
    @Test fun `initial state has empty alerts`()
    @Test fun `loadAlerts updates uiState with alerts`()
    @Test fun `refresh sets isRefreshing and reloads`()
}
```

### 2. `android/app/src/test/java/com/sudarshanchakra/ui/screens/water/WaterViewModelTest.kt`
```kotlin
class WaterViewModelTest {
    @Test fun `initial state has empty tanks`()
    @Test fun `loadTanks fetches from repository`()
    @Test fun `refresh reloads tanks`()
}
```

### 3. `android/app/src/test/java/com/sudarshanchakra/ui/screens/siren/SirenViewModelTest.kt`
```kotlin
class SirenViewModelTest {
    @Test fun `triggerSiren calls repository`()
    @Test fun `stopSiren calls repository`()
}
```

### 4. `android/app/src/test/java/com/sudarshanchakra/ui/screens/login/LoginViewModelTest.kt`
```kotlin
class LoginViewModelTest {
    @Test fun `login success sets isLoggedIn`()
    @Test fun `login failure sets error message`()
}
```

### Dependencies needed in `build.gradle.kts` (testImplementation):
```kotlin
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
testImplementation("io.mockk:mockk:1.13.8")
```

## Verification
```bash
cd android && ./gradlew test 2>&1 | grep -E "PASSED|FAILED|tests"
# Expected: 10+ tests passing
```

---

