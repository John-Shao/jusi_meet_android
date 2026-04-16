package com.jusi.meet.ui.room

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.data.repository.RoomRepository
import com.jusi.meet.livekit.LiveKitController
import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "RoomViewModel"

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
    /**
     * True when we were disconnected because the host ended the meeting
     * (server pushed [DisconnectReason.ROOM_DELETED] / [DisconnectReason.ROOM_CLOSED])
     * rather than because the local user chose to leave. Drives the
     * auto-leave + "host ended" sheet on Home.
     */
    val hostEnded: Boolean = false,
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

    /** Exposed so UI can pin the live WebRTC AudioTrack to an output device. */
    val callAudioDeviceModule get() = controller.callAudioDeviceModule

    // Seed mic/camera with the user's Preview-time intent. Otherwise the
    // default (true) races with connect(): refreshParticipants() runs the
    // instant we go Connected, but `local.isCameraEnabled` stays false until
    // the TrackPublished event lands — so the toolbar button briefly shows
    // OFF even though the camera is coming up.
    private val _state = MutableStateFlow(
        RoomUiState(
            micEnabled = initialMicEnabled,
            cameraEnabled = initialCameraEnabled,
        ),
    )
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

    /**
     * Set to true when the local user initiates leaving (Leave button or
     * host-only End meeting). Used to distinguish "host of this meeting
     * ended it" from "a *different* host ended the meeting out from under
     * me" — the server pushes ROOM_DELETED to everyone including the
     * acting host, and we don't want the acting host to see the
     * "host ended" sheet on themselves.
     */
    private var userInitiatedLeave = false

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

                    is RoomEvent.Disconnected -> {
                        // How the backend ends a meeting: the `/end/` endpoint
                        // calls LiveKit's `remove_participant` for every
                        // participant (see backend
                        // core/services/participants_management.py#remove_all).
                        // That surfaces on clients as PARTICIPANT_REMOVED, not
                        // ROOM_DELETED. We also keep ROOM_DELETED / ROOM_CLOSED
                        // as belt-and-suspenders for edge cases (LiveKit
                        // garbage-collecting empty rooms, direct DeleteRoom
                        // calls, etc.).
                        val hostEnded = !userInitiatedLeave && (
                            event.reason == DisconnectReason.PARTICIPANT_REMOVED ||
                                event.reason == DisconnectReason.ROOM_DELETED ||
                                event.reason == DisconnectReason.ROOM_CLOSED
                            )
                        _state.update {
                            it.copy(
                                phase = RoomUiState.Phase.Disconnected,
                                hostEnded = hostEnded,
                            )
                        }
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

        // Note: do NOT overwrite mic/cameraEnabled from `local.isXxxEnabled`
        // here. Track publication is async, so right after connect or a
        // toggle the LiveKit-observed state lags the user's intent and
        // would flicker the toolbar button. The toggle functions own these
        // fields; this method only refreshes participant/focus data.
        _state.update {
            it.copy(
                participants = ordered,
                focusIdentity = nextFocus,
            )
        }
    }

    private fun Participant.toUi(isLocal: Boolean): ParticipantUi {
        val cameraPub = getTrackPublication(Track.Source.CAMERA)
        // When a participant turns off their camera, LiveKit typically mutes
        // the publication instead of unpublishing — `cameraPub.track` stays
        // non-null, and handing a muted track to VideoTrackView would keep
        // painting the last received frame (frozen image). Treat muted as
        // "no video" so the avatar placeholder kicks in.
        val videoTrack = if (cameraPub?.muted == false) cameraPub.track as? VideoTrack else null
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
        val next = !_state.value.micEnabled
        // Optimistic UI — flip the toolbar immediately, then ask LiveKit.
        _state.update { it.copy(micEnabled = next) }
        viewModelScope.launch {
            runCatching { controller.setMicrophoneEnabled(next) }
                .onFailure { _state.update { it.copy(micEnabled = !next) } }
            refreshParticipants()
        }
    }

    fun toggleCamera() {
        val next = !_state.value.cameraEnabled
        _state.update { it.copy(cameraEnabled = next) }
        viewModelScope.launch {
            runCatching { controller.setCameraEnabled(next) }
                .onFailure { _state.update { it.copy(cameraEnabled = !next) } }
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
     *
     * Two scenarios to handle:
     *
     * 1. Still connected to LiveKit (short background). Just force a
     *    camera re-publish — Android pauses the camera capturer on screen
     *    lock and LiveKit won't restart it on its own.
     *
     * 2. LiveKit connection dropped while backgrounded (long background,
     *    Doze, network flap). We would otherwise be stuck on a dead
     *    RoomContent forever. Attempt a fresh `connect()`:
     *    - On success: business as usual.
     *    - On failure: treat it as "room is gone" and fall through to
     *      the host-ended flow (auto-leave + sheet on Home).
     */
    fun onLifecycleStart() {
        if (!pausedInBackground) return
        pausedInBackground = false

        // Already flagged host-ended (server kicked us while backgrounded,
        // or a previous reconnect gave up). RoomScreen's
        // LaunchedEffect(state.hostEnded) auto-leaves on resume — don't
        // fight it.
        if (_state.value.hostEnded) return

        val roomState = controller.room.state
        Log.i(TAG, "onLifecycleStart: room.state=$roomState phase=${_state.value.phase}")

        // Anything other than CONNECTED needs intervention. LiveKit's own
        // reconnect can leave the room parked in RECONNECTING indefinitely
        // when the OS froze the socket during Doze, so we force a clean
        // re-join rather than waiting for LiveKit to give up.
        if (roomState != Room.State.CONNECTED) {
            reconnectOrGiveUp()
            return
        }

        // Still connected — just repair the camera capturer (Android frees
        // it on screen lock and LiveKit doesn't restart it).
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

    private fun reconnectOrGiveUp() {
        viewModelScope.launch {
            _state.update { it.copy(phase = RoomUiState.Phase.Connecting) }
            val wantMic = _state.value.micEnabled
            val wantCamera = _state.value.cameraEnabled

            val result = runCatching {
                // `room.connect()` throws IllegalStateException unless state
                // is DISCONNECTED. If LiveKit is still mid-RECONNECTING
                // (Android just unfroze us), force-tear-down first and
                // wait for the state transition to settle.
                if (controller.room.state != Room.State.DISCONNECTED) {
                    Log.i(TAG, "reconnect: forcing disconnect from state=${controller.room.state}")
                    controller.disconnect()
                    withTimeoutOrNull(3_000) {
                        while (controller.room.state != Room.State.DISCONNECTED) {
                            delay(50)
                        }
                    }
                }

                withTimeout(15_000) {
                    controller.connect(livekitUrl, livekitToken)
                }
                check(controller.room.state == Room.State.CONNECTED) {
                    "room state is ${controller.room.state} after connect()"
                }
                controller.setMicrophoneEnabled(wantMic)
                controller.setCameraEnabled(wantCamera)
            }

            if (result.isSuccess) {
                Log.i(TAG, "reconnect: success")
                _state.update { it.copy(phase = RoomUiState.Phase.Connected) }
                refreshParticipants()
            } else {
                // Couldn't re-join — assume the room is gone (host ended
                // it, LiveKit GC'd an empty room, token expired). Flip to
                // the host-ended path so the user lands on Home with the
                // "主持人已结束会议" sheet, instead of being stuck on a
                // frozen dead room.
                Log.w(TAG, "reconnect: giving up", result.exceptionOrNull())
                _state.update {
                    it.copy(
                        phase = RoomUiState.Phase.Disconnected,
                        hostEnded = true,
                    )
                }
            }
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
        userInitiatedLeave = true
        controller.disconnect()
    }

    /** End the room via backend API, then disconnect. Only owner should call this. */
    fun endMeeting(onDone: () -> Unit) {
        userInitiatedLeave = true
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
