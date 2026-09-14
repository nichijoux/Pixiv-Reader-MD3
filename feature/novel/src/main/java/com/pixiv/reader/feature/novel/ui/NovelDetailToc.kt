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
            .padding(horizontal = Spacing.md, vertical = Spacing.smPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 序号徽标（HTML `.tidx`：28dp、chip 圆角、当前章主色）
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

/** 系列目录标题（HTML `.sectitle`）：MenuBook 图标 + 15sp Bold 标题 + 数量胶囊 `.cnt`。 */
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
        // 数量胶囊（HTML `.sectitle .cnt`：primary-container 底 + primary 字 + 圆角胶囊）
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
 * 系列目录（手机 / 平板两种容器形态合一）：
 * [maxHeight] 非 null = 手机单列形态（标题 + 限高卡片列表，装饰在列表容器上，「查看完整系列」在卡片外）；
 * null = 平板左栏形态（整卡装饰 + 列表 weight 撑满，「查看完整系列」在卡片内）。
 *
 * @param seriesNovels 系列分册列表
 * @param currentId 当前打开的小说 id（高亮 + 自动滚动定位）
 * @param seriesId 系列 id（null 不显示「查看完整系列」）
 * @param onOpenNovel 分册点击
 * @param onOpenSeries 「查看完整系列」点击
 * @param maxHeight 列表最大高度（手机限高内部滚动）；null = 平板左栏 weight 填充
 * @param modifier 外部 Modifier
 * @return 无返回值（渲染 Composable）
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
    // 自动滚动定位到当前章节（首次加载 / 系列数据更新时），当前章不在列表中则不滚动
    LaunchedEffect(seriesNovels, currentId) {
        val index = seriesNovels.indexOfFirst { it.id == currentId }
        if (index >= 0) listState.scrollToItem(index)
    }
    if (maxHeight != null) {
        // 手机端：标题在卡片外，列表自身限高 + 卡片装饰（不随分册数量增高）
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
        // 平板左栏：整卡装饰（标题/查看完整系列都在卡片内），列表 weight 撑满内部滚动
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

/**
 * 目录分册行列表（两种容器形态共用的 LazyColumn 内容）。
 *
 * @param listState 调用方创建并驱动自动滚动的列表状态
 * @param seriesNovels 系列分册列表
 * @param currentId 当前打开的小说 id（高亮）
 * @param onOpenNovel 分册点击
 * @param modifier 列表 Modifier（限高装饰 / weight 由调用方传入）
 * @return 无返回值（渲染 Composable）
 */
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

/** 目录卡片装饰：圆角 + 描边 + 浅底（手机列表容器 / 平板整卡共用）。 */
@Composable
private fun Modifier.tocCardDecoration(): Modifier =
    this
        .clip(AppShapes.card)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.card)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)

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
