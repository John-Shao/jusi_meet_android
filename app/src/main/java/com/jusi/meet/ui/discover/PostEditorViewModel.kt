package com.jusi.meet.ui.discover

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.data.api.dto.PostDetailDto
import com.jusi.meet.data.api.dto.PostVisibility
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

    data class UiState(
        val mode: Mode = Mode.CREATE,
        val title: String = "",
        val description: String = "",
        val tags: List<String> = emptyList(),
        val tagDraft: String = "",
        val pickedUris: List<Uri> = emptyList(),
        val visibility: String = PostVisibility.PUBLIC,
        val loading: Boolean = false,
        val publishing: Boolean = false,
        val error: String? = null,
        val publishedPostId: String? = null,
        val updatedPostId: String? = null,
        // Read-only mirror of the existing images when editing.
        val existingImageUrls: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var editingPostId: String? = null

    fun loadForEdit(postId: String) {
        editingPostId = postId
        _state.value = UiState(mode = Mode.EDIT, loading = true)
        viewModelScope.launch {
            postRepository.detail(postId)
                .onSuccess { post ->
                    _state.value = _state.value.copy(
                        loading = false,
                        title = post.title,
                        description = post.description,
                        tags = post.tags,
                        visibility = post.visibility,
                        existingImageUrls = post.images.map { it.url },
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

    fun setTagDraft(v: String) {
        if (v.length <= 20) _state.value = _state.value.copy(tagDraft = v)
    }

    fun commitTagDraft() {
        val cur = _state.value
        val tag = cur.tagDraft.trim()
        if (tag.isBlank()) return
        if (cur.tags.size >= 5) {
            _state.value = cur.copy(tagDraft = "")
            return
        }
        if (cur.tags.contains(tag)) {
            _state.value = cur.copy(tagDraft = "")
            return
        }
        _state.value = cur.copy(tags = cur.tags + tag, tagDraft = "")
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

    fun setPickedUris(uris: List<Uri>) {
        _state.value = _state.value.copy(pickedUris = uris.take(9))
    }

    fun publish() {
        val cur = _state.value
        if (cur.publishing) return
        if (cur.mode == Mode.CREATE && cur.pickedUris.isEmpty()) {
            _state.value = cur.copy(error = "no_images")
            return
        }

        _state.value = cur.copy(publishing = true, error = null)
        viewModelScope.launch {
            if (cur.mode == Mode.CREATE) {
                postRepository.createPostWithImages(
                    title = cur.title,
                    description = cur.description,
                    tags = cur.tags,
                    uris = cur.pickedUris,
                    visibility = cur.visibility,
                )
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
