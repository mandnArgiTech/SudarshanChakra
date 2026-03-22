package com.sudarshanchakra.ui.screens.cameras

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudarshanchakra.data.repository.ServerSettingsRepository
import com.sudarshanchakra.ui.theme.CardWhite
import com.sudarshanchakra.ui.theme.CreamBackground
import com.sudarshanchakra.ui.theme.GeorgiaFamily
import com.sudarshanchakra.ui.theme.SurfaceLight
import com.sudarshanchakra.ui.theme.Terracotta
import com.sudarshanchakra.ui.theme.TextMuted
import com.sudarshanchakra.ui.theme.TextPrimary
import com.sudarshanchakra.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject

data class RecordingDate(
    val date: String,
    val hours: List<RecordingHour>,
)

data class RecordingHour(
    val hour: String,
    val segments: List<String>,
)

data class VideoPlayerUiState(
    val recordings: List<RecordingDate> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentVideoUrl: String? = null,
    val currentSegment: String? = null,
)

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    serverSettingsRepository: ServerSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    val edgeGuiBaseUrl: StateFlow<String> = serverSettingsRepository.settings
        .map { it.edgeGuiBaseUrl.trim().trimEnd('/') }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun loadRecordings(cameraId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val base = edgeGuiBaseUrl.value
                if (base.isBlank()) {
                    _uiState.update { it.copy(isLoading = false, error = "Edge GUI URL not configured") }
                    return@launch
                }
                val json = withContext(Dispatchers.IO) {
                    URL("$base/api/recordings/$cameraId").readText()
                }
                val obj = JSONObject(json)
                val datesArr = obj.optJSONArray("dates") ?: run {
                    _uiState.update { it.copy(isLoading = false, recordings = emptyList()) }
                    return@launch
                }
                val dates = mutableListOf<RecordingDate>()
                for (i in 0 until datesArr.length()) {
                    val d = datesArr.getJSONObject(i)
                    val hoursArr = d.optJSONArray("hours") ?: continue
                    val hours = mutableListOf<RecordingHour>()
                    for (j in 0 until hoursArr.length()) {
                        val h = hoursArr.getJSONObject(j)
                        val segsArr = h.optJSONArray("segments") ?: continue
                        val segs = mutableListOf<String>()
                        for (k in 0 until segsArr.length()) {
                            segs.add(segsArr.getString(k))
                        }
                        hours.add(RecordingHour(h.getString("hour"), segs))
                    }
                    dates.add(RecordingDate(d.getString("date"), hours))
                }
                _uiState.update { it.copy(isLoading = false, recordings = dates) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun playSegment(cameraId: String, date: String, hour: String, segment: String) {
        val base = edgeGuiBaseUrl.value
        if (base.isBlank()) return
        val url = "$base/api/video/$cameraId/$date/$hour/$segment"
        _uiState.update { it.copy(currentVideoUrl = url, currentSegment = segment) }
    }
}

@Composable
fun VideoPlayerScreen(
    cameraId: String,
    onBack: () -> Unit,
    viewModel: VideoPlayerViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(cameraId) {
        viewModel.loadRecordings(cameraId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceLight)
                        .padding(6.dp),
                    tint = TextPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = "Recordings",
                    fontFamily = GeorgiaFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = TextPrimary,
                )
                Text(
                    text = cameraId,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary,
                )
            }
            IconButton(onClick = { viewModel.loadRecordings(cameraId) }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        if (uiState.currentVideoUrl != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(Uri.parse(uiState.currentVideoUrl))
                            setOnPreparedListener { mp ->
                                mp.isLooping = false
                                start()
                            }
                        }
                    },
                    update = { view ->
                        view.setVideoURI(Uri.parse(uiState.currentVideoUrl))
                        view.start()
                    },
                )
            }
            if (uiState.currentSegment != null) {
                Text(
                    text = "Playing: ${uiState.currentSegment}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceLight),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = TextMuted,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select a segment to play", color = TextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Terracotta)
                }
            }
            uiState.error != null -> {
                Text(
                    text = uiState.error ?: "Error",
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            uiState.recordings.isEmpty() -> {
                Text(
                    text = "No recordings found",
                    color = TextMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.recordings.forEach { dateEntry ->
                        item {
                            Text(
                                text = dateEntry.date,
                                fontWeight = FontWeight.Bold,
                                color = Terracotta,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        dateEntry.hours.forEach { hourEntry ->
                            item {
                                Text(
                                    text = "${hourEntry.hour}:00",
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                                )
                            }
                            items(hourEntry.segments) { segment ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp)
                                        .clickable {
                                            viewModel.playSegment(
                                                cameraId, dateEntry.date,
                                                hourEntry.hour, segment
                                            )
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (segment == uiState.currentSegment)
                                            Terracotta.copy(alpha = 0.1f)
                                        else CardWhite
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayCircle,
                                            contentDescription = "Play",
                                            tint = if (segment == uiState.currentSegment) Terracotta else TextMuted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Text(
                                            text = segment.removeSuffix(".mp4"),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            color = TextPrimary,
                                            modifier = Modifier.padding(start = 12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
