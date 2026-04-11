package com.jusi.meet.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.data.api.dto.LiveKitDto
import com.jusi.meet.data.auth.TokenStore
import com.jusi.meet.data.repository.RoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreviewUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class RoomTarget(
    val roomId: String,
    val livekitUrl: String,
    val livekitToken: String,
    val displayName: String,
    val slug: String,
    val isAdmin: Boolean,
)

class PreviewViewModel(
    private val tokenStore: TokenStore,
    private val roomRepository: RoomRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PreviewUiState())
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    val defaultMeetingName: String
        get() {
            val phone = tokenStore.phone ?: "Android User"
            return "${phone}的会议"
        }

    fun createMeeting(meetingName: String, onSuccess: (RoomTarget) -> Unit) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }

        val username = tokenStore.phone ?: "Android User"
        viewModelScope.launch {
            roomRepository.createRoom(username, meetingName).fold(
                onSuccess = { room ->
                    val lk = room.livekit
                    if (lk == null) {
                        _state.update { it.copy(isLoading = false, errorMessage = "Room has no LiveKit info") }
                    } else {
                        _state.update { it.copy(isLoading = false) }
                        onSuccess(RoomTarget(
                            roomId = room.id,
                            livekitUrl = lk.url,
                            livekitToken = lk.token,
                            displayName = room.name ?: room.slug ?: room.id,
                            slug = room.slug ?: room.id,
                            isAdmin = room.is_administrable == true,
                        ))
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to create room")
                    }
                },
            )
        }
    }

    fun joinRoom(slug: String, onSuccess: (RoomTarget) -> Unit) {
        val trimmed = slug.trim()
        if (trimmed.isEmpty()) return
        if (_state.value.isLoading) return

        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            roomRepository.getRoom(trimmed).fold(
                onSuccess = { room ->
                    val lk = room.livekit
                    if (lk == null) {
                        _state.update { it.copy(isLoading = false, errorMessage = "Room has no LiveKit info") }
                    } else {
                        _state.update { it.copy(isLoading = false) }
                        onSuccess(RoomTarget(
                            roomId = room.id,
                            livekitUrl = lk.url,
                            livekitToken = lk.token,
                            displayName = room.name ?: room.slug ?: room.id,
                            slug = room.slug ?: room.id,
                            isAdmin = room.is_administrable == true,
                        ))
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to load room")
                    }
                },
            )
        }
    }

    class Factory(private val app: JusiMeetApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PreviewViewModel(
                tokenStore = app.tokenStore,
                roomRepository = app.roomRepository,
            ) as T
    }
}
