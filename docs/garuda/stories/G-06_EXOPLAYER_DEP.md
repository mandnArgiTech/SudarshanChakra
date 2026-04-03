# G-06: Add ExoPlayer media3 Dependency

## Status
NOT DONE — `VideoPlayerScreen.kt` (359 lines) exists but won't compile because media3 is missing from dependencies.

## File to MODIFY

### `android/app/build.gradle.kts`

Add these two lines in the `dependencies {}` block, after the existing `room-ktx` line:
```kotlin
implementation("androidx.media3:media3-exoplayer:1.3.1")
implementation("androidx.media3:media3-ui:1.3.1")
```

## Verification
```bash
cd android && ./gradlew assembleDebug 2>&1 | tail -5
# Expected: BUILD SUCCESSFUL
# VideoPlayerScreen.kt should compile without errors
```

## Note
This is a 1-minute fix but it BLOCKS G-11 (Android alert video clip). Execute first.
