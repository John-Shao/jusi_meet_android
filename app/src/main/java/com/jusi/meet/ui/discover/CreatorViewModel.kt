package com.jusi.meet.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.data.api.dto.PostListItemDto
import com.jusi.meet.data.api.dto.PublicUserDto
import com.jusi.meet.data.repository.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CreatorViewModel(
    private val postRepository: PostRepository,
) : ViewModel() {

    data class UiState(
        val user: PublicUserDto? = null,
        val sort: DiscoverFeedViewModel.Sort = DiscoverFeedViewModel.Sort.LATEST,
        val posts: List<PostListItemDto> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val hasMore: Boolean = true,
        val nextPage: Int = 1,
        val toggling: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var userId: String = ""

    fun load(userId: String) {
        this.userId = userId
        _state.value = UiState(loading = true)
        viewModelScope.launch {
            postRepository.publicUser(userId)
                .onSuccess { _state.value = _state.value.copy(user = it) }
            // Load first page of posts in parallel-ish (after user info).
            loadPosts(reset = true)
        }
    }

    fun setSort(sort: DiscoverFeedViewModel.Sort) {
        if (_state.value.sort == sort) return
        _state.value = _state.value.copy(sort = sort)
        loadPosts(reset = true)
    }

    fun loadMore() {
        loadPosts(reset = false)
    }

    private fun loadPosts(reset: Boolean) {
        val cur = _state.value
        if (cur.loading && !reset) return
        if (!cur.hasMore && !reset) return
        viewModelScope.launch {
            val page = if (reset) 1 else cur.nextPage
            _state.value = cur.copy(
                loading = true,
                error = null,
                posts = if (reset) emptyList() else cur.posts,
                hasMore = if (reset) true else cur.hasMore,
            )
            postRepository.userPosts(userId, ordering = _state.value.sort.ordering, page = page)
                .onSuccess { resp ->
                    val merged = (if (reset) emptyList() else _state.value.posts) + resp.results
                    _state.value = _state.value.copy(
                        posts = merged,
                        loading = false,
                        hasMore = resp.next != null,
                        nextPage = page + 1,
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

    fun toggleFollow() {
        val u = _state.value.user ?: return
        if (_state.value.toggling) return
        _state.value = _state.value.copy(toggling = true)
        viewModelScope.launch {
            postRepository.toggleFollow(u.id, u.is_following)
                .onSuccess { resp ->
                    _state.value = _state.value.copy(
                        toggling = false,
                        user = u.copy(
                            is_following = resp.is_following,
                            follower_count = u.follower_count + (if (resp.is_following) 1 else -1).coerceAtLeast(-u.follower_count),
                        )
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(toggling = false)
                }
        }
    }

    companion object {
        fun factory(app: JusiMeetApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CreatorViewModel(app.postRepository) as T
        }
    }
}
