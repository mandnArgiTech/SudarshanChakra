# Multi-Tenant SaaS Architecture Plan

> **Role:** Senior Software Architect
> **Objective:** Evolve SudarshanChakra from single-farm monolith to commercial multi-tenant SaaS without breaking the existing system.

---

## What Already Exists (Good Foundation)

Before adding complexity, here's what we DON'T need to rebuild:

| Foundation | Status | Notes |
|:-----------|:-------|:------|
| `farm_id` on all major tables | ✅ | users, edge_nodes, worker_tags, water_tanks, motors — already tenant-scoped |
| 3 roles (admin/manager/viewer) | ✅ | Role enum + JWT claims + User model |
| JWT auth with role claim | ✅ | JwtService.generateToken(username, role) |
| SecurityConfig per service | ✅ | All 4 services have JwtAuthFilter |
| CRUD controllers | ✅ | User, Camera, Zone, Node, Siren, Water, Motor, Tag, Alert |
| API Gateway routing | ✅ | Spring Cloud Gateway with service routes |

**What's missing:** tenant isolation in queries, module-based access control, subscription management, farms table, audit logging, and deployment packaging.

---

## Design Principles

1. **Don't break existing:** Every change is additive. Current single-farm deployment continues working with a default farm + default admin.
2. **farm_id = tenant_id:** The existing `farm_id` column IS the tenant identifier. No new column needed.
3. **Module = feature flag:** Each module (cameras, sirens, water, alerts) is a feature flag on the tenant subscription — not a separate deployment.
4. **Shared database, filtered by tenant:** Row-level isolation via `WHERE farm_id = ?`. No separate databases per tenant.
5. **Progressive enhancement:** Phase 1 works for your farm. Phase 2 onboards customer #2. Phase 3 scales to many.

---

## Data Model Evolution

### New: `farms` Table (Tenant)

```sql
CREATE TABLE farms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(50) UNIQUE NOT NULL,          -- URL-friendly: "sanga-reddy-farm"
    owner_name VARCHAR(200),
    contact_phone VARCHAR(20),
    contact_email VARCHAR(255),
    address TEXT,
    location_lat REAL,
    location_lng REAL,
    timezone VARCHAR(50) DEFAULT 'Asia/Kolkata',
    status VARCHAR(20) DEFAULT 'active',       -- active, suspended, trial
    subscription_plan VARCHAR(50) DEFAULT 'full', -- full, water_only, cameras_only, custom
    modules_enabled JSONB DEFAULT '["alerts","cameras","sirens","water","pumps","zones","devices","workers","analytics"]',
    max_cameras INT DEFAULT 8,
    max_nodes INT DEFAULT 2,
    max_users INT DEFAULT 10,
    trial_ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Seed your own farm (existing data keeps working — id must match `farm_id` in seed data)
INSERT INTO farms (id, name, slug, owner_name, subscription_plan)
VALUES ('a0000000-0000-0000-0000-000000000001', 'Sanga Reddy Farm', 'sanga-reddy', 'Devi Prasad', 'full');
```

**Key:** `modules_enabled` is a JSON array of module names. The frontend reads this to show/hide entire sections.

### Enhanced: `users` Table

```sql
ALTER TABLE users ADD COLUMN display_name VARCHAR(200);
ALTER TABLE users ADD COLUMN permissions JSONB DEFAULT '[]';
    -- Fine-grained overrides: ["cameras:view","sirens:trigger","alerts:acknowledge"]
    -- Empty = use role defaults
ALTER TABLE users ADD COLUMN modules_override JSONB;
    -- NULL = inherit farm's modules_enabled
    -- Set to override per-user (e.g., field worker only sees alerts+cameras)
```

### New: `audit_log` Table

```sql
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    farm_id UUID NOT NULL REFERENCES farms(id),
    user_id UUID REFERENCES users(id),
    action VARCHAR(50) NOT NULL,           -- 'siren.trigger', 'zone.create', 'user.login'
    entity_type VARCHAR(50),               -- 'siren', 'zone', 'camera', 'alert'
    entity_id VARCHAR(100),
    details JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_audit_farm_time ON audit_log(farm_id, created_at DESC);
CREATE INDEX idx_audit_action ON audit_log(action);
```

---

## RBAC Architecture

### Role Hierarchy

```
SUPER_ADMIN  → Platform operator (you). Can see all farms, manage subscriptions.
ADMIN        → Farm owner. Full access to their farm's modules.
MANAGER      → Farm manager. Can manage devices, acknowledge alerts, control sirens.
OPERATOR     → Field operator. Can view alerts, cameras. Cannot modify config.
VIEWER       → Read-only. Can view dashboards and alerts. No actions.
```

