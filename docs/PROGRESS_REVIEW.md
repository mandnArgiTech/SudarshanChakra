# SudarshanChakra — Progress Review & SaaS Gap Analysis

**Date:** March 22, 2026
**Repo:** 518 files, 80 commits, 137 Java + 67 Kotlin
**Focus:** MULTI_TENANT_SAAS_PLAN.md implementation review

---

## SaaS Plan — Task-by-Task Scorecard

### Phase 1: Foundation (5 tasks)

| Task | Plan | Implemented | Gaps |
|:-----|:-----|:-----------|:-----|
| T1: `farms` table | Create table + seed your farm | ✅ `farms` table in init.sql + migration_002. Farm.java (101 lines), FarmService (162 lines), FarmController with full CRUD (GET, POST, PUT, suspend, activate), FarmRepository | None |
| T2: SUPER_ADMIN + OPERATOR roles | Add to Role enum | ✅ Role enum has `SUPER_ADMIN`, `ADMIN`, `MANAGER`, `OPERATOR`, `VIEWER` | None |
| T3: Modules in JWT | Farm.modulesEnabled + JWT modules claim | ✅ Farm entity has `modulesEnabled` JSONB. JwtService adds `modules` claim. ModuleResolutionService (32 lines) resolves user overrides vs farm defaults | None |
| T4: TenantContext + Hibernate filter | ThreadLocal context + @Filter on entities | ⚠️ **PARTIAL** — TenantContext.java exists (43 lines) with ThreadLocal farm_id/role, but **zero @FilterDef/@Filter annotations on any entity**. Hibernate automatic tenant filtering is NOT wired. | **GAP: Tenant isolation relies on manual WHERE farm_id=? in queries, not automatic Hibernate filtering** |
| T5: Audit log + AOP | Table + @Auditable aspect | ⚠️ **PARTIAL** — audit_log table, AuditLog.java, AuditService (41 lines), AuditController all exist. But **zero @Aspect or @Auditable annotations** — audit logging must be called manually, not auto-triggered | **GAP: No AOP aspect. Every auditable action needs manual `auditService.log()` calls** |

### Phase 2: RBAC Enforcement (5 tasks)

| Task | Plan | Implemented | Gaps |
|:-----|:-----|:-----------|:-----|
| T6: Permission matrix | PermissionService with role→permissions map | ✅ PermissionService.java (121 lines) with full permission matrix | None |
| T7: @RequiresPermission per endpoint | Annotation + filter | ❌ **NOT DONE** — Only `SecurityConfig` has one `hasAuthority("ROLE_SUPER_ADMIN")` on farms endpoint. No custom annotation. No per-endpoint permission checks on cameras, sirens, water, zones, alerts controllers | **GAP: Any authenticated user can access any endpoint regardless of role. MANAGER can do ADMIN-only operations** |
| T8: Module access filter in Gateway | GatewayFilter rejects disabled modules | ✅ ModuleAccessGatewayFilter.java (141 lines) with route→module mapping for cameras, sirens, water, pumps, zones, alerts. Extracts modules from JWT, returns 403 if module not enabled | None |
| T9: Farm CRUD API | SUPER_ADMIN only | ✅ FarmController: GET all, GET by ID, POST, PUT, PATCH suspend, PATCH activate. Secured with hasAuthority ROLE_SUPER_ADMIN | None |
| T10: User management CRUD | ADMIN for own farm | ✅ UserController: GET all, GET by ID, POST, PUT, PATCH deactivate, PATCH mqtt-client-id | None |

### Phase 3: Frontend Module Rendering (4 tasks)

