# G-06: Add ExoPlayer media3 Dependency

## Status
**COMPLETE** — `androidx.media3:media3-exoplayer:1.3.1` and `androidx.media3:media3-ui:1.3.1` are declared in `android/app/build.gradle.kts` (after Room).

## Clarification
`VideoPlayerScreen.kt` currently uses `android.widget.VideoView` and did not require Media3 to compile. These dependencies are the **prerequisite for G-11** (alert video clip) and any future ExoPlayer-based playback.

## Implemented

### `android/app/build.gradle.kts`

```kotlin
// Media3 / ExoPlayer (G-06 — prerequisite for G-11 alert video clip)
implementation("androidx.media3:media3-exoplayer:1.3.1")
implementation("androidx.media3:media3-ui:1.3.1")
```

## Verification
```bash
cd android && ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug --no-daemon 2>&1 | tail -5
# Expected: BUILD SUCCESSFUL
```

Requires a valid Android SDK (`ANDROID_HOME` or `android/local.properties` with `sdk.dir`).

## Note
This unblocks G-11 (Android alert video clip).
