# Story 11: Dashboard MDM Pages

## Prerequisites
- Stories 01-05 complete (backend MDM APIs running)

## Goal
Add MDM device management pages to the React dashboard. Follows existing sc-* dark theme. Visual reference: `docs/mdm/mdm-dashboard-mockups.jsx`.

## Reference files (READ FIRST)
- `docs/mdm/mdm-dashboard-mockups.jsx` — VISUAL REFERENCE. Match this exactly.
- `dashboard/src/pages/AdminFarmsPage.tsx` — page structure pattern (fetch + list + card)
- `dashboard/src/components/Layout/Sidebar.tsx` — nav item pattern with module filtering
- `dashboard/src/App.tsx` — route registration pattern
- `dashboard/src/api/saas.ts` — API hook pattern
- `dashboard/src/components/ModuleRoute.tsx` — module guard pattern

## Files to CREATE

### 1. `dashboard/src/api/mdm.ts`
```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client'; // Use existing API client pattern

export interface MdmDevice {
  id: string;
  farmId: string;
  userId?: string;
  deviceName: string;
  androidId: string;
  model?: string;
  osVersion?: string;
  appVersion?: string;
  isDeviceOwner: boolean;
  isLockTaskActive: boolean;
  whitelistedApps: string[];
  policies: Record<string, boolean>;
  lastHeartbeat?: string;
  lastTelemetrySync?: string;
  status: 'pending' | 'active' | 'locked' | 'wiped' | 'decommissioned';
  createdAt: string;
}

export interface AppUsageRecord {
  date: string;
  packageName: string;
  appLabel: string;
  foregroundTimeSec: number;
  launchCount: number;
  category: string;
}

export interface CallLogRecord {
  phoneNumberMasked: string;
  callType: 'incoming' | 'outgoing' | 'missed' | 'rejected';
  callTimestamp: string;
  durationSec: number;
  contactName: string;
}

export interface ScreenTimeRecord {
  date: string;
  totalScreenTimeSec: number;
  unlockCount: number;
}

export interface MdmCommandRecord {
  id: string;
  command: string;
  status: string;
  issuedAt: string;
  executedAt?: string;
  result?: Record<string, unknown>;
}

export function useMdmDevices() {
  return useQuery({ queryKey: ['mdm-devices'], queryFn: () => apiFetch<MdmDevice[]>('/api/v1/mdm/devices') });
}

export function useMdmDevice(id: string) {
  return useQuery({ queryKey: ['mdm-device', id], queryFn: () => apiFetch<MdmDevice>(`/api/v1/mdm/devices/${id}`) });
}

export function useDeviceUsage(id: string, from: string, to: string) {
  return useQuery({ queryKey: ['mdm-usage', id, from, to], queryFn: () => apiFetch<AppUsageRecord[]>(`/api/v1/mdm/devices/${id}/usage?from=${from}&to=${to}`) });
}

export function useDeviceCalls(id: string, from: string, to: string) {
  return useQuery({ queryKey: ['mdm-calls', id, from, to], queryFn: () => apiFetch<CallLogRecord[]>(`/api/v1/mdm/devices/${id}/calls?from=${from}&to=${to}`) });
}

export function useDeviceScreenTime(id: string, from: string, to: string) {
  return useQuery({ queryKey: ['mdm-screentime', id, from, to], queryFn: () => apiFetch<ScreenTimeRecord[]>(`/api/v1/mdm/devices/${id}/screentime?from=${from}&to=${to}`) });
}

export function useDeviceCommands(id: string) {
  return useQuery({ queryKey: ['mdm-commands', id], queryFn: () => apiFetch<MdmCommandRecord[]>(`/api/v1/mdm/commands/${id}`) });
}

export function useSendCommand() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { deviceId: string; command: string; payload?: Record<string, unknown> }) =>
      apiFetch('/api/v1/mdm/commands', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['mdm-commands'] }),
  });
}
```

### 2. `dashboard/src/pages/mdm/MdmDeviceListPage.tsx`
Render a list of managed devices with status badges, screen time summary, and action buttons.
Follow the mockup in `mdm-dashboard-mockups.jsx` → `DeviceList` component.
Use existing components: Card from Tailwind classes, Badge pattern from AlertsPage.

### 3. `dashboard/src/pages/mdm/MdmDeviceDetailPage.tsx`
Drill-down page showing:
- Device info header (name, model, OS, app version, kiosk status)
- Quick action buttons (Force Sync, Push OTA, Lock Screen, Wipe)
- Stacked bar chart for 7-day screen time (SC/WhatsApp/YouTube/Maps/Phone/Other)
- Call log table (time, type badge, masked number, duration, contact)
- Policies panel (toggle display for each policy)
- Command history list

Follow the mockup in `mdm-dashboard-mockups.jsx` → `DeviceDetail` component.

