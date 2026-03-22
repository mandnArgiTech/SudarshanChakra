# Video Playback Plan — Watch Cameras Like YouTube

## The Problem

We record 10-minute MP4 segments on the edge SSD. These are standard H.264 files — every browser and phone can play them natively. But there's no HTTP endpoint to serve them and no player UI anywhere. It's like having YouTube's video library with no youtube.com.

## What We Need

```
LIVE VIEW:   Watch camera right now (MJPEG stream from inference frames, 2-3 FPS)
PLAYBACK:    Watch any recorded segment (MP4 via HTTP, with seek/pause like YouTube)
CLIP VIEW:   Watch 30-second alert clip (MP4, linked from alert detail page)
```

All three work in browsers (`<video>` tag) and Android (ExoPlayer) with zero plugins.

---

## How It Works (Simple)

```
Browser/Android requests: GET /api/video/cam-01/2026-03-22/08/cam-01_08-00.mp4
                                          ↓
Edge Flask serves the file with HTTP Range support (byte-range requests)
                                          ↓
Browser <video src="..."> plays it natively — seek, pause, fullscreen, speed control
Android ExoPlayer plays it natively — same thing
```

For live view:
```
Browser requests: GET /api/live/cam-01
                          ↓
Edge Flask streams MJPEG (multipart/x-mixed-replace) from inference frame cache
                          ↓
Browser <img src="..."> shows continuously updating live feed
Android: Coil/Glide loads MJPEG or polls JPEG every 500ms
```

**This is exactly how YouTube works** — MP4 served over HTTP with range requests. The browser `<video>` tag handles everything: seek bar, pause, fullscreen, playback speed.

---

## Implementation — 8 Tasks

### Task 1: Edge Flask — Serve MP4 Recordings

**File:** `edge/edge_gui.py` — add new routes

```python
import mimetypes
from flask import send_file, request, Response

VIDEO_DIR = os.getenv("VIDEO_DIR", "/data/video")

@app.route("/api/video/<camera_id>/<date>/<hour>/<filename>")
def serve_video(camera_id, date, hour, filename):
    """Serve recorded MP4 segment with HTTP Range support for seeking."""
    # Security: sanitize all path components
    safe_cam = secure_filename(camera_id)
    safe_date = secure_filename(date)
    safe_hour = secure_filename(hour)
    safe_file = secure_filename(filename)
    
    path = os.path.join(VIDEO_DIR, safe_cam, safe_date, safe_hour, safe_file)
    
    if not os.path.isfile(path):
        abort(404)
    
    # HTTP Range support — required for video seeking
    return send_file(path, mimetype="video/mp4", conditional=True)

@app.route("/api/video/<camera_id>/recordings")
def list_recordings(camera_id):
    """List all available dates for a camera."""
    cam_dir = os.path.join(VIDEO_DIR, secure_filename(camera_id))
    if not os.path.isdir(cam_dir):
        return jsonify({"dates": []})
    dates = sorted([d for d in os.listdir(cam_dir) if os.path.isdir(os.path.join(cam_dir, d))])
    return jsonify({"camera_id": camera_id, "dates": dates})

@app.route("/api/video/<camera_id>/recordings/<date>")
def list_segments(camera_id, date):
    """List all segments for a camera on a given date."""
    date_dir = os.path.join(VIDEO_DIR, secure_filename(camera_id), secure_filename(date))
    segments = []
    if os.path.isdir(date_dir):
        for hour in sorted(os.listdir(date_dir)):
            hour_dir = os.path.join(date_dir, hour)
            if os.path.isdir(hour_dir):
                for f in sorted(os.listdir(hour_dir)):
                    if f.endswith(".mp4"):
                        full = os.path.join(hour_dir, f)
                        segments.append({
                            "file": f,
                            "hour": hour,
                            "path": f"{camera_id}/{date}/{hour}/{f}",
                            "size_mb": round(os.path.getsize(full) / 1048576, 1),
                        })
    return jsonify({"camera_id": camera_id, "date": date, "segments": segments})
```

Flask's `send_file` with `conditional=True` automatically handles `Range` headers — this gives you seeking in the browser for free.

---

### Task 2: Edge Flask — Live MJPEG Stream

**File:** `edge/edge_gui.py`

Stream the latest inference frames as MJPEG (Motion JPEG). This gives ~2-3 FPS "live" view with detection boxes drawn on the frames.

```python
@app.route("/api/live/<camera_id>")
def live_stream(camera_id):
    """MJPEG live stream from inference frame cache."""
    def generate():
        while True:
            entry = _snapshot_cache.get(camera_id)
            if entry:
                ts, jpeg_bytes = entry
                yield (b"--frame\r\n"
                       b"Content-Type: image/jpeg\r\n\r\n" +
                       jpeg_bytes + b"\r\n")
            time.sleep(0.4)  # ~2.5 FPS
    
    return Response(
        generate(),
        mimetype="multipart/x-mixed-replace; boundary=frame"
    )
```

The browser plays this with just `<img src="/api/live/cam-01">` — it auto-refreshes because MJPEG is a streaming format that browsers understand natively.