### Permission Matrix

```
Permission              SUPER_ADMIN  ADMIN  MANAGER  OPERATOR  VIEWER
─────────────────────────────────────────────────────────────────────
farms:manage            ✓
farms:view              ✓            ✓
users:manage            ✓            ✓
users:view              ✓            ✓      ✓
alerts:view             ✓            ✓      ✓        ✓         ✓
alerts:acknowledge      ✓            ✓      ✓        ✓
alerts:resolve          ✓            ✓      ✓
cameras:view            ✓            ✓      ✓        ✓         ✓
cameras:manage          ✓            ✓      ✓
cameras:ptz             ✓            ✓      ✓        ✓
zones:view              ✓            ✓      ✓        ✓         ✓
zones:manage            ✓            ✓      ✓
sirens:view             ✓            ✓      ✓        ✓         ✓
sirens:trigger          ✓            ✓      ✓
devices:view            ✓            ✓      ✓        ✓         ✓
devices:manage          ✓            ✓
water:view              ✓            ✓      ✓        ✓         ✓
water:manage            ✓            ✓      ✓
pumps:view              ✓            ✓      ✓        ✓         ✓
pumps:control           ✓            ✓      ✓
analytics:view          ✓            ✓      ✓        ✓         ✓
settings:manage         ✓            ✓
audit:view              ✓            ✓
```

### JWT Token (Enhanced)

```json
{
    "sub": "deviprasad",
    "farm_id": "a0000000-0000-0000-0000-000000000001",
    "role": "admin",
    "modules": ["alerts","cameras","sirens","water","pumps","zones","devices","workers","analytics"],
    "permissions": [],
    "iat": 1711100000,
    "exp": 1711186400
}
```

The `modules` array comes from the farm's `modules_enabled` (or user's `modules_override` if set). Frontend reads this to render only the allowed sections.

---

## Tenant Isolation Strategy

### Backend: Automatic farm_id Filtering

Every query MUST filter by `farm_id`. Instead of sprinkling `WHERE farm_id = ?` everywhere, use a shared base:

```java
// Shared filter applied via JwtAuthFilter → SecurityContext
@Component
public class TenantContext {
    private static final ThreadLocal<UUID> currentFarmId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentRole = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> currentModules = new ThreadLocal<>();
    
    public static UUID getFarmId() { return currentFarmId.get(); }
    public static void setFarmId(UUID id) { currentFarmId.set(id); }
    // ... getters/setters for role, modules
    public static void clear() { currentFarmId.remove(); currentRole.remove(); currentModules.remove(); }
}

// JwtAuthFilter extracts farm_id from JWT and sets TenantContext
// Every repository/service uses TenantContext.getFarmId()
```

**Hibernate filter approach** (automatic, no manual WHERE needed):

```java
@Entity
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "farmId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "farm_id = :farmId")
public class Camera {
    @Column(name = "farm_id") private UUID farmId;
    // ...
}

// Activated in a request-scoped bean:
Session session = entityManager.unwrap(Session.class);
session.enableFilter("tenantFilter").setParameter("farmId", TenantContext.getFarmId());
```

This means **zero changes to existing repository queries** — Hibernate automatically appends `AND farm_id = ?` to every SELECT.

### Module Access: Gateway-Level Enforcement

API Gateway checks module access BEFORE routing:

```java
// Custom GatewayFilter
@Component
public class ModuleAccessFilter implements GatewayFilter {
    // Route → required module mapping
    private static final Map<String, String> ROUTE_MODULES = Map.of(
        "/api/v1/cameras/**", "cameras",
        "/api/v1/sirens/**", "sirens",
        "/api/v1/water/**", "water",
        "/api/v1/pumps/**", "pumps",
        "/api/v1/zones/**", "zones",
        "/api/v1/alerts/**", "alerts"
    );
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Extract modules from JWT
        List<String> userModules = extractModulesFromJwt(exchange);
        String requiredModule = matchRoute(exchange.getRequest().getPath());
        
        if (requiredModule != null && !userModules.contains(requiredModule)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
}
```

A water-only customer calling `/api/v1/cameras` gets a clean 403 — no data leak, no confusion.

---

## Module Provisioning — SaaS Packages

### Predefined Plans

