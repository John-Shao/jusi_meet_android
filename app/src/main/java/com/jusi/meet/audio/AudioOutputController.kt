package com.jusi.meet.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.audio.AudioSwitchHandler
import kotlin.reflect.KClass

enum class AudioOutput { Speaker, Earpiece, Mute }

private const val TAG = "AudioOutputCtrl"

/**
 * Best-effort controller for call audio routing inside the app.
 *
 * Implementation note: relying solely on LiveKit's AudioSwitchHandler /
 * Twilio AudioSwitch is unreliable across OEMs — even with
 * `forceHandleAudioRouting = true` set before the SDK starts the handler,
 * `selectDevice(Earpiece)` can silently no-op on some devices. We instead
 * drive the audio route directly via [AudioManager] (the same path most
 * conferencing apps end up taking), and call `selectDevice` in addition so
 * AudioSwitch's observers stay in sync.
 */
class AudioOutputController(
    context: Context,
    private val audioSwitchHandler: AudioSwitchHandler = AudioSwitchHandler(context.applicationContext),
    private val manageHandlerLifecycle: Boolean = true,
    private val muteOutput: ((Boolean) -> Unit)? = null,
) {

    private val audioManager = checkNotNull(
        context.applicationContext.getSystemService(AudioManager::class.java)
    ) { "AudioManager unavailable" }

    private val originalVoiceCallMuted = audioManager.isStreamMute(AudioManager.STREAM_VOICE_CALL)
    private val originalAudioMode = audioManager.mode
    private val originalSpeakerphoneOn = getSpeakerphoneState()

    private var appliedOutput: AudioOutput? = null
    private var lastAudibleOutput = AudioOutput.Speaker
    private var started = false

    init {
        audioSwitchHandler.forceHandleAudioRouting = true
    }

    @Suppress("DEPRECATION")
    private fun getSpeakerphoneState(): Boolean = audioManager.isSpeakerphoneOn

    @Suppress("DEPRECATION")
    private fun setSpeakerphoneState(enabled: Boolean) {
        audioManager.isSpeakerphoneOn = enabled
    }

    fun start() {
        if (!manageHandlerLifecycle || started) return
        audioSwitchHandler.forceHandleAudioRouting = true
        started = runCatching { audioSwitchHandler.start() }.isSuccess
    }

    fun apply(output: AudioOutput) {
        start()
        if (appliedOutput == output) return

        // Ensure we're in voice-call mode so Earpiece is even an option.
        runCatching { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }

        val ok = runCatching {
            when (output) {
                AudioOutput.Speaker -> {
                    routeToSpeaker()
                    applyMutedState(false)
                    lastAudibleOutput = AudioOutput.Speaker
                }
                AudioOutput.Earpiece -> {
                    routeToEarpiece()
                    applyMutedState(false)
                    lastAudibleOutput = AudioOutput.Earpiece
                }
                AudioOutput.Mute -> {
                    routeToLastAudibleOutput()
                    applyMutedState(true)
                }
            }
        }.isSuccess

        if (ok) appliedOutput = output
    }

    fun stop() {
        runCatching {
            if (muteOutput == null) {
                setSystemMute(originalVoiceCallMuted)
            } else {
                applyMutedState(false)
            }
        }
        // Restore the audio routing the system had before we touched it so
        // we don't leave other apps stuck in MODE_IN_COMMUNICATION + speaker.
        runCatching {
            clearCommunicationDevice()
            setSpeakerphoneState(originalSpeakerphoneOn)
            audioManager.mode = originalAudioMode
        }

        if (manageHandlerLifecycle && started) {
            runCatching { audioSwitchHandler.stop() }
            started = false
        }

        appliedOutput = null
        lastAudibleOutput = AudioOutput.Speaker
    }

    // ── Routing ───────────────────────────────────────────────────────────

    private fun routeToSpeaker() {
        // Align AudioSwitch's preferred device list with our intent FIRST.
        // Otherwise the handler's observer re-applies its old preference and
        // snaps the route back as soon as we change it.
        runCatching {
            audioSwitchHandler.preferredDeviceList =
                listOf(AudioDevice.Speakerphone::class.java)
        }
        runCatching { selectFirstAvailable(listOf(AudioDevice.Speakerphone::class)) }

        val handled = setCommunicationDevice(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        if (!handled) setSpeakerphoneState(true)

        Log.d(TAG, "routeToSpeaker: ${currentRouteSummary()} avail=${availableSummary()}")
    }

    /**
     * "Earpiece" semantically means "private listening". Prefer Bluetooth or
     * wired headset when present, fall back to the built-in earpiece.
     */
    private fun routeToEarpiece() {
        runCatching {
            audioSwitchHandler.preferredDeviceList = listOf(
                AudioDevice.BluetoothHeadset::class.java,
                AudioDevice.WiredHeadset::class.java,
                AudioDevice.Earpiece::class.java,
            )
        }
        runCatching {
            selectFirstAvailable(
                listOf(
                    AudioDevice.BluetoothHeadset::class,
                    AudioDevice.WiredHeadset::class,
                    AudioDevice.Earpiece::class,
                )
            )
        }

        val handled = setCommunicationDevice(
            listOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            )
        )
        if (!handled) setSpeakerphoneState(false)

        Log.d(TAG, "routeToEarpiece: ${currentRouteSummary()} avail=${availableSummary()}")
    }

    private fun routeToLastAudibleOutput() {
        when (lastAudibleOutput) {
            AudioOutput.Speaker -> routeToSpeaker()
            AudioOutput.Earpiece -> routeToEarpiece()
            AudioOutput.Mute -> routeToSpeaker()
        }
    }

    /**
     * On API 31+, AudioManager.setCommunicationDevice is the canonical way
     * to pin a call audio route. Returns true if a device matched the
     * preference and was successfully selected.
     */
    private fun setCommunicationDevice(preferredTypesInOrder: List<Int>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val devices = audioManager.availableCommunicationDevices
        for (type in preferredTypesInOrder) {
            val device = devices.firstOrNull { it.type == type } ?: continue
            val ok = runCatching { audioManager.setCommunicationDevice(device) }
                .getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    private fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        }
    }

    private fun selectFirstAvailable(preferences: List<KClass<out AudioDevice>>) {
        val available = audioSwitchHandler.availableAudioDevices
        for (cls in preferences) {
            val device = available.firstOrNull { cls.isInstance(it) } ?: continue
            audioSwitchHandler.selectDevice(device)
            return
        }
    }

    // ── Mute ──────────────────────────────────────────────────────────────

    private fun applyMutedState(muted: Boolean) {
        if (muteOutput != null) {
            muteOutput.invoke(muted)
        } else {
            setSystemMute(muted)
        }
    }

    private fun setSystemMute(muted: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_VOICE_CALL,
                if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0,
            )
        } else {
            @Suppress("DEPRECATION")
            audioManager.setStreamMute(AudioManager.STREAM_VOICE_CALL, muted)
        }
    }

    // ── Diagnostics ───────────────────────────────────────────────────────

    private fun currentRouteSummary(): String {
        val mode = audioManager.mode
        val spk = getSpeakerphoneState()
        val commDev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.let { "${it.productName}/${it.type}" } ?: "null"
        } else "n/a"
        return "mode=$mode spk=$spk commDev=$commDev"
    }

    private fun availableSummary(): String {
        val asw = runCatching {
            audioSwitchHandler.availableAudioDevices.joinToString(",") {
                it::class.simpleName ?: "?"
            }
        }.getOrDefault("?")
        val am = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.joinToString(",") { "${it.type}" }
        } else "n/a"
        return "switch=[$asw] am=[$am]"
    }
}
