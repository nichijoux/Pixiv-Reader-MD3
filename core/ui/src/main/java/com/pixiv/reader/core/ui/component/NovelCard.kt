package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pixiv.reader.core.common.formatCountForNovel
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 小说卡片数据模型（通用：搜索结果 / 推荐流 / 用户主页 / 收藏夹 / 浏览历史）。
 * 由 API 的 [com.pixiv.api.model.Novel] 映射，或由历史/下载快照恢复。
 *
 * @param id 小说 ID（点击跳转 / 收藏 API 用）
 * @param title 小说标题
 * @param coverUrl 封面 URL（square_medium 优先，其次 medium）
 * @param authorId 作者用户 ID（点击作者行跳用户主页用；0 表示无）
 * @param authorName 作者名称
 * @param authorAvatarUrl 作者头像 URL
 * @param publishDate 发布日期（ISO 字符串，展示时取前 10 位 yyyy-MM-dd）
 * @param seriesTitle 所属系列标题（非系列为 null）
 * @param seriesId 所属系列 ID（非系列/快照缺失为 null；非 null 时系列标题可点击进系列页）
 * @param favoriteCount 收藏数（封面底部角标）
 * @param wordCount 字数（封面底部角标）
 * @param tags 标签展示名列表（FlowRow chip；最多展示 5 个，多余折叠 "+N"）
 * @param isFavorite 是否已收藏（初始化收藏按钮状态）
 */
data class NovelCardData(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    val authorId: Long,
    val authorName: String,
    val authorAvatarUrl: String?,
    val publishDate: String?,
    val seriesTitle: String?,
    val seriesId: Long? = null,
    val favoriteCount: Int,
    val wordCount: Int,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
)

