package com.jusi.meet.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jusi.meet.R
import io.livekit.android.compose.ui.VideoRenderer
import io.livekit.android.room.Room

/**
 * Renders one participant's video tile.  When the participant has no
 * camera track (camera off, joining, etc.) it shows a "no video" placeholder
 * with their name.
 *
 * The mic-off badge is overlaid on the bottom-left so the local user has
 * confirmation that their own mute toggled.
 */
@Composable
fun ParticipantTile(
    room: Room,
    participant: ParticipantUi,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val track = participant.videoTrack
        if (track != null) {
            VideoRenderer(
                room = room,
                videoTrack = track,
                modifier = Modifier.fillMaxSize(),
                mirror = participant.isLocal,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.room_no_video),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Bottom overlay: name + mic state.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!participant.isMicEnabled) {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            Text(
                text = participant.name,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
