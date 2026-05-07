package com.jusi.meet.ui.discover.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Read-only display of tags as suggestion chips. Used on the post detail
 * page and in feed cards (when we want to show tags).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChipsReadOnly(
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            SuggestionChip(
                onClick = {},
                label = { Text("#$tag") },
            )
        }
    }
}

/**
 * Editable tag chips for the post editor. Each chip has a close button to
 * remove it; the caller renders an input field separately.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChipsEditable(
    tags: List<String>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEachIndexed { index, tag ->
            AssistChip(
                onClick = { onRemove(index) },
                label = { Text("#$tag") },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}
