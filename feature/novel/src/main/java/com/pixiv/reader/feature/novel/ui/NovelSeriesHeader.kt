package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.NovelSeriesDetail
import com.pixiv.reader.core.common.format.formatCountForNovel
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.card.SeriesBookCover
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.text.HtmlCaptionText
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.novel.R

/**
 * 系列信息头：大封面（真实图/图标兜底）+ 标题/简介 + 篇数/连载态徽章 + 作者行（头像/名称/关注）
 * + 操作行（追更 + 下载）+ 总字数。
 *
 * @param detail 系列详情（标题/徽章/作者/简介等）
 * @param onOpenAuthor 点击作者名打开主页（参数为作者 id）
 * @param coverUrl 系列封面 URL（首册 medium；null 走书本图标兜底）
 * @param onOpenCover 点击封面打开全屏大图
 * @param isAuthorFollowed 作者是否已关注
 * @param isAuthorFollowing 关注请求进行中（进行中禁用关注按钮防连点）
 * @param onToggleFollowAuthor 关注/取关作者回调
 * @param isWatchlisted 本系列是否已追更
 * @param isWatchlisting 追更请求进行中（进行中禁用追更按钮防连点）
 * @param onToggleWatchlist 追更/取消追更回调
 * @param downloading 下载进行中（进行中禁用下载按钮）
 * @param onDownload 点击下载按钮（先拉全量分册后由调用方打开下载弹窗）
 * @return 无返回值（渲染 Composable）
 */
@Composable
internal fun SeriesHeader(
    detail: NovelSeriesDetail,
    onOpenAuthor: (Long) -> Unit,
    coverUrl: String?,
    onOpenCover: () -> Unit,
    isAuthorFollowed: Boolean = false,
    isAuthorFollowing: Boolean = false,
    onToggleFollowAuthor: () -> Unit = {},
    isWatchlisted: Boolean = false,
    isWatchlisting: Boolean = false,
    onToggleWatchlist: () -> Unit = {},
    downloading: Boolean = false,
    onDownload: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // 大封面：3:4（110×148dp）；有真实封面图走 PixivImage（自动 Referer），无则 MD3 图标容器兜底
            if (!coverUrl.isNullOrBlank()) {
                PixivImage(
                    url = coverUrl,
                    contentDescription = detail.title,
                    modifier = Modifier
                        .width(110.dp)
                        .aspectRatio(3f / 4f)
                        .clip(AppShapes.card)
                        .clickable(onClick = onOpenCover),
                )
            } else {
                SeriesBookCover(
                    modifier = Modifier.size(width = 110.dp, height = 148.dp),
                    iconSize = 44.dp,
                )
            }
            Column(modifier = Modifier.padding(start = Spacing.lg).weight(1f)) {
                Text(
                    text = detail.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = Spacing.smPlus),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeriesMetaChip(
                        text = stringResource(R.string.novel_series_parts, detail.content_count),
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer,
                    )
                    SeriesMetaChip(
                        text = stringResource(
                            if (detail.is_concluded == true) R.string.novel_series_concluded else R.string.novel_series_ongoing,
                        ),
                        container = if (detail.is_concluded == true) scheme.errorContainer else scheme.secondaryContainer,
                        content = if (detail.is_concluded == true) scheme.onErrorContainer else scheme.onSecondaryContainer,
                    )
                }
                if (detail.total_character_count > 0) {
                    Text(
                        text = stringResource(
                            R.string.novel_series_total_chars,
                            formatCountForNovel(detail.total_character_count),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.smPlus),
                    )
                }
            }
        }
        val caption = detail.caption?.takeIf { it.isNotBlank() }
            ?: detail.display_text?.takeIf { it.isNotBlank() }
        if (caption != null) {
            // 简介：HTML 富文本（加粗/换行/pixiv 深链样式），3 行截断；系列页无链接跳转回调
            HtmlCaptionText(
                html = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.mdPlus),
            )
        }
        // 作者行：头像 + 名称（可点击进主页）+ 关注/取关按钮（占满整行）
        detail.user?.let { author ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.mdPlus),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    name = author.name,
                    avatarUrl = author.profile_image_urls?.best(),
                    modifier = Modifier.size(Sizes.s44),
                )
                Text(
                    text = author.name.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenAuthor(author.id) }
                        .padding(start = Spacing.md),
                )
                AuthorFollowPill(
                    isFollowed = isAuthorFollowed,
                    enabled = !isAuthorFollowing,
                    onClick = onToggleFollowAuthor,
                )
            }
        }
        // 操作行：追更（铃铛图标，已追更切换实心/文案）+ 下载（整系列 / 选取部分由弹窗决定）
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FilledTonalButton(
                onClick = onToggleWatchlist,
                enabled = !isWatchlisting,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (isWatchlisted) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.s18),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (isWatchlisted) R.string.novel_watchlisted else R.string.novel_watch))
            }
            Button(
                onClick = onDownload,
                enabled = !downloading,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.s18),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.novel_download))
            }
        }
    }
}

/** MD3 药丸徽章（AssistChip 视觉，扁平无交互）。 */
@Composable
private fun SeriesMetaChip(
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
            .padding(horizontal = Spacing.md, vertical = 5.dp),
    )
}