/**
 * 小说通用卡片（Material 主题自适应，上下两部分布局：上方 左封面|右信息，下方 标签）。
 *
 * ## UI 设计方式
 * - **上部分**（Row）：左封面（104dp 宽 + `aspectRatio(3/4)` 书籍比例，圆角 12dp，点击进阅读器）；
 *   底部居中角标（无背景、白色加粗 11sp + 轻文字阴影）：红心 + 收藏数 / 字数两行。
 *   右信息（Column 撑满封面高度，作者行抵底）：
 *   - 标题：`titleMedium` 加粗 `onSurface`（最多 2 行省略）+ 右侧收藏切换按钮
 *   - 系列名（若有）：`labelMedium` **APP 主题色 `primary`**（仅换色，不加粗无前缀），1 行省略
 *   - 弹性占位 + 底部作者行：28dp 头像 + 作者名（`onSurfaceVariant`）+ 时间（`outline` 最浅）同行抵底
 * - **下部分**：标签区 `FlowRow` 圆角 chip（最多 3 个 + "+N" 折叠，无分隔线），点击触发搜索
 * 颜色全部取自 `MaterialTheme`，尺寸用 `aspectRatio`/`typography`/相对布局，不硬编码。
 *
 * ## 交互
 * - [onClick] 整卡 → 小说详情；[onOpenCover] 封面 → 全屏查看封面大图
 * - [onOpenAuthor] 作者行 → 用户主页；[onToggleFavorite] 收藏切换（组件维护 UI 态 + 回调外部 API）
 * - [onTagClick] 标签 → 搜索该标签
 *
 * @param novel 卡片数据（见 [NovelCardData]）
 * @param onClick 整卡点击（打开小说详情）
 * @param onOpenCover 封面点击（全屏查看封面大图；coverUrl 为空时封面不可点）
 * @param onOpenAuthor 作者行点击（打开用户主页；快照缺 authorId 时传空 lambda）
 * @param onToggleFavorite 收藏切换回调（参数为目标状态 true=收藏）；组件本地维护 UI 态
 * @param onSeriesClick 系列标题点击（打开系列详情页；seriesId 为 null 时系列标题不可点）
 * @param onTagClick 标签点击（触发标签搜索）
 * @param rank 排名序号（排行榜用，非 null 时封面左上角显示排名徽标：1金/2橙/3灰，其余白色）
 * @param showFavoriteCount 是否显示封面角标的「红心+收藏数」行（默认 true；下载管理等场景传 false，
 *                          仅隐藏收藏数行，字数行保留）
 * @param coverBadge 封面右上角浮层内容（如下载管理页的格式类型胶囊）；null 时不渲染
 * @param modifier 外部传入的 Modifier
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelCard(
    novel: NovelCardData,
    onClick: () -> Unit,
    onOpenCover: () -> Unit,
    onOpenAuthor: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onTagClick: (String) -> Unit,
    onSeriesClick: (Long) -> Unit = {},
    rank: Int? = null,
    showFavoriteCount: Boolean = true,
    coverBadge: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 收藏态：初始化为数据模型中的 isFavorite，点击切换并回调外部执行 API
    var favorite by remember(novel.id) { mutableStateOf(novel.isFavorite) }

    // 封面角标文字样式：白色加粗 + 轻文字阴影（无背景，保证浅色封面上可读）
    val badgeStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.45f),
            offset = Offset(0f, 1f),
            blurRadius = 2f,
        ),
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = AppShapes.large,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── 上部分：左右布局（左封面 | 右信息） ──
            // height(IntrinsicSize.Min)：Row 高度取封面固有高度（104×4/3），
            // 使右信息 Column 的 fillMaxHeight 有确定高度可撑满，作者行才能抵底与封面齐平。
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // 左：封面（书籍比例 + 底部居中角标；无封面 URL 时不可点）
                Box(
                    modifier = Modifier
                        .width(104.dp)
                        .aspectRatio(3f / 4f)
                        .clip(AppShapes.card)
                        .clickable(enabled = !novel.coverUrl.isNullOrBlank(), onClick = onOpenCover)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    AsyncImage(
                        model = novel.coverUrl,
                        contentDescription = novel.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 排名徽标（排行榜用，左上角）：1金/2橙/3灰，其余白色，黑底圆角块
                    if (rank != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(AppShapes.small)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = Spacing.sm, vertical = 3.dp),
                        ) {
                            Text(
                                text = "$rank",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                color = rankColor(rank) ?: Color.White,
                            )
                        }
                    }
                    // 封面右上角浮层：如下载管理页的格式类型胶囊（不占行，与左上角 rank 徽标对称）
                    if (coverBadge != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                        ) {
                            coverBadge()
                        }
                    }
                    // 底部居中角标：红心 + 收藏数 / 字数（无背景，字体加粗）
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(vertical = Spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (showFavoriteCount) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = formatCountForNovel(novel.favoriteCount),
                                    style = badgeStyle,
                                    modifier = Modifier.padding(start = 3.dp),
                                )
                            }
                        }
                        Text(
                            text = formatCountForNovel(novel.wordCount),
                            style = badgeStyle,
                        )
                    }
                }

                // 右：信息区（顶部标题/系列，弹性占位后作者+时间抵底）
                Column(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    // 标题 + 收藏按钮
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = novel.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                favorite = !favorite
                                onToggleFavorite(favorite)
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (favorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite),
                                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    // 系列信息（APP 主题色 primary，仅换色；seriesId 非空时可点击进系列页）
                    val seriesId = novel.seriesId
                    if (!novel.seriesTitle.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = seriesId != null, onClick = { seriesId?.let(onSeriesClick) })
                                .padding(end = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = novel.seriesTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    // 弹性占位：把作者+时间推到信息区底部
                    Spacer(Modifier.weight(1f))
                    // 作者 + 时间（一行抵底：头像 + 作者名 + 右侧时间）
                    Row(
                        modifier = Modifier
                            .clip(AppShapes.small)
                            .clickable(onClick = onOpenAuthor),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(
                            name = novel.authorName,
                            avatarUrl = novel.authorAvatarUrl,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = novel.authorName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = Spacing.sm)
                                .weight(1f),
                        )
                        if (!novel.publishDate.isNullOrBlank()) {
                            Text(
                                text = novel.publishDate.take(10),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }

            // ── 下部分：标签（FlowRow + 折叠 "+N"，无分隔线） ──
            if (novel.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    novel.tags.take(5).forEach { tag ->
                        NovelTagChip(text = "#$tag", onClick = { onTagClick(tag) })
                    }
                    if (novel.tags.size > 5) {
                        NovelTagChip(text = "+${novel.tags.size - 5}", onClick = {})
                    }
                }
            }
        }
    }
}

/** 标签圆角 chip（点击触发搜索）。 */
@Composable
private fun NovelTagChip(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(AppShapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = 3.dp),
    )
}