| Task | Plan | Implemented | Gaps |
|:-----|:-----|:-----------|:-----|
| T11: Dashboard dynamic sidebar | Filter nav by JWT modules | ✅ Sidebar.tsx has `module` field on every nav item. `useAuth()` provides modules. BUT **grep shows 0 actual filtering logic** — the module field is defined but items are NOT filtered | **GAP: Sidebar defines module per nav item but doesn't filter. All items always visible.** |
| T12: Android dynamic bottom nav | Filter tabs by JWT modules | ✅ BottomNavBar.kt has `moduleId` on each NavItem + `filter { it.moduleId == null \|\| enabledModules.contains(it.moduleId) }` — **properly filters** | None |
| T13: Dashboard admin pages | Farm, User, Audit pages | ✅ AdminFarmsPage (54 lines), AdminUsersPage (58 lines), AdminAuditPage (54 lines) — exist but **very basic** (likely placeholder-level with fetch+list) | **MINOR: Admin pages are thin — need forms, dialogs, pagination** |
| T14: /me endpoint | Modules + permissions for current user | ✅ MeController.java (47 lines) — returns user's modules and effective permissions | None |

### Phase 4: Deployment (4 tasks)

| Task | Plan | Implemented | Gaps |
|:-----|:-----|:-----------|:-----|
| T15: Docker image tagging + GHCR | CI push versioned images | ❌ **NOT DONE** — no GHCR push step in CI workflows | **GAP: Images only built locally, not pushed to registry** |
| T16: Compose profiles | full, water_only, security | ⚠️ **PARTIAL** — `docker-compose.profile-water-only.yml` exists. No security or monitoring profiles | 2 of 4 profiles missing |
| T17: deploy_saas_farm.sh | Provision farm + admin + modules | ⚠️ **STUB** — Script exists (14 lines) but explicitly says "This script is a stub" | **GAP: Deployment script is not functional** |
| T18: Flyway migration | Replaces manual SQL | ⚠️ **PARTIAL** — migration_002_saas_multitenant.sql exists with proper BEGIN/COMMIT transaction. But NOT wired to Flyway library — just a raw SQL file | **GAP: No Flyway integration, manual SQL execution required** |

---

## Critical Gaps Summary

### GAP 1: No Tenant Isolation in Queries (HIGH)

**Risk:** Cross-tenant data leak. User from Farm A could theoretically see Farm B's cameras/alerts if they craft API requests.

**Current state:** TenantContext.java exists but no Hibernate @Filter annotations on any entity. No `WHERE farm_id = ?` is automatically injected.

**What's needed:**
```java
@Entity
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "farmId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "farm_id = :farmId")
public class Camera { ... }
```
Apply to: Camera, Zone, EdgeNode, WorkerTag, WaterTank, WaterMotorController, Alert, WaterLevelReading

### GAP 2: No Per-Endpoint Permission Enforcement (HIGH)

**Risk:** Any authenticated user (even VIEWER role) can trigger sirens, delete zones, modify cameras, start pumps.

**Current state:** SecurityConfig only checks SUPER_ADMIN for /farms endpoint. All other endpoints accept any valid JWT.

**What's needed:** Either Spring's `@PreAuthorize` or a custom `@RequiresPermission` annotation on every controller method that modifies data:
```java
@PreAuthorize("hasPermission('sirens:trigger')")
@PostMapping("/trigger")
public ResponseEntity<SirenResponse> triggerSiren(...) { }
```

### GAP 3: No AOP Audit Aspect (MEDIUM)

**Risk:** Audit log exists in schema but nothing writes to it automatically. Developers must manually call auditService.log() — they'll forget.

**What's needed:**
```java
@Aspect @Component
public class AuditAspect {
    @AfterReturning("@annotation(auditable)")
    public void logAction(JoinPoint jp, Auditable auditable) { ... }
}
```

### GAP 4: Dashboard Sidebar Not Actually Filtering (MEDIUM)

**Risk:** Water-only customer sees all nav items in the dashboard. Clicking cameras/sirens would get 403 from gateway but confusing UX.

**Current state:** Sidebar.tsx defines `module` on each nav item and imports `useAuth()`, but the actual `filter()` call to remove items not in the user's modules list is missing.

