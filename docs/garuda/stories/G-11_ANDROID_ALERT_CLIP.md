# G-11: Android alert detail — video evidence (Media3 / ExoPlayer)

## Status

**COMPLETE (baseline)** — Alert detail plays edge-hosted MP4 evidence with **Media3 `ExoPlayer`** and **`PlayerView`** when alert **`metadata`** JSON includes a non-empty **`clip_path`** (same contract as dashboard G-09 and the edge [`alert_engine.py`](edge/alert_engine.py) payload).

## Implementation (authoritative)

| Piece | Location |
|-------|----------|
| Player UI | [`AlertDetailScreen.kt`](android/app/src/main/java/com/sudarshanchakra/ui/screens/alerts/AlertDetailScreen.kt) — `AlertClipPlayer` (`ExoPlayer` + `LaunchedEffect(clipUrl)` + `DisposableEffect` release) |
| Edge base URL | [`AlertViewModel.edgeGuiBaseUrl`](android/app/src/main/java/com/sudarshanchakra/ui/screens/alerts/AlertViewModel.kt) — from **Server settings** (same as Edge GUI / snapshots) |
| `clip_path` parsing | [`Alert.kt`](android/app/src/main/java/com/sudarshanchakra/domain/model/Alert.kt) — `clipPathFromMetadata()`, `hasClipEvidence()` |
| Media3 deps | [`android/app/build.gradle.kts`](android/app/build.gradle.kts) — `media3-exoplayer`, `media3-ui` (G-06 prerequisite) |

## Data and URL

- **Signal:** `metadata.clip_path` (snake_case), not a separate API field on the `Alert` data class (avoids duplicating backend JSON).
- **Playback URL:** `{edgeGuiBase}/api/clips/{urlEncodedAlertId}.mp4` — **alert id** is URL-encoded for reserved characters.
- **Missing Edge GUI base:** UI shows copy to set **Edge GUI base URL** in Server settings (direct HTTP to Flask, not Spring gateway).

## Historical note

An older draft used **`VideoView`**, a top-level **`clipPath`** property on `Alert`, and **`RuntimeConnectionConfig.getEdgeBaseUrl(context)`** — that **getter does not exist**; runtime config uses **`getEdgeGuiBaseUrl()`** without `Context`, and the screen correctly uses the **ViewModel** settings flow. The old snippet is **not** the source of truth.

## Verification

```bash
cd android && ./gradlew assembleDebug
# Set Edge GUI base to reachable edge (e.g. http://10.0.2.2:5000 on emulator).
# Open an alert whose metadata includes clip_path → Evidence clip with ExoPlayer controls.
```

## Limitations / follow-ups

- **Cleartext HTTP** to a LAN edge may require **`usesCleartextTraffic`** or a **network security config** on some builds.
- **404 / race:** `clip_path` may appear before the `.mp4` exists on disk — user can leave and re-open; optional retry UI later.
- Snapshot block above the clip is still a **placeholder** (icon + label); loading a real snapshot image is a separate enhancement.