---

### Task 3: Dashboard — Video Player Page

**New file:** `dashboard/src/pages/VideoPlayerPage.tsx`

A full video playback page with:
- Camera selector dropdown
- Date picker (calendar)
- Timeline bar showing all segments for selected date (clickable)
- HTML5 `<video>` player with native controls (play, pause, seek, fullscreen, speed)
- Alert markers on the timeline (red dots where alerts occurred)
- Previous/Next segment buttons

```tsx
function VideoPlayer({ src }: { src: string }) {
    return (
        <video
            src={src}
            controls
            autoPlay
            className="w-full rounded-lg bg-black"
            style={{ maxHeight: "70vh" }}
        >
            Your browser supports MP4 playback.
        </video>
    );
}

// The src is: http://<edge-ip>:5000/api/video/cam-01/2026-03-22/08/cam-01_08-00.mp4
// Browser handles everything — seek bar, pause, fullscreen, speed control
```

---

### Task 4: Dashboard — Live Camera View

**Modify:** `dashboard/src/pages/CamerasPage.tsx`

Replace the broken snapshot approach with MJPEG live stream:

```tsx
function LiveCameraFeed({ cameraId, edgeBase }: { cameraId: string; edgeBase: string }) {
    // MJPEG stream — browser renders it like a video
    return (
        <img
            src={`${edgeBase}/api/live/${cameraId}`}
            alt={`Live: ${cameraId}`}
            className="w-full rounded-lg bg-black"
        />
    );
}
```

The `<img>` tag with an MJPEG source auto-updates — the browser continuously renders new frames as they arrive over the HTTP connection. No JavaScript polling needed.

**Fallback for when MJPEG isn't available** (edge unreachable):
```tsx
// Fall back to polling JPEG snapshots every 2 seconds
function SnapshotFeed({ cameraId, edgeBase }) {
    const [ts, setTs] = useState(Date.now());
    useEffect(() => { 
        const i = setInterval(() => setTs(Date.now()), 2000);
        return () => clearInterval(i);
    }, []);
    return <img src={`${edgeBase}/api/snapshot/${cameraId}?t=${ts}`} />;
}
```

---

### Task 5: Dashboard — Alert Detail Video Clip

**Modify:** `dashboard/src/pages/AlertDetailPage.tsx` (or AlertsPage)

When viewing an alert, show the 30-second video clip:

```tsx
function AlertClip({ alertId, edgeBase }: { alertId: string; edgeBase: string }) {
    const clipUrl = `${edgeBase}/api/clips/${alertId}.mp4`;
    
    return (
        <div>
            <h3>Video Evidence</h3>
            <video src={clipUrl} controls className="w-full rounded-lg" />
        </div>
    );
}
```

---

### Task 6: Android — ExoPlayer Video Screen

**New file:** `android/.../ui/screens/video/VideoPlayerScreen.kt`

Use Google's ExoPlayer (bundled with Media3) to play MP4 from edge:

```kotlin
@Composable
fun VideoPlayerScreen(videoUrl: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply { player = exoPlayer }
        },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    )
}
```

Add to `build.gradle.kts`:
```kotlin
implementation("androidx.media3:media3-exoplayer:1.3.1")
implementation("androidx.media3:media3-ui:1.3.1")
```

---

### Task 7: Android — Live Camera View

**Modify:** `android/.../ui/screens/cameras/CameraGridScreen.kt`

