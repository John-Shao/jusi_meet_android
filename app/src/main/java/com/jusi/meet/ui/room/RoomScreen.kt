package com.jusi.meet.ui.room

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hearing
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

    // Restart the local camera when the Activity returns from background /
    // screen-lock. Android releases the camera on lock, and LiveKit doesn't
    // auto-restart it.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onLifecycleStop()
                Lifecycle.Event.ON_START -> viewModel.onLifecycleStart()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                    onPinParticipant = viewModel::pinParticipant,
                    onUnpinParticipant = viewModel::unpinParticipant,
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
    onPinParticipant: (String) -> Unit,
    onUnpinParticipant: () -> Unit,
    onLeave: () -> Unit,
    onEndMeeting: () -> Unit,
) {
    var toolbarsVisible by remember { mutableStateOf(true) }
    var showParticipants by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var audioOutput by remember { mutableStateOf(AudioOutput.Speaker) }

    // When toolbars are visible, inset the video area so tiles don't sit
    // behind them. Inset = system inset (status/nav bar) + toolbar content
    // height + a fixed visual gap, so the gap stays constant regardless of
    // gesture vs. 3-button navigation. When hidden, video goes fullscreen.
    val insets = WindowInsets
    val statusTop = insets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = insets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Top toolbar content: vertical padding 8*2 + IconButton 40 = 56dp
    // Bottom toolbar content: vertical padding 4*2 + icon 40 + 2 + label ~14 = ~64dp
    val gap = 8.dp
    val topInset by animateDpAsState(
        targetValue = if (toolbarsVisible) statusTop + 56.dp + gap else 0.dp,
        label = "topInset",
    )
    val bottomInset by animateDpAsState(
        targetValue = if (toolbarsVisible) navBottom + 64.dp + gap else 0.dp,
        label = "bottomInset",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { toolbarsVisible = !toolbarsVisible },
    ) {
        // Video grid
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset, bottom = bottomInset),
        ) {
            VideoGrid(
                state = state,
                room = room,
                onPin = onPinParticipant,
                onUnpin = onUnpinParticipant,
            )
        }

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
                audioOutput = audioOutput,
                onSpeakerClick = { showAudioSheet = true },
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

    // Audio output sheet
    if (showAudioSheet) {
        AudioOutputSheet(
            current = audioOutput,
            onSelect = { audioOutput = it; showAudioSheet = false },
            onDismiss = { showAudioSheet = false },
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
    audioOutput: AudioOutput,
    onSpeakerClick: () -> Unit,
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
            onClick = onSpeakerClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp),
        ) {
            Icon(
                imageVector = when (audioOutput) {
                    AudioOutput.Speaker -> Icons.AutoMirrored.Filled.VolumeUp
                    AudioOutput.Earpiece -> Icons.Default.Hearing
                    AudioOutput.Mute -> Icons.AutoMirrored.Filled.VolumeOff
                },
                contentDescription = stringResource(R.string.preview_speaker),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
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
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
    onPin: (String) -> Unit,
    onUnpin: () -> Unit,
) {
    val participants = state.participants
    if (participants.isEmpty()) return

    val focus = state.focusIdentity?.takeIf { id -> participants.any { it.identity == id } }

    if (focus != null) {
        FocusLayout(
            room = room,
            participants = participants,
            focusIdentity = focus,
            onPin = onPin,
            onUnpin = onUnpin,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        GalleryLayout(
            room = room,
            participants = participants,
            focusIdentity = null,
            onPin = onPin,
            modifier = Modifier.fillMaxSize(),
        )
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
                        Icon(
                            imageVector = if (p.isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null,
                            tint = if (p.isMicEnabled) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFFF6B6B),
                            modifier = Modifier.size(20.dp),
                        )
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

// ── Audio output ─────────────────────────────────────────────────────────

enum class AudioOutput { Speaker, Earpiece, Mute }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioOutputSheet(
    current: AudioOutput,
    onSelect: (AudioOutput) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            AudioOutputOption(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = stringResource(R.string.preview_speaker),
                isSelected = current == AudioOutput.Speaker,
                onClick = { onSelect(AudioOutput.Speaker) },
            )
            HorizontalDivider()
            AudioOutputOption(
                icon = Icons.Default.Hearing,
                label = stringResource(R.string.preview_earpiece),
                isSelected = current == AudioOutput.Earpiece,
                onClick = { onSelect(AudioOutput.Earpiece) },
            )
            HorizontalDivider()
            AudioOutputOption(
                icon = Icons.AutoMirrored.Filled.VolumeOff,
                label = stringResource(R.string.preview_mute),
                isSelected = current == AudioOutput.Mute,
                onClick = { onSelect(AudioOutput.Mute) },
            )
            Spacer(Modifier.height(8.dp))
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

@Composable
private fun AudioOutputOption(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) Color(0xFF3366FF) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) Color(0xFF3366FF) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF3366FF),
                modifier = Modifier.size(24.dp),
            )
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
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = bgColor,
                contentColor = iconTint,
            ),
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(2.dp))
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
