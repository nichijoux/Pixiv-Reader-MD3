package com.pixiv.reader.feature.comic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.comic.R

/**
 * COMIC 作品卡（首页 / 排行 / 搜索共用的最小网格卡）：竖版封面 + 标题 +
 * 作者 + 话数。
 *
 * @param title 作品名（最多两行）
 * @param author 作者（最多一行）
 * @param coverUrl 封面 URL（可空，渲染占位色块）
 * @param storiesCount 章节总数
 * @param onClick 点击回调（进详情）
 * @param modifier 外部传入的 Modifier
 * @return 无返回值
 */
@Composable
internal fun ComicWorkCard(
    title: String,
    author: String,
    coverUrl: String?,
    storiesCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        PixivImage(
            url = coverUrl,
            contentDescription = title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_COVER_RATIO),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text = author,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
        Text(
            text = stringResource(R.string.comic_stories_count, storiesCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
}

/** 卡片封面固定宽高比（COMIC 缩略为竖版书页，实测约 1:1.4）。 */
private const val CARD_COVER_RATIO = 0.71f
