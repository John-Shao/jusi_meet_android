package com.jusi.meet.livekit

import android.content.Context
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.EventListenable
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track

/**
 * Thin imperative wrapper around the LiveKit Android SDK.
 *
 * Owns one [Room] instance and exposes the small surface the [com.jusi.meet.ui.room.RoomViewModel]
 * needs: connect, disconnect, mic toggle, camera toggle, switch camera, plus
 * the underlying event flow.
 *
 * Mirrors the web client's settings ([adaptiveStream], [dynacast]).
 *
 * Audio routing: the SDK creates and owns an [io.livekit.android.audio.AudioSwitchHandler]
 * accessible via [Room.getAudioSwitchHandler]. UI code drives routing through
 * that single handler — we do not create a second one.
 */
class LiveKitController(appContext: Context) {

    val room: Room = LiveKit.create(
        appContext = appContext.applicationContext,
        options = RoomOptions(
            adaptiveStream = true,
            dynacast = true,
        ),
    ).also { r ->
        // CRITICAL: set forceHandleAudioRouting BEFORE r.connect() is called.
        // AudioSwitchHandler reads this flag inside its start(), which the SDK
        // invokes during connect. If we wait until UI compose to set it, the
        // underlying AudioSwitch has already been initialized in
        // monitor-only mode, and selectDevice(Earpiece) becomes a no-op
        // (Speaker still works because system default is loudspeaker).
        r.audioSwitchHandler?.forceHandleAudioRouting = true
    }

    val events: EventListenable<RoomEvent> get() = room.events

    suspend fun connect(url: String, token: String) {
        room.connect(
            url = url,
            token = token,
            options = ConnectOptions(autoSubscribe = true),
        )
    }

    suspend fun setMicrophoneEnabled(enabled: Boolean) {
        room.localParticipant.setMicrophoneEnabled(enabled)
    }

    suspend fun setCameraEnabled(enabled: Boolean) {
        room.localParticipant.setCameraEnabled(enabled)
    }

    /**
     * Best-effort camera flip.  The LiveKit camera capturer exposes a switch
     * helper through the track's capturer; if the underlying capturer is not
     * a multi-camera capturer (e.g. emulator with a single virtual webcam),
     * the call is silently a no-op.
     */
    fun switchCamera() {
        val pub = room.localParticipant.getTrackPublication(Track.Source.CAMERA) ?: return
        val track = pub.track as? LocalVideoTrack ?: return
        runCatching { track.switchCamera() }
    }

    fun disconnect() {
        room.disconnect()
    }

    fun release() {
        runCatching { room.release() }
    }
}
