package com.jusi.meet.data.api

import com.jusi.meet.data.api.dto.CreateRoomRequest
import com.jusi.meet.data.api.dto.RoomDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Room control plane.  The backend's URL accepts either the UUID id or the
 * human-readable slug in the same path slot, so a single signature works.
 *
 * Auth: requires `Authorization: Bearer <access_token>` (added by
 * AuthInterceptor) for non-public rooms.
 */
interface RoomApi {

    @GET("api/v1.0/rooms/{idOrSlug}/")
    suspend fun getRoom(@Path("idOrSlug") idOrSlug: String): RoomDto

    @POST("api/v1.0/rooms/")
    suspend fun createRoom(
        @Query("username") username: String,
        @Body body: CreateRoomRequest,
    ): RoomDto
}
