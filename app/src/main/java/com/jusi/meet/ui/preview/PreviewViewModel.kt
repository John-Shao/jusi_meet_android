package com.jusi.meet.ui.preview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.R
import com.jusi.meet.data.auth.TokenStore
import com.jusi.meet.data.repository.RoomRepository
import com.jusi.meet.util.ErrorScope
import com.jusi.meet.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreviewUiState(
    val isLoading: Boolean = false,
    /**
     * Persistent error banner text. Null when no error is showing. The
     * banner stays visible until the user edits the meeting input or
     * explicitly dismisses it — transient UX like Toast/Snackbar doesn't
     * fit errors like "会议号无效" where the user needs time to read and
     * correct the input.
     */
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
    application: Application,
    private val tokenStore: TokenStore,
    private val roomRepository: RoomRepository,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PreviewUiState())
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    /** The display name used as participant identity in LiveKit: nickname first, then phone. */
    private val displayUsername: String
        get() = tokenStore.nickname?.takeIf { it.isNotBlank() } ?: tokenStore.phone ?: "Android User"

    val defaultMeetingName: String
        get() = "${displayUsername}的会议"

    fun createMeeting(meetingName: String, onSuccess: (RoomTarget) -> Unit) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }

        val username = displayUsername
        viewModelScope.launch {
            roomRepository.createRoom(username, meetingName).fold(
                onSuccess = { room ->
                    val lk = room.livekit
                    if (lk == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = getApplication<Application>().getString(R.string.error_unknown),
                            )
                        }
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
                        it.copy(
                            isLoading = false,
                            errorMessage = e.toUserMessage(getApplication(), ErrorScope.GENERIC),
                        )
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
            roomRepository.getRoom(trimmed, displayUsername).fold(
                onSuccess = { room ->
                    val lk = room.livekit
                    if (lk == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = getApplication<Application>().getString(R.string.error_unknown),
                            )
                        }
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
                        it.copy(
                            isLoading = false,
                            errorMessage = e.toUserMessage(getApplication(), ErrorScope.ROOM_FETCH),
                        )
                    }
                },
            )
        }
    }

    fun dismissError() {
        if (_state.value.errorMessage != null) {
            _state.update { it.copy(errorMessage = null) }
        }
    }

    class Factory(private val app: JusiMeetApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PreviewViewModel(
                application = app,
                tokenStore = app.tokenStore,
                roomRepository = app.roomRepository,
            ) as T
    }
}
