package com.pixiv.reader.core.ui.component.card
import com.pixiv.reader.core.ui.component.image.PixivImage

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.format.formatCountForNovel
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 系列卡片数据（数据驱动：用户页系列列表 / 追更列表共用）。
 *
 * @param title 系列标题
 * @param caption 简介（用户页系列简介；追更场景无简介不传）
 * @param coverUrl 封面 URL（null 用 [SeriesBookCover] 图标容器兜底）
 * @param partsCount 已发布篇数（叠加显示在封面底部信息条）
 * @param totalChars 总字数（叠加显示在封面底部信息条，<=0 不显示）
 * @param isConcluded 连载状态（null 不显示状态徽章，徽章在封面左上角）
 * @param watchlisted 是否已追更（显示「已追更」徽章，封面左上角；追更列表全量追更可不传）
 * @param authorName 作者名称（null/空白不显示作者行）
 * @param authorAvatarUrl 作者头像 URL
 * @param updatedAt 最近更新时间（作者行右侧，icon + 日期）
 */
data class SeriesCardData(
    val title: String,
    val caption: String? = null,
    val coverUrl: String? = null,
    val partsCount: Int = 0,
    val totalChars: Int = 0,
    val isConcluded: Boolean? = null,
    val watchlisted: Boolean = false,
    val authorName: String? = null,
    val authorAvatarUrl: String? = null,
    val updatedAt: String? = null,
)

/**
 * 系列卡片：封面 96x128（左上角状态徽章 + 底部 [icon] N 篇 [icon] N 字信息条），
 * 右侧列撑满封面高度（`height(IntrinsicSize.Min)` + `fillMaxHeight`）：
 * **标题顶对齐封面顶部**，`Spacer(weight 1f)` 弹性占位后 **作者行（头像+名称+更新时间）抵底对齐封面底部**。
 * 原实现位于 feature:user 用户页系列分区（内部组件），为供小说追更页复用上移到 core:ui。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesCard(
    data: SeriesCardData,
    onClick: () -> Unit,
    onOpenAuthor: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 封面（96x128）：真实封面 / 图标兜底 + 底部半透明统计条（篇数/字数）
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 128.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            if (!data.coverUrl.isNullOrBlank()) {
                PixivImage(
                    url = data.coverUrl,
                    contentDescription = data.title,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SeriesBookCover(
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 48.dp,
                )
            }
            // 底部信息条：半透明黑底 + [icon] N 篇 [icon] N 字（FlowRow 窄屏换行）
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    CoverStat(
                        icon = Icons.Filled.Collections,
                        text = stringResource(R.string.series_parts, data.partsCount),
                    )
                    if (data.totalChars > 0) {
                        CoverStat(
                            icon = Icons.Filled.Notes,
                            text = stringResource(
                                R.string.series_chars,
                                formatCountForNovel(data.totalChars),
                            ),
                        )
                    }
                }
            }
        }
        // 右侧列：撑满封面高度——标题顶对齐封面顶部，作者行抵底对齐封面底部
        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Text(
                text = data.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // 内容简介（1 行截断，不显示完整；保证总高度不超过封面 128dp、作者行恒抵底）
            val caption = data.caption
            if (!caption.isNullOrBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // 状态徽章（连载中/已完结 + 已追更，彩色药丸，FlowRow 窄屏换行）
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val concluded = data.isConcluded
                if (concluded != null) {
                    SeriesStatusBadge(
                        text = stringResource(
                            if (concluded) R.string.series_concluded else R.string.series_ongoing,
                        ),
                        container = if (concluded) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        content = if (concluded) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
                if (data.watchlisted) {
                    SeriesStatusBadge(
                        text = stringResource(R.string.series_watchlisted),
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 弹性占位：把作者行推到列底部（与封面底部对齐）
            Spacer(Modifier.weight(1f))
            // 作者行：头像 + 名称 + 右侧更新时间 icon + 日期（抵底）
            if (!data.authorName.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .clip(AppShapes.small)
                        .clickable(onClick = onOpenAuthor),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(
                        name = data.authorName,
                        avatarUrl = data.authorAvatarUrl,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = data.authorName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = Spacing.sm)
                            .weight(1f),
                    )
                    if (!data.updatedAt.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Filled.Update,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = data.updatedAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** MD3 药丸徽章（AssistChip 视觉，扁平无交互；右侧状态行用）。 */
@Composable
private fun SeriesStatusBadge(
    text: String,
    container: Color,
    content: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .clip(AppShapes.pill)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** 封面信息条单项：小图标 + 白字（叠加在封面上）。 */
@Composable
private fun CoverStat(
    icon: ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
