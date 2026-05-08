package com.jusi.meet.ui.discover

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.R
import com.jusi.meet.data.api.dto.PostVisibility
import com.jusi.meet.ui.discover.components.TagChipsEditable
import com.jusi.meet.ui.discover.components.TagPickerSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEditorScreen(
    postId: String?,
    onBack: () -> Unit,
    onPublished: (String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as JusiMeetApp
    val viewModel: PostEditorViewModel = viewModel(factory = PostEditorViewModel.factory(app))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(postId) { if (postId != null) viewModel.loadForEdit(postId) }

    LaunchedEffect(state.publishedPostId) {
        state.publishedPostId?.let { onPublished(it) }
    }
    LaunchedEffect(state.updatedPostId) {
        state.updatedPostId?.let { onPublished(it) }
    }

    val isEdit = state.mode == PostEditorViewModel.Mode.EDIT

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9),
    ) { uris -> if (uris.isNotEmpty()) viewModel.setPickedImageUris(uris) }

    // PickVisualMedia.SingleMimeType("video/*") routes to the system Photo
    // Picker on Android 13+ and falls back to ACTION_GET_CONTENT below.
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.setPickedVideoUri(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (isEdit) R.string.post_edit else R.string.discover_publish))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            keyboard?.hide()
                            viewModel.publish()
                        },
                        enabled = !state.publishing && !state.loading,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        if (state.publishing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            stringResource(
                                if (state.publishing) R.string.post_editor_publish_busy
                                else if (isEdit) R.string.post_editor_save
                                else R.string.discover_publish
                            )
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // ----- Edit-mode preview (read-only) ---------------------------
            if (isEdit) {
                if (state.existingMediaUrls.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.existingMediaUrls) { url ->
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (state.existingMediaIsVideo) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(32.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ----- Create-mode media picker --------------------------
                // Mode toggle (image / video). Not shown in EDIT mode.
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = listOf(
                        PostEditorViewModel.MediaMode.IMAGE to R.string.post_editor_mode_image,
                        PostEditorViewModel.MediaMode.VIDEO to R.string.post_editor_mode_video,
                    )
                    modes.forEachIndexed { i, (m, label) ->
                        SegmentedButton(
                            selected = state.mediaMode == m,
                            onClick = { viewModel.setMediaMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(stringResource(label)) }
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (state.mediaMode == PostEditorViewModel.MediaMode.VIDEO) {
                    // Single-video picker — tap the tile to launch system
                    // PickVisualMedia in video-only mode.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.VideoOnly
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.pickedVideoUri != null) {
                            // Show a static thumbnail placeholder; we don't
                            // play in the editor (avoid spinning ExoPlayer
                            // while the user is still typing). Tap re-picks.
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp),
                            )
                            Text(
                                text = stringResource(R.string.post_editor_video_picked),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                            )
                        } else {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.post_editor_pick_video),
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.post_video_pick_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.pickedImageUris) { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        imagePickerLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.discover_publish))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.post_image_pick_max),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                placeholder = { Text(stringResource(R.string.post_editor_title_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                placeholder = { Text(stringResource(R.string.post_editor_description_hint)) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Tag picker — admin maintains the list, user only chooses.
            TagChipsEditable(
                tags = state.tags,
                onRemove = viewModel::removeTag,
            )
            Spacer(Modifier.height(if (state.tags.isEmpty()) 0.dp else 8.dp))
            OutlinedButton(
                onClick = { viewModel.openTagPicker() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        R.string.post_editor_select_tags,
                        state.tags.size, 5,
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            // Visibility toggle (public / private). Affects who can see the
            // post in feeds, on profile pages, and via direct URL.
            Text(
                text = stringResource(R.string.post_visibility),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    PostVisibility.PUBLIC to R.string.post_visibility_public,
                    PostVisibility.PRIVATE to R.string.post_visibility_private,
                ).forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = state.visibility == value,
                        onClick = { viewModel.setVisibility(value) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) {
                        Text(stringResource(label))
                    }
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                val errMsg = when (state.error) {
                    "UnsupportedMime" -> stringResource(R.string.post_image_unsupported)
                    "TooLarge" -> stringResource(R.string.post_image_too_large)
                    "VideoTooLarge" -> stringResource(R.string.post_video_too_large)
                    "VideoTooLong" -> stringResource(R.string.post_video_too_long)
                    "ThumbnailFailed" -> stringResource(R.string.post_video_thumbnail_failed)
                    "TooManyImages" -> stringResource(R.string.post_image_pick_max)
                    "no_media", "no_images" -> stringResource(R.string.post_image_pick_max)
                    else -> stringResource(
                        if (isEdit) R.string.post_save_failed else R.string.post_publish_failed
                    )
                }
                Text(text = errMsg, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (state.tagPickerOpen) {
        TagPickerSheet(
            availableTags = state.availableTags,
            selectedLabels = state.tags,
            onToggle = viewModel::toggleTag,
            onDismiss = viewModel::closeTagPicker,
        )
    }
}
