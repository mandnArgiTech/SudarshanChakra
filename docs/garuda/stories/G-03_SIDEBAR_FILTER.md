# G-03: Dashboard Sidebar Module Filter

## Status

**COMPLETE** — sidebar and client routes both respect JWT/user `modules`; checklist updated.

## Implementation

### Sidebar

[`dashboard/src/components/Layout/Sidebar.tsx`](../../../dashboard/src/components/Layout/Sidebar.tsx) builds the main nav from `mainNavItems`, each entry carrying a `module` id (or `null` for always-visible items such as **Settings**):

```tsx
const visibleMain = mainNavItems.filter((item) => hasModule(item.module));
```

Admin links are **role-based** (`user.role`), not module-based, matching `RoleRoute` in `App.tsx`.

### Route guard (defense in depth)

[`dashboard/src/components/ModuleRoute.tsx`](../../../dashboard/src/components/ModuleRoute.tsx) uses the same `hasModule` from [`dashboard/src/hooks/useAuth.ts`](../../../dashboard/src/hooks/useAuth.ts). If the user lacks the module, they are redirected with `getFirstAccessibleNavPath` from [`dashboard/src/utils/moduleNavigation.ts`](../../../dashboard/src/utils/moduleNavigation.ts) so we **do not** send them to `/` when `/` itself requires `alerts` (avoids redirect loops).

### `hasModule` rules (`useAuth`)

- `module === null` → **allowed** (e.g. Settings).
- `user.modules` **missing** or **length 0** → **full access** (legacy tokens).
- Otherwise → allowed only if `user.modules.includes(moduleId)` (exact string match; must align with auth-service / JWT).

API responses must populate `User.modules` on login (not only a differently named field).

## Action

- [x] Mark **G-03** done in [`docs/garuda/GARUDA_CHECKLIST.md`](../GARUDA_CHECKLIST.md).

## Verification

```bash
# Login with a user whose JWT/user payload has modules=["water","pumps","alerts"]
# Navigate to dashboard
# Sidebar should show: Dashboard, Alerts, Water, Pumps, Settings
# Sidebar should NOT show: Cameras, Sirens, Zones, Devices, Workers, Analytics, MDM (unless mdm in modules)
```

Deep-linking to a disallowed path (e.g. `/cameras` without `cameras`) should redirect to the first allowed nav path (same order as sidebar), or `/settings` as final fallback.

## Tests

Vitest: `hasModule` behavior in [`dashboard/src/hooks/useAuth.test.tsx`](../../../dashboard/src/hooks/useAuth.test.tsx); `getFirstAccessibleNavPath` in [`dashboard/src/utils/moduleNavigation.test.ts`](../../../dashboard/src/utils/moduleNavigation.test.ts).
