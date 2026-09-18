package com.pixiv.reader.feature.comic.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pixiv.api.model.ComicEpisode
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.NotificationHostState
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.comic.R
import com.pixiv.reader.feature.comic.state.ComicPageUi
import com.pixiv.reader.feature.comic.state.ComicReaderUiState
import com.pixiv.reader.feature.comic.state.ComicReaderViewModel

/**
 * COMIC 阅读器全屏路由：沉浸式竖向连续滚动（条漫），点击画面切换顶栏；
 * 页级失败可单页重试，末尾提供「下一话」续读入口。
 *
 * @param onBack 返回回调（上层 safeBack）
 * @param onOpenNext 续读下一话（上层以替换栈方式导航，避免阅读器无限压栈）
 * @param viewModel Hilt 注入的阅读器 VM
 * @return 无返回值（组合输出 UI）
 */
@Composable
fun ComicReaderRoute(
    onBack: () -> Unit,
    onOpenNext: (Long) -> Unit,
    viewModel: ComicReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hostState = rememberNotificationHostState()

    // 沉浸式：进入隐藏系统栏（边缘滑动临时呼出），离开页面时恢复
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? ComponentActivity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    UiMessageEffect(viewModel.message, hostState)

    ComicReaderContent(
        state = state,
        hostState = hostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onRetryPage = viewModel::retryPage,
        onOpenNext = onOpenNext,
    )
}

/**
 * 阅读器内容（黑底容器 + 三态）。
 *
 * @param state 阅读器状态流
 * @param hostState 通知宿主状态（加载失败消息）
 * @param onBack 返回回调
 * @param onRetry 整章重试
 * @param onRetryPage 单页重试
 * @param onOpenNext 续读下一话
 * @return 无返回值
 */
@Composable
internal fun ComicReaderContent(
    state: ComicReaderUiState,
    hostState: NotificationHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryPage: (Int) -> Unit,
    onOpenNext: (Long) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(Modifier.fillMaxSize()) {
            when {
                state.isLoading -> LoadingBox()
                state.error != null || state.episode == null -> ErrorBox(
                    // 异常 message 面向用户不可读时回退通用文案（ErrorBox 内部空值兜底）
                    message = state.error,
                    onRetry = onRetry,
                )
                else -> ComicReaderPager(
                    state = state,
                    onBack = onBack,
                    onRetryPage = onRetryPage,
                    onOpenNext = onOpenNext,
                )
            }
            NotificationHost(hostState)
        }
    }
}

/**
 * 沉浸式滚动主体：LazyColumn 竖向连续滚动 + 点击切换顶栏 + 页码指示。
 *
 * @param state 已就绪的阅读器状态
 * @param onBack 返回回调
 * @param onRetryPage 单页重试
 * @param onOpenNext 续读下一话
 * @return 无返回值
 */
@Composable
private fun ComicReaderPager(
    state: ComicReaderUiState,
    onBack: () -> Unit,
    onRetryPage: (Int) -> Unit,
    onOpenNext: (Long) -> Unit,
) {
    val episode = state.episode ?: return
    val listState = rememberLazyListState()
    var chromeVisible by remember { mutableStateOf(true) }
    // 当前页码 = 最近完整可见项（供顶栏指示器）
    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceAtMost(state.pages.size - 1) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 无涟漪点击切顶栏（indication=null 避免全屏水波纹干扰阅读）
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { chromeVisible = !chromeVisible },
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // 元数据宽高在拉取时已确定，占位即按真实比例渲染，滚动无跳动
            items(count = state.pages.size, key = { it }) { index ->
                val meta = episode.pages.orEmpty().getOrNull(index)
                ComicReaderPageItem(
                    ui = state.pages[index],
                    width = meta?.width ?: 3,
                    height = meta?.height ?: 4,
                    index = index,
                    onRetryPage = onRetryPage,
                )
            }
            item(key = "comic_reader_end") {
                ComicReaderEndSection(episode.nextEpisode, onOpenNext)
            }
        }

        // 悬浮顶栏：返回 + 标题 + 页码指示（点击画面切换显隐）
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.comic_cd_back),
                        tint = Color.White,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = episode.workTitle.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                    )
                    Text(
                        text = episode.title.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.comic_reader_page_indicator,
                        currentPage + 1,
                        state.pages.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(end = Spacing.lg),
                )
            }
        }
    }
}

/**
 * 单页渲染：等比占位 + 状态覆盖（就绪文件 / 失败重试；加载中纯黑占位）。
 *
 * @param ui 页状态
 * @param width 元数据宽（占位比例用）
 * @param height 元数据高
 * @param index 页序号（重试回传）
 * @param onRetryPage 单页重试回调
 * @return 无返回值
 */
@Composable
private fun ComicReaderPageItem(
    ui: ComicPageUi,
    width: Int,
    height: Int,
    index: Int,
    onRetryPage: (Int) -> Unit,
) {
    val ratio = if (width > 0 && height > 0) width.toFloat() / height else 3f / 4f
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (val page = ui) {
            is ComicPageUi.Ready -> AsyncImage(
                model = page.file,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth,
            )
            is ComicPageUi.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.comic_reader_page_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Button(
                    onClick = { onRetryPage(index) },
                    modifier = Modifier.padding(top = Spacing.md),
                ) {
                    Text(stringResource(R.string.comic_reader_page_retry))
                }
            }
            ComicPageUi.Loading -> Unit
        }
    }
}

/**
 * 末尾续读区：下一话可读显示入口按钮，否则显示「已是最新一话」。
 *
 * @param nextEpisode 下一话（无则只显示结束文案）
 * @param onOpenNext 续读回调
 * @return 无返回值
 */
@Composable
private fun ComicReaderEndSection(
    nextEpisode: ComicEpisode?,
    onOpenNext: (Long) -> Unit,
) {
    val readable = nextEpisode?.isReadable == true
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (readable && nextEpisode != null) {
            val label = stringResource(
                R.string.comic_reader_next_episode,
                nextEpisode.numberingTitle.orEmpty(),
            )
            Button(onClick = { onOpenNext(nextEpisode.id) }) { Text(label) }
        } else {
            Text(
                text = stringResource(R.string.comic_reader_ended),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}
