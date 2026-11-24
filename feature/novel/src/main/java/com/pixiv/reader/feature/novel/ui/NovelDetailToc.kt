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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.format.formatCount
import com.pixiv.reader.core.common.format.formatCountForNovel
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.novel.R

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
            .padding(horizontal = Spacing.md, vertical = Spacing.smPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.s28)
                .clip(AppShapes.cardSmall)
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
                .padding(start = Spacing.smPlus),
        ) {
            Text(
                text = novel.title.orEmpty(),
                style = if (isCurrent) novelTocRowStyle().copy(fontWeight = FontWeight.SemiBold) else novelTocRowStyle(),
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = Spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
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
                    .padding(horizontal = Spacing.sm, vertical = 3.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun TocTitle(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.novel_toc_title),
            style = novelTocTitleStyle(),
            modifier = Modifier.padding(start = 7.dp),
        )
        Box(
            modifier = Modifier
                .padding(start = Spacing.sm)
                .clip(AppShapes.pill)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 9.dp, vertical = Spacing.xxs),
        ) {
            Text(
                text = count.toString(),
                style = novelCountBadgeStyle(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 系列目录，两种容器形态合一：[maxHeight] 非 null = 手机限高形态（标题与「查看完整系列」在卡片外）；
 * null = 平板左栏（整卡装饰 + 列表 weight 撑满，「查看完整系列」在卡片内）。
 */
@Composable
internal fun NovelTocList(
    seriesNovels: List<Novel>,
    currentId: Long,
    seriesId: Long?,
    onOpenNovel: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    maxHeight: Dp?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 当前章不在列表时（如详情页未加载完）不滚
    LaunchedEffect(seriesNovels, currentId) {
        val index = seriesNovels.indexOfFirst { it.id == currentId }
        if (index >= 0) listState.scrollToItem(index)
    }
    if (maxHeight != null) {
        // 手机端：标题在卡片外，仅列表自身卡片装饰
        Column(modifier = modifier) {
            TocTitle(
                count = seriesNovels.size,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
            TocRows(
                listState = listState,
                seriesNovels = seriesNovels,
                currentId = currentId,
                onOpenNovel = onOpenNovel,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .tocCardDecoration(),
            )
            SeriesMoreRow(seriesId, onOpenSeries)
        }
    } else {
        // 平板左栏：整卡装饰，列表 weight 撑满内部滚动
        Column(
            modifier = modifier
                .tocCardDecoration()
                .padding(vertical = Spacing.xs),
        ) {
            TocTitle(
                count = seriesNovels.size,
                modifier = Modifier.padding(horizontal = Spacing.mdPlus, vertical = Spacing.smPlus),
            )
            TocRows(
                listState = listState,
                seriesNovels = seriesNovels,
                currentId = currentId,
                onOpenNovel = onOpenNovel,
                modifier = Modifier.weight(1f),
            )
            SeriesMoreRow(seriesId, onOpenSeries)
        }
    }
}

@Composable
private fun TocRows(
    listState: LazyListState,
    seriesNovels: List<Novel>,
    currentId: Long,
    onOpenNovel: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(seriesNovels) { index, chapter ->
            ChapterRow(
                novel = chapter,
                index = index,
                isCurrent = chapter.id == currentId,
                onClick = { onOpenNovel(chapter.id) },
            )
        }
    }
}

/** 目录卡片装饰：圆角 + 描边 + 浅底（两种形态共用）。 */
@Composable
private fun Modifier.tocCardDecoration(): Modifier =
    this
        .clip(AppShapes.card)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.card)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)

/** 「查看完整系列 ›」行（无系列 id 时不渲染）。 */
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
            .padding(vertical = Spacing.md),
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
            modifier = Modifier.size(Sizes.s16),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
