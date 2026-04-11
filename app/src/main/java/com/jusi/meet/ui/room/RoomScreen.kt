package com.jusi.meet.ui.room

import android.app.Application
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jusi.meet.R

@Composable
fun RoomScreen(
    livekitUrl: String,
    livekitToken: String,
    roomName: String,
    roomSlug: String,
    initialMicEnabled: Boolean = true,
    initialCameraEnabled: Boolean = true,
    onLeave: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    val viewModel: RoomViewModel = viewModel(
        factory = RoomViewModel.Factory(app, livekitUrl, livekitToken, initialMicEnabled, initialCameraEnabled),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (state.phase) {
            RoomUiState.Phase.Connecting -> ConnectingView()
            RoomUiState.Phase.Error -> ErrorView(state.errorMessage, onLeave)
            RoomUiState.Phase.Connected,
            RoomUiState.Phase.Disconnected -> {
                // Video grid fills the entire screen
                VideoGrid(
                    state = state,
                    room = viewModel.room,
                )

                // Top bar overlay
                TopBarOverlay(
                    roomName = roomName,
                    roomSlug = roomSlug,
                )

                // Bottom control bar overlay
                BottomControlOverlay(
                    micEnabled = state.micEnabled,
                    cameraEnabled = state.cameraEnabled,
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
}

@Composable
private fun TopBarOverlay(
    roomName: String,
    roomSlug: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = roomName.ifBlank { stringResource(R.string.room_title) },
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            if (roomSlug.isNotBlank()) {
                Text(
                    text = stringResource(R.string.room_slug_label, roomSlug),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun VideoGrid(
    state: RoomUiState,
    room: io.livekit.android.room.Room,
) {
    val participants = state.participants
    val count = participants.size

    if (count == 0) return

    if (count == 1) {
        // Single participant: full screen
        ParticipantTile(
            room = room,
            participant = participants[0],
            modifier = Modifier.fillMaxSize(),
        )
    } else if (count == 2) {
        // Two participants: stack vertically, each takes half
        Column(modifier = Modifier.fillMaxSize()) {
            participants.forEach { participant ->
                ParticipantTile(
                    room = room,
                    participant = participant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    } else {
        // 3+: adaptive grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(participants, key = { it.identity }) { participant ->
                ParticipantTile(
                    room = room,
                    participant = participant,
                    modifier = Modifier.aspectRatio(3f / 4f),
                )
            }
        }
    }
}

@Composable
private fun BottomControlOverlay(
    micEnabled: Boolean,
    cameraEnabled: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHangup: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(
                icon = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                label = stringResource(if (micEnabled) R.string.room_action_mic_off else R.string.room_action_mic_on),
                isOn = micEnabled,
                onClick = onToggleMic,
            )
            ControlButton(
                icon = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                label = stringResource(if (cameraEnabled) R.string.room_action_camera_off else R.string.room_action_camera_on),
                isOn = cameraEnabled,
                onClick = onToggleCamera,
            )
            ControlButton(
                icon = Icons.Default.Cameraswitch,
                label = stringResource(R.string.room_action_switch_camera),
                isOn = true,
                onClick = onSwitchCamera,
            )
            // Hangup button (red)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FilledIconButton(
                    onClick = onHangup,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFEE4444),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = stringResource(R.string.room_action_hangup),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.room_action_hangup),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    isOn: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isOn) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f)
    val iconTint = if (isOn) Color.White else Color(0xFFFF6B6B)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = bgColor,
                contentColor = iconTint,
            ),
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun ConnectingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.room_connecting),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun ErrorView(message: String?, onLeave: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message ?: stringResource(R.string.error_unknown),
                color = Color(0xFFFF6B6B),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onLeave,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE4444)),
            ) {
                Text(stringResource(R.string.room_action_hangup), color = Color.White)
            }
        }
    }
}