```json
{
    "plans": {
        "full": {
            "name": "SudarshanChakra Complete",
            "modules": ["alerts","cameras","sirens","water","pumps","zones","devices","workers","analytics"],
            "max_cameras": 16, "max_nodes": 4, "max_users": 20
        },
        "water_only": {
            "name": "Water Level & Pump Control",
            "modules": ["water","pumps","alerts"],
            "max_cameras": 0, "max_nodes": 0, "max_users": 5
        },
        "security": {
            "name": "Farm Security",
            "modules": ["alerts","cameras","sirens","zones","devices"],
            "max_cameras": 8, "max_nodes": 2, "max_users": 10
        },
        "monitoring": {
            "name": "Farm Monitoring",
            "modules": ["alerts","cameras","water","analytics"],
            "max_cameras": 4, "max_nodes": 1, "max_users": 5
        },
        "custom": {
            "name": "Custom",
            "modules": [],
            "max_cameras": 0, "max_nodes": 0, "max_users": 0
        }
    }
}
```

### How It Looks: Water-Only Customer Login

```
Customer "AquaFarm" has plan: water_only
Modules enabled: ["water", "pumps", "alerts"]

Android App:
┌─────────────────────────┐
│  AquaFarm               │
│  Water Level & Pumps    │
├─────────────────────────┤
│                         │
│  💧 Main Tank: 72%     │
│  ████████████░░░        │
│  1,768 / 2,438 L       │
│                         │
│  ⟳ Pump: IDLE          │
│  [Start Pump]           │
│                         │
│  Alerts: 2 new          │
│  ⚠ Tank low at 18%     │
│  ⚠ Pump ran 45 min     │
│                         │
├─────────────────────────┤
│ [Water] [Alerts] [Settings]│
│                         │
│ (NO Cameras tab)        │
│ (NO Siren tab)          │
│ (NO Devices tab)        │
└─────────────────────────┘

Dashboard (browser):
┌───────────────────────────────────┐
│ SUDARSHAN CHAKRA │ AquaFarm     │
├──────┬────────────────────────────┤
│      │                            │
│ 💧   │  Water Level Dashboard     │
│Water │  [Tank gauge] [History]    │
│      │                            │
│ ⟳    │  Pump Control              │
│Pumps │  [Motor status] [Logs]     │
│      │                            │
│ ⚠   │  Alerts                    │
│Alerts│  [Alert feed] [Filters]    │
│      │                            │
│ ⚙   │  Settings                  │
│      │                            │
│      │  (Cameras, Siren, Zones,   │
│      │   Devices — NOT shown)     │
└──────┴────────────────────────────┘
```

---

## Dynamic CRUD Management Layer

### What Needs Full CRUD

Every entity must be manageable through the API — no more static SQL inserts.

| Entity | Existing CRUD | Missing Operations | Tenant-Scoped |
|:-------|:-------------|:-------------------|:--------------|
| Farm | ❌ None | Create, Read, Update, Suspend | By SUPER_ADMIN |
| User | Partial (register/login) | List, Update role, Deactivate, Reset password | farm_id |
| Edge Node | Read only | Create, Update, Delete, Health status | farm_id |
| Camera | Create, Read | Update, Delete, Enable/Disable, PTZ config | via node → farm_id |
| Zone | Create, Read, Delete | Update polygon, Enable/Disable | via camera → farm_id |
| Siren | Trigger, Stop, History | Create siren config, Update, Delete | farm_id |
| Alert | Read, Acknowledge, Resolve | Delete, Export, Bulk actions | farm_id |
| Water Tank | Create, Read | Update thresholds, Delete, History export | farm_id |
| Motor/Pump | Create, Read, Command | Update config, Delete, Run log export | farm_id |
| Worker Tag | Create, Read, Delete | Update, Enable/Disable | farm_id |
| Audit Log | ❌ None | Read (admin only), Export, Filter | farm_id |

### New API Endpoints

```
FARMS (SUPER_ADMIN only):
  GET    /api/v1/farms                → List all farms
  POST   /api/v1/farms                → Create new farm + initial admin user
  GET    /api/v1/farms/{id}           → Farm detail + stats
  PUT    /api/v1/farms/{id}           → Update farm config/subscription
  PATCH  /api/v1/farms/{id}/suspend   → Suspend farm (disable login)
  PATCH  /api/v1/farms/{id}/activate  → Reactivate

USERS (ADMIN for own farm, SUPER_ADMIN for any):
  GET    /api/v1/users                → List users in my farm
  POST   /api/v1/users                → Create user (with role + module override)
  GET    /api/v1/users/{id}           → User detail
  PUT    /api/v1/users/{id}           → Update role, permissions, modules
  PATCH  /api/v1/users/{id}/deactivate
  PATCH  /api/v1/users/{id}/reset-password

AUDIT LOG (ADMIN):
  GET    /api/v1/audit                → List audit events (filterable by action, entity, date)
  GET    /api/v1/audit/export         → CSV export

MODULE INFO:
  GET    /api/v1/me/modules           → My enabled modules (for frontend rendering)
  GET    /api/v1/me/permissions       → My effective permissions
```

