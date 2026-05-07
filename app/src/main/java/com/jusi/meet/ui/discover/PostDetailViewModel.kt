package com.jusi.meet.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.data.api.dto.PostDetailDto
import com.jusi.meet.data.auth.TokenStore
import com.jusi.meet.data.repository.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PostDetailViewModel(
    private val postRepository: PostRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val post: PostDetailDto? = null,
        val error: String? = null,
        val toggling: Boolean = false,
        val deleted: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(postId: String) {
        _state.value = UiState(loading = true)
        viewModelScope.launch {
            postRepository.detail(postId)
                .onSuccess { _state.value = UiState(loading = false, post = it) }
                .onFailure { e ->
                    _state.value = UiState(loading = false, error = e.message ?: "load failed")
                }
        }
    }

    fun isAuthorMe(): Boolean {
        val authorId = _state.value.post?.author?.id ?: return false
        return authorId == tokenStore.userId
    }

    fun toggleFavorite() {
        val post = _state.value.post ?: return
        if (_state.value.toggling) return
        _state.value = _state.value.copy(toggling = true)
        viewModelScope.launch {
            postRepository.toggleFavorite(post.id, post.is_favorited)
                .onSuccess { resp ->
                    _state.value = _state.value.copy(
                        toggling = false,
                        post = post.copy(
                            is_favorited = resp.is_favorited,
                            favorite_count = resp.favorite_count,
                        )
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(toggling = false)
                }
        }
    }

    fun delete() {
        val post = _state.value.post ?: return
        viewModelScope.launch {
            postRepository.delete(post.id)
                .onSuccess { _state.value = _state.value.copy(deleted = true) }
        }
    }

    companion object {
        fun factory(app: JusiMeetApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PostDetailViewModel(app.postRepository, app.tokenStore) as T
        }
    }
}