### 4. `dashboard/src/components/mdm/ScreenTimeChart.tsx`
Horizontal stacked bar chart component.
Colors: SudarshanChakra=#22c55e, WhatsApp=#25D366, YouTube=#FF0000, Maps=#3b82f6, Phone=#f59e0b, Other=#1a2235.
Use existing sc-* surface/border/text colors for layout.

### 5. `dashboard/src/components/mdm/CallLogTable.tsx`
Table component with columns: Time, Type (badge), Number (monospace), Duration, Contact.
Follow the pattern in existing alert tables.

### 6. `dashboard/src/components/mdm/CommandPanel.tsx`
Command history list + quick action buttons.
Each command shows: timestamp, command name, status badge (color-coded).

## Files to MODIFY

### 1. `dashboard/src/components/Layout/Sidebar.tsx`
Add to `mainNavItems` array (BEFORE the settings item):
```typescript
import { Smartphone } from 'lucide-react';
// Add this nav item:
{ to: '/mdm', icon: Smartphone, label: 'MDM', module: 'mdm' },
```
This uses existing module filtering — only visible when farm has 'mdm' module.

### 2. `dashboard/src/App.tsx`
Add routes inside the existing `<Routes>`:
```tsx
import MdmDeviceListPage from '@/pages/mdm/MdmDeviceListPage';
import MdmDeviceDetailPage from '@/pages/mdm/MdmDeviceDetailPage';

// Inside Routes, add:
<Route path="mdm" element={<ModuleRoute module="mdm"><MdmDeviceListPage /></ModuleRoute>} />
<Route path="mdm/devices/:id" element={<ModuleRoute module="mdm"><MdmDeviceDetailPage /></ModuleRoute>} />
```

### 3. `dashboard/src/types/index.ts`
Add MDM types if not already imported from api/mdm.ts.

## Verification
```bash
cd dashboard && npm run build
# Should compile with zero errors
# Start dev server: npm run dev
# Navigate to /mdm — should show device list
# Navigate to /mdm/devices/{id} — should show detail with charts
```

---

## ADDENDUM: Location Tracking in Dashboard

### Add to `MdmDeviceDetailPage.tsx`:

After the screen time chart section, add a **Location** section:

```tsx
// Location card — shows map with trail + interval control
<Card className="p-4 mb-3">
  <div className="flex justify-between items-center mb-3">
    <h3 className="text-sm font-semibold text-sc-text">Location</h3>
    <div className="flex items-center gap-2 text-xs text-sc-text-dim">
      <span>Tracking every</span>
      <select value={locationInterval} onChange={e => updateInterval(+e.target.value)}
        className="bg-sc-surface-alt border border-sc-border rounded px-2 py-1 text-sc-text text-xs">
        <option value={30}>30 sec</option>
        <option value={60}>1 min</option>
        <option value={120}>2 min</option>
        <option value={300}>5 min</option>
        <option value={600}>10 min</option>
      </select>
    </div>
  </div>
  
  {/* Map showing location trail — use Leaflet or Google Maps */}
  <div className="w-full h-48 rounded-lg bg-sc-surface-alt border border-sc-border flex items-center justify-center">
    {/* Leaflet map with polyline trail of last 24h locations */}
    {/* Current position shown as pulsing accent dot */}
    {/* Path shown as sc-accent colored polyline */}
    <span className="text-sc-text-muted text-xs">Map: last 24h trail</span>
  </div>

  <div className="flex justify-between mt-2 text-xs text-sc-text-dim">
    <span>Last: 17.5123°N, 78.2456°E · 2 min ago</span>
    <span>Accuracy: 8m · GPS</span>
  </div>
</Card>
```

### Add to `src/api/mdm.ts`:
```typescript
export function useDeviceLocation(id: string, from: string, to: string) {
  return useQuery({
    queryKey: ['mdm-location', id, from, to],
    queryFn: () => apiFetch<LocationRecord[]>(`/api/v1/mdm/devices/${id}/location?from=${from}&to=${to}`),
    refetchInterval: 60000, // Auto-refresh every 60s
  });
}

export function useUpdateLocationInterval() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ deviceId, intervalSec }: { deviceId: string; intervalSec: number }) =>
      apiFetch(`/api/v1/mdm/devices/${deviceId}/location-interval`, {
        method: 'PUT',
        body: JSON.stringify({ interval_sec: intervalSec }),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['mdm-device'] }),
  });
}

export interface LocationRecord {
  latitude: number;
  longitude: number;
  accuracyMeters?: number;
  altitudeMeters?: number;
  speedMps?: number;
  provider?: string;
  batteryPercent?: number;
  recordedAt: string;
}
```

### Add to dashboard device list — show last location:
In each device card, show: `Last seen: 17.51°N, 78.24°E · 2 min ago`

### Dashboard dependency:
```bash
cd dashboard && npm install leaflet react-leaflet @types/leaflet
```
