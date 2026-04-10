package com.jusi.meet.ui.room

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jusi.meet.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    livekitUrl: String,
    livekitToken: String,
    roomName: String,
    onLeave: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    // Permission gate.
    val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    var permissionsGranted by remember {
        mutableStateOf(needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionsGranted = result.values.all { it }
    }

    if (!permissionsGranted) {
        PermissionGate(
            onRequest = { permissionLauncher.launch(needed) },
            onLeave = onLeave,
        )
        return
    }

    val viewModel: RoomViewModel = viewModel(
        factory = RoomViewModel.Factory(app, livekitUrl, livekitToken),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(roomName.ifBlank { stringResource(R.string.room_title) }) })
        },
    ) { padding ->
        when (state.phase) {
            RoomUiState.Phase.Connecting -> ConnectingView(padding)
            RoomUiState.Phase.Error -> ErrorView(padding, state.errorMessage, onLeave)
            RoomUiState.Phase.Connected,
            RoomUiState.Phase.Disconnected -> RoomBody(
                padding = padding,
                state = state,
                room = viewModel.room,
                onToggleMic = viewModel::toggleMic,
                onToggleCamera = viewModel::toggleCamera,
                onSwitchCamera = viewModel::switchCamera,
                onHangup = {
                    viewModel.leave()
                    onLeave()
                },
            )
        }
    }
}

@Composable
private fun RoomBody(
    padding: PaddingValues,
    state: RoomUiState,
    room: io.livekit.android.room.Room,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHangup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(8.dp),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.participants, key = { it.identity }) { participant ->
                ParticipantTile(
                    room = room,
                    participant = participant,
                    modifier = Modifier.aspectRatio(3f / 4f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        ControlBar(
            micEnabled = state.micEnabled,
            cameraEnabled = state.cameraEnabled,
            onToggleMic = onToggleMic,
            onToggleCamera = onToggleCamera,
            onSwitchCamera = onSwitchCamera,
            onHangup = onHangup,
        )
    }
}

@Composable
private fun ControlBar(
    micEnabled: Boolean,
    cameraEnabled: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHangup: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
        FilledIconButton(onClick = onToggleMic) {
            Icon(
                imageVector = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = stringResource(
                    if (micEnabled) R.string.room_action_mic_off else R.string.room_action_mic_on,
                ),
            )
        }
        FilledIconButton(onClick = onToggleCamera) {
            Icon(
                imageVector = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                contentDescription = stringResource(
                    if (cameraEnabled) R.string.room_action_camera_off else R.string.room_action_camera_on,
                ),
            )
        }
        FilledIconButton(onClick = onSwitchCamera) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = stringResource(R.string.room_action_switch_camera),
            )
        }
        FilledIconButton(
            onClick = onHangup,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = Color.White,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = stringResource(R.string.room_action_hangup),
            )
        }
    }
}

@Composable
private fun ConnectingView(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.room_connecting))
        }
    }
}

@Composable
private fun ErrorView(padding: PaddingValues, message: String?, onLeave: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message ?: stringResource(R.string.error_unknown),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onLeave,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.room_action_hangup))
            }
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit, onLeave: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.room_permission_required))
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequest) {
                Text(stringResource(R.string.room_grant_permissions))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onLeave,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

