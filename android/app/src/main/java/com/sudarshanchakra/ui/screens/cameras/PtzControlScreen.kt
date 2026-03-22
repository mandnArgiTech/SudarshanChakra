package com.sudarshanchakra.ui.screens.cameras

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class PtzPreset(val token: String, val name: String)

data class PtzUiState(
    val presets: List<PtzPreset> = emptyList(),
    val hasPtz: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PtzViewModel @Inject constructor(
    serverSettingsRepository: ServerSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PtzUiState())
    val uiState: StateFlow<PtzUiState> = _uiState.asStateFlow()

    val edgeGuiBaseUrl: StateFlow<String> = serverSettingsRepository.settings
        .map { it.edgeGuiBaseUrl.trim().trimEnd('/') }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun loadCapabilities(cameraId: String) {
        viewModelScope.launch {
            try {
                val base = edgeGuiBaseUrl.value
                if (base.isBlank()) return@launch
                val json = withContext(Dispatchers.IO) {
                    URL("$base/api/ptz/$cameraId/capabilities").readText()
                }
                val obj = JSONObject(json)
                _uiState.value = _uiState.value.copy(
                    hasPtz = obj.optBoolean("has_ptz", false)
                )
                loadPresets(cameraId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun loadPresets(cameraId: String) {
        viewModelScope.launch {
            try {
                val base = edgeGuiBaseUrl.value
                if (base.isBlank()) return@launch
                val json = withContext(Dispatchers.IO) {
                    URL("$base/api/ptz/$cameraId/presets").readText()
                }
                val arr = JSONArray(json)
                val presets = mutableListOf<PtzPreset>()
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    presets.add(PtzPreset(p.getString("token"), p.getString("name")))
                }
                _uiState.value = _uiState.value.copy(presets = presets)
            } catch (_: Exception) { }
        }
    }

    fun move(cameraId: String, pan: Float, tilt: Float, zoom: Float) {
        viewModelScope.launch {
            postPtz(cameraId, "move", """{"pan":$pan,"tilt":$tilt,"zoom":$zoom}""")
        }
    }

    fun stop(cameraId: String) {
        viewModelScope.launch {
            postPtz(cameraId, "stop", "{}")
        }
    }

    fun gotoPreset(cameraId: String, token: String) {
        viewModelScope.launch {
            postPtz(cameraId, "preset/goto", """{"token":"$token"}""")
        }
    }

    private suspend fun postPtz(cameraId: String, path: String, body: String) {
        val base = edgeGuiBaseUrl.value
        if (base.isBlank()) return
        withContext(Dispatchers.IO) {
            try {
                val conn = URL("$base/api/ptz/$cameraId/$path").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream).use { it.write(body) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) { }
        }
    }
}

@Composable
fun PtzControlScreen(
    cameraId: String,
    onBack: () -> Unit,
    viewModel: PtzViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val edgeBase by viewModel.edgeGuiBaseUrl.collectAsState()

    var snapshotTick by remember { mutableStateOf(0) }
    LaunchedEffect(edgeBase) {
        if (edgeBase.isNotBlank()) {
            while (isActive) {
                delay(1000)
                snapshotTick++
            }
        }
    }

    LaunchedEffect(cameraId) {
        viewModel.loadCapabilities(cameraId)
    }

    val snapshotUrl = remember(cameraId, edgeBase, snapshotTick) {
        if (edgeBase.isBlank()) null
        else "$edgeBase/api/snapshot/$cameraId?t=$snapshotTick"
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
                    text = "PTZ Control",
                    fontFamily = GeorgiaFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = TextPrimary,
                )
                Text(text = cameraId, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
        ) {
            if (snapshotUrl != null) {
                AsyncImage(
                    model = snapshotUrl,
                    contentDescription = "Live feed",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No edge URL configured", color = TextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        @Composable
        fun DirButton(icon: ImageVector, label: String, onClick: () -> Unit, onRelease: () -> Unit) {
            IconButton(
                onClick = { onClick(); },
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardWhite),
            ) {
                Icon(icon, contentDescription = label, tint = TextPrimary, modifier = Modifier.size(28.dp))
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(
                onClick = { viewModel.move(cameraId, 0f, 0.5f, 0f) },
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(CardWhite),
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, "Up", tint = TextPrimary, modifier = Modifier.size(28.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { viewModel.move(cameraId, -0.5f, 0f, 0f) },
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(CardWhite),
                ) {
                    Icon(Icons.Filled.KeyboardArrowLeft, "Left", tint = TextPrimary, modifier = Modifier.size(28.dp))
                }
                IconButton(
                    onClick = { viewModel.stop(cameraId) },
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Terracotta.copy(alpha = 0.1f)),
                ) {
                    Icon(Icons.Filled.Stop, "Stop", tint = Terracotta, modifier = Modifier.size(28.dp))
                }
                IconButton(
                    onClick = { viewModel.move(cameraId, 0.5f, 0f, 0f) },
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(CardWhite),
                ) {
                    Icon(Icons.Filled.KeyboardArrowRight, "Right", tint = TextPrimary, modifier = Modifier.size(28.dp))
                }
            }
            IconButton(
                onClick = { viewModel.move(cameraId, 0f, -0.5f, 0f) },
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(CardWhite),
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, "Down", tint = TextPrimary, modifier = Modifier.size(28.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(
                onClick = { viewModel.move(cameraId, 0f, 0f, -0.5f) },
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(CardWhite),
            ) {
                Icon(Icons.Filled.ZoomOut, "Zoom Out", tint = TextPrimary)
            }
            Text("Zoom", color = TextSecondary, modifier = Modifier.align(Alignment.CenterVertically))
            IconButton(
                onClick = { viewModel.move(cameraId, 0f, 0f, 0.5f) },
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(CardWhite),
            ) {
                Icon(Icons.Filled.ZoomIn, "Zoom In", tint = TextPrimary)
            }
        }

        if (uiState.presets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Presets",
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.presets) { preset ->
                    OutlinedButton(
                        onClick = { viewModel.gotoPreset(cameraId, preset.token) },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(preset.name, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
