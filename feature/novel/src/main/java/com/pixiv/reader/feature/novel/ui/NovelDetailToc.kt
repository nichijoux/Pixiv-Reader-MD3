package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.formatCount
import com.pixiv.reader.core.common.formatCountForNovel
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.novel.R

/** 系列目录行（HTML `.trow`）：序号徽标 + 标题 + 字数/收藏 + 当前章胶囊。 */
@Composable
internal fun ChapterRow(
    novel: Novel,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 序号徽标（HTML `.tidx`：28dp、圆角 9、当前章主色）
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondaryContainer,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                style = novelSmallLabelStyle().copy(fontWeight = FontWeight.Bold),
                color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = novel.title.orEmpty(),
                style = if (isCurrent) novelTocRowStyle().copy(fontWeight = FontWeight.SemiBold) else novelTocRowStyle(),
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.novel_chapter_word, formatCountForNovel(novel.text_length ?: 0)),
                    style = novelSmallLabelStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.novel_chapter_bookmark, formatCount((novel.total_bookmarks ?: 0).toLong())),
                    style = novelSmallLabelStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isCurrent) {
            Text(
                text = stringResource(R.string.novel_chapter_current),
                style = novelCurrentBadgeStyle(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(AppShapes.pill)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 系列目录标题（HTML `.sectitle`）：MenuBook 图标 + 15sp Bold 标题 + 数量胶囊 `.cnt`。 */
@Composable
internal fun TocTitle(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.novel_toc_title),
            style = novelTocTitleStyle(),
            modifier = Modifier.padding(start = 7.dp),
        )
        // 数量胶囊（HTML `.sectitle .cnt`：primary-container 底 + primary 字 + 圆角胶囊）
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(AppShapes.pill)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 9.dp, vertical = 2.dp),
        ) {
            Text(
                text = count.toString(),
                style = novelCountBadgeStyle(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 系列目录（手机端单列）：标题 + 限高内部滚动列表 + 查看完整系列（不随分册数量增高）。 */
@Composable
internal fun NovelTocScroll(
    seriesNovels: List<Novel>,
    currentId: Long,
    seriesId: Long?,
    onOpenNovel: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TocTitle(
            count = seriesNovels.size,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clip(AppShapes.card)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.card)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(rememberScrollState()),
        ) {
            seriesNovels.forEachIndexed { index, chapter ->
                ChapterRow(
                    novel = chapter,
                    index = index,
                    isCurrent = chapter.id == currentId,
                    onClick = { onOpenNovel(chapter.id) },
                )
            }
        }
        SeriesMoreRow(seriesId, onOpenSeries)
    }
}

/** 系列目录（平板左栏卡片）：固定于 banner 下方（sticky 等效），列表内部滚动。 */
@Composable
internal fun NovelTocPanel(
    seriesNovels: List<Novel>,
    currentId: Long,
    seriesId: Long?,
    onOpenNovel: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(AppShapes.card)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 4.dp),
    ) {
        TocTitle(
            count = seriesNovels.size,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            seriesNovels.forEachIndexed { index, chapter ->
                ChapterRow(
                    novel = chapter,
                    index = index,
                    isCurrent = chapter.id == currentId,
                    onClick = { onOpenNovel(chapter.id) },
                )
            }
        }
        SeriesMoreRow(seriesId, onOpenSeries)
    }
}

/** 「查看完整系列 ›」行（HTML `.tocmore`，无系列 id 时不渲染）。 */
@Composable
internal fun SeriesMoreRow(
    seriesId: Long?,
    onOpenSeries: (Long) -> Unit,
) {
    if (seriesId == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSeries(seriesId) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.novel_series_view_all),
            style = novelMetaStyle().copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
