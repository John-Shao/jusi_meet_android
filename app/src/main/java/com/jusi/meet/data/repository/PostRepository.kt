package com.jusi.meet.data.repository

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import com.jusi.meet.data.api.PostApi
import com.jusi.meet.data.api.UserApi
import com.jusi.meet.data.api.dto.CreatePostRequest
import com.jusi.meet.data.api.dto.FavoriteToggleResponse
import com.jusi.meet.data.api.dto.FollowToggleResponse
import com.jusi.meet.data.api.dto.PaginatedDto
import com.jusi.meet.data.api.dto.PostDetailDto
import com.jusi.meet.data.api.dto.PostImageInputDto
import com.jusi.meet.data.api.dto.PostListItemDto
import com.jusi.meet.data.api.dto.PostVisibility
import com.jusi.meet.data.api.dto.PublicUserDto
import com.jusi.meet.data.api.dto.UpdatePostRequest
import com.jusi.meet.data.api.dto.UploadUrlRequest
import com.jusi.meet.data.auth.AuthInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Discover-feed network operations: feed listing, post CRUD, favourite,
 * follow, and the multi-image post-creation flow.
 *
 * Image upload mirrors [ProfileRepository] except every image walks
 * presigned PUT independently, then all `object_key`s are submitted in a
 * single `POST /api/v1.0/posts/`. Per-image client-side validation is
 * stricter (5 MiB cap matches MAX_POST_IMAGE_SIZE on the backend).
 */
class PostRepository(
    private val postApi: PostApi,
    private val userApi: UserApi,
    private val okHttpClient: OkHttpClient,
    private val contentResolver: ContentResolver,
) {

    sealed class UploadError(message: String) : Exception(message) {
        object UnsupportedMime : UploadError("Unsupported MIME type")
        object TooLarge : UploadError("Image exceeds 5 MiB limit")
        object Empty : UploadError("Image is empty")
        object TooManyImages : UploadError("At most 9 images per post")
        object NoImages : UploadError("At least 1 image is required")
    }

    // --- Feed / detail / mutate ---------------------------------------------

    suspend fun feed(
        ordering: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): Result<PaginatedDto<PostListItemDto>> = runCatching {
        withContext(Dispatchers.IO) {
            postApi.listPosts(ordering = ordering, page = page, pageSize = pageSize)
        }
    }

    suspend fun userPosts(
        userId: String,
        ordering: String? = null,
        page: Int? = null,
    ): Result<PaginatedDto<PostListItemDto>> = runCatching {
        withContext(Dispatchers.IO) {
            postApi.listUserPosts(userId, ordering = ordering, page = page)
        }
    }

    suspend fun myPosts(
        ordering: String? = null,
        page: Int? = null,
    ): Result<PaginatedDto<PostListItemDto>> = runCatching {
        withContext(Dispatchers.IO) {
            postApi.listMyPosts(ordering = ordering, page = page)
        }
    }

    suspend fun myFavorites(page: Int? = null): Result<PaginatedDto<PostListItemDto>> =
        runCatching {
            withContext(Dispatchers.IO) { postApi.listMyFavorites(page = page) }
        }

    suspend fun detail(id: String): Result<PostDetailDto> = runCatching {
        withContext(Dispatchers.IO) { postApi.getPost(id) }
    }

    suspend fun update(
        id: String,
        title: String?,
        description: String?,
        tags: List<String>?,
        visibility: String? = null,
    ): Result<PostDetailDto> = runCatching {
        withContext(Dispatchers.IO) {
            postApi.updatePost(id, UpdatePostRequest(title, description, tags, visibility))
        }
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { postApi.deletePost(id) }
    }

    // --- Favorite / follow toggles ------------------------------------------

    /**
     * Idempotent favourite toggle. ``currentlyFavorited`` decides which
     * verb to send. Returns the server's view of the new state.
     */
    suspend fun toggleFavorite(
        postId: String,
        currentlyFavorited: Boolean,
    ): Result<FavoriteToggleResponse> = runCatching {
        withContext(Dispatchers.IO) {
            if (currentlyFavorited) postApi.unfavorite(postId) else postApi.favorite(postId)
        }
    }

    /**
     * Idempotent follow toggle. Unlike favourite, the unfollow path returns
     * 204 with no body — we synthesize a [FollowToggleResponse] for callers.
     */
    suspend fun toggleFollow(
        userId: String,
        currentlyFollowing: Boolean,
    ): Result<FollowToggleResponse> = runCatching {
        withContext(Dispatchers.IO) {
            if (currentlyFollowing) {
                postApi.unfollow(userId)
                FollowToggleResponse(is_following = false)
            } else {
                postApi.follow(userId)
            }
        }
    }

    suspend fun publicUser(userId: String): Result<PublicUserDto> = runCatching {
        withContext(Dispatchers.IO) { postApi.getPublicUser(userId) }
    }

    // --- Multi-image post creation ------------------------------------------

    /**
     * Upload N images (1..9) to TOS via presigned PUT, then commit a Post
     * referencing them in a single backend call. Throws [UploadError] for
     * client-side validation issues.
     */
    suspend fun createPostWithImages(
        title: String,
        description: String,
        tags: List<String>,
        uris: List<Uri>,
        visibility: String = PostVisibility.PUBLIC,
    ): Result<PostDetailDto> = runCatching {
        if (uris.isEmpty()) throw UploadError.NoImages
        if (uris.size > 9) throw UploadError.TooManyImages

        withContext(Dispatchers.IO) {
            val inputs = uris.mapIndexed { index, uri ->
                uploadOne(uri, order = index)
            }
            postApi.createPost(
                CreatePostRequest(
                    title = title,
                    description = description,
                    tags = tags,
                    visibility = visibility,
                    images = inputs,
                )
            )
        }
    }

    private suspend fun uploadOne(uri: Uri, order: Int): PostImageInputDto {
        val mime = resolveMime(uri)
        if (mime !in ALLOWED_MIME) throw UploadError.UnsupportedMime
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw UploadError.Empty
        if (bytes.isEmpty()) throw UploadError.Empty
        if (bytes.size > MAX_POST_IMAGE_BYTES) throw UploadError.TooLarge

        // Decode bounds (no full bitmap allocation) to learn intrinsic size.
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val width = opts.outWidth.takeIf { it > 0 } ?: 1
        val height = opts.outHeight.takeIf { it > 0 } ?: 1

        val presigned = userApi.requestProfileUploadUrl(
            UploadUrlRequest(
                kind = "post",
                content_type = mime,
                size = bytes.size.toLong(),
            )
        )

        val putRequest = Request.Builder()
            .url(presigned.upload_url)
            .put(bytes.toRequestBody(mime.toMediaTypeOrNull()))
            .header(AuthInterceptor.NO_AUTH, "1")
            .apply { presigned.headers.forEach { (k, v) -> header(k, v) } }
            .build()

        okHttpClient.newCall(putRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Storage PUT failed: HTTP ${response.code}")
            }
        }

        return PostImageInputDto(
            object_key = presigned.object_key,
            width = width,
            height = height,
            size_bytes = bytes.size.toLong(),
            order = order,
        )
    }

    private fun resolveMime(uri: Uri): String {
        contentResolver.getType(uri)?.let { return it }
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: ""
    }

    private companion object {
        const val MAX_POST_IMAGE_BYTES = 5L * 1024L * 1024L
        val ALLOWED_MIME = setOf("image/jpeg", "image/png", "image/webp")
    }
}
