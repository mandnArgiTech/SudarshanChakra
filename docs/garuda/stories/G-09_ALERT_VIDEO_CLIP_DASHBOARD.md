# G-09: Dashboard alert detail — video clip player

## Status

**COMPLETE (baseline)** — The alerts UI shows an HTML5 **`<video>`** for 30s evidence when the alert’s **`metadata`** JSON includes a non-empty **`clip_path`** (as produced by the edge [`alert_engine.py`](edge/alert_engine.py)). Clips are served from the edge Flask route **`GET /api/clips/{alertId}.mp4`** ([`edge_gui.py`](edge/edge_gui.py)).

Treat as **PARTIAL** only if you require **per-edge-node clip URLs** (today one global edge base — see limitations below).

## Implementation (authoritative)

| Piece | Location |
|-------|----------|
| Evidence panel (snapshot + clip) | [`dashboard/src/components/AlertTable.tsx`](dashboard/src/components/AlertTable.tsx) — `AlertEvidencePanel`, expanded from each row |
| Clip URL + `clip_path` detection | [`dashboard/src/lib/edgeSnapshot.ts`](dashboard/src/lib/edgeSnapshot.ts) — `edgeAlertClipUrl`, `alertHasClipEvidence` |
| Alerts page | [`dashboard/src/pages/AlertsPage.tsx`](dashboard/src/pages/AlertsPage.tsx) — composes `AlertTable` (no clip logic here) |
| Tests | [`dashboard/src/components/AlertTable.test.tsx`](dashboard/src/components/AlertTable.test.tsx) |

## Data contract

- **Signal for “has clip”:** `metadata` string (JSON) with **`clip_path`** (snake_case), not a top-level TypeScript `clipPath` on [`Alert`](dashboard/src/types/index.ts). That matches the edge payload and alert-service storage.
- **Playback URL:** `edgeAlertClipUrl(alert.id)` → `{base}/api/clips/{id}.mp4` with `encodeURIComponent(id)`, or **`/edge/api/clips/...`** when `VITE_EDGE_SNAPSHOT_BASE` is unset.

## Networking

| Mode | Behavior |
|------|----------|
| **`VITE_EDGE_SNAPSHOT_BASE` set** (e.g. `http://192.168.x.x:5000`) | Browser loads clips directly from the edge Flask host. Watch **CORS / mixed content** if the dashboard is HTTPS and the edge is HTTP. |
| **Unset** (dev / gateway path) | Clip `src` is same-origin **`/edge/...`**. Vite proxies `/edge` → API gateway; gateway routes `/edge/**` to `EDGE_FLASK_BASE_URL` with **StripPrefix=1** and removes `Authorization` ([`api-gateway` `application.yml`](backend/api-gateway/src/main/resources/application.yml)). |

## Verification

1. Ensure recording + clip extraction run on the edge; trigger an alert so **`metadata`** contains **`clip_path`**.
2. Open **Alerts** in the dashboard; expand **Evidence** (desktop: **Clip** / **Img** toggle; mobile: **Evidence**).
3. Confirm **Snapshot** (if `snapshotUrl` set) appears **above** the **Alert clip** player; play the video or use **Open clip in new tab**.

```bash
cd dashboard && npm test -- AlertTable
```

## Historical note

An older draft asked for edits in **`AlertsPage.tsx`**, a top-level **`clipPath`** field, and **`getEdgeSnapshotBase()`** gating the player. The shipped design uses **`AlertTable`**, **`metadata.clip_path`**, and **`edgeAlertClipUrl`** (direct edge base **or** `/edge/...` fallback). That snippet is **not** the source of truth.

## Limitations / follow-ups

- **Multi-edge:** One global edge base; alerts from other nodes may request the wrong host until node-specific edge URLs exist.
- **Async extraction:** `clip_path` may be present before the `.mp4` exists → short-lived **404**; UI does not auto-retry.
- **G-11 (Android)** may use a parallel pattern (`clipPath` on the Kotlin model); dashboard stays aligned with **`metadata`** unless the API adds a first-class field.