---

## Frontend Module Rendering

### Dashboard Sidebar — Dynamic

```tsx
// Sidebar reads modules from JWT/API
const { modules } = useAuth();

const allNavItems = [
    { route: '/dashboard', module: 'alerts', label: 'Dashboard', icon: LayoutDashboard },
    { route: '/alerts',    module: 'alerts',  label: 'Alerts',    icon: AlertTriangle },
    { route: '/cameras',   module: 'cameras', label: 'Cameras',   icon: Camera },
    { route: '/zones',     module: 'zones',   label: 'Zones',     icon: Hexagon },
    { route: '/devices',   module: 'devices', label: 'Devices',   icon: Cpu },
    { route: '/siren',     module: 'sirens',  label: 'Siren',     icon: Siren },
    { route: '/water',     module: 'water',   label: 'Water',     icon: Droplet },
    { route: '/pumps',     module: 'pumps',   label: 'Pumps',     icon: Zap },
    { route: '/workers',   module: 'devices', label: 'Workers',   icon: Users },
    { route: '/analytics', module: 'analytics', label: 'Analytics', icon: BarChart3 },
    { route: '/settings',  module: null,       label: 'Settings',  icon: Settings }, // always shown
];

// Filter to only show enabled modules
const visibleNavItems = allNavItems.filter(item => 
    item.module === null || modules.includes(item.module)
);
```

### Android Bottom Nav — Dynamic

```kotlin
// Same filtering logic in Kotlin
val enabledModules = sessionViewModel.modules.collectAsState()

val allTabs = listOf(
    NavTab("alerts", "Alerts", Icons.Filled.NotificationsActive, "alerts"),
    NavTab("cameras", "Cameras", Icons.Filled.Videocam, "cameras"),
    NavTab("siren", "Siren", Icons.Filled.Campaign, "sirens"),
    NavTab("water", "Water", Icons.Filled.WaterDrop, "water"),
    NavTab("devices", "Devices", Icons.Filled.Sensors, "devices"),
    NavTab("settings", "Settings", Icons.Filled.Settings, null), // always shown
)

val visibleTabs = allTabs.filter { tab ->
    tab.requiredModule == null || enabledModules.value.contains(tab.requiredModule)
}
```

---

## Audit Logging

Every significant action is logged automatically via an AOP aspect:

```java
@Aspect
@Component
public class AuditAspect {
    @Autowired private AuditLogRepository auditRepo;
    
    @AfterReturning("@annotation(Auditable)")
    public void logAction(JoinPoint jp) {
        Auditable ann = getAnnotation(jp);
        auditRepo.save(AuditLog.builder()
            .farmId(TenantContext.getFarmId())
            .userId(TenantContext.getUserId())
            .action(ann.action())          // "siren.trigger"
            .entityType(ann.entityType())  // "siren"
            .entityId(extractId(jp))
            .details(serializeArgs(jp))
            .ipAddress(getClientIp())
            .build());
    }
}

// Usage:
@Auditable(action = "siren.trigger", entityType = "siren")
public SirenResponse triggerSiren(SirenTriggerRequest request) { ... }
```

Actions logged: login, logout, siren trigger/stop, alert acknowledge/resolve, zone create/update/delete, camera add/remove, user create/deactivate, pump start/stop, config changes.

---

## Build, Package & Deploy Strategy

### Container Images

Every service builds into a versioned Docker image:

```
sudarshanchakra/auth-service:1.2.0
sudarshanchakra/alert-service:1.2.0
sudarshanchakra/device-service:1.2.0
sudarshanchakra/siren-service:1.2.0
sudarshanchakra/api-gateway:1.2.0
sudarshanchakra/dashboard:1.2.0
sudarshanchakra/edge-ai:1.2.0
sudarshanchakra/simulator:1.2.0
```

### Deployment Profiles

