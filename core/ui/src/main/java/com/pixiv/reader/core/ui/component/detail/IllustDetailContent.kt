package com.pixiv.reader.core.ui.component.detail

import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.format.formatCount
import com.pixiv.reader.core.network.model.IllustPageInfo
import com.pixiv.reader.core.network.ugoira.UgoiraFrame
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.image.UgoiraPlayer
import com.pixiv.reader.core.ui.component.image.ZoomableImage
import com.pixiv.reader.core.ui.component.input.VerticalActionButton
import com.pixiv.reader.core.ui.component.text.HtmlCaptionText
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.FavoriteRed
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.ViewerScrim
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 插画详情渲染块（core:ui 下沉，供详情路由与排行榜右栏共用）。
 *
 * 说明：core:ui 不依赖 feature 层，因此文案不直接取 R.string，由调用方经 [strings] 传入
 * （详情路由传 feature:illust 文案，排行榜传 feature:manga 文案）。
 */
data class IllustDetailStrings(
    val loadRetry: String,
    val fullscreen: String,
    val statView: String,
    val statBookmark: String,
    val statPages: String,
    val expand: String,
    val collapse: String,
    val follow: String,
    val followed: String,
    val related: String,
    val bookmark: String,
    val bookmarked: String,
    val download: String,
    val comments: String,
)

