package com.jusi.meet.ui.discover

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.data.api.dto.PostDetailDto
import com.jusi.meet.data.api.dto.PostMediaType
import com.jusi.meet.data.api.dto.PostVisibility
import com.jusi.meet.data.api.dto.TagDto
import com.jusi.meet.data.repository.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [PostEditorScreen] in two modes:
 *   - **create** (postId == null): pick images → write title/description/tags → publish
 *   - **edit** (postId != null): preload existing title/description/tags, allow PATCH;
 *     image list is not editable in this mode.
 */
class PostEditorViewModel(
    private val postRepository: PostRepository,
) : ViewModel() {

    enum class Mode { CREATE, EDIT }

    /**
     * Discriminator for the editor's media mode in CREATE.
     *
     * When editing existing posts, the user can't change media — only
     * title / description / tags / visibility. So [MediaMode] is irrelevant
     * in EDIT mode and we just hide the media picker.
     */
    enum class MediaMode { IMAGE, VIDEO }

    data class UiState(
        val mode: Mode = Mode.CREATE,
        val mediaMode: MediaMode = MediaMode.IMAGE,
        val title: String = "",
        val description: String = "",
        // Selected tag labels that will be sent on publish.
        val tags: List<String> = emptyList(),
        // Active labels fetched from /api/v1.0/tags/ — drives the picker.
        val availableTags: List<TagDto> = emptyList(),
        // True while the bottom-sheet picker is open.
        val tagPickerOpen: Boolean = false,
        // Image mode: 1..9 image URIs.
        val pickedImageUris: List<Uri> = emptyList(),
        // Video mode: a single video URI (or null if none yet).
        val pickedVideoUri: Uri? = null,
        val visibility: String = PostVisibility.PUBLIC,
        val loading: Boolean = false,
        val publishing: Boolean = false,
        val error: String? = null,
        val publishedPostId: String? = null,
        val updatedPostId: String? = null,
        // Read-only mirror of the existing media when editing.
        val existingMediaUrls: List<String> = emptyList(),
        val existingMediaIsVideo: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var editingPostId: String? = null

    init {
        loadAvailableTags()
    }

    private fun loadAvailableTags() {
        viewModelScope.launch {
            postRepository.listTags()
                .onSuccess { tags ->
                    _state.value = _state.value.copy(availableTags = tags)
                }
            // On failure we leave availableTags empty; the picker will
            // render the "no tags available" empty-state.
        }
    }

    fun loadForEdit(postId: String) {
        editingPostId = postId
        // Preserve any tags already loaded by init so the picker doesn't
        // briefly show "no tags available" while detail is in flight.
        _state.value = _state.value.copy(
            mode = Mode.EDIT,
            loading = true,
        )
        viewModelScope.launch {
            postRepository.detail(postId)
                .onSuccess { post ->
                    val isVideo = post.media.firstOrNull()?.media_type == PostMediaType.VIDEO
                    _state.value = _state.value.copy(
                        loading = false,
                        mediaMode = if (isVideo) MediaMode.VIDEO else MediaMode.IMAGE,
                        title = post.title,
                        description = post.description,
                        tags = post.tags,
                        visibility = post.visibility,
                        existingMediaUrls = post.media.map {
                            if (it.media_type == PostMediaType.VIDEO) it.thumbnail_url else it.url
                        },
                        existingMediaIsVideo = isVideo,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "load failed"
                    )
                }
        }
    }

    fun setTitle(v: String) {
        if (v.length <= 100) _state.value = _state.value.copy(title = v)
    }

    fun setDescription(v: String) {
        if (v.length <= 2000) _state.value = _state.value.copy(description = v)
    }

    fun openTagPicker() {
        _state.value = _state.value.copy(tagPickerOpen = true)
    }

    fun closeTagPicker() {
        _state.value = _state.value.copy(tagPickerOpen = false)
    }

    /**
     * Multi-select toggle from the picker. If already selected → remove.
     * If not selected and current selection < 5 → add. The 5-cap is also
     * defended on the backend; doing it here keeps the user from staring
     * at a successful tap that doesn't visibly do anything.
     */
    fun toggleTag(label: String) {
        val cur = _state.value
        val newTags = if (label in cur.tags) {
            cur.tags - label
        } else {
            if (cur.tags.size >= 5) cur.tags else cur.tags + label
        }
        _state.value = cur.copy(tags = newTags)
    }

    fun removeTag(index: Int) {
        val cur = _state.value
        if (index !in cur.tags.indices) return
        _state.value = cur.copy(tags = cur.tags.filterIndexed { i, _ -> i != index })
    }

    fun setVisibility(value: String) {
        if (value != PostVisibility.PUBLIC && value != PostVisibility.PRIVATE) return
        _state.value = _state.value.copy(visibility = value)
    }

    fun setPickedImageUris(uris: List<Uri>) {
        // Switching to image mode also clears any previously-picked video.
        _state.value = _state.value.copy(
            mediaMode = MediaMode.IMAGE,
            pickedImageUris = uris.take(9),
            pickedVideoUri = null,
        )
    }

    fun setPickedVideoUri(uri: Uri?) {
        _state.value = _state.value.copy(
            mediaMode = MediaMode.VIDEO,
            pickedVideoUri = uri,
            pickedImageUris = emptyList(),
        )
    }

    fun setMediaMode(mode: MediaMode) {
        if (_state.value.mediaMode == mode) return
        _state.value = _state.value.copy(
            mediaMode = mode,
            // Clear the other side's picks so we never accidentally publish
            // a stale image batch in video mode (or vice versa).
            pickedImageUris = if (mode == MediaMode.IMAGE) _state.value.pickedImageUris else emptyList(),
            pickedVideoUri = if (mode == MediaMode.VIDEO) _state.value.pickedVideoUri else null,
        )
    }

    fun publish() {
        val cur = _state.value
        if (cur.publishing) return
        if (cur.mode == Mode.CREATE) {
            val nothingPicked = (cur.mediaMode == MediaMode.IMAGE && cur.pickedImageUris.isEmpty()) ||
                (cur.mediaMode == MediaMode.VIDEO && cur.pickedVideoUri == null)
            if (nothingPicked) {
                _state.value = cur.copy(error = "no_media")
                return
            }
        }

        _state.value = cur.copy(publishing = true, error = null)
        viewModelScope.launch {
            if (cur.mode == Mode.CREATE) {
                val result = if (cur.mediaMode == MediaMode.VIDEO) {
                    postRepository.createPostWithVideo(
                        title = cur.title,
                        description = cur.description,
                        tags = cur.tags,
                        videoUri = cur.pickedVideoUri!!,
                        visibility = cur.visibility,
                    )
                } else {
                    postRepository.createPostWithImages(
                        title = cur.title,
                        description = cur.description,
                        tags = cur.tags,
                        uris = cur.pickedImageUris,
                        visibility = cur.visibility,
                    )
                }
                result
                    .onSuccess { post ->
                        _state.value = _state.value.copy(
                            publishing = false,
                            publishedPostId = post.id,
                        )
                    }
                    .onFailure { e ->
                        _state.value = _state.value.copy(
                            publishing = false,
                            error = e::class.simpleName ?: "publish failed",
                        )
                    }
            } else {
                val id = editingPostId ?: return@launch
                postRepository.update(
                    id = id,
                    title = cur.title,
                    description = cur.description,
                    tags = cur.tags,
                    visibility = cur.visibility,
                )
                    .onSuccess {
                        _state.value = _state.value.copy(
                            publishing = false,
                            updatedPostId = id,
                        )
                    }
                    .onFailure { e ->
                        _state.value = _state.value.copy(
                            publishing = false,
                            error = e::class.simpleName ?: "save failed",
                        )
                    }
            }
        }
    }

    companion object {
        fun factory(app: JusiMeetApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PostEditorViewModel(app.postRepository) as T
        }
    }
}