```yaml
# docker-compose.full.yml — All modules (your farm)
# docker-compose.water-only.yml — Water + Pump only (for AquaFarm customer)
# docker-compose.security.yml — Cameras + Sirens + Alerts only
```

Each profile includes only the required services:

```yaml
# docker-compose.water-only.yml
services:
    postgres: ...
    rabbitmq: ...
    auth-service: ...       # Always needed
    device-service: ...     # Manages tanks + pumps
    alert-service: ...      # Water level alerts
    api-gateway: ...        # Always needed
    dashboard: ...          # Shows only water modules
    # NO siren-service
    # NO edge-ai (no cameras)
```

### Deployment Script

```bash
# Deploy full system for a new farm
./deploy.sh --plan full --farm-name "Green Valley Farm" \
    --admin-user admin --admin-email admin@gvf.com \
    --domain gvf.sudarshanchakra.com

# Deploy water-only for a customer
./deploy.sh --plan water_only --farm-name "AquaFarm" \
    --admin-user aquaadmin --admin-email admin@aquafarm.com \
    --domain aqua.sudarshanchakra.com

# The script:
# 1. Creates farm record in DB
# 2. Creates initial admin user
# 3. Sets modules_enabled based on plan
# 4. Starts only the required Docker services
# 5. Configures Nginx for the domain
# 6. Prints login credentials
```

### Versioned Releases

```
GitHub Releases:
  v1.0.0 — Single farm (current)
  v2.0.0 — Multi-tenant + RBAC + modules
  v2.1.0 — PTZ + video playback
  v2.2.0 — SaaS provisioning + deployment scripts

Each release includes:
  - Docker images (pushed to GHCR)
  - docker-compose files per profile
  - Database migration scripts (Flyway)
  - Release notes
```

---

## Migration Path (Zero Downtime)

### Step 1: Create `farms` table, seed your farm

```sql
-- Your existing data keeps working
INSERT INTO farms (id, name, slug, subscription_plan, modules_enabled)
VALUES ('a0000000-0000-0000-0000-000000000001', 'Sanga Reddy Farm', 'sanga-reddy', 'full',
        '["alerts","cameras","sirens","water","pumps","zones","devices","workers","analytics"]');

-- Point existing users/nodes/tanks to this farm
-- (They already have farm_id, just ensure it matches)
UPDATE users SET farm_id = 'a0000000-0000-0000-0000-000000000001' WHERE farm_id IS NULL;
```

### Step 2: Add SUPER_ADMIN role

```sql
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(20);
-- Add check constraint for new role
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check 
    CHECK (role IN ('super_admin', 'admin', 'manager', 'operator', 'viewer'));

-- Make yourself super_admin
UPDATE users SET role = 'super_admin' WHERE username = 'deviprasad';
```

### Step 3: Add modules to JWT (backward compatible)

```java
// JwtService — add modules claim
public String generateToken(String username, String role, UUID farmId, List<String> modules) {
    return Jwts.builder()
        .subject(username)
        .claim("role", role)
        .claim("farm_id", farmId.toString())
        .claim("modules", modules)        // NEW — old tokens without this still work
        .claim("permissions", permissions)  // NEW
        ...
}
```

Old JWTs without `modules` claim → treated as `["all"]` (full access). No existing user breaks.

### Step 4: Enable tenant filter (opt-in)

```java
// Start with filter disabled, enable per-service
@Value("${tenant.filter.enabled:false}")
private boolean tenantFilterEnabled;
```

Turn on per service once verified. Rollback = set to `false`.

---

## Implementation — 15 Tasks, 4 Phases

### Phase 1: Foundation (3 days)

| # | Task | Impact |
|:--|:-----|:-------|
| 1 | Create `farms` table + seed your farm | Zero — additive SQL |
| 2 | Add SUPER_ADMIN + OPERATOR roles to enum | Zero — extends existing |
| 3 | Add `modules_enabled` to farms, `modules` to JWT | Zero — old tokens still work |
| 4 | Create `TenantContext` + Hibernate tenant filter | Zero — disabled by default |
| 5 | Create `audit_log` table + AOP aspect | Zero — additive |

### Phase 2: RBAC Enforcement (3 days)

| # | Task | Impact |
|:--|:-----|:-------|
| 6 | Permission matrix in shared library | Zero — not enforced yet |
| 7 | `@RequiresPermission` annotation + filter | Enforced per-endpoint |
| 8 | Module access filter in API Gateway | 403 for disabled modules |
| 9 | Farm CRUD API (SUPER_ADMIN) | New endpoints only |
| 10 | User management CRUD (ADMIN) | Extends existing |

