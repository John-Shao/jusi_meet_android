package com.jusi.meet.ui.room

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    roomId: String,
    livekitUrl: String,
    livekitToken: String,
    roomName: String,
    roomSlug: String,
    isAdmin: Boolean,
    initialMicEnabled: Boolean = true,
    initialCameraEnabled: Boolean = true,
    onLeave: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    val viewModel: RoomViewModel = viewModel(
        factory = RoomViewModel.Factory(app, roomId, livekitUrl, livekitToken, initialMicEnabled, initialCameraEnabled),
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
                RoomContent(
                    state = state,
                    room = viewModel.room,
                    roomName = roomName,
                    roomSlug = roomSlug,
                    isAdmin = isAdmin,
                    onToggleMic = viewModel::toggleMic,
                    onToggleCamera = viewModel::toggleCamera,
                    onSwitchCamera = viewModel::switchCamera,
                    onLeave = {
                        viewModel.leave()
                        onLeave()
                    },
                    onEndMeeting = {
                        viewModel.endMeeting { onLeave() }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomContent(
    state: RoomUiState,
    room: io.livekit.android.room.Room,
    roomName: String,
    roomSlug: String,
    isAdmin: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onLeave: () -> Unit,
    onEndMeeting: () -> Unit,
) {
    var toolbarsVisible by remember { mutableStateOf(true) }
    var showParticipants by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { toolbarsVisible = !toolbarsVisible },
    ) {
        // Video grid
        VideoGrid(state = state, room = room)

        // Top toolbar (drawer-style)
        AnimatedVisibility(
            visible = toolbarsVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopToolbar(
                roomName = roomName,
                roomSlug = roomSlug,
                onLeave = { showLeaveDialog = true },
            )
        }

        // Bottom toolbar (drawer-style)
        AnimatedVisibility(
            visible = toolbarsVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomToolbar(
                micEnabled = state.micEnabled,
                cameraEnabled = state.cameraEnabled,
                participantCount = state.participants.size,
                onToggleMic = onToggleMic,
                onToggleCamera = onToggleCamera,
                onSwitchCamera = onSwitchCamera,
                onShowParticipants = { showParticipants = true },
            )
        }
    }

    // Participants bottom sheet
    if (showParticipants) {
        ParticipantsSheet(
            participants = state.participants,
            onDismiss = { showParticipants = false },
        )
    }

    // Leave/End meeting dialog
    if (showLeaveDialog) {
        LeaveDialog(
            isAdmin = isAdmin,
            onLeave = { showLeaveDialog = false; onLeave() },
            onEndMeeting = { showLeaveDialog = false; onEndMeeting() },
            onDismiss = { showLeaveDialog = false },
        )
    }
}

// ── Top toolbar ──────────────────────────────────────────────────────────

@Composable
private fun TopToolbar(
    roomName: String,
    roomSlug: String,
    onLeave: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Left: speaker icon
        IconButton(
            onClick = { /* TODO: audio output picker */ },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(R.string.preview_speaker),
                tint = Color.White,
            )
        }

        // Center: room name + slug
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = roomName.ifBlank { stringResource(R.string.room_title) },
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
            )
            if (roomSlug.isNotBlank()) {
                Text(
                    text = stringResource(R.string.room_slug_label, roomSlug),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }

        // Right: leave/end button
        TextButton(
            onClick = onLeave,
            modifier = Modifier.align(Alignment.CenterEnd),
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF4444)),
        ) {
            Text(
                text = stringResource(R.string.room_end),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

// ── Bottom toolbar ───────────────────────────────────────────────────────

@Composable
private fun BottomToolbar(
    micEnabled: Boolean,
    cameraEnabled: Boolean,
    participantCount: Int,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onShowParticipants: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(
            icon = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
            label = stringResource(if (micEnabled) R.string.room_action_mic_on else R.string.room_action_mic_off),
            isOn = micEnabled,
            onClick = onToggleMic,
        )
        ControlButton(
            icon = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
            label = stringResource(if (cameraEnabled) R.string.room_action_camera_on else R.string.room_action_camera_off),
            isOn = cameraEnabled,
            onClick = onToggleCamera,
        )
        ControlButton(
            icon = Icons.Default.Cameraswitch,
            label = stringResource(R.string.room_action_switch_camera),
            isOn = true,
            onClick = onSwitchCamera,
        )
        ControlButton(
            icon = Icons.Default.Groups,
            label = stringResource(R.string.room_participants, participantCount),
            isOn = true,
            onClick = onShowParticipants,
        )
    }
}

// ── Video grid ───────────────────────────────────────────────────────────

@Composable
private fun VideoGrid(
    state: RoomUiState,
    room: io.livekit.android.room.Room,
) {
    val participants = state.participants
    val count = participants.size

    if (count == 0) return

    if (count == 1) {
        ParticipantTile(
            room = room,
            participant = participants[0],
            modifier = Modifier.fillMaxSize(),
        )
    } else if (count == 2) {
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

// ── Participants bottom sheet ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParticipantsSheet(
    participants: List<ParticipantUi>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.room_participants, participants.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            HorizontalDivider()
            LazyColumn {
                items(participants) { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(6.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = p.name + if (p.isLocal) stringResource(R.string.room_participant_me) else "",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (!p.isMicEnabled) {
                            Icon(
                                imageVector = Icons.Default.MicOff,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Leave dialog ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveDialog(
    isAdmin: Boolean,
    onLeave: () -> Unit,
    onEndMeeting: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isAdmin) {
                Text(
                    text = stringResource(R.string.room_leave_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                HorizontalDivider()
            }

            // Leave meeting
            TextButton(
                onClick = onLeave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = stringResource(R.string.room_leave),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            // End meeting (red) - only for owner
            if (isAdmin) {
                HorizontalDivider()
                TextButton(
                    onClick = onEndMeeting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF4444)),
                ) {
                    Text(
                        text = stringResource(R.string.room_end_meeting),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Cancel
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Shared components ────────────────────────────────────────────────────

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    isOn: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isOn) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f)
    val iconTint = if (isOn) Color.White else Color(0xFFFF6B6B)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            Text(stringResource(R.string.room_connecting), color = Color.White)
        }
    }
}

@Composable
private fun ErrorView(message: String?, onLeave: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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
