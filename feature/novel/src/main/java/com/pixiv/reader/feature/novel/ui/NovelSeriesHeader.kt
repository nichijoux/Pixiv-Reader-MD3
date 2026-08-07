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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
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
import com.pixiv.reader.core.common.formatCountForNovel
import com.pixiv.reader.core.ui.component.PixivImage
import com.pixiv.reader.core.ui.component.SeriesBookCover
import com.pixiv.reader.core.ui.component.UserAvatar
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.feature.novel.R

/** 系列信息头：大封面（真实图/图标兜底）+ 标题/简介 + 篇数/连载态徽章 + 作者行（头像/名称/关注）+ 下载按钮 + 总字数。 */
@Composable
internal fun SeriesHeader(
    detail: NovelSeriesDetail,
    onOpenAuthor: (Long) -> Unit,
    coverUrl: String?,
    onOpenCover: () -> Unit,
    isAuthorFollowed: Boolean = false,
    isAuthorFollowing: Boolean = false,
    onToggleFollowAuthor: () -> Unit = {},
    downloading: Boolean = false,
    onDownload: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
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
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenCover),
                )
            } else {
                SeriesBookCover(
                    modifier = Modifier.size(width = 110.dp, height = 148.dp),
                    iconSize = 44.dp,
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = detail.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
        val caption = detail.caption?.takeIf { it.isNotBlank() }
            ?: detail.display_text?.takeIf { it.isNotBlank() }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        // 作者行：头像 + 名称（可点击进主页）+ 关注/取关按钮（占满整行）
        detail.user?.let { author ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    name = author.name,
                    avatarUrl = author.profile_image_urls?.best(),
                    modifier = Modifier.size(44.dp),
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
                        .padding(start = 12.dp),
                )
                AuthorFollowPill(
                    isFollowed = isAuthorFollowed,
                    enabled = !isAuthorFollowing,
                    onClick = onToggleFollowAuthor,
                )
            }
        }
        // 下载按钮：整行主题色（整系列 / 选取部分由弹窗决定）
        Button(
            onClick = onDownload,
            enabled = !downloading,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.novel_download))
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
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}
