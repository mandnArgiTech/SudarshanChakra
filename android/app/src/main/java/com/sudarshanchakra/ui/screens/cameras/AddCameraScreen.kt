package com.sudarshanchakra.ui.screens.cameras

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudarshanchakra.data.api.executeApi
import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.domain.model.Camera
import com.sudarshanchakra.ui.theme.CreamBackground
import com.sudarshanchakra.ui.theme.GeorgiaFamily
import com.sudarshanchakra.ui.theme.SurfaceLight
import com.sudarshanchakra.ui.theme.Terracotta
import com.sudarshanchakra.ui.theme.TextPrimary
import com.sudarshanchakra.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddCameraUiState(
    val saving: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class AddCameraViewModel @Inject constructor(
    private val apiService: ApiService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddCameraUiState())
    val uiState: StateFlow<AddCameraUiState> = _uiState.asStateFlow()

    fun saveCamera(camera: Camera) {
        viewModelScope.launch {
            _uiState.value = AddCameraUiState(saving = true)
            val result = executeApi { apiService.createCamera(camera) }
            result.fold(
                onSuccess = { _uiState.value = AddCameraUiState(success = true) },
                onFailure = { _uiState.value = AddCameraUiState(error = it.message) },
            )
        }
    }
}

@Composable
fun AddCameraScreen(
    onBack: () -> Unit,
    viewModel: AddCameraViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nodeId by remember { mutableStateOf("") }
    var rtspUrl by remember { mutableStateOf("") }
    var resolution by remember { mutableStateOf("640x480") }

    if (uiState.success) {
        onBack()
        return
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
            Text(
                text = "Add Camera",
                fontFamily = GeorgiaFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = TextPrimary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = id,
                onValueChange = { id = it },
                label = { Text("Camera ID") },
                placeholder = { Text("cam-05") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Camera Name") },
                placeholder = { Text("Barn Camera") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = nodeId,
                onValueChange = { nodeId = it },
                label = { Text("Edge Node ID") },
                placeholder = { Text("edge-node-a") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = rtspUrl,
                onValueChange = { rtspUrl = it },
                label = { Text("RTSP URL") },
                placeholder = { Text("rtsp://user:pass@ip:554/stream2") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = resolution,
                onValueChange = { resolution = it },
                label = { Text("Resolution") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.error ?: "",
                    color = Color.Red,
                    fontSize = 12.sp,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.saveCamera(
                        Camera(
                            id = id,
                            nodeId = nodeId,
                            name = name,
                            rtspUrl = rtspUrl,
                            status = "unknown",
                            resolution = resolution,
                        )
                    )
                },
                enabled = !uiState.saving && id.isNotBlank() && name.isNotBlank()
                        && nodeId.isNotBlank() && rtspUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
            ) {
                Icon(Icons.Filled.Save, "Save", modifier = Modifier.size(18.dp))
                Text(
                    text = if (uiState.saving) "  Saving..." else "  Save Camera",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