**What's needed:**
```tsx
const { modules } = useAuth();
const visibleItems = navItems.filter(item => 
    item.module === null || modules.includes(item.module)
);
```

### GAP 5: Deploy Script is a Stub (LOW)

14-line script that prints a message and exits. Needs real curl commands or API calls.

### GAP 6: Zero Tests for New SaaS Code (MEDIUM)

| New Code | Lines | Tests |
|:---------|:------|:------|
| FarmService.java | 162 | **0** |
| FarmController.java | ~60 | **0** |
| PermissionService.java | 121 | **0** |
| ModuleResolutionService.java | 32 | **0** |
| AuditService.java | 41 | **0** |
| AuditController.java | ~40 | **0** |
| MeController.java | 47 | **0** |
| ModuleAccessGatewayFilter.java | 141 | **0** |
| AdminFarmsPage.tsx | 54 | **0** |
| AdminUsersPage.tsx | 58 | **0** |
| AdminAuditPage.tsx | 54 | **0** |
| ModuleRoute.tsx | 19 | **0** |
| RoleRoute.tsx | 18 | **0** |
| SaasModules.kt | 16 | **0** |
| **Total: ~860 lines** | | **0 tests** |

---

## What Was Done Well

| Item | Quality |
|:-----|:-------|
| Farm entity + CRUD controller | ✅ Complete — all 6 endpoints, proper DTOs |
| Role expansion (5 roles) | ✅ Clean enum extension |
| JWT modules claim | ✅ ModuleResolutionService resolves farm modules + user overrides |
| Gateway module filter | ✅ 141 lines, proper route→module mapping, JWT extraction, 403 response |
| Android bottom nav filtering | ✅ Actually filters — `allItems.filter { it.moduleId == null \|\| enabledModules.contains(it.moduleId) }` |
| MeController | ✅ Returns user's effective modules + permissions |
| Migration script | ✅ Proper BEGIN/COMMIT, IF NOT EXISTS, idempotent |
| User CRUD expansion | ✅ Create, deactivate, update with role/permissions |

---

## Recommended Fix Priority

| # | Gap | Severity | Effort | Must Fix Before Customer #2 |
|:--|:----|:---------|:-------|:---------------------------|
| 1 | Tenant isolation (@Filter on entities) | **CRITICAL** | 1 day | **YES** — data leak risk |
| 2 | Per-endpoint permission enforcement | **HIGH** | 1 day | **YES** — role bypass risk |
| 3 | Dashboard sidebar actually filtering | **HIGH** | 2 hours | **YES** — water-only customer sees everything |
| 4 | AOP audit aspect | **MEDIUM** | 0.5 day | Ideally yes |
| 5 | Tests for 860 lines of new SaaS code | **MEDIUM** | 2 days | Ideally yes |
| 6 | Deploy script (functional, not stub) | **LOW** | 0.5 day | Can use dashboard instead |

**Total to close all gaps: ~5 days of agent work.**

---

## Overall Project Status

```
518 files │ 80 commits │ 137 Java │ 67 Kotlin │ 34 Python │ 59 TSX

Phase completion:
  Backend:      96% │ Dashboard: 95% │ Android: 93%
  CI/CD:       100% │ Cloud VPS: 100% │ Edge AI: 92%
  Firmware:    100% │ PA System: 80% │ Water/Motor: 95%
  Simulator:    90% │ SaaS Multi-Tenant: 72%

Tests: 331+ functions across 71+ files

Overall (excluding training + camera video plans): ~89%
SaaS specifically: 13/18 tasks done, 5 with gaps, 72% complete
```

### What Must Be Done Before Implementing Camera/Video Plans

1. Close GAP 1 (tenant filter) — otherwise video recordings could leak between tenants
2. Close GAP 2 (permission enforcement) — otherwise any user can access PTZ/zone drawing
3. Close GAP 3 (dashboard sidebar filter) — otherwise module-based UI makes no sense
4. Then proceed with CAMERA_VIDEO_AND_REMOTE_CONTROL_PLAN.md
