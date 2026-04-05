# G-05: SaaS Test Coverage

## Status
**COMPLETE (backend)** — Unit tests added for `FarmService`, `PermissionService`, `ModuleResolutionService`, and extended `ModuleAccessGatewayFilterTest`.

## Prerequisites
G-01 through G-04 complete.

## Implemented

### 1. `backend/auth-service/src/test/java/com/sudarshanchakra/auth/service/FarmServiceTest.java`

`@ExtendWith(MockitoExtension.class)`, `@Mock` repositories + `PasswordEncoder`, `@InjectMocks FarmService`.

- `createFarm_savesAndReturnsResponse`
- `createFarm_duplicateSlug_throwsException`
- `getFarmById_found_returnsResponse`
- `getFarmById_notFound_throwsException`
- `suspendFarm_setsStatusSuspended`
- `createFarm_withInitialAdmin_savesUser` (initial admin branch)
- `activateFarm_setsStatusActive`

### 2. `backend/auth-service/src/test/java/com/sudarshanchakra/auth/service/PermissionServiceTest.java`

Stateless `PermissionService` — no mocks.

- `superAdmin_hasAllPermissions`
- `viewer_cannotTriggerSiren`
- `viewer_canViewAlerts`
- `manager_canAcknowledgeAlerts`
- `operator_cannotDeleteZones`

### 3. `backend/auth-service/src/test/java/com/sudarshanchakra/auth/service/ModuleResolutionServiceTest.java`

`@Mock FarmRepository`, `@InjectMocks ModuleResolutionService`.

- `resolveModules_farmModules_returnsFarmList`
- `resolveModules_userOverride_returnsUserList` (verifies no farm lookup when override set)
- `resolveModules_emptyModules_returnsAllModules`
- `resolveModules_superAdmin_returnsAllModules` (bonus)

### 4. `backend/api-gateway/src/test/java/com/sudarshanchakra/gateway/filter/ModuleAccessGatewayFilterTest.java`

Existing reactive unit tests (`MockServerWebExchange` + `StepVerifier`) plus:

- `camerasPath_forbiddenWhenModulesExcludeCameras` (maps to disabled module / 403)
- `camerasPath_okWhenModulesIncludesCameras` (maps to allowed module passes)
- `noModulesInJwt_treatedAsFullAccess`
- `publicEndpoint_noModuleCheck`

## Out of scope (checklist vs story)

Dashboard **Admin pages** Vitest coverage is tracked separately on the master checklist; this story only specified **backend** tests.

## Verification
```bash
cd backend && ./gradlew :auth-service:test :api-gateway:test --info 2>&1 | grep -E "PASSED|FAILED|tests"
# Expected: all tests passing, zero failures
```
