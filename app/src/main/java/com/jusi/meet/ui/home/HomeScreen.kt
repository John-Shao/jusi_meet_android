package com.jusi.meet.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onJoinRoom: (livekitUrl: String, livekitToken: String, name: String) -> Unit,
    onSignedOut: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as JusiMeetApp
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(app))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    TextButton(onClick = {
                        viewModel.signOut()
                        onSignedOut()
                    }) {
                        Text(stringResource(R.string.home_logout))
                    }
                },
            )
        },
    ) { padding ->
        HomeContent(
            padding = padding,
            state = state,
            onRoomInputChange = viewModel::onRoomInputChange,
            onJoin = {
                viewModel.joinRoom { target ->
                    onJoinRoom(target.livekit.url, target.livekit.token, target.displayName)
                }
            },
        )
    }
}

@Composable
private fun HomeContent(
    padding: PaddingValues,
    state: HomeUiState,
    onRoomInputChange: (String) -> Unit,
    onJoin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        state.phone?.let { phone ->
            Text(
                text = stringResource(R.string.home_signed_in_as, phone),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(24.dp))
        }

        OutlinedTextField(
            value = state.roomInput,
            onValueChange = onRoomInputChange,
            label = { Text(stringResource(R.string.home_room_label)) },
            placeholder = { Text(stringResource(R.string.home_room_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onJoin,
            enabled = state.roomInput.isNotBlank() && !state.isJoining,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isJoining) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
            } else {
                Text(stringResource(R.string.home_join))
            }
        }

        state.errorMessage?.let { msg ->
            Spacer(Modifier.height(16.dp))
            Text(msg, color = MaterialTheme.colorScheme.error)
        }
    }
}