Show live feed by polling JPEG snapshots (MJPEG doesn't work in Android ImageView):

```kotlin
@Composable
fun LiveCameraFeed(cameraId: String, edgeBase: String) {
    var imageUrl by remember { mutableStateOf("$edgeBase/api/snapshot/$cameraId?t=${System.currentTimeMillis()}") }
    
    LaunchedEffect(cameraId) {
        while (true) {
            delay(500) // Refresh every 500ms = 2 FPS
            imageUrl = "$edgeBase/api/snapshot/$cameraId?t=${System.currentTimeMillis()}"
        }
    }
    
    AsyncImage(
        model = imageUrl,
        contentDescription = "Live: $cameraId",
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    )
}
```

For recorded playback, navigate to `VideoPlayerScreen` with the segment URL.

---

### Task 8: API Gateway — Proxy Video from Edge

The dashboard runs on the VPS (`vivasvan-tech.in:9080`) but video files live on the edge node (`192.168.1.50:5000`). The browser can't reach the edge directly if it's behind NAT.

**Solution:** API gateway proxies video requests to the edge node via VPN:

```yaml
# api-gateway application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: edge-video
          uri: http://10.8.0.10:5000    # Edge node VPN IP
          predicates:
            - Path=/edge/video/**
          filters:
            - RewritePath=/edge/video/(?<segment>.*), /api/video/${segment}
        - id: edge-live
          uri: http://10.8.0.10:5000
          predicates:
            - Path=/edge/live/**
          filters:
            - RewritePath=/edge/live/(?<segment>.*), /api/live/${segment}
```

Now the dashboard can use:
```
/edge/video/cam-01/2026-03-22/08/cam-01_08-00.mp4  → proxied to edge
/edge/live/cam-01                                    → proxied MJPEG stream
```

**For Android app** accessing from outside the farm LAN: same proxy through the VPS.

---

## How It All Looks to the User

### Dashboard — Cameras Page
```
┌─────────────────────────────────────────────────┐
│  Cameras                                [Grid]   │
├──────────┬──────────┬──────────┬──────────┤
│ cam-01   │ cam-02   │ cam-03   │ cam-04   │
│ [LIVE    │ [LIVE    │ [LIVE    │ [LIVE    │
│  FEED]   │  FEED]   │  FEED]   │  FEED]   │
│ Front    │ Storage  │ Pond     │ East     │
│ Gate     │ Shed     │ Area     │ Perim    │
│ ● Online │ ● Online │ ⚠ Alert │ ● Online │
│ [▶ Recordings]      [▶ Recordings]       │
└──────────┴──────────┴──────────┴──────────┘
```

### Dashboard — Video Player (click "Recordings")
```
┌─────────────────────────────────────────────────┐
│  cam-01: Front Gate  │ Date: [2026-03-22 ▾]     │
├─────────────────────────────────────────────────┤
│                                                   │
│   ┌─────────────────────────────────────────┐   │
│   │                                           │   │
│   │           MP4 VIDEO PLAYING               │   │
│   │          (native <video> tag)             │   │
│   │         seek, pause, fullscreen           │   │
│   │                                           │   │
│   └─────────────────────────────────────────┘   │
│                                                   │
│   ◄ ████░░░░░████░░░░████░░░░░░████░░░░ ►      │
│     00:00  04:00  08:00  12:00  16:00  20:00     │
│              ▲         ▲                          │
│           🔴 snake   🔴 person                   │
│                                                   │
│   Segments: [00:00] [00:10] [00:20] ... [23:50]  │
└─────────────────────────────────────────────────┘
```

### Dashboard — Alert Detail (with video clip)
```
┌─────────────────────────────────────────────────┐
│  🔴 CRITICAL: Snake detected at Storage Shed    │
│  cam-05 · 2026-03-22 02:15:30 · 78% confidence │
├─────────────────────────────────────────────────┤
│  Snapshot:              Video Clip (30s):        │
│  ┌──────────┐          ┌──────────────────┐     │
│  │ [JPEG]   │          │ [▶ MP4 CLIP]     │     │
│  │ snake    │          │ 15s before alert  │     │
│  │ detected │          │ to 15s after      │     │
│  └──────────┘          └──────────────────┘     │
│                                                   │
│  [Acknowledge]  [Mark False Positive]  [Trigger Siren]
└─────────────────────────────────────────────────┘
```

### Android — Same Experience
```
┌─────────────────────────┐
│ 📹 Front Gate           │
│ ┌─────────────────────┐ │
│ │                     │ │
│ │   LIVE FEED         │ │
│ │   (polling JPEG)    │ │
│ │                     │ │
│ └─────────────────────┘ │
│ [▶ Recordings]  ● Online│
│                         │
│ Tap alert → video clip: │
│ ┌─────────────────────┐ │
│ │ ExoPlayer            │ │
│ │ ▶ ████░░░░░░ 0:15   │ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

---

## File Changes

### New Files

| File | Purpose |
|:-----|:--------|
| `dashboard/src/pages/VideoPlayerPage.tsx` | Full recording playback with timeline + alert markers |
| `dashboard/src/components/LiveCameraFeed.tsx` | MJPEG live stream component |
| `dashboard/src/components/VideoClipPlayer.tsx` | Alert-linked 30s clip player |
| `android/.../ui/screens/video/VideoPlayerScreen.kt` | ExoPlayer for MP4 playback |

### Modified Files

| File | Changes |
|:-----|:--------|
| `edge/edge_gui.py` | `/api/video/...` serve MP4, `/api/live/...` MJPEG stream, `/api/video/.../recordings` list segments |
| `dashboard/src/pages/CamerasPage.tsx` | Live MJPEG feed, "Recordings" button per camera |
| `dashboard/src/pages/AlertsPage.tsx` | Video clip player in alert detail |
| `dashboard/src/navigation` | Route for /video/:cameraId |
| `android/.../CameraGridScreen.kt` | Live snapshot polling, "Recordings" button |
| `android/.../AlertDetailScreen.kt` | Video clip player |
| `android/.../NavGraph.kt` | Route for video player screen |
| `android/build.gradle.kts` | Add media3-exoplayer dependency |
| `backend/api-gateway/application.yml` | Proxy routes for /edge/video/ and /edge/live/ |

---

## Priority

**Phase 1 (Day 1): Serve + play recorded video**
- Task 1: Edge Flask serves MP4 with range support
- Task 3: Dashboard `<video>` player page
- Task 6: Android ExoPlayer screen

**Phase 2 (Day 2): Live view + alert clips**
- Task 2: Edge MJPEG stream
- Task 4: Dashboard live camera feed
- Task 5: Alert detail video clip
- Task 7: Android live feed + clip

**Phase 3 (Half day): Remote access**
- Task 8: API gateway proxy for video and live streams
