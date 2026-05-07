package com.jusi.meet.ui.discover

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MyWorksScreen(
    onBack: () -> Unit,
    onPostClick: (String) -> Unit,
    onPublishClick: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as JusiMeetApp
    val viewModel: MyWorksViewModel = viewModel(
        factory = MyWorksViewModel.factory(app, MyWorksViewModel.Source.MY_POSTS)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_my_works)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPublishClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.discover_publish)) },
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

        when {
            state.posts.isEmpty() && state.loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.posts.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.discover_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    items(state.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onClick = { onPostClick(post.id) },
                            modifier = Modifier.combinedClickable(
                                onClick = { onPostClick(post.id) },
                                onLongClick = { pendingDeleteId = post.id },
                            ),
                        )
                    }
                    if (state.loading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(modifier = Modifier.height(20.dp)) }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.post_delete)) },
            text = { Text(stringResource(R.string.post_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePost(id)
                    pendingDeleteId = null
                }) {
                    Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
