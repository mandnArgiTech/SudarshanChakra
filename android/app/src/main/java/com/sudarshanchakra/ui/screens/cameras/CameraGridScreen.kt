package com.sudarshanchakra.ui.screens.cameras

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.sudarshanchakra.data.repository.DeviceRepository
import com.sudarshanchakra.data.repository.ServerSettingsRepository
import com.sudarshanchakra.domain.model.Camera
import com.sudarshanchakra.ui.theme.CardWhite
import com.sudarshanchakra.ui.theme.CreamBackground
import com.sudarshanchakra.ui.theme.GeorgiaFamily
import com.sudarshanchakra.ui.theme.StatusOffline
import com.sudarshanchakra.ui.theme.StatusOnline
import com.sudarshanchakra.ui.theme.SurfaceLight
import com.sudarshanchakra.ui.theme.Terracotta
import com.sudarshanchakra.ui.theme.TextMuted
import com.sudarshanchakra.ui.theme.TextPrimary
import com.sudarshanchakra.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

data class CameraGridUiState(
    val cameras: List<Camera> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    serverSettingsRepository: ServerSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CameraGridUiState())
    val uiState: StateFlow<CameraGridUiState> = _uiState.asStateFlow()

    /** Edge Flask origin for JPEG snapshots; empty if unset in Server settings. */
    val edgeGuiBaseUrl: StateFlow<String> = serverSettingsRepository.settings
        .map { it.edgeGuiBaseUrl.trim().trimEnd('/') }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private var hasLoadedOnce = false

    init {
        loadCameras()
    }

    fun loadCameras() {
        viewModelScope.launch {
            val blocking = !hasLoadedOnce
            _uiState.update {
                if (blocking) it.copy(isLoading = true, error = null)
                else it.copy(isRefreshing = true, error = null)
            }
            val result = deviceRepository.getCameras()
            result.fold(
                onSuccess = { cameras ->
                    hasLoadedOnce = true
                    _uiState.update {
                        it.copy(cameras = cameras, isLoading = false, isRefreshing = false)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "Failed to load cameras",
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CameraGridScreen(
    viewModel: CameraViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val edgeBase by viewModel.edgeGuiBaseUrl.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cameras",
                    fontFamily = GeorgiaFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = TextPrimary,
                )
                Text(
                    text = "${uiState.cameras.size} cameras connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            IconButton(onClick = { viewModel.loadCameras() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh cameras")
            }
        }

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Terracotta)
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.BrokenImage,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = TextMuted,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: "Error loading cameras",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
            uiState.cameras.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = TextMuted,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No cameras found",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Connect cameras to edge nodes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            }
            else -> {
                val pullRefreshState = rememberPullRefreshState(
                    refreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.loadCameras() },
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.cameras, key = { it.id }) { camera ->
                            CameraCard(camera = camera, edgeSnapshotBase = edgeBase)
                        }
                    }
                    PullRefreshIndicator(
                        refreshing = uiState.isRefreshing,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraCard(camera: Camera, edgeSnapshotBase: String) {
    val isOnline = camera.status.equals("ONLINE", ignoreCase = true) ||
        camera.status.equals("ACTIVE", ignoreCase = true)

    var snapshotTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(edgeSnapshotBase) {
        if (edgeSnapshotBase.isNotBlank()) {
            while (isActive) {
                delay(3000)
                snapshotTick++
            }
        }
    }
    val snapshotUrl = remember(camera.id, edgeSnapshotBase, snapshotTick) {
        if (edgeSnapshotBase.isBlank()) {
            null
        } else {
            val enc = URLEncoder.encode(camera.id, StandardCharsets.UTF_8.name())
            "$edgeSnapshotBase/api/snapshot/$enc?t=$snapshotTick"
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(SurfaceLight),
                contentAlignment = Alignment.Center
            ) {
                if (snapshotUrl != null) {
                    AsyncImage(
                        model = snapshotUrl,
                        contentDescription = camera.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Filled.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = TextMuted,
                    )
                }
                if (camera.resolution != null && snapshotUrl == null) {
                    Text(
                        text = camera.resolution,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) StatusOnline else StatusOffline)
                )
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = camera.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) StatusOnline else StatusOffline)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOnline) "Online" else "Offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOnline) StatusOnline else StatusOffline
                    )
                }
            }
        }
    }
}
