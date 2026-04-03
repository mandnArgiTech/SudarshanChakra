# G-03: Dashboard Sidebar Module Filter

## Status
**ALREADY DONE** — verified in codebase.

`dashboard/src/components/Layout/Sidebar.tsx` line 64:
```tsx
const visibleMain = mainNavItems.filter((item) => hasModule(item.module));
```

## Action
No code changes needed. Mark as DONE in GARUDA_CHECKLIST.md.

## Verification
```bash
# Login with a water_only user whose JWT has modules=["water","pumps","alerts"]
# Navigate to dashboard
# Sidebar should show: Dashboard, Alerts, Water, Pumps, Settings
# Sidebar should NOT show: Cameras, Sirens, Zones, Devices, Workers, Analytics
```
