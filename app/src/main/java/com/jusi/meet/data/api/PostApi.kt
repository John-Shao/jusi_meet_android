package com.jusi.meet.data.api

import com.jusi.meet.data.api.dto.CreatePostRequest
import com.jusi.meet.data.api.dto.FavoriteToggleResponse
import com.jusi.meet.data.api.dto.FollowToggleResponse
import com.jusi.meet.data.api.dto.PaginatedDto
import com.jusi.meet.data.api.dto.PostDetailDto
import com.jusi.meet.data.api.dto.PostListItemDto
import com.jusi.meet.data.api.dto.PublicUserDto
import com.jusi.meet.data.api.dto.TagDto
import com.jusi.meet.data.api.dto.UpdatePostRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Discover-feed endpoints. Documented in
 * ../jusi_meet_suite1.9/docs/mobile-integration-discover.md.
 *
 * Image upload itself reuses the profile upload flow (kind="post"); see
 * UserApi.requestProfileUploadUrl. After PUT-ing bytes to TOS, the client
 * commits via `POST /api/v1.0/posts/` carrying the resulting object_keys.
 */
interface PostApi {

    // --- feed / detail / mutate -----------------------------------------

    @GET("api/v1.0/posts/")
    suspend fun listPosts(
        @Query("ordering") ordering: String? = null,
        @Query("author") author: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
    ): PaginatedDto<PostListItemDto>

    @GET("api/v1.0/posts/{id}/")
    suspend fun getPost(@Path("id") id: String): PostDetailDto

    @POST("api/v1.0/posts/")
    suspend fun createPost(@Body body: CreatePostRequest): PostDetailDto

    @PATCH("api/v1.0/posts/{id}/")
    suspend fun updatePost(
        @Path("id") id: String,
        @Body body: UpdatePostRequest,
    ): PostDetailDto

    @DELETE("api/v1.0/posts/{id}/")
    suspend fun deletePost(@Path("id") id: String)

    // --- favorite -------------------------------------------------------

    @POST("api/v1.0/posts/{id}/favorite/")
    suspend fun favorite(@Path("id") id: String): FavoriteToggleResponse

    @DELETE("api/v1.0/posts/{id}/favorite/")
    suspend fun unfavorite(@Path("id") id: String): FavoriteToggleResponse

    // --- public users / their posts -------------------------------------

    @GET("api/v1.0/users/{id}/")
    suspend fun getPublicUser(@Path("id") id: String): PublicUserDto

    @GET("api/v1.0/users/{id}/posts/")
    suspend fun listUserPosts(
        @Path("id") userId: String,
        @Query("ordering") ordering: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
    ): PaginatedDto<PostListItemDto>

    @GET("api/v1.0/users/me/posts/")
    suspend fun listMyPosts(
        @Query("ordering") ordering: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
    ): PaginatedDto<PostListItemDto>

    @GET("api/v1.0/users/me/favorites/")
    suspend fun listMyFavorites(
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
    ): PaginatedDto<PostListItemDto>

    // --- follow ---------------------------------------------------------

    @POST("api/v1.0/users/{id}/follow/")
    suspend fun follow(@Path("id") id: String): FollowToggleResponse

    @DELETE("api/v1.0/users/{id}/follow/")
    suspend fun unfollow(@Path("id") id: String)

    // --- predefined tags (read-only) ------------------------------------

    /**
     * The endpoint disables DRF pagination, so the response body is a
     * raw JSON array of tags rather than a paginated envelope.
     */
    @GET("api/v1.0/tags/")
    suspend fun listTags(): List<TagDto>
}
