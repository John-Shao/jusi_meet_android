package com.jusi.meet.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.R
import com.jusi.meet.ui.discover.components.PostCard

@Composable
fun DiscoverFeedScreen(
    onPostClick: (String) -> Unit,
    onCreatorClick: (String) -> Unit,
    onPublishClick: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as JusiMeetApp
    val viewModel: DiscoverFeedViewModel = viewModel(factory = DiscoverFeedViewModel.factory(app))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top sort selector — small, takes 2 of 4 segments to look balanced.
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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

            val gridState = rememberLazyStaggeredGridState()

            // Trigger loadMore when the user scrolls within 6 items of the
            // bottom. derivedStateOf ensures we only re-evaluate when scroll
            // position changes, not on every recomposition.
            val shouldLoadMore by remember {
                derivedStateOf {
                    val lastVisible =
                        gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val total = gridState.layoutInfo.totalItemsCount
                    total > 0 && lastVisible >= total - 6
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) viewModel.loadMore()
            }

            if (state.posts.isEmpty() && !state.loading && state.error == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.discover_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (state.posts.isEmpty() && state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.posts.isEmpty() && state.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.discover_load_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.posts, key = { it.id }) { post ->
                        PostCard(post = post, onClick = { onPostClick(post.id) })
                    }
                    if (state.loading) {
                        item {
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
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onPublishClick,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.discover_publish)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        )
    }
}
