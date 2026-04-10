package com.jusi.meet.data.repository

import com.jusi.meet.BuildConfig
import com.jusi.meet.data.api.RoomApi
import com.jusi.meet.data.api.dto.CreateRoomRequest
import com.jusi.meet.data.api.dto.RoomDto

/**
 * Fetches a room's connection info from the backend.  The interesting payload
 * is the nested `livekit: { url, room, token }` block which is what
 * [com.jusi.meet.livekit.LiveKitController] needs to actually connect.
 */
class RoomRepository(
    private val roomApi: RoomApi,
) {

    /** Create a new room and return its connection info. */
    suspend fun createRoom(username: String, roomName: String): Result<RoomDto> = runCatching {
        val room = roomApi.createRoom(username, CreateRoomRequest(name = roomName))
        applyLivekitOverride(room)
    }

    /** Resolve a room by id (UUID) or slug.  Returns Result.failure on error. */
    suspend fun getRoom(idOrSlug: String): Result<RoomDto> = runCatching {
        val room = roomApi.getRoom(idOrSlug.trim())
        applyLivekitOverride(room)
    }

    /** Apply optional LiveKit URL override (used for local-dev port forwarding). */
    private fun applyLivekitOverride(room: RoomDto): RoomDto {
        val override = BuildConfig.JUSI_MEET_LIVEKIT_URL_OVERRIDE
        return if (override.isNotBlank() && room.livekit != null) {
            room.copy(livekit = room.livekit.copy(url = override))
        } else {
            room
        }
    }
}
