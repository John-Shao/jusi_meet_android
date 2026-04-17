package com.jusi.meet

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.jusi.meet.ui.nav.AppNav
import com.jusi.meet.ui.theme.JusiMeetTheme

private const val TAG = "MainActivity"

/**
 * Compose-visible flag for "Activity is in Picture-in-Picture mode right now".
 * RoomScreen reads this to render [com.jusi.meet.ui.room.PipLayout] instead
 * of the full toolbar/gallery when we're in the PiP window.
 */
val LocalIsInPipMode = compositionLocalOf { false }

class MainActivity : ComponentActivity() {

    /**
     * `true` while the user is actually in a connected meeting. RoomScreen
     * flips this via a DisposableEffect. Used by [onUserLeaveHint] (pre-12
     * fallback) so we never PiP from Login / Home.
     */
    private var inMeeting: Boolean = false

    private val pipModeState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalIsInPipMode provides pipModeState.value) {
                JusiMeetTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNav()
                    }
                }
            }
        }
    }

    /**
     * Called by RoomScreen when the user enters / leaves a connected meeting.
     * On Android 12+ this also drives the system's auto-enter-PiP behaviour:
     * while in a meeting, a home-gesture auto-pips; outside, the Activity
     * backgrounds normally.
     */
    fun setMeetingInProgress(active: Boolean) {
        inMeeting = active
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { setPictureInPictureParams(buildPipParams(active)) }
                .onFailure { Log.w(TAG, "setPictureInPictureParams failed", it) }
        }
    }

    // Pre-12 fallback: onUserLeaveHint fires on Home press. Android 12+ with
    // setAutoEnterEnabled handles the gesture path itself.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!inMeeting) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        runCatching { enterPictureInPictureMode(buildPipParams(autoEnter = false)) }
            .onFailure { Log.w(TAG, "enterPictureInPictureMode failed", it) }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipModeState.value = isInPictureInPictureMode
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(3, 4))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
        }
        return builder.build()
    }
}
