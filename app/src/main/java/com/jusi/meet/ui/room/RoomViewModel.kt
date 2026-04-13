package com.jusi.meet.ui.room

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.data.repository.RoomRepository
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
    val isSpeaking: Boolean = false,
)

/** UI-facing snapshot of the room. */
data class RoomUiState(
    val phase: Phase = Phase.Connecting,
    val participants: List<ParticipantUi> = emptyList(),
    val micEnabled: Boolean = true,
    val cameraEnabled: Boolean = true,
    val errorMessage: String? = null,
    /** Identity of the participant pinned to the focus (big) tile; null = Gallery mode. */
    val focusIdentity: String? = null,
) {
    enum class Phase { Connecting, Connected, Error, Disconnected }
}

class RoomViewModel(
    application: Application,
    private val roomId: String,
    private val livekitUrl: String,
    private val livekitToken: String,
    private val roomRepository: RoomRepository,
    private val initialMicEnabled: Boolean = true,
    private val initialCameraEnabled: Boolean = true,
) : AndroidViewModel(application) {

    private val controller = LiveKitController(application)

    val room: Room get() = controller.room

    private val _state = MutableStateFlow(RoomUiState())
    val state: StateFlow<RoomUiState> = _state.asStateFlow()

    /**
     * Identities of participants currently detected as active speakers by
     * LiveKit. Kept separately so refreshParticipants() can merge it into
     * ParticipantUi without depending on timing of the event.
     */
    private var activeSpeakerIds: Set<String> = emptySet()

    /**
     * Stable ordering of identities. Once a participant enters the list we
     * keep their position; newcomers are appended. This avoids the whole
     * gallery reshuffling when tracks re-publish.
     */
    private val orderedIdentities = LinkedHashSet<String>()

    init {
        observeEvents()
        connect()
    }

    private fun connect() {
        viewModelScope.launch {
            runCatching {
                controller.connect(livekitUrl, livekitToken)
                controller.setMicrophoneEnabled(initialMicEnabled)
                controller.setCameraEnabled(initialCameraEnabled)
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
                    is RoomEvent.TrackUnmuted -> refreshParticipants()

                    is RoomEvent.ActiveSpeakersChanged -> {
                        activeSpeakerIds = event.speakers
                            .mapNotNull { it.identity?.value }
                            .toSet()
                        refreshParticipants()
                    }

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

        // Build identity → UI map from current room state.
        val byIdentity = linkedMapOf<String, ParticipantUi>()
        local.toUi(isLocal = true).let { byIdentity[it.identity] = it }
        remotes.forEach { p ->
            val ui = p.toUi(isLocal = false)
            byIdentity[ui.identity] = ui
        }

        // Maintain stable ordering: keep previously-seen identities first
        // in their original order, then append newcomers.
        orderedIdentities.retainAll(byIdentity.keys)
        byIdentity.keys.forEach { orderedIdentities.add(it) }

        val ordered = orderedIdentities.mapNotNull { byIdentity[it] }

        // Clear focus if the pinned participant has left.
        val focus = _state.value.focusIdentity
        val nextFocus = focus?.takeIf { id -> byIdentity.containsKey(id) }

        _state.update {
            it.copy(
                participants = ordered,
                micEnabled = local.isMicrophoneEnabled,
                cameraEnabled = local.isCameraEnabled,
                focusIdentity = nextFocus,
            )
        }
    }

    private fun Participant.toUi(isLocal: Boolean): ParticipantUi {
        val cameraPub = getTrackPublication(Track.Source.CAMERA)
        val videoTrack = cameraPub?.track as? VideoTrack
        val id = identity?.value ?: sid.value
        return ParticipantUi(
            identity = id,
            name = name?.takeIf { it.isNotBlank() } ?: identity?.value ?: "—",
            isLocal = isLocal,
            isMicEnabled = isMicrophoneEnabled,
            videoTrack = videoTrack,
            isSpeaking = id in activeSpeakerIds,
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

    /** True once we've observed an ON_STOP — used to decide if we need to
     *  restart the local camera on the next ON_START (e.g. after screen lock). */
    private var pausedInBackground = false

    fun onLifecycleStop() {
        pausedInBackground = true
    }

    /**
     * Called when the host Activity comes back to the foreground.
     * Android pauses the camera capturer when the screen locks / the app goes
     * to background; LiveKit does not auto-restart it. We force a
     * disable→enable cycle so the local preview comes back.
     */
    fun onLifecycleStart() {
        if (!pausedInBackground) return
        pausedInBackground = false

        if (_state.value.phase != RoomUiState.Phase.Connected) return
        if (!_state.value.cameraEnabled) return

        viewModelScope.launch {
            runCatching {
                controller.setCameraEnabled(false)
                controller.setCameraEnabled(true)
            }
            refreshParticipants()
        }
    }

    /** Enter Focus mode pinning the given participant. */
    fun pinParticipant(identity: String) {
        _state.update { it.copy(focusIdentity = identity) }
    }

    /** Exit Focus mode. */
    fun unpinParticipant() {
        _state.update { it.copy(focusIdentity = null) }
    }

    fun leave() {
        controller.disconnect()
    }

    /** End the room via backend API, then disconnect. Only owner should call this. */
    fun endMeeting(onDone: () -> Unit) {
        viewModelScope.launch {
            roomRepository.endRoom(roomId)
            controller.disconnect()
            onDone()
        }
    }

    override fun onCleared() {
        super.onCleared()
        controller.disconnect()
        controller.release()
    }

    class Factory(
        private val application: Application,
        private val roomId: String,
        private val livekitUrl: String,
        private val livekitToken: String,
        private val initialMicEnabled: Boolean = true,
        private val initialCameraEnabled: Boolean = true,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = application as JusiMeetApp
            return RoomViewModel(
                application, roomId, livekitUrl, livekitToken,
                app.roomRepository, initialMicEnabled, initialCameraEnabled,
            ) as T
        }
    }
}