### Phase 3: Frontend Module Rendering (2 days)

| # | Task | Impact |
|:--|:-----|:-------|
| 11 | Dashboard: dynamic sidebar from JWT modules | Shows same nav for full plan |
| 12 | Android: dynamic bottom nav from JWT modules | Shows same tabs for full plan |
| 13 | Dashboard: admin pages (user management, farm settings, audit log) | New pages |
| 14 | Android: settings screen shows user management for admins | Extends existing |

### Phase 4: Deployment Packaging (2 days)

| # | Task | Impact |
|:--|:-----|:-------|
| 15 | Docker image tagging + GHCR push in CI | Additive CI step |
| 16 | Compose profiles (full, water_only, security) | New files |
| 17 | `deploy.sh` with farm provisioning | New script |
| 18 | Flyway migration scripts | Replaces manual SQL |

**Total: 18 tasks, 10 days, zero breaking changes.**

---

## Implementation status (codebase sync)

| Area | Status | Location / notes |
|:-----|:-------|:-------------------|
| `farms` table + default tenant row | Done | `cloud/db/init.sql`; existing seed `farm_id` = `a0000000-0000-0000-0000-000000000001` |
| DB migration for existing DBs | Done | `cloud/db/migration_002_saas_multitenant.sql` |
| `users` extended roles + `display_name`, `permissions`, `modules_override` | Done | `init.sql` + JPA `User` |
| `audit_log` table | Done | `init.sql`; writes on login + user admin actions |
| SUPER_ADMIN, OPERATOR roles + permission matrix service | Done | `Role`, `PermissionService` |
| JWT `farm_id`, `modules`, `permissions` | Done | `JwtService`, `AuthService` |
| `TenantContext` from JWT filter | Done | `JwtAuthFilter` + `TenantContext` (Hibernate `@Filter` not enabled — optional later) |
| Farm CRUD + suspend/activate | Done | `FarmController` / `FarmService` — `ROLE_SUPER_ADMIN` |
| User list/create/update/deactivate | Done | `UserController` / `UserService` |
| `GET /api/v1/me/modules`, `/me/permissions` | Done | `MeController` |
| `GET /api/v1/audit` (paged) | Done | `AuditController` |
| API Gateway module filter | Done | `ModuleAccessGatewayFilter`; `jwt.secret` + `sc.gateway.module-filter.enabled` |
| Gateway routes for farms/audit/me | Done | `api-gateway/application.yml` |
| Dashboard dynamic sidebar + module routes | Done | `Sidebar`, `ModuleRoute`, `useAuth.hasModule` |
| Dashboard admin pages (farms / users / audit) | Done | `AdminFarmsPage`, `AdminUsersPage`, `AdminAuditPage` + `api/saas.ts` |
| Android dynamic bottom nav | Done | `BottomNavBar` + `AuthRepository.enabledModules` from login `user.modules` |
| Mockup `saas-screens-mockup.jsx` | Reference | Real UI: `/admin/farms`, `/admin/users`, `/admin/audit`; water-only = JWT modules hide nav |
| `@Auditable` AOP aspect | Not done | Partial: explicit `AuditService.log` calls; full aspect optional |
| Hibernate tenant `@Filter` | Not done | Planned opt-in; `TenantContext` ready |
| Flyway | Not done | Use `migration_002_saas_multitenant.sql` + `init.sql` for greenfield |
| Compose profiles + `deploy.sh` + GHCR matrix | Done (G-18) | Profiles on `cloud/docker-compose.vps.yml`; `scripts/deploy_saas_farm.sh`; `cloud/deploy.sh --profile`; reference `cloud/docker-compose.profile-*.yml` |
| Auth-service tests | Done | H2 in `application-test.yml`; integration tests still `@Tag("integration")` + Docker |

---

## Non-Breaking Guarantee

| Change | Existing Single-Farm Impact |
|:-------|:---------------------------|
| `farms` table + seed | Your farm gets a record; existing queries unaffected |
| SUPER_ADMIN role | You become super_admin; existing admin/manager/viewer unchanged |
| `modules` in JWT | Old tokens without modules → full access (backward compatible) |
| Tenant filter | Disabled by default; enable per-service after testing |
| Module access filter | Full plan = all modules enabled → no change |
| Dynamic sidebar/nav | Full plan → all nav items visible → looks identical |
| audit_log | New table, new writes; existing code untouched |
| Deploy profiles | New compose files; existing docker-compose.vps.yml unchanged |
