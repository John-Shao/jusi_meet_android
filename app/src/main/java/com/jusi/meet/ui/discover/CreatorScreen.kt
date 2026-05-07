package com.jusi.meet.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.R
import com.jusi.meet.ui.discover.components.PostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorScreen(
    userId: String,
    onBack: () -> Unit,
    onPostClick: (String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as JusiMeetApp
    val viewModel: CreatorViewModel = viewModel(factory = CreatorViewModel.factory(app))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(userId) { viewModel.load(userId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.user?.full_name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        val gridState = rememberLazyStaggeredGridState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val lastVisible =
                    gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = gridState.layoutInfo.totalItemsCount
                total > 0 && lastVisible >= total - 6
            }
        }
        LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Header — cover banner + avatar + counts + follow button.
            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                Header(state, onFollowClick = { viewModel.toggleFollow() })
            }
            // Sort selector — full-line item too.
            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    DiscoverFeedViewModel.Sort.values().forEachIndexed { i, sort ->
                        SegmentedButton(
                            selected = state.sort == sort,
                            onClick = { viewModel.setSort(sort) },
                            shape = SegmentedButtonDefaults.itemShape(
                                i, DiscoverFeedViewModel.Sort.values().size
                            ),
                        ) {
                            Text(
                                stringResource(
                                    if (sort == DiscoverFeedViewModel.Sort.LATEST)
                                        R.string.discover_sort_latest
                                    else R.string.discover_sort_hottest
                                )
                            )
                        }
                    }
                }
            }
            items(state.posts, key = { it.id }) { post ->
                PostCard(post = post, onClick = { onPostClick(post.id) })
            }
            if (state.loading && state.posts.isNotEmpty()) {
                item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    }
                }
            }
            if (state.posts.isEmpty() && state.loading) {
                item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: CreatorViewModel.UiState,
    onFollowClick: () -> Unit,
) {
    val user = state.user
    Column(modifier = Modifier.fillMaxWidth()) {
        // Cover banner (16:9-ish).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            if (user != null && user.cover_url.isNotBlank()) {
                AsyncImage(
                    model = user.cover_url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Avatar overlapping bottom edge.
        Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp)
                    .offset(y = (-30).dp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                if (user != null && user.avatar_url.isNotBlank()) {
                    AsyncImage(
                        model = user.avatar_url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }

        if (user == null) return@Column

        // Name + follow button row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.full_name ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (user.intro.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = user.intro,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (user.is_following) {
                OutlinedButton(onClick = onFollowClick, enabled = !state.toggling) {
                    Text(stringResource(R.string.creator_unfollow))
                }
            } else {
                Button(onClick = onFollowClick, enabled = !state.toggling) {
                    Text(stringResource(R.string.creator_follow))
                }
            }
        }

        // Counts row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            CountCol(stringResource(R.string.creator_post_count, user.post_count))
            Spacer(Modifier.width(20.dp))
            CountCol(stringResource(R.string.creator_follower_count, user.follower_count))
            Spacer(Modifier.width(20.dp))
            CountCol(stringResource(R.string.creator_following_count, user.following_count))
        }
    }
}

@Composable
private fun CountCol(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
