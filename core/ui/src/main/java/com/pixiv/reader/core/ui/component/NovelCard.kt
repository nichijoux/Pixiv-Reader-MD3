package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pixiv.reader.core.common.formatCountForNovel
import com.pixiv.reader.core.ui.R

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
 * @param favoriteCount 收藏数（封面底部角标）
 * @param wordCount 字数（封面底部角标）
 * @param tags 标签展示名列表（FlowRow chip；最多展示 3 个，多余折叠 "+N"）
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
    val favoriteCount: Int,
    val wordCount: Int,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
)

/**
 * 小说通用卡片（Material 主题自适应，横向布局：左侧封面 + 右侧信息）。
 *
 * ## UI 设计方式
 * - **封面**（88dp 宽 + `aspectRatio(3/4)` 书籍比例，圆角 12dp）：图片底部叠加黑色渐变遮罩，
 *   其上显示收藏数（♡ + 数值）与字数两行角标；点击封面进入阅读器。
 * - **信息区**（Column）：
 *   - 标题行：`titleMedium` 加粗标题（最多 2 行省略）+ 右侧收藏图标按钮
 *   - 系列信息（若有）：`labelMedium` 次级色，最多 1 行
 *   - 作者行：24dp 头像 + 作者名 + 发布日期（点击作者行进主页）
 *   - 标签区：`FlowRow` 圆角 chip（最多 3 个 + "+N" 折叠），点击标签触发搜索
 * 颜色全部取自 `MaterialTheme`，尺寸用 `aspectRatio`/`typography`/相对布局，不硬编码。
 *
 * ## 交互
 * - [onClick] 整卡 → 小说详情；[onOpenReader] 封面 → 阅读器
 * - [onOpenAuthor] 作者行 → 用户主页；[onToggleFavorite] 收藏切换（组件维护 UI 态 + 回调外部 API）
 * - [onTagClick] 标签 → 搜索该标签
 *
 * @param novel 卡片数据（见 [NovelCardData]）
 * @param onClick 整卡点击（打开小说详情）
 * @param onOpenReader 封面点击（打开阅读器，历史/下载等可复用此入口直达阅读）
 * @param onOpenAuthor 作者行点击（打开用户主页；快照缺 authorId 时传空 lambda）
 * @param onToggleFavorite 收藏切换回调（参数为目标状态 true=收藏）；组件本地维护 UI 态
 * @param onTagClick 标签点击（触发标签搜索）
 * @param modifier 外部传入的 Modifier
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelCard(
    novel: NovelCardData,
    onClick: () -> Unit,
    onOpenReader: () -> Unit,
    onOpenAuthor: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 收藏态：初始化为数据模型中的 isFavorite，点击切换并回调外部执行 API
    var favorite by remember(novel.id) { mutableStateOf(novel.isFavorite) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            // ── 左：封面（书籍比例 + 底部叠加收藏/字数） ──
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenReader)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                AsyncImage(
                    model = novel.coverUrl,
                    contentDescription = novel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // 底部渐变遮罩 + 统计
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.7f),
                            ),
                        ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = formatCountForNovel(novel.favoriteCount),
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 3.dp),
                            )
                        }
                        Text(
                            text = formatCountForNovel(novel.wordCount),
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // ── 右：信息区 ──
            Column(
                modifier = Modifier
                    .padding(start = 14.dp)
                    .weight(1f),
            ) {
                // 标题 + 收藏按钮
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = novel.title,
                        style = MaterialTheme.typography.titleMedium,
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
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (favorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite),
                            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                // 系列信息
                if (!novel.seriesTitle.isNullOrBlank()) {
                    Text(
                        text = novel.seriesTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                // 作者行（点击进主页）
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
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
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    if (!novel.publishDate.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = novel.publishDate.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 标签：FlowRow + 折叠 "+N"
                if (novel.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        novel.tags.take(3).forEach { tag ->
                            NovelTagChip(text = "#$tag", onClick = { onTagClick(tag) })
                        }
                        if (novel.tags.size > 3) {
                            NovelTagChip(text = "+${novel.tags.size - 3}", onClick = {})
                        }
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
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
