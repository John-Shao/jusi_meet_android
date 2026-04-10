package com.jusi.meet.ui.room

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jusi.meet.livekit.LiveKitController
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI-facing snapshot of one participant in the room. */
data class ParticipantUi(
    val identity: String,
    val name: String,
    val isLocal: Boolean,
    val isMicEnabled: Boolean,
    val videoTrack: VideoTrack?,
)

/** UI-facing snapshot of the room. */
data class RoomUiState(
    val phase: Phase = Phase.Connecting,
    val participants: List<ParticipantUi> = emptyList(),
    val micEnabled: Boolean = true,
    val cameraEnabled: Boolean = true,
    val errorMessage: String? = null,
) {
    enum class Phase { Connecting, Connected, Error, Disconnected }
}

class RoomViewModel(
    application: Application,
    private val livekitUrl: String,
    private val livekitToken: String,
) : AndroidViewModel(application) {

    private val controller = LiveKitController(application)

    /** Exposed for the UI so it can render video tracks against the right Room. */
    val room: Room get() = controller.room

    private val _state = MutableStateFlow(RoomUiState())
    val state: StateFlow<RoomUiState> = _state.asStateFlow()

    init {
        observeEvents()
        connect()
    }

    private fun connect() {
        viewModelScope.launch {
            runCatching {
                controller.connect(livekitUrl, livekitToken)
                controller.setMicrophoneEnabled(true)
                controller.setCameraEnabled(true)
            }.onSuccess {
                _state.update { it.copy(phase = RoomUiState.Phase.Connected) }
                refreshParticipants()
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        phase = RoomUiState.Phase.Error,
                        errorMessage = e.localizedMessage ?: "Failed to connect",
                    )
                }
            }
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            controller.events.collect { event ->
                when (event) {
                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.ParticipantDisconnected,
                    is RoomEvent.TrackSubscribed,
                    is RoomEvent.TrackUnsubscribed,
                    is RoomEvent.TrackPublished,
                    is RoomEvent.TrackUnpublished,
                    is RoomEvent.TrackMuted,
                    is RoomEvent.TrackUnmuted,
                    is RoomEvent.ActiveSpeakersChanged -> refreshParticipants()

                    is RoomEvent.Disconnected -> _state.update {
                        it.copy(phase = RoomUiState.Phase.Disconnected)
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun refreshParticipants() {
        val local = controller.room.localParticipant
        val remotes = controller.room.remoteParticipants.values

        val ui = buildList {
            add(local.toUi(isLocal = true))
            remotes.forEach { add(it.toUi(isLocal = false)) }
        }

        _state.update {
            it.copy(
                participants = ui,
                micEnabled = local.isMicrophoneEnabled,
                cameraEnabled = local.isCameraEnabled,
            )
        }
    }

    private fun Participant.toUi(isLocal: Boolean): ParticipantUi {
        val cameraPub = getTrackPublication(Track.Source.CAMERA)
        val videoTrack = cameraPub?.track as? VideoTrack
        return ParticipantUi(
            identity = identity?.value ?: sid.value,
            name = name?.takeIf { it.isNotBlank() } ?: identity?.value ?: "—",
            isLocal = isLocal,
            isMicEnabled = isMicrophoneEnabled,
            videoTrack = videoTrack,
        )
    }

    fun toggleMic() {
        viewModelScope.launch {
            val next = !_state.value.micEnabled
            controller.setMicrophoneEnabled(next)
            refreshParticipants()
        }
    }

    fun toggleCamera() {
        viewModelScope.launch {
            val next = !_state.value.cameraEnabled
            controller.setCameraEnabled(next)
            refreshParticipants()
        }
    }

    fun switchCamera() {
        controller.switchCamera()
    }

    fun leave() {
        controller.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        controller.disconnect()
        controller.release()
    }

    class Factory(
        private val application: Application,
        private val livekitUrl: String,
        private val livekitToken: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoomViewModel(application, livekitUrl, livekitToken) as T
    }
}
