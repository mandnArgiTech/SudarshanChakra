# G-09: Dashboard Alert Detail Video Clip Player

## Prerequisites
- Edge alert_engine.py already extracts 30s clips and includes clip_path in alert payload ✓
- Edge Flask `/api/clips/{alert_id}.mp4` endpoint already exists ✓

## File to MODIFY

### `dashboard/src/pages/AlertsPage.tsx` (or wherever alert detail is rendered)

Read the file first. Find the alert detail section that shows the snapshot image.

Add BELOW the snapshot `<img>`:
```tsx
{/* Video clip — 30s around alert */}
{alert.clipPath && edgeBase && (
    <div className="mt-3">
        <h4 className="text-xs font-semibold text-sc-text-dim mb-2">Video evidence (30 seconds)</h4>
        <video
            src={`${edgeBase}/api/clips/${alert.id}.mp4`}
            controls
            preload="metadata"
            className="w-full rounded-lg bg-black"
            style={{ maxHeight: '360px' }}
        />
    </div>
)}
```

Where `edgeBase` comes from `getEdgeSnapshotBase()` (already imported in CamerasPage — import in AlertsPage too):
```tsx
import { getEdgeSnapshotBase } from '@/lib/edgeSnapshot';
const edgeBase = getEdgeSnapshotBase();
```

Also check if the alert type/interface includes `clipPath`. If not, add to `dashboard/src/types/index.ts`:
```tsx
export interface Alert {
    // ... existing fields ...
    clipPath?: string;
}
```

## Verification
```bash
# Trigger an alert while video recording is active
# View alert in dashboard → should show video player below snapshot
# Click play → 30s clip plays with seek/pause controls
```

---

