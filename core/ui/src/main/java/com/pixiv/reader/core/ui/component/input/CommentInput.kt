package com.pixiv.reader.core.ui.component.input

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Stamp
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.component.emoji.PIXIV_EMOJI_IDS
import com.pixiv.reader.core.ui.component.emoji.PIXIV_EMOJI_TAGS
import com.pixiv.reader.core.ui.component.emoji.PixivEmojiTagChip
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * 评论输入框（插画/小说详情评论区共用）。
 *
 * ## UI 设计方式
 * 主行：左表情按钮（选文本表情插入草稿 / 选贴纸直接发送）+ `OutlinedTextField`（单行输入，
 * `weight(1f)` 占满）+ 发送 `IconButton`。点击表情按钮弹出**底部沉浸式表情栏**
 * （[ModalBottomSheet]：从屏幕底部滑出、覆盖评论列表，含标准滑入/滑出动画），栏内
 * `HorizontalPager` **左右滑动切换**「文本表情 / 贴纸」两个 Tab（配 [TabRow] 指示）。
 *
 * **文本表情以文本形式插入草稿**（`(xxx)`，发布/渲染时由评论侧 `CommentText` 转成行内小图）；
 * 贴纸选后直接发送（`stamp_id`）。本组件不做富文本图像内联编辑。
 *
 * @param draft 当前输入内容（由外部 ViewModel 持有状态）
 * @param onDraftChange 输入内容变化回调
 * @param onPost 发送评论回调（外部校验非空并调 API，成功后清空 draft）
 * @param modifier 外部传入的 Modifier
 * @param stamps pixiv 贴纸目录（外部经 `getStamps` 加载）；空则仅显示文本表情 Tab
 * @param onStampPick 选贴纸回调（参数为贴纸，外部发 `stamp_id`）；null 隐藏表情按钮
 * @param onEmojiPick 选文本表情回调（参数为 `(xxx)` 标签，插入草稿）；null 时仅贴纸
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentInput(
    draft: String,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
    modifier: Modifier = Modifier,
    stamps: List<Stamp> = emptyList(),
    onStampPick: ((Stamp) -> Unit)? = null,
    onEmojiPick: ((String) -> Unit)? = null,
) {
    var panelExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 表情按钮：仅当提供了表情选择回调时显示
        if (onStampPick != null || onEmojiPick != null) {
            IconButton(onClick = { panelExpanded = !panelExpanded }) {
                Icon(
                    Icons.Filled.Mood,
                    contentDescription = stringResource(R.string.comment_emoji_panel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.comment_placeholder)) },
            singleLine = true,
        )
        IconButton(onClick = onPost) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.comment_send), tint = MaterialTheme.colorScheme.primary)
        }
    }
    // 底部沉浸式表情栏：ModalBottomSheet 从屏幕底部滑出，覆盖评论列表（不占输入条布局高度）
    if (panelExpanded) {
        ModalBottomSheet(
            onDismissRequest = { panelExpanded = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            CommentEmojiPanel(
                stamps = stamps,
                onStampPick = { stamp ->
                    onStampPick?.invoke(stamp)
                    panelExpanded = false
                },
                onEmojiPick = { tag ->
                    onEmojiPick?.invoke(tag)
                },
            )
        }
    }
}

/**
 * 评论表情选择面板：`HorizontalPager` 左右滑动切换「文本表情 / 贴纸」两个 Tab（配 [TabRow]）。
 * 文本表情点击回调 [onEmojiPick]（插入草稿，渲染侧转图）；贴纸点击回调 [onStampPick]（直接发送）。
 * @OptIn 试验 API FlowRow。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentEmojiPanel(
    stamps: List<Stamp>,
    onStampPick: (Stamp) -> Unit,
    onEmojiPick: (String) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    // 背景由外层 ModalBottomSheet 的 containerColor 提供；内容加 navigationBarsPadding 避开系统导航栏，
    // 面板背景色铺满到底（状态色延伸到系统区域，无突兀白条/padding）
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.comment_emoji_title)) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.comment_stamp_title)) },
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.heightIn(max = 280.dp),
            ) { page ->
                when (page) {
                    // Tab 0：文本表情（FlowRow 图像 chip）
                    0 -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PIXIV_EMOJI_TAGS.forEach { tag ->
                                PixivEmojiTagChip(tag = tag, onClick = { onEmojiPick("($tag)") })
                            }
                        }
                    }
                    // Tab 1：贴纸（LazyVerticalGrid）
                    else -> {
                        if (stamps.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.lg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.comment_stamp_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 72.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(stamps, key = { it.stamp_id }) { stamp ->
                                    PixivImage(
                                        url = stamp.stamp_url,
                                        contentDescription = stringResource(R.string.comment_stamp_cd),
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                            .clickable { onStampPick(stamp) },
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
}
