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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** 小说卡片数据模型（通用：搜索结果 / 推荐流 / 用户主页 / 收藏夹）。 */
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
 * 小说搜索结果卡片（Material 主题自适应，横向：封面 + 信息）。
 *
 * - 封面：书籍宽高比、圆角，底部叠加收藏数 / 字数；点击打开阅读器
 * - 信息：标题（高优先级）+ 收藏图标按钮；系列信息；作者头像/名 + 发布日期（点击作者进主页）
 * - 标签：FlowRow 圆角 chip，多余折叠 "+N"；点击标签搜索
 *
 * 颜色全部取自 MaterialTheme，尺寸用 aspectRatio / typography / 相对布局。
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
                                text = formatCompact(novel.favoriteCount),
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 3.dp),
                            )
                        }
                        Text(
                            text = formatCompact(novel.wordCount),
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
                            contentDescription = if (favorite) "取消收藏" else "收藏",
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

/** 计数紧凑格式化：≥1 万显示 "x.x万"。 */
private fun formatCompact(count: Int): String = when {
    count >= 10000 -> String.format(java.util.Locale.US, "%.1f万", count / 10000f)
    else -> count.toString()
}
