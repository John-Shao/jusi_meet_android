package com.jusi.meet.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.data.api.dto.PostListItemDto
import com.jusi.meet.data.repository.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs both [MyWorksScreen] (source = my_posts) and [MyFavoritesScreen]
 * (source = my_favorites). Pagination + delete + unfavorite are handled
 * here so the screens stay thin.
 */
class MyWorksViewModel(
    private val postRepository: PostRepository,
    private val source: Source,
) : ViewModel() {

    enum class Source { MY_POSTS, MY_FAVORITES }

    data class UiState(
        val posts: List<PostListItemDto> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val hasMore: Boolean = true,
        val nextPage: Int = 1,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = UiState(loading = true)
        loadMore()
    }

    fun loadMore() {
        val cur = _state.value
        if (cur.loading && cur.posts.isNotEmpty()) return
        if (!cur.hasMore && cur.posts.isNotEmpty()) return
        viewModelScope.launch {
            _state.value = cur.copy(loading = true, error = null)
            val result = when (source) {
                Source.MY_POSTS -> postRepository.myPosts(page = cur.nextPage)
                Source.MY_FAVORITES -> postRepository.myFavorites(page = cur.nextPage)
            }
            result
                .onSuccess { resp ->
                    val merged = cur.posts + resp.results
                    _state.value = _state.value.copy(
                        posts = merged,
                        loading = false,
                        hasMore = resp.next != null,
                        nextPage = cur.nextPage + 1,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message,
                    )
                }
        }
    }

    /** Delete one of my posts; only valid on MY_POSTS source. */
    fun deletePost(postId: String) {
        if (source != Source.MY_POSTS) return
        viewModelScope.launch {
            postRepository.delete(postId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        posts = _state.value.posts.filterNot { it.id == postId }
                    )
                }
        }
    }

    /** Unfavorite a post; only valid on MY_FAVORITES source. */
    fun unfavorite(postId: String) {
        if (source != Source.MY_FAVORITES) return
        viewModelScope.launch {
            postRepository.toggleFavorite(postId, currentlyFavorited = true)
                .onSuccess {
                    _state.value = _state.value.copy(
                        posts = _state.value.posts.filterNot { it.id == postId }
                    )
                }
        }
    }

    companion object {
        fun factory(app: JusiMeetApp, source: Source) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MyWorksViewModel(app.postRepository, source) as T
        }
    }
}
