# G-11: Android Alert Video Clip (ExoPlayer)

## Prerequisites
- G-06 complete (media3 dependency added)

## File to MODIFY

### `android/.../ui/screens/alerts/AlertDetailScreen.kt`

Read the file. Find where the alert snapshot image is displayed.

Add BELOW the snapshot:
```kotlin
// Video clip player
if (!alert.clipPath.isNullOrBlank()) {
    val context = LocalContext.current
    val edgeBase = remember { RuntimeConnectionConfig.getEdgeBaseUrl(context) }
    val clipUrl = "$edgeBase/api/clips/${alert.id}.mp4"
    
    val exoPlayer = remember(clipUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(clipUrl))
            prepare()
        }
    }
    
    DisposableEffect(clipUrl) {
        onDispose { exoPlayer.release() }
    }
    
    Text(
        text = "Video evidence",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    
    AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp)),
    )
}
```

### Imports needed:
```kotlin
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import android.view.ViewGroup
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
```

### Check Alert data class
Ensure `clipPath` field exists in `android/.../domain/model/Alert.kt`:
```kotlin
data class Alert(
    // ... existing fields ...
    val clipPath: String? = null,
)
```

## Verification
```bash
cd android && ./gradlew assembleDebug
# Open alert with video clip → ExoPlayer renders with play/pause/seek controls
```

---

