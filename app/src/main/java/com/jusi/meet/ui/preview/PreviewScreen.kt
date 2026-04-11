package com.jusi.meet.ui.preview

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.R

enum class PreviewMode { Create, Join }

/**
 * Pre-meeting preview screen used for both creating and joining meetings.
 *
 * - **Create mode**: editable meeting name, camera preview, "开始会议" button.
 * - **Join mode**: meeting ID input, camera preview, "加入会议" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    mode: PreviewMode,
    onEnterRoom: (livekitUrl: String, livekitToken: String, name: String, slug: String, mic: Boolean, cam: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as JusiMeetApp
    val previewViewModel: PreviewViewModel = viewModel(factory = PreviewViewModel.Factory(app))
    val state by previewViewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Permission handling
    val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    var permissionsGranted by remember {
        mutableStateOf(needed.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionsGranted = result.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) permissionLauncher.launch(needed)
    }

    var micEnabled by remember { mutableStateOf(true) }
    var cameraEnabled by remember { mutableStateOf(true) }

    // Mode-specific state
    var meetingName by remember { mutableStateOf(previewViewModel.defaultMeetingName) }
    var meetingId by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            // Header: editable meeting name (create) or meeting ID input (join)
            when (mode) {
                PreviewMode.Create -> {
                    OutlinedTextField(
                        value = meetingName,
                        onValueChange = { meetingName = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 48.dp),
                        color = Color(0xFF3366FF),
                    )
                }
                PreviewMode.Join -> {
                    OutlinedTextField(
                        value = meetingId,
                        onValueChange = { value ->
                            // Only allow digits, max 6 characters
                            meetingId = value.filter { it.isDigit() }.take(6)
                        },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.preview_meeting_id_hint),
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 48.dp),
                        color = Color(0xFF3366FF),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Camera preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (cameraEnabled && permissionsGranted) {
                    CameraPreview()
                } else {
                    Text(
                        text = stringResource(R.string.room_no_video),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Mic / Camera toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                ToggleCard(
                    icon = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                    label = stringResource(R.string.preview_mic),
                    isOn = micEnabled,
                    onClick = { micEnabled = !micEnabled },
                    modifier = Modifier.weight(1f),
                )
                ToggleCard(
                    icon = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    label = stringResource(R.string.preview_camera),
                    isOn = cameraEnabled,
                    onClick = { cameraEnabled = !cameraEnabled },
                    modifier = Modifier.weight(1f),
                )
            }

            // Error message
            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.weight(1f))

            // Action button
            val actionLabel = when (mode) {
                PreviewMode.Create -> stringResource(R.string.preview_start_meeting)
                PreviewMode.Join -> stringResource(R.string.preview_join_meeting)
            }
            val actionEnabled = when (mode) {
                PreviewMode.Create -> meetingName.isNotBlank() && !state.isLoading
                PreviewMode.Join -> meetingId.length == 6 && !state.isLoading
            }

            Button(
                onClick = {
                    val callback = { target: RoomTarget ->
                        onEnterRoom(target.livekitUrl, target.livekitToken, target.displayName, target.slug, micEnabled, cameraEnabled)
                    }
                    when (mode) {
                        PreviewMode.Create -> previewViewModel.createMeeting(meetingName, callback)
                        PreviewMode.Join -> previewViewModel.joinRoom(meetingId, callback)
                    }
                },
                enabled = actionEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3366FF)),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ToggleCard(
    icon: ImageVector,
    label: String,
    isOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconTint = if (isOn) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.error
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CameraPreview() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose { cameraProvider?.unbindAll() }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}
