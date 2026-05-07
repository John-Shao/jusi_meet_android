package com.jusi.meet.data.api.dto

import com.squareup.moshi.JsonClass

/** Compact author block embedded in feed cards (no counts). */
@JsonClass(generateAdapter = true)
data class AuthorEmbedDto(
    val id: String,
    val full_name: String?,
    val short_name: String?,
    val avatar_url: String,
)

/** Public-facing user profile (also used in post detail / creator page). */
@JsonClass(generateAdapter = true)
data class PublicUserDto(
    val id: String,
    val full_name: String?,
    val short_name: String?,
    val intro: String,
    val avatar_url: String,
    val cover_url: String,
    val post_count: Int,
    val follower_count: Int,
    val following_count: Int,
    val is_following: Boolean,
)

/** One image attached to a post (server-returned). */
@JsonClass(generateAdapter = true)
data class PostImageDto(
    val id: String,
    val url: String,
    val width: Int,
    val height: Int,
    val size_bytes: Long,
    val order: Int,
)

/** One image as supplied at post-creation time (client-provided). */
@JsonClass(generateAdapter = true)
data class PostImageInputDto(
    val object_key: String,
    val width: Int,
    val height: Int,
    val size_bytes: Long,
    val order: Int,
)

/** Post visibility tier returned / accepted by the backend. */
object PostVisibility {
    const val PUBLIC = "public"
    const val PRIVATE = "private"
}

/** Feed-card payload. */
@JsonClass(generateAdapter = true)
data class PostListItemDto(
    val id: String,
    val author: AuthorEmbedDto,
    val title: String,
    val tags: List<String>,
    val visibility: String,
    val favorite_count: Int,
    val first_image: PostImageDto?,
    val is_favorited: Boolean,
    val created_at: String,
)

/** Detail payload — full author block + all images + description. */
@JsonClass(generateAdapter = true)
data class PostDetailDto(
    val id: String,
    val author: PublicUserDto,
    val title: String,
    val description: String,
    val tags: List<String>,
    val visibility: String,
    val favorite_count: Int,
    val first_image: PostImageDto?,
    val images: List<PostImageDto>,
    val is_favorited: Boolean,
    val created_at: String,
    val updated_at: String,
)

/** Body for `POST /posts/`. */
@JsonClass(generateAdapter = true)
data class CreatePostRequest(
    val title: String,
    val description: String,
    val tags: List<String>,
    val visibility: String,
    val images: List<PostImageInputDto>,
)

/** Body for `PATCH /posts/{id}/`. */
@JsonClass(generateAdapter = true)
data class UpdatePostRequest(
    val title: String?,
    val description: String?,
    val tags: List<String>?,
    val visibility: String?,
)

/** Server response from `POST /posts/{id}/favorite/`. */
@JsonClass(generateAdapter = true)
data class FavoriteToggleResponse(
    val is_favorited: Boolean,
    val favorite_count: Int,
)

/** Server response from `POST /users/{id}/follow/`. */
@JsonClass(generateAdapter = true)
data class FollowToggleResponse(
    val is_following: Boolean,
)

/** Standard DRF page envelope. */
@JsonClass(generateAdapter = true)
data class PaginatedDto<T>(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<T>,
)
