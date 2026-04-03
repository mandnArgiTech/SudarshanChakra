# G-05: SaaS Test Coverage

## Status
NOT DONE — FarmService, PermissionService, ModuleResolutionService, ModuleAccessGatewayFilter have zero tests.

## Prerequisites
G-01 through G-04 complete.

## Files to CREATE

### 1. `backend/auth-service/src/test/java/com/sudarshanchakra/auth/service/FarmServiceTest.java`

Follow pattern of `backend/auth-service/src/test/java/com/sudarshanchakra/auth/service/AuthServiceTest.java`.

Tests (minimum 5):
```java
@Test void createFarm_savesAndReturnsResponse()
@Test void createFarm_duplicateSlug_throwsException()
@Test void getFarmById_found_returnsResponse()
@Test void getFarmById_notFound_throwsException()
@Test void suspendFarm_setsStatusSuspended()
```

Use `@ExtendWith(MockitoExtension.class)`, `@Mock FarmRepository`, `@InjectMocks FarmService`.

### 2. `backend/auth-service/src/test/java/com/sudarshanchakra/auth/service/PermissionServiceTest.java`

Tests (minimum 5):
```java
@Test void superAdmin_hasAllPermissions()
@Test void viewer_cannotTriggerSiren()
@Test void viewer_canViewAlerts()
@Test void manager_canAcknowledgeAlerts()
@Test void operator_cannotDeleteZones()
```

### 3. `backend/auth-service/src/test/java/com/sudarshanchakra/auth/service/ModuleResolutionServiceTest.java`

Tests (minimum 3):
```java
@Test void resolveModules_farmModules_returnsFarmList()
@Test void resolveModules_userOverride_returnsUserList()
@Test void resolveModules_emptyModules_returnsAllModules()
```

### 4. `backend/api-gateway/src/test/java/com/sudarshanchakra/gateway/filter/ModuleAccessGatewayFilterTest.java`

Tests (minimum 4):
```java
@Test void allowedModule_passes()
@Test void disabledModule_returns403()
@Test void noModulesInJwt_treatedAsFullAccess()
@Test void publicEndpoint_noModuleCheck()
```

Note: Gateway tests may need `@SpringBootTest(webEnvironment=RANDOM_PORT)` with `WebTestClient` or mock the exchange directly.

## Verification
```bash
cd backend && ./gradlew :auth-service:test :api-gateway:test --info 2>&1 | grep -E "PASSED|FAILED|tests"
# Expected: 17+ new tests passing, zero failures
```
