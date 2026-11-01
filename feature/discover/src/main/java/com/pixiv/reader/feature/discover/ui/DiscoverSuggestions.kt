package com.pixiv.reader.feature.discover.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pixiv.api.model.AutocompleteTag
import com.pixiv.reader.feature.discover.R
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 按 ID 直达入口的渲染描述：图标 + 类型名资源 + 点击回调。
 *
 * @property icon 入口图标
 * @property labelRes 目标类型名（小说 / 插画 / 用户 / 系列）
 * @property onClick 点击后执行的跳转动作
 */
private data class IdQuickOpenAction(
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
    val onClick: () -> Unit,
)

/**
 * 聚焦态：搜索联想列表。
 *
 * 纯数字输入（[numericId] 非空）时，在联想条目之前渲染「按 ID 打开」入口区
 * （分区标题 + 小说/插画/用户/系列四张两列卡片），点击即跳对应详情页且不写搜索历史；
 * 此时空联想不再显示 [R.string.search_hint] 占位（入口区本身就是有效内容）。
 *
 * @param suggestions 服务端返回的标签联想
 * @param numericId 输入解析出的纯数字 ID，null 表示不展示 ID 直达入口
 * @param onOpenNovel 按 ID 打开小说详情
 * @param onOpenIllust 按 ID 打开插画详情
 * @param onOpenUser 按 ID 打开用户主页
 * @param onOpenSeries 按 ID 打开小说系列
 * @param onPick 点击普通联想条目（回填关键词并搜索）
 * @return 无返回值
 */
@Composable
internal fun SuggestionList(
    suggestions: List<AutocompleteTag>,
    numericId: Long?,
    onOpenNovel: (Long) -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onPick: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 纯数字输入：顶部「按 ID 打开」入口区，顺序固定为小说/插画/用户/系列
        if (numericId != null) {
            item(key = "id_quick_open_header") {
                IdQuickOpenHeader(numericId = numericId)
            }
            item(key = "id_quick_open_cards") {
                val idActions = listOf(
                    IdQuickOpenAction(Icons.AutoMirrored.Filled.MenuBook, R.string.search_type_novel) {
                        onOpenNovel(numericId)
                    },
                    IdQuickOpenAction(Icons.Filled.Image, R.string.search_type_illust) {
                        onOpenIllust(numericId)
                    },
                    IdQuickOpenAction(Icons.Filled.Person, R.string.search_type_user) {
                        onOpenUser(numericId)
                    },
                    IdQuickOpenAction(Icons.Filled.CollectionsBookmark, R.string.search_type_series) {
                        onOpenSeries(numericId)
                    },
                )
                // 两列网格：每行两张卡，窄屏也不至于把类型名截断
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                ) {
                    idActions.chunked(2).forEachIndexed { rowIndex, rowActions ->
                        if (rowIndex > 0) Spacer(Modifier.height(Spacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            rowActions.forEach { action ->
                                IdQuickOpenCard(
                                    icon = action.icon,
                                    label = stringResource(action.labelRes),
                                    onClick = action.onClick,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // 奇数补位：保持两列等宽节奏
                            if (rowActions.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            // 入口区与标签联想之间分隔；无联想时不画多余分隔线
            if (suggestions.isNotEmpty()) {
                item(key = "id_quick_open_divider") {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    )
                }
            }
        }
        // 无联想且无 ID 直达内容时才提示先输入关键词
        if (suggestions.isEmpty() && numericId == null) {
            item {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lgPlus),
                )
            }
        }
        items(suggestions, key = { it.name ?: it.hashCode() }) { tag ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { tag.name?.let(onPick) }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.s18))
                Text(
                    text = tag.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = Spacing.md),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 「按 ID 打开」分区标题：标签图标 + 标题 + 右侧等宽感 ID 徽标。
 *
 * 标题与 ID 分列两端，卡片区只保留类型名，视觉更轻且信息无重复。
 *
 * @param numericId 当前输入的纯数字 ID（渲染为 `#123456`）
 * @return 无返回值
 */
@Composable
private fun IdQuickOpenHeader(numericId: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.xsPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Tag,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Sizes.s18),
        )
        Text(
            text = stringResource(R.string.search_id_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(start = Spacing.xsPlus)
                .weight(1f),
        )
        // ID 徽标：胶囊淡底承载纯数字，与标题形成主次层级
        Text(
            text = "#$numericId",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .clip(AppShapes.pill)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = Spacing.smPlus, vertical = Spacing.xxs),
        )
    }
}

/**
 * 按 ID 直达入口卡：圆形强调色图标底 + 类型名，整卡可点；
 * 按压时 spring 微缩（与发现页入口卡、热门标签胶囊同一 Expressive 触感）。
 *
 * @param icon 目标类型图标
 * @param label 目标类型名（小说 / 插画 / 用户 / 系列）
 * @param onClick 点击回调（跳对应详情页）
 * @param modifier 布局参数（两列网格调用方传 `weight(1f)`）
 * @return 无返回值
 */
@Composable
private fun IdQuickOpenCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 按压微缩：Expressive 空间弹簧（位移/形变档）
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "idQuickOpenPress",
    )
    Surface(
        onClick = onClick,
        shape = AppShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.smPlus),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // 圆形淡强调底：与发现页入口卡同一图标容器语言
            Box(
                modifier = Modifier
                    .size(Sizes.s32)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), AppShapes.circle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Sizes.s18),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 占满剩余宽度：长语言（如英文 Illustrations）省略号收敛，不挤压图标
                modifier = Modifier.weight(1f),
            )
        }
    }
}
