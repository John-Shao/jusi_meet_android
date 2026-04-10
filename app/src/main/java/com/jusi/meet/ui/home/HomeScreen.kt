package com.jusi.meet.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onJoinRoom: (livekitUrl: String, livekitToken: String, name: String, slug: String) -> Unit,
    onSignedOut: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as JusiMeetApp
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(app))
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showJoinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = {
                        viewModel.signOut()
                        onSignedOut()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.home_logout),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        HomeContent(
            padding = padding,
            state = state,
            onCreateMeeting = {
                viewModel.createMeeting { target ->
                    onJoinRoom(target.livekit.url, target.livekit.token, target.displayName, target.slug)
                }
            },
            onJoinClick = { showJoinDialog = true },
        )
    }

    if (showJoinDialog) {
        JoinRoomDialog(
            roomInput = state.roomInput,
            isJoining = state.isJoining,
            errorMessage = state.errorMessage,
            onRoomInputChange = viewModel::onRoomInputChange,
            onJoin = {
                viewModel.joinRoom { target ->
                    showJoinDialog = false
                    onJoinRoom(target.livekit.url, target.livekit.token, target.displayName, target.slug)
                }
            },
            onDismiss = { showJoinDialog = false },
        )
    }
}

@Composable
private fun HomeContent(
    padding: PaddingValues,
    state: HomeUiState,
    onCreateMeeting: () -> Unit,
    onJoinClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ActionCard(
                icon = Icons.Default.Videocam,
                label = stringResource(R.string.home_create_meeting),
                backgroundColor = Color(0xFFD6E4FF),
                iconTint = Color(0xFF3366FF),
                isLoading = state.isCreating,
                onClick = onCreateMeeting,
                modifier = Modifier.weight(1f),
            )
            ActionCard(
                icon = Icons.Default.AddBox,
                label = stringResource(R.string.home_join_meeting),
                backgroundColor = Color(0xFFD6E4FF),
                iconTint = Color(0xFF3366FF),
                isLoading = false,
                onClick = onJoinClick,
                modifier = Modifier.weight(1f),
            )
        }

        state.errorMessage?.let { msg ->
            Spacer(Modifier.height(16.dp))
            Text(msg, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    iconTint: Color,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(enabled = !isLoading, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = iconTint,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun JoinRoomDialog(
    roomInput: String,
    isJoining: Boolean,
    errorMessage: String?,
    onRoomInputChange: (String) -> Unit,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_join_meeting)) },
        text = {
            Column {
                OutlinedTextField(
                    value = roomInput,
                    onValueChange = onRoomInputChange,
                    label = { Text(stringResource(R.string.home_room_label)) },
                    placeholder = { Text(stringResource(R.string.home_room_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onJoin,
                enabled = roomInput.isNotBlank() && !isJoining,
            ) {
                if (isJoining) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.home_join))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
