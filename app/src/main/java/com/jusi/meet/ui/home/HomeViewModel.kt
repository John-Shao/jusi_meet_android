package com.jusi.meet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.data.api.dto.LiveKitDto
import com.jusi.meet.data.auth.TokenStore
import com.jusi.meet.data.repository.AuthRepository
import com.jusi.meet.data.repository.RoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val phone: String? = null,
    val roomInput: String = "",
    val isJoining: Boolean = false,
    val errorMessage: String? = null,
)

/** Result of a successful room lookup, ready to be passed to RoomScreen. */
data class JoinTarget(
    val livekit: LiveKitDto,
    val displayName: String,
)

class HomeViewModel(
    private val tokenStore: TokenStore,
    private val authRepository: AuthRepository,
    private val roomRepository: RoomRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(phone = tokenStore.phone))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun onRoomInputChange(value: String) {
        _state.update { it.copy(roomInput = value, errorMessage = null) }
    }

    fun joinRoom(onResolved: (JoinTarget) -> Unit) {
        val raw = _state.value.roomInput.trim()
        if (raw.isEmpty()) return
        if (_state.value.isJoining) return

        _state.update { it.copy(isJoining = true, errorMessage = null) }
        viewModelScope.launch {
            roomRepository.getRoom(raw).fold(
                onSuccess = { room ->
                    val lk = room.livekit
                    if (lk == null) {
                        _state.update { it.copy(isJoining = false, errorMessage = "Room has no LiveKit info") }
                    } else {
                        _state.update { it.copy(isJoining = false) }
                        onResolved(JoinTarget(livekit = lk, displayName = room.name ?: room.slug ?: room.id))
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isJoining = false, errorMessage = e.localizedMessage ?: "Failed to load room")
                    }
                },
            )
        }
    }

    fun signOut() {
        authRepository.signOut()
    }

    class Factory(private val app: JusiMeetApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(
                tokenStore = app.tokenStore,
                authRepository = app.authRepository,
                roomRepository = app.roomRepository,
            ) as T
    }
}
