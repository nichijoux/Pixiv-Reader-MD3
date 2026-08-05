package com.pixiv.reader.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.ImageUrls
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.IllustCard
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import kotlinx.coroutines.launch

/**
 * 下载管理：TabRow（插画/小说/离线）+ HorizontalPager 滑动切换。
 * 插画用 `IllustCard`（宽高完整显示）、小说/离线用 `NovelCard`；每项右上角删除按钮。
 * 小说（txt/epub 导出文件）点击 → 解析本地文件本地阅读。
 *
 * @param onBack 返回
 * @param onOpenIllust 打开插画详情
 * @param onOpenNovel 打开小说详情
 * @param onOpenCover 打开小说封面全屏大图
 * @param onOpenReader 打开小说阅读器（离线直达）
 * @param onOpenLocalReader 打开本地文件阅读（local_reader）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenLocalReader: (Long) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val filter by viewModel.filterFlow.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { DownloadFilter.entries.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 滑动切页 → 同步筛选
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page in DownloadFilter.entries.indices) {
            viewModel.selectFilter(DownloadFilter.entries[page])
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = filter.ordinal.coerceAtMost(DownloadFilter.entries.size - 1),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    DownloadFilter.entries.forEachIndexed { index, f ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(stringResource(f.labelRes)) },
                        )
                    }
                }
                HorizontalPager(state = pagerState) { page ->
                    when (DownloadFilter.entries.getOrNull(page)) {
                        DownloadFilter.ILLUST -> IllustDownloadList(
                            entries = entries.filter { it.targetType == "illust" },
                            onOpenIllust = onOpenIllust,
                            onDelete = viewModel::delete,
                        )
                        DownloadFilter.NOVEL -> NovelDownloadList(
                            entries = entries.filter { it.targetType == "novel" },
                            context = context,
                            onOpenCover = onOpenCover,
                            onOpen = { entry ->
                                if (isLocalFile(entry)) {
                                    viewModel.openLocal(entry) { onOpenLocalReader(entry.targetId) }
                                } else {
                                    onOpenNovel(entry.targetId)
                                }
                            },
                            onDelete = viewModel::delete,
                        )
                        DownloadFilter.OFFLINE -> NovelDownloadList(
                            entries = entries.filter { it.targetType == "novel_offline" },
                            context = context,
                            onOpenCover = onOpenCover,
                            onOpen = { entry -> onOpenReader(entry.targetId) },
                            onDelete = viewModel::delete,
                        )
                        null -> {}
                    }
                }
            }
        }
    }
}

// ── 插画：IllustCard 瀑布流 ─────────────────────────────────────────────────

@Composable
private fun IllustDownloadList(
    entries: List<DownloadEntryEntity>,
    onOpenIllust: (Long) -> Unit,
    onDelete: (DownloadEntryEntity) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyBox(stringResource(R.string.downloads_empty_illust))
        return
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        items(entries, key = { it.targetId }) { entry ->
            Box {
                IllustCard(
                    illust = entry.toDownloadIllust(),
                    onClick = { onOpenIllust(entry.targetId) },
                )
                DeleteOverlay(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    onDelete = { onDelete(entry) },
                )
            }
        }
    }
}

// ── 小说 / 离线：NovelCard ───────────────────────────────────────────────────

@Composable
private fun NovelDownloadList(
    entries: List<DownloadEntryEntity>,
    context: Context,
    onOpenCover: (String) -> Unit,
    onOpen: (DownloadEntryEntity) -> Unit,
    onDelete: (DownloadEntryEntity) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyBox(stringResource(R.string.downloads_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(entries, key = { it.targetId }) { entry ->
            Box {
                val card = entry.toDownloadNovelCard(context)
                NovelCard(
                    novel = card,
                    onClick = { onOpen(entry) },
                    onOpenCover = { card.coverUrl?.let(onOpenCover) },
                    onOpenAuthor = {},
                    onToggleFavorite = {},
                    onTagClick = {},
                )
                DeleteOverlay(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    onDelete = { onDelete(entry) },
                )
            }
        }
    }
}

/** 右上角圆形删除按钮。 */
@Composable
private fun DeleteOverlay(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
) {
    IconButton(
        onClick = onDelete,
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.cd_delete),
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}

// ── 数据转换 ────────────────────────────────────────────────────────────────

private fun DownloadEntryEntity.toDownloadIllust(): Illust = Illust(
    id = targetId,
    title = title,
    image_urls = ImageUrls(medium = coverUrl),
    width = width,
    height = height,
)

private fun DownloadEntryEntity.toDownloadNovelCard(context: Context): NovelCardData = NovelCardData(
    id = targetId,
    title = title ?: context.getString(R.string.untitled),
    coverUrl = coverUrl,
    authorId = 0,
    authorName = "",
    authorAvatarUrl = null,
    publishDate = null,
    seriesTitle = null,
    favoriteCount = 0,
    wordCount = 0,
)

private fun isLocalFile(entry: DownloadEntryEntity): Boolean {
    val ext = entry.localPath?.substringAfterLast('.', "")?.lowercase() ?: return false
    return ext == "txt" || ext == "epub"
}
