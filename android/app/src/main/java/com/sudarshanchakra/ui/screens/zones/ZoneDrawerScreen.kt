package com.sudarshanchakra.ui.screens.zones

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class ZoneDrawerViewModel @Inject constructor(
    serverSettingsRepository: ServerSettingsRepository,
) : ViewModel() {
    val edgeGuiBaseUrl: StateFlow<String> = serverSettingsRepository.settings
        .map { it.edgeGuiBaseUrl.trim().trimEnd('/') }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val saveResult = MutableStateFlow<String?>(null)

    fun saveZone(cameraId: String, name: String, type: String, priority: String,
                 targets: String, points: List<Offset>, canvasWidth: Float, canvasHeight: Float) {
        viewModelScope.launch {
            val base = edgeGuiBaseUrl.value
            if (base.isBlank()) return@launch

            val polygon = JSONArray()
            points.forEach { p ->
                val arr = JSONArray()
                arr.put(p.x.toInt())
                arr.put(p.y.toInt())
                polygon.put(arr)
            }

            val targetArr = JSONArray()
            targets.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { targetArr.put(it) }

            val zoneId = "zone-" + name.lowercase().replace(Regex("[^a-z0-9]"), "-")
            val body = JSONObject().apply {
                put("id", zoneId)
                put("name", name)
                put("type", type)
                put("priority", priority)
                put("target_classes", targetArr)
                put("polygon", polygon)
            }

            try {
                val result = withContext(Dispatchers.IO) {
                    val conn = URL("$base/api/zones/$cameraId").openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
                    val code = conn.responseCode
                    val resp = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    resp
                }
                val obj = JSONObject(result)
                if (obj.optBoolean("success", false)) {
                    saveResult.value = "Zone saved successfully"
                } else {
                    saveResult.value = "Error: ${obj.optString("error", "Unknown")}"
                }
            } catch (e: Exception) {
                saveResult.value = "Failed: ${e.message}"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneDrawerScreen(
    cameraId: String,
    onBack: () -> Unit,
    viewModel: ZoneDrawerViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val edgeBase by viewModel.edgeGuiBaseUrl.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val points = remember { mutableStateListOf<Offset>() }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("intrusion") }
    var priority by remember { mutableStateOf("high") }
    var targets by remember { mutableStateOf("person") }

    val snapshotUrl = remember(cameraId, edgeBase) {
        if (edgeBase.isBlank()) null else "$edgeBase/api/snapshot/$cameraId?t=0"
    }

    LaunchedEffect(saveResult) {
        if (saveResult?.startsWith("Zone saved") == true) {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .verticalScroll(rememberScrollState()),
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
                    text = "Draw Zone",
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            points.add(offset)
                        }
                    },
            ) {
                if (snapshotUrl != null) {
                    AsyncImage(
                        model = snapshotUrl,
                        contentDescription = "Camera snapshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (points.size >= 2) {
                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                            if (points.size > 2) close()
                        }
                        drawPath(path, Color(0x33F59E0B))
                        drawPath(path, Color(0xFFF59E0B), style = Stroke(width = 2f))
                    }
                    points.forEach { p ->
                        drawCircle(Color(0xFFF59E0B), 6f, p)
                    }
                }
            }
        }

        Text(
            text = "${points.size} points — tap on image to add vertices (min 3)",
            color = Terracotta,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { if (points.isNotEmpty()) points.removeAt(points.size - 1) },
                enabled = points.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Undo, "Undo", modifier = Modifier.size(16.dp))
                Text(" Undo", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { points.clear() },
                enabled = points.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Delete, "Clear", modifier = Modifier.size(16.dp))
                Text(" Clear", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Zone Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = targets,
                onValueChange = { targets = it },
                label = { Text("Target Classes (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (saveResult != null && !saveResult!!.startsWith("Zone saved")) {
            Text(
                text = saveResult ?: "",
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    viewModel.saveZone(cameraId, name, type, priority, targets,
                        points.toList(), 0f, 0f)
                },
                enabled = points.size >= 3 && name.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Save, "Save", modifier = Modifier.size(16.dp))
                Text(" Save Zone")
            }
        }
    }
}
