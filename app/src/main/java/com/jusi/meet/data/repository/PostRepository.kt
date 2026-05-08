package com.jusi.meet.data.repository

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import com.jusi.meet.data.api.PostApi
import com.jusi.meet.data.api.UserApi
import com.jusi.meet.data.api.dto.CreatePostRequest
import com.jusi.meet.data.api.dto.FavoriteToggleResponse
import com.jusi.meet.data.api.dto.FollowToggleResponse
import com.jusi.meet.data.api.dto.PaginatedDto
import com.jusi.meet.data.api.dto.PostDetailDto
import com.jusi.meet.data.api.dto.PostListItemDto
import com.jusi.meet.data.api.dto.PostMediaInputDto
import com.jusi.meet.data.api.dto.PostMediaType
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
import java.io.ByteArrayOutputStream

/**
 * Discover-feed network operations: feed listing, post CRUD, favourite,
 * follow, and the multi-media post-creation flow (images + short video).
 *
 * Image-post: each image PUT through presigned URL (kind="post"), then
 * commit a Post referencing all object_keys.
 *
 * Video-post: PUT video bytes (kind="post_video"), extract a poster frame
 * client-side via [MediaMetadataRetriever] and PUT it as a JPEG image
 * (kind="post"), then commit a Post with a single video media entry that
 * carries both the video object_key and the thumbnail object_key.
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
        object VideoTooLarge : UploadError("Video exceeds 50 MiB limit")
        object VideoTooLong : UploadError("Video exceeds 60 seconds")
        object Empty : UploadError("File is empty")
        object TooManyImages : UploadError("At most 9 images per post")
        object NoMedia : UploadError("At least 1 image is required")
        object ThumbnailFailed : UploadError("Failed to extract video thumbnail")
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

    suspend fun toggleFavorite(
        postId: String,
        currentlyFavorited: Boolean,
    ): Result<FavoriteToggleResponse> = runCatching {
        withContext(Dispatchers.IO) {
            if (currentlyFavorited) postApi.unfavorite(postId) else postApi.favorite(postId)
        }
    }

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

    // --- Image post creation -----------------------------------------------

    suspend fun createPostWithImages(
        title: String,
        description: String,
        tags: List<String>,
        uris: List<Uri>,
        visibility: String = PostVisibility.PUBLIC,
    ): Result<PostDetailDto> = runCatching {
        if (uris.isEmpty()) throw UploadError.NoMedia
        if (uris.size > 9) throw UploadError.TooManyImages

        withContext(Dispatchers.IO) {
            val inputs = uris.mapIndexed { index, uri ->
                uploadImage(uri, order = index)
            }
            postApi.createPost(
                CreatePostRequest(
                    title = title,
                    description = description,
                    tags = tags,
                    visibility = visibility,
                    media = inputs,
                )
            )
        }
    }

    // --- Video post creation -----------------------------------------------

    /**
     * Upload one short video + auto-extracted thumbnail, then commit a Post.
     *
     * Steps (all sequential):
     *   1) Read video bytes; compute size, MIME, dimensions, duration via
     *      [MediaMetadataRetriever]
     *   2) PUT video to TOS using kind="post_video"
     *   3) Extract first frame as JPEG; PUT to TOS using kind="post"
     *   4) POST /posts/ with a single ``video`` media entry referencing both
     *      object_keys + the duration in seconds
     */
    suspend fun createPostWithVideo(
        title: String,
        description: String,
        tags: List<String>,
        videoUri: Uri,
        visibility: String = PostVisibility.PUBLIC,
    ): Result<PostDetailDto> = runCatching {
        withContext(Dispatchers.IO) {
            val mime = resolveMime(videoUri)
            if (mime !in ALLOWED_VIDEO_MIME) throw UploadError.UnsupportedMime

            val bytes = contentResolver.openInputStream(videoUri)?.use { it.readBytes() }
                ?: throw UploadError.Empty
            if (bytes.isEmpty()) throw UploadError.Empty
            if (bytes.size > MAX_POST_VIDEO_BYTES) throw UploadError.VideoTooLarge

            // Probe video metadata.
            val metadata = MediaMetadataRetriever()
            val durationMs: Long
            val width: Int
            val height: Int
            val firstFrame: Bitmap?
            try {
                metadata.setDataSource(contentResolver.openFileDescriptor(videoUri, "r")!!.fileDescriptor)
                durationMs = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                firstFrame = metadata.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                metadata.release()
            }

            val durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(1)
            if (durationSeconds > MAX_POST_VIDEO_DURATION_SECONDS) throw UploadError.VideoTooLong

            val thumbBitmap = firstFrame ?: throw UploadError.ThumbnailFailed
            val thumbBytes = ByteArrayOutputStream().use { out ->
                thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
            // Defensive: thumbnail must fit the image cap.
            if (thumbBytes.size > MAX_POST_IMAGE_BYTES) throw UploadError.TooLarge

            // Step 2: PUT video.
            val videoPresigned = userApi.requestProfileUploadUrl(
                UploadUrlRequest(
                    kind = "post_video",
                    content_type = mime,
                    size = bytes.size.toLong(),
                )
            )
            putToStorage(videoPresigned.upload_url, videoPresigned.headers, bytes, mime)

            // Step 3: PUT thumbnail.
            val thumbMime = "image/jpeg"
            val thumbPresigned = userApi.requestProfileUploadUrl(
                UploadUrlRequest(
                    kind = "post",
                    content_type = thumbMime,
                    size = thumbBytes.size.toLong(),
                )
            )
            putToStorage(thumbPresigned.upload_url, thumbPresigned.headers, thumbBytes, thumbMime)

            // Step 4: commit post.
            postApi.createPost(
                CreatePostRequest(
                    title = title,
                    description = description,
                    tags = tags,
                    visibility = visibility,
                    media = listOf(
                        PostMediaInputDto(
                            media_type = PostMediaType.VIDEO,
                            object_key = videoPresigned.object_key,
                            thumbnail_object_key = thumbPresigned.object_key,
                            width = width.coerceAtLeast(1),
                            height = height.coerceAtLeast(1),
                            size_bytes = bytes.size.toLong(),
                            order = 0,
                            duration_seconds = durationSeconds,
                        )
                    ),
                )
            )
        }
    }

    // --- Helpers -----------------------------------------------------------

    private suspend fun uploadImage(uri: Uri, order: Int): PostMediaInputDto {
        val mime = resolveMime(uri)
        if (mime !in ALLOWED_IMAGE_MIME) throw UploadError.UnsupportedMime
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

        putToStorage(presigned.upload_url, presigned.headers, bytes, mime)

        return PostMediaInputDto(
            media_type = PostMediaType.IMAGE,
            object_key = presigned.object_key,
            width = width,
            height = height,
            size_bytes = bytes.size.toLong(),
            order = order,
        )
    }

    private fun putToStorage(
        uploadUrl: String,
        signedHeaders: Map<String, String>,
        bytes: ByteArray,
        mime: String,
    ) {
        val putRequest = Request.Builder()
            .url(uploadUrl)
            .put(bytes.toRequestBody(mime.toMediaTypeOrNull()))
            .header(AuthInterceptor.NO_AUTH, "1")
            .apply { signedHeaders.forEach { (k, v) -> header(k, v) } }
            .build()

        okHttpClient.newCall(putRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Storage PUT failed: HTTP ${response.code}")
            }
        }
    }

    private fun resolveMime(uri: Uri): String {
        contentResolver.getType(uri)?.let { return it }
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: ""
    }

    private companion object {
        const val MAX_POST_IMAGE_BYTES = 5L * 1024L * 1024L
        const val MAX_POST_VIDEO_BYTES = 50L * 1024L * 1024L
        const val MAX_POST_VIDEO_DURATION_SECONDS = 60
        val ALLOWED_IMAGE_MIME = setOf("image/jpeg", "image/png", "image/webp")
        val ALLOWED_VIDEO_MIME = setOf("video/mp4")
    }
}
