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
 * Backs [DiscoverFeedScreen]. Holds the paginated feed list and a sort
 * mode (latest / hottest); exposes ``loadMore`` for the grid to call when
 * the user scrolls near the bottom.
 *
 * Memory-only state: switching tabs does not persist scroll position. We
 * accept this MVP simplification.
 */
class DiscoverFeedViewModel(
    private val postRepository: PostRepository,
) : ViewModel() {

    enum class Sort(val ordering: String) {
        LATEST("-created_at"),
        HOTTEST("-favorite_count"),
    }

    data class UiState(
        val sort: Sort = Sort.LATEST,
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

    fun setSort(sort: Sort) {
        if (_state.value.sort == sort) return
        _state.value = UiState(sort = sort)
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(
            posts = emptyList(),
            loading = true,
            error = null,
            hasMore = true,
            nextPage = 1,
        )
        loadMore()
    }

    fun loadMore() {
        val cur = _state.value
        if (cur.loading && cur.posts.isNotEmpty()) return
        if (!cur.hasMore && cur.posts.isNotEmpty()) return
        viewModelScope.launch {
            _state.value = cur.copy(loading = true, error = null)
            postRepository.feed(ordering = cur.sort.ordering, page = cur.nextPage)
                .onSuccess { page ->
                    val merged = cur.posts + page.results
                    _state.value = _state.value.copy(
                        posts = merged,
                        loading = false,
                        hasMore = page.next != null,
                        nextPage = cur.nextPage + 1,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "load failed",
                    )
                }
        }
    }

    /**
     * Optimistically swap a post's favourite state in the in-memory list
     * (used when the user taps favourite from the feed without opening the
     * detail). The detail screen has its own ViewModel and updates
     * independently; the feed will pick up the canonical state on next
     * refresh.
     */
    fun applyFavoriteChange(postId: String, isFavorited: Boolean, favoriteCount: Int) {
        _state.value = _state.value.copy(
            posts = _state.value.posts.map {
                if (it.id == postId) it.copy(
                    is_favorited = isFavorited,
                    favorite_count = favoriteCount,
                ) else it
            }
        )
    }

    companion object {
        fun factory(app: JusiMeetApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DiscoverFeedViewModel(app.postRepository) as T
        }
    }
}
