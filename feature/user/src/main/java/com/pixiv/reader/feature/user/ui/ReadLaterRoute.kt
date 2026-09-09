package com.pixiv.reader.feature.user.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import com.pixiv.api.model.ImageUrls
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.database.entity.ReadLaterEntity
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.NovelCardData
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.feature.user.state.ReadLaterViewModel
import kotlinx.coroutines.launch

/**
 * 稍后再看页（Me 页入口）：作品 / 小说 分段控件 + HorizontalPager 左右滑动切换 + 快照卡片列表。
 * 卡片数据从 `payloadJson` 离线还原（与浏览历史同范式：插画直解 `Illust`、小说逐字段重建防 NPE）；
 * 移除入口 = 长按卡片（全局动作菜单「移出稍后再看」），另提供顶栏清空（确认框）。
 *
 * @param onBack 返回
 * @param onOpenIllust 打开插画详情
 * @param onOpenNovel 打开小说详情
 * @param onOpenUser 打开用户主页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadLaterRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: ReadLaterViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }
    val gson = remember { Gson() }
    // 左右滑动切页：分段点击滚动 Pager，滑动落页回写筛选（与历史/下载页的 TabRow+Pager 同范式）
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    // 滑动切页 → 同步筛选（页 0=作品 / 1=小说，与 VM 默认筛选一致）
    LaunchedEffect(pagerState.currentPage) {
        viewModel.setFilter(if (pagerState.currentPage == 0) "illust" else "novel")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.read_later_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        // 清空：Expressive 圆形 tonal 图标按钮（删除色），点击弹确认框
                        FilledTonalIconButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.history_clear),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
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
                // 类型分段：作品 / 小说（Expressive 二选一段控件，与追更页同语汇）；
                // 选中态跟随 Pager 落页，点击反向滚动 Pager（与历史/下载页 TabRow+Pager 同范式）
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                ) {
                    SegmentedButton(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.history_filter_illust)) },
                    )
                    SegmentedButton(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.history_filter_novel)) },
                    )
                }
                // 左右滑动切换：页 0=作品瀑布流 / 页 1=小说列表，各自持有滚动状态
                HorizontalPager(state = pagerState) { page ->
                    when (page) {
                        0 -> ReadLaterIllustList(
                            entries = items,
                            gson = gson,
                            onOpenIllust = onOpenIllust,
                            onOpenUser = onOpenUser,
                        )
                        else -> ReadLaterNovelList(
                            entries = items,
                            gson = gson,
                            context = context,
                            onOpenNovel = onOpenNovel,
                            onOpenUser = onOpenUser,
                        )
                    }
                }
            }
        }
    }

    // 清空稍后再看确认
    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.read_later_clear_title),
            message = stringResource(R.string.read_later_clear_message),
            confirmText = stringResource(R.string.history_clear),
            onConfirm = {
                viewModel.clearAll()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

/** 稍后再看·插画列表：payloadJson 还原 `Illust` + 瀑布流（含收藏按钮，无分页）。 */
@Composable
private fun ReadLaterIllustList(
    entries: List<ReadLaterEntity>,
    gson: Gson,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    // payloadJson 解析一次并随 entries 记忆（List<ReadLaterEntity> 不稳定，无 remember 时父级
    // 任何重组都会触发全量 Gson 反序列化）
    val illusts = remember(entries) { entries.map { it.toIllust(gson) } }
    if (illusts.isEmpty()) {
        ReadLaterEmpty(
            icon = Icons.Filled.Image,
            title = stringResource(R.string.read_later_empty_illust_title),
            hint = stringResource(R.string.read_later_empty_hint),
        )
        return
    }
    IllustWaterfallGrid(
        illusts = illusts,
        onItemClick = onOpenIllust,
        onLoadMore = {},
        hasMore = false,
        isLoadingMore = false,
        onOpenUser = onOpenUser,
    )
}

/** 稍后再看·小说列表：payloadJson 还原 `NovelCardData` + NovelCard 列表（无分页）。 */
@Composable
private fun ReadLaterNovelList(
    entries: List<ReadLaterEntity>,
    gson: Gson,
    context: Context,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    if (entries.isEmpty()) {
        ReadLaterEmpty(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = stringResource(R.string.read_later_empty_novel_title),
            hint = stringResource(R.string.read_later_empty_hint),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        items(entries, key = { it.id }) { entry ->
            val card = entry.toNovelCardData(gson, context)
            NovelCard(
                novel = card,
                onClick = { onOpenNovel(entry.targetId) },
                onOpenAuthor = { card.authorId.takeIf { it != 0L }?.let(onOpenUser) },
                onToggleFavorite = {},
                onTagClick = {},
            )
        }
    }
}

// ── 快照还原（与浏览历史同范式） ─────────────────────────────────────────────

/**
 * Expressive 空态：圆形图标底 + 标题 + 操作提示（稍后再看页专用）。
 *
 * @param icon 空态图标（插画=图片 / 小说=书本）
 * @param title 空态主文案
 * @param hint 操作提示副文案
 * @return 无返回值
 */
@Composable
private fun ReadLaterEmpty(
    icon: ImageVector,
    title: String,
    hint: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 图标：primary 8% 圆底 + primary 图标（与 ConfirmDialog 图标底同语汇）
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(AppShapes.circle)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/**
 * 快照还原插画：直解完整 `Illust`（Gson 往返安全：字段全为可空/基本类型，缺失落 JVM 默认值）；
 * 解析失败 / id 异常回退最小数据（标题 + 封面）。
 */
private fun ReadLaterEntity.toIllust(gson: Gson): Illust {
    val parsed = payloadJson?.let {
        runCatching { gson.fromJson(it, Illust::class.java) }.getOrNull()
    }
    if (parsed != null && parsed.id != 0L) return parsed
    return Illust(id = targetId, title = title, image_urls = ImageUrls(medium = coverUrl))
}

/**
 * 快照还原小说卡：逐字段重建（Gson UnsafeAllocator 会给缺失的非空字段塞 null，
 * 直接透传会在 NovelCard 渲染期 NPE——与浏览历史同款防御）；解析失败回退最小数据。
 */
private fun ReadLaterEntity.toNovelCardData(gson: Gson, context: Context): NovelCardData {
    val parsed = payloadJson?.let {
        runCatching { gson.fromJson(it, NovelCardData::class.java) }.getOrNull()
    }
    if (parsed != null && parsed.id != 0L) {
        return NovelCardData(
            id = parsed.id,
            title = parsed.title?.takeIf { it.isNotBlank() }
                ?: (title ?: context.getString(R.string.untitled)),
            coverUrl = parsed.coverUrl ?: coverUrl,
            authorId = parsed.authorId,
            authorName = parsed.authorName.orEmpty(),
            authorAvatarUrl = parsed.authorAvatarUrl,
            publishDate = parsed.publishDate,
            seriesTitle = parsed.seriesTitle,
            seriesId = parsed.seriesId,
            favoriteCount = parsed.favoriteCount,
            wordCount = parsed.wordCount,
            tags = parsed.tags.orEmpty(),
            isFavorite = parsed.isFavorite,
        )
    }
    return NovelCardData(
        id = targetId,
        title = title ?: context.getString(R.string.untitled),
        coverUrl = coverUrl,
        authorId = 0,
        authorName = "",
        authorAvatarUrl = null,
        publishDate = null,
        seriesTitle = null,
        seriesId = null,
        favoriteCount = 0,
        wordCount = 0,
    )
}
