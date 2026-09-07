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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 收藏编辑弹层（ModalBottomSheet）：公开/私密二选段 + 标签 chip 多选（含新建标签）+ 保存按钮。
 * 纯展示组件（状态全参数、动作全回调），由插画详情 / 小说详情 / 查看器接线 [com.pixiv.reader.core.network.favorite.BookmarkEditor] 状态。
 * 「visible=false 时不组合」由本组件内部处理，调用方直接常驻调用即可。
 *
 * @param visible 弹层开关（false 不渲染）
 * @param restrict 当前选择的可见性：`PixivConstants.RESTRICT_PUBLIC` / `RESTRICT_PRIVATE`
 * @param savedTags 已选标签名列表（chip 勾选态）
 * @param allTags 标签目录（名称 + 使用次数；新建标签即时追加）
 * @param tagsLoading 标签目录拉取中（目录区转圈占位）
 * @param saving 保存中（确认按钮禁用 + 转圈）
 * @param onDismiss 关闭弹层（点外部 / 返回 / 手动关闭）
 * @param onRestrictChange 可见性切换回调（传目标 restrict，联动重拉目录）
 * @param onToggleTag 标签勾选切换回调（传标签名）
 * @param onCreateTag 新建标签回调（传输入框内容；成功后由调用方清空输入由本组件内部处理）
 * @param onConfirm 保存回调
 * @return 无返回值
 */
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
    // 新建标签输入草稿（组件内自管；确认添加后清空）
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
            // 可见性二选段：公开收藏 / 私密收藏（私密仅自己可见）
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
                SegmentedButton(
                    selected = restrict == PixivConstants.RESTRICT_PUBLIC,
                    onClick = { onRestrictChange(PixivConstants.RESTRICT_PUBLIC) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.bookmark_restrict_public)) },
                )
                SegmentedButton(
                    selected = restrict == PixivConstants.RESTRICT_PRIVATE,
                    onClick = { onRestrictChange(PixivConstants.RESTRICT_PRIVATE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.bookmark_restrict_private)) },
                )
            }
            // 标签目录：FlowRow 多选 chip（目录拉取中转圈占位）
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
            // 新建标签：输入 + 添加（空白禁用；添加后清空输入并选中）
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
            // 保存：全宽实心按钮（保存中转圈禁用）
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
