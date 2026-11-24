package com.pixiv.reader.core.ui.component.bookmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixiv.api.PixivConstants
import com.pixiv.api.model.BookmarkTag
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.component.input.PillSegmentedRow
import com.pixiv.reader.core.ui.theme.Spacing

/** 收藏编辑弹层（[visible] 为 false 时内部跳过组合，调用方可常驻挂载）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun BookmarkEditSheet(
    visible: Boolean,
    restrict: String,
    savedTags: List<String>,
    allTags: List<BookmarkTag>,
    tagsLoading: Boolean,
    saving: Boolean,
    onDismiss: () -> Unit,
    onRestrictChange: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    var newTagDraft by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.bookmark_edit_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            PillSegmentedRow(
                options = listOf(
                    PixivConstants.RESTRICT_PUBLIC to R.string.bookmark_restrict_public,
                    PixivConstants.RESTRICT_PRIVATE to R.string.bookmark_restrict_private,
                ),
                selected = restrict,
                onSelect = onRestrictChange,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )
            if (tagsLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LoadingIndicator(modifier = Modifier.width(48.dp))
                }
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                ) {
                    allTags.forEach { tag ->
                        val name = tag.name.orEmpty()
                        if (name.isBlank()) return@forEach
                        FilterChip(
                            selected = name in savedTags,
                            onClick = { onToggleTag(name) },
                            label = { Text("#$name") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newTagDraft,
                    onValueChange = { newTagDraft = it },
                    placeholder = { Text(stringResource(R.string.bookmark_new_tag_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.sm))
                FilledTonalButton(
                    enabled = newTagDraft.isNotBlank(),
                    onClick = {
                        onCreateTag(newTagDraft)
                        newTagDraft = ""
                    },
                ) {
                    Text(stringResource(R.string.bookmark_tag_add))
                }
            }
            Button(
                onClick = onConfirm,
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
            ) {
                if (saving) {
                    LoadingIndicator(
                        modifier = Modifier.width(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.bookmark_save))
                }
            }
        }
    }
}