/**
 * 详情内容（可滚动整页）：多 P Pager + 信息区（标题/作者/统计/标签/操作行/简介）+ 相关作品。
 * 操作按钮行放在标签之后、简介之前（排行榜右栏布局要求；详情页手机端仍走固定底栏）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IllustDetailContent(
    illust: Illust?,
    pages: List<IllustPageInfo>,
    ugoiraFrames: List<UgoiraFrame>,
    ugoiraProgress: Float?,
    relatedItems: List<Illust>,
    strings: IllustDetailStrings,
    onPageChange: (Int) -> Unit,
    onOpenViewer: (Int) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenIllust: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    isAuthorFollowed: Boolean,
    isAuthorFollowing: Boolean,
    onToggleFollowAuthor: () -> Unit,
    isBookmarked: Boolean,
    isBookmarking: Boolean,
    onToggleBookmark: () -> Unit,
    onDownload: () -> Unit,
    onOpenComments: () -> Unit,
    expandableIntro: Boolean = true,
    showActionRow: Boolean = true,
    modifier: Modifier = Modifier,
    // 简介内 `pixiv://novels/{id}` 链接回调（默认不接线，样式仍显示）
    onOpenNovel: (Long) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (pages.isNotEmpty()) {
            IllustPagePager(
                pages = pages,
                ugoiraFrames = ugoiraFrames,
                ugoiraProgress = ugoiraProgress,
                onPageChange = onPageChange,
                onOpenViewer = onOpenViewer,
                strings = strings,
            )
        }
        IllustInfoSection(
            illust = illust,
            strings = strings,
            onOpenUser = onOpenUser,
            onOpenIllust = onOpenIllust,
            onSearchTag = onSearchTag,
            onOpenViewer = onOpenViewer,
            isAuthorFollowed = isAuthorFollowed,
            isAuthorFollowing = isAuthorFollowing,
            onToggleFollowAuthor = onToggleFollowAuthor,
            isBookmarked = isBookmarked,
            isBookmarking = isBookmarking,
            onToggleBookmark = onToggleBookmark,
            onDownload = onDownload,
            onOpenComments = onOpenComments,
            expandableIntro = expandableIntro,
            showActionRow = showActionRow,
            onOpenNovel = onOpenNovel,
        )
        IllustRelatedSection(
            items = relatedItems,
            strings = strings,
            onOpenIllust = onOpenIllust,
        )
    }
}

/**
 * 多 P Pager：容器高度按「图片真实宽高比」自适应（无黑边、无闪变）。
 * 用 AsyncImage 的 onSuccess 拿到 drawable 真实尺寸计算比例；
 * spinner 仅在真正加载中显示，加载完成或失败即隐藏。
 *
 * @param ugoiraFrames 动图帧（ugoira 作品由 ViewModel 加载）；非空时首页（动图作品仅 1 页）
 *   叠加 [UgoiraPlayer] 播放动画——静态封面 AsyncImage 保留（量比例 + 帧未就绪兜底显示）
 * @param ugoiraProgress 动图 zip 下载进度 0..1；非 null 且帧未就绪时首页叠加转圈 + 百分比
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun IllustPagePager(
    pages: List<IllustPageInfo>,
    onPageChange: (Int) -> Unit,
    onOpenViewer: (Int) -> Unit,
    ugoiraFrames: List<UgoiraFrame> = emptyList(),
    ugoiraProgress: Float? = null,
    strings: IllustDetailStrings,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) { onPageChange(currentPage) }

    val current = pages.getOrNull(currentPage)
    // 图片加载完成后的真实宽高比（drawable.intrinsic）。
    // 以 URL 为 remember key：切页时组合期同步重置——必须先于同组合内 AsyncImage 的回调。
    // 不能用 LaunchedEffect 重置：Coil 内存命中走 Main.immediate 同步 fast path，
    // onSuccess 会在组合期间先触发、随后被 LaunchedEffect 覆盖回 false，且不再有第二次回调，
    // 导致「图片已显示但转圈永挂」（预加载完成的页必现）。
    var imageRatio by remember(current?.displayUrl) { mutableStateOf<Float?>(null) }
    // 当前页加载完成或失败（用于隐藏 spinner）
    var loadDone by remember(current?.displayUrl) { mutableStateOf(false) }
    // 加载失败标记（显示手动重试覆盖层）+ 重试代次（自增强制重建请求绕过失败状态）
    var loadFailed by remember(current?.displayUrl) { mutableStateOf(false) }
    var retryKey by remember(current?.displayUrl) { mutableIntStateOf(0) }
    fun retry() {
        loadFailed = false
        loadDone = false
        imageRatio = null
        retryKey++
    }

    // 优先图片真实比例，其次网页宽高，最后兜底
    val webRatio = current
        ?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { it.width.toFloat() / it.height.toFloat() }
    val effectiveRatio = imageRatio ?: webRatio ?: 1.5f
    val ready = imageRatio != null || webRatio != null

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black),
    ) {
        val targetHeight = if (effectiveRatio > 0f) maxWidth / effectiveRatio else 400.dp
        val pagerHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = tween(durationMillis = 250),
            label = "pagerHeight",
        )
        // 嵌套 lambda（pager item）无法隐式访问 BoxWithConstraints receiver：提前捕获
        val pagerMaxWidth = maxWidth

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pagerHeight),
        ) {
            HorizontalPager(
                state = pagerState,
                // 预组合相邻页：翻页前邻图已在加载，滑动即显
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                val p = pages[index]
                if (index == currentPage) {
                    Box {
                        // key(retryKey)：手动重试时自增，强制重建 painter 重新发起请求
                        key(retryKey) {
                            AsyncImage(
                                model = p.displayUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                onSuccess = { res ->
                                    // Coil 2.7：State.Success.result 是 SuccessResult，含 drawable
                                    val d = res.result.drawable
                                    if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                                        imageRatio =
                                            d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()
                                    }
                                    loadDone = true
                                    loadFailed = false
                                },
                                onError = {
                                    loadDone = true
                                    loadFailed = true
                                },
                            )
                        }
                        // 加载失败：点击手动重试（动图封面失败不拦截播放，帧就绪照常播）
                        if (loadFailed && ugoiraFrames.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable { retry() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = strings.loadRetry,
                                        tint = Color.White,
                                        modifier = Modifier.size(Sizes.s28),
                                    )
                                    Text(
                                        text = strings.loadRetry,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(top = Spacing.xsPlus),
                                    )
                                }
                            }
                        }
                        // 动图：帧就绪播放 zip 帧动画；下载中显示转圈 + 百分比；均叠加静态封面之上
                        if (index == 0) {
                            when {
                                ugoiraFrames.isNotEmpty() -> {
                                    val maxDecodeSize =
                                        with(LocalDensity.current) { pagerMaxWidth.roundToPx() }
                                    UgoiraPlayer(
                                        frames = ugoiraFrames,
                                        modifier = Modifier.fillMaxSize(),
                                        maxDecodeSize = maxDecodeSize,
                                        contentScale = ContentScale.Fit,
                                    )
                                }

                                ugoiraProgress != null -> {
                                    // zip 下载中：半透明黑底 + 转圈 + 百分比（帧就绪后切换播放）
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.35f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                        ) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(Sizes.s32),
                                            )
                                            Text(
                                                text = "${(ugoiraProgress * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    PixivImage(
                        url = p.displayUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            if (!ready && !loadDone) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }
            }
            // 页码 + 全屏入口（统一 32dp 高胶囊，视觉一致）
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.smPlus),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(AppShapes.card)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = Spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${pages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(Sizes.s32)
                        .clip(AppShapes.card)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { onOpenViewer(pagerState.currentPage) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fullscreen,
                        contentDescription = strings.fullscreen,
                        tint = Color.White,
                        modifier = Modifier.size(Sizes.s18),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IllustInfoSection(
    illust: Illust?,
    strings: IllustDetailStrings,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenViewer: (Int) -> Unit,
    isAuthorFollowed: Boolean,
    isAuthorFollowing: Boolean,
    onToggleFollowAuthor: () -> Unit,
    isBookmarked: Boolean,
    isBookmarking: Boolean,
    onToggleBookmark: () -> Unit,
    onDownload: () -> Unit,
    onOpenComments: () -> Unit,
    expandableIntro: Boolean,
    showActionRow: Boolean = true,
    // 简介富文本链接回调（pixiv://illusts / novels / users）
    onOpenIllust: (Long) -> Unit = {},
    onOpenNovel: (Long) -> Unit = {},
) {
    if (illust == null) return
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 18.dp)) {
        Text(
            text = illust.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        // 作者行：头像 + 昵称（整行可点击进主页）› 关注/取关胶囊顶到行最右
        val authorId = illust.user?.id
        Row(
            modifier = Modifier
                .padding(top = Spacing.mdPlus)
                .clip(AppShapes.cardSmall)
                .clickable(enabled = authorId != null) { authorId?.let(onOpenUser) }
                .padding(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus),
        ) {
            UserAvatar(
                name = illust.user?.name,
                avatarUrl = illust.user?.profile_image_urls?.best(),
                modifier = Modifier.size(Sizes.s40),
            )
            Text(
                text = illust.user?.name.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 占满剩余宽度：weight 槽占满 → 关注胶囊被推到行最右
                modifier = Modifier.weight(1f),
            )
            AuthorFollowPill(
                isFollowed = isAuthorFollowed,
                enabled = !isAuthorFollowing,
                onClick = onToggleFollowAuthor,
                strings = strings,
            )
        }
        // 统计行：浏览 / 收藏 / 页数 均分（纯文字，无卡片背景）
        Row(modifier = Modifier.padding(top = Spacing.lg)) {
            StatBlock(
                icon = Icons.Filled.Visibility,
                value = formatCount((illust.total_view ?: 0).toLong()),
                label = strings.statView,
                modifier = Modifier.weight(1f),
            )
            StatBlock(
                icon = Icons.Filled.Favorite,
                value = formatCount((illust.total_bookmarks ?: 0).toLong()),
                label = strings.statBookmark,
                modifier = Modifier.weight(1f),
            )
            StatBlock(
                icon = Icons.Filled.Collections,
                value = "${illust.page_count}P",
                label = strings.statPages,
                modifier = Modifier.weight(1f),
            )
        }
        // 标签：可点击跳搜索该 tag
        val tags = illust.tags.orEmpty().take(8)
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = Spacing.mdPlus),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                verticalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
            ) {
                tags.forEach { tag ->
                    val tagName = tag.displayName ?: tag.name.orEmpty()
                    if (tagName.isNotBlank()) {
                        Text(
                            text = "#$tagName",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(AppShapes.card)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { onSearchTag(tagName) }
                                .padding(horizontal = Spacing.smPlus, vertical = Spacing.xs),
                        )
                    }
                }
            }
        }
        // 操作按钮行（标签之后、简介之前）：收藏 / 全屏 / 下载 / 评论。
        // 手机详情页走固定底栏（showActionRow=false），排行右栏等内嵌布局用此行进操作。
        if (showActionRow) {
            ActionButtonsRow(
                isBookmarked = isBookmarked,
                isBookmarking = isBookmarking,
                onToggleBookmark = onToggleBookmark,
                onOpenViewer = { onOpenViewer(0) },
                onDownload = onDownload,
                onOpenComments = onOpenComments,
                strings = strings,
            )
        }
        // 简介：HTML 富文本（加粗/换行/pixiv 深链可点），首行缩进两格；
        // 手机 6 行截断 + 展开/收起（短简介不显示按钮），平板完整显示
        val caption = illust.caption
        if (!caption.isNullOrBlank()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            var truncated by remember { mutableStateOf(false) }
            val clamped = expandableIntro && !expanded
            HtmlCaptionText(
                html = caption,
                // 首行缩进两格放 style（Text 无 textIndent 参数）
                style = MaterialTheme.typography.bodyMedium.copy(textIndent = TextIndent(firstLine = 2.em)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (clamped) 6 else Int.MAX_VALUE,
                overflow = if (clamped) TextOverflow.Ellipsis else TextOverflow.Clip,
                // 展开/收起时高度平滑过渡
                modifier = Modifier
                    .padding(top = Spacing.mdPlus)
                    .animateContentSize(),
                onTextLayout = { layout: TextLayoutResult ->
                    // 仅在 clamped 时检测是否真的溢出（展开后 maxLines 无限，溢出恒 false）
                    if (clamped) truncated = layout.hasVisualOverflow
                },
                onOpenIllust = onOpenIllust,
                onOpenNovel = onOpenNovel,
                onOpenUser = onOpenUser,
            )
            if (expandableIntro && (truncated || expanded)) {
                // 展开 / 收起：居中按钮（未截断时不出现）
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (expanded) strings.collapse else strings.expand,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 操作按钮行（标签下方）：收藏 / 全屏 / 下载 / 评论（不固定，跟随内容滚动）。 */
@Composable
private fun ActionButtonsRow(
    isBookmarked: Boolean,
    isBookmarking: Boolean,
    onToggleBookmark: () -> Unit,
    onOpenViewer: () -> Unit,
    onDownload: () -> Unit,
    onOpenComments: () -> Unit,
    strings: IllustDetailStrings,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        VerticalActionButton(
            icon = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            label = if (isBookmarked) strings.bookmarked else strings.bookmark,
            active = isBookmarked,
            enabled = !isBookmarking,
            onClick = onToggleBookmark,
            modifier = Modifier.weight(1f),
            // 收藏激活用红心（保留 App 收藏色习惯）
            activeIconTint = FavoriteRed,
        )
        VerticalActionButton(
            icon = Icons.Filled.Fullscreen,
            label = strings.fullscreen,
            active = false,
            enabled = true,
            onClick = onOpenViewer,
            modifier = Modifier.weight(1f),
        )
        VerticalActionButton(
            icon = Icons.Filled.Download,
            label = strings.download,
            active = false,
            enabled = true,
            onClick = onDownload,
            modifier = Modifier.weight(1f),
        )
        VerticalActionButton(
            icon = Icons.Filled.ModeComment,
            label = strings.comments,
            active = false,
            enabled = true,
            onClick = onOpenComments,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 统计块：图标 + 数值 + 标签（weight(1f) 均分整行，块内水平居中）。 */
@Composable
private fun StatBlock(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.s16),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
}

/** 作者关注 / 取关胶囊（作者行顶右）：未关注 = 实心主色胶囊，已关注 = 浅底 + 主色边框。 */
@Composable
private fun AuthorFollowPill(
    isFollowed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    strings: IllustDetailStrings,
) {
    val container = if (isFollowed) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val content = if (isFollowed) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    Text(
        text = if (isFollowed) strings.followed else strings.follow,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = content,
        maxLines = 1,
        modifier = Modifier
            .clip(AppShapes.pill)
            .border(
                width = 1.dp,
                color = if (isFollowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                shape = AppShapes.pill,
            )
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 5.dp),
    )
}

// ── 相关作品 ──

@Composable
fun IllustRelatedSection(
    items: List<Illust>,
    strings: IllustDetailStrings,
    onOpenIllust: (Long) -> Unit,
) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
        Text(
            strings.related,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus)) {
            items(items, key = { it.id }) { related ->
                Column(
                    modifier = Modifier
                        .width(150.dp)
                        .clip(AppShapes.cardSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onOpenIllust(related.id) },
                ) {
                    PixivImage(
                        url = related.image_urls?.medium ?: related.image_urls?.square_medium,
                        contentDescription = related.title,
                        // 固定 4:3 比例（非正方形、非真实宽高）
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        text = related.title.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(Spacing.xsPlus),
                    )
                }
            }
        }
    }
}
