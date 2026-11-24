package com.pixiv.reader.core.ui.component.detail

import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.format.formatCount
import com.pixiv.reader.core.network.model.IllustPageInfo
import com.pixiv.reader.core.network.ugoira.UgoiraState
import com.pixiv.reader.core.ui.component.card.TagChip
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.PixivHaptics
import com.pixiv.reader.core.ui.component.feedback.rememberHapticCue
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.image.UgoiraPlayer
import com.pixiv.reader.core.ui.component.input.VerticalActionButton
import com.pixiv.reader.core.ui.component.text.HtmlCaptionText
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.ImageScrim
import com.pixiv.reader.core.ui.theme.SettingAccentAmber
import com.pixiv.reader.core.ui.theme.SettingAccentBlue
import com.pixiv.reader.core.ui.theme.SettingAccentPink
import com.pixiv.reader.core.ui.theme.SettingAccentTeal
import com.pixiv.reader.core.ui.theme.SettingAccentViolet
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.core.ui.theme.extendedColors
import com.pixiv.reader.core.ui.theme.resolveAccent

/** core:ui 不依赖 feature 资源，详情文案由调用方经此注入。 */
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
 * 传入 [seed] 时即使 [pages] 为空也先渲染 pager 占位，确保 hero 共享元素自进入详情起由单一容器独占；
 * 需同时传 [heroIllustId] 在 [illust] 到达前锚定共享元素 key。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IllustDetailContent(
    illust: Illust?,
    pages: List<IllustPageInfo>,
    ugoiraState: UgoiraState,
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
    onOpenNovel: (Long) -> Unit = {},
    seed: HeroSeed? = null,
    heroIllustId: Long? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 持有 seed 时即渲染 pager，保证 hero 元素全程由同一容器独占
        if (pages.isNotEmpty() || seed != null) {
            IllustPagePager(
                pages = pages,
                ugoiraState = ugoiraState,
                onPageChange = onPageChange,
                onOpenViewer = onOpenViewer,
                strings = strings,
                illustId = illust?.id ?: heroIllustId,
                placeholderUrl = illust?.image_urls?.medium,
                // 用 illust 宽高兜底首页比例，避免 pages 补齐尺寸前落到 1.5f 导致接棒跳变
                fallbackRatio = illust
                    ?.takeIf { it.width > 0 && it.height > 0 }
                    ?.let { it.width.toFloat() / it.height.toFloat() },
                seed = seed,
            )
        }
        if (illust == null) {
            IllustInfoSkeleton()
        } else {
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
        }
        IllustRelatedSection(
            items = relatedItems,
            strings = strings,
            onOpenIllust = onOpenIllust,
        )
        // 兼容直通导航栏的平板 pane；上层已消费 inset 时（手机）高度为 0
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * 多 P 大图 Pager：仅换页时用弹簧吸附高度，同页比例修正（原图就绪/hero 接棒）直接落地以防二次跳动；
 * 占位态（`pages` 为空但有 [seed]）在同一 hero 容器内渲染种子封面，避免 loading/content 互斥子树重播 morph 动画。
 */
private sealed interface PageImageState {
    data object Loading : PageImageState

    data class Loaded(val ratio: Float?) : PageImageState

    data object Failed : PageImageState
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IllustPagePager(
    pages: List<IllustPageInfo>,
    onPageChange: (Int) -> Unit,
    onOpenViewer: (Int) -> Unit,
    ugoiraState: UgoiraState = UgoiraState.Idle,
    strings: IllustDetailStrings,
    illustId: Long? = null,
    placeholderUrl: String? = null,
    fallbackRatio: Float? = null,
    seed: HeroSeed? = null,
) {
    val placeholderOnly = pages.isEmpty() && seed != null
    val placeholderRatio = seed?.aspectRatio ?: fallbackRatio ?: 1.5f
    val pagerState = rememberPagerState(
        pageCount = { if (pages.isEmpty() && seed != null) 1 else pages.size },
    )
    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) { onPageChange(currentPage) }

    val current = pages.getOrNull(currentPage)
    // 必须以 URL 为 key 同步重置状态，不可用 LaunchedEffect：Coil 内存命中走 Main.immediate 同步触发
    // onSuccess，若后续被 LaunchedEffect 覆盖回 Loading 会导致预加载页转圈永挂
    var imageState by remember(current?.displayUrl) { mutableStateOf<PageImageState>(PageImageState.Loading) }
    var retryKey by remember(current?.displayUrl) { mutableIntStateOf(0) }
    fun retry() {
        imageState = PageImageState.Loading
        retryKey++
    }

    val imageRatio = (imageState as? PageImageState.Loaded)?.ratio
    val webRatio = current
        ?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { it.width.toFloat() / it.height.toFloat() }
    val effectiveRatio = if (placeholderOnly) {
        placeholderRatio
    } else {
        imageRatio ?: webRatio ?: fallbackRatio ?: 1.5f
    }
    // fallbackRatio 仅为估值不代表图片就绪，不计入 ready 以免首帧误隐藏转圈
    val ready = imageRatio != null || webRatio != null
    // 图片未就绪前用 surface 底色避免露黑块，就绪后切黑底压 letterbox 黑帧
    val containerColor = if (placeholderOnly || (!ready && imageState is PageImageState.Loading)) {
        MaterialTheme.colorScheme.surface
    } else {
        Color.Black
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor),
    ) {
        val targetHeight = if (effectiveRatio > 0f) maxWidth / effectiveRatio else 400.dp
        // LaunchedEffect 非组合上下文，须在组合期提前捕获 motionScheme
        val heightSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
        val heightAnim = remember { Animatable(targetHeight, Dp.VectorConverter) }
        var heightSettled by remember { mutableStateOf(false) }
        var lastPageForHeight by remember { mutableIntStateOf(currentPage) }
        LaunchedEffect(currentPage, targetHeight) {
            val firstComposition = !heightSettled
            val pageChanged = !firstComposition && lastPageForHeight != currentPage
            // 先落标记再启动动画，防止动画被打断重入时重复判定为换页
            heightSettled = true
            lastPageForHeight = currentPage
            when {
                firstComposition -> heightAnim.snapTo(targetHeight)
                pageChanged -> heightAnim.animateTo(targetHeight, heightSpec)
                else -> heightAnim.snapTo(targetHeight)
            }
        }
        val pagerMaxWidth = maxWidth
        val heroModifier = if (illustId != null) {
            Modifier.heroBoundsModifier(illustCoverKey(illustId))
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightAnim.value)
                .then(heroModifier),
        ) {
            // 占位态 pages 为空，跳过 HorizontalPager 防索引越界，在同一 hero 容器内直接展示种子
            if (placeholderOnly) {
                val seedCoverUrl = seed.coverUrl.ifBlank { null }
                PixivImage(
                    url = seedCoverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    val p = pages[index]
                    if (index == currentPage) {
                        Box {
                            // 原图就绪前用已缓存 medium 垫底防接棒空窗；null 时跳过，避免 PixivImage 占位色块盖掉黑底
                            if (placeholderUrl != null) {
                                PixivImage(
                                    url = placeholderUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            // 自增 retryKey 强制重建 AsyncImage 重新发起请求
                            key(retryKey) {
                                AsyncImage(
                                    model = p.displayUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                    onSuccess = { res ->
                                        val d = res.result.drawable
                                        val ratio = if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                                            d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()
                                        } else {
                                            null
                                        }
                                        imageState = PageImageState.Loaded(ratio)
                                    },
                                    onError = {
                                        imageState = PageImageState.Failed
                                    },
                                )
                            }
                            // 动图封面失败不拦截播放，帧就绪后照常覆盖显示
                            if (imageState is PageImageState.Failed && ugoiraState !is UgoiraState.Ready) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(ImageScrim)
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
                            if (index == 0) {
                                when (ugoiraState) {
                                    is UgoiraState.Ready -> {
                                        val maxDecodeSize =
                                            with(LocalDensity.current) { pagerMaxWidth.roundToPx() }
                                        UgoiraPlayer(
                                            frames = ugoiraState.frames,
                                            modifier = Modifier.fillMaxSize(),
                                            maxDecodeSize = maxDecodeSize,
                                            contentScale = ContentScale.Fit,
                                        )
                                    }

                                    is UgoiraState.Loading -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(ImageScrim),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                            ) {
                                                LoadingIndicator(color = Color.White)
                                                Text(
                                                    text = "${(ugoiraState.progress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White,
                                                )
                                            }
                                        }
                                    }

                                    UgoiraState.Idle, UgoiraState.Failed -> Unit
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
            }
            if (!placeholderOnly) {
                if (!ready && imageState is PageImageState.Loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator(color = Color.White)
                    }
                }
                // 外层 48dp 隐形热区满足 M3 最小触控目标，内层保持 32dp 视觉按钮
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(start = Spacing.sm, end = 0.dp, bottom = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .height(Sizes.s32)
                            .clip(AppShapes.card)
                            .background(ImageScrim)
                            .padding(horizontal = Spacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${pages.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                    val fullscreenInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(Sizes.s48)
                            .clickable(
                                role = Role.Button,
                                interactionSource = fullscreenInteraction,
                                indication = null,
                            ) { onOpenViewer(pagerState.currentPage) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(Sizes.s32)
                                .clip(AppShapes.card)
                                .background(ImageScrim)
                                .indication(fullscreenInteraction, ripple(bounded = true, color = Color.White)),
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
                modifier = Modifier.weight(1f),
            )
            AuthorFollowPill(
                isFollowed = isAuthorFollowed,
                enabled = !isAuthorFollowing,
                onClick = onToggleFollowAuthor,
                strings = strings,
            )
        }
        Row(modifier = Modifier.padding(top = Spacing.lg)) {
            StatBlock(
                icon = Icons.Filled.Visibility,
                value = formatCount((illust.total_view ?: 0).toLong()),
                label = strings.statView,
                modifier = Modifier.weight(1f),
                iconAccent = SettingAccentBlue,
            )
            StatBlock(
                icon = Icons.Filled.Favorite,
                value = formatCount((illust.total_bookmarks ?: 0).toLong()),
                label = strings.statBookmark,
                modifier = Modifier.weight(1f),
                iconAccent = SettingAccentPink,
            )
            StatBlock(
                icon = Icons.Filled.Collections,
                value = "${illust.page_count}P",
                label = strings.statPages,
                modifier = Modifier.weight(1f),
                iconAccent = SettingAccentAmber,
            )
        }
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
                        TagChip(
                            text = "#$tagName",
                            onClick = { onSearchTag(tagName) },
                        )
                    }
                }
            }
        }
        if (showActionRow) {
            ActionButtonsRow(
                illustId = illust.id,
                isBookmarked = isBookmarked,
                isBookmarking = isBookmarking,
                onToggleBookmark = onToggleBookmark,
                onOpenViewer = { onOpenViewer(0) },
                onDownload = onDownload,
                onOpenComments = onOpenComments,
                strings = strings,
            )
        }
        val caption = illust.caption
        if (!caption.isNullOrBlank()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            var truncated by remember { mutableStateOf(false) }
            val clamped = expandableIntro && !expanded
            HtmlCaptionText(
                html = caption,
                style = MaterialTheme.typography.bodyMedium.copy(textIndent = TextIndent(firstLine = 2.em)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (clamped) 6 else Int.MAX_VALUE,
                overflow = if (clamped) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = Modifier
                    .padding(top = Spacing.mdPlus)
                    .animateContentSize(),
                onTextLayout = { layout: TextLayoutResult ->
                    // 仅截断态检测溢出（展开后 maxLines 为无限，hasVisualOverflow 恒为 false）
                    if (clamped) truncated = layout.hasVisualOverflow
                },
                onOpenIllust = onOpenIllust,
                onOpenNovel = onOpenNovel,
                onOpenUser = onOpenUser,
            )
            if (expandableIntro && (truncated || expanded)) {
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

@Composable
private fun ActionButtonsRow(
    illustId: Long,
    isBookmarked: Boolean,
    isBookmarking: Boolean,
    onToggleBookmark: () -> Unit,
    onOpenViewer: () -> Unit,
    onDownload: () -> Unit,
    onOpenComments: () -> Unit,
    strings: IllustDetailStrings,
) {
    val favoriteBounce = remember { Animatable(1f) }
    // key 为 illustId：平板 pane 切换作品不重建组合，须重置上一次收藏态防跨作品误播弹跳与触觉
    var wasBookmarked by remember(illustId) { mutableStateOf(isBookmarked) }
    val haptic = rememberHapticCue()
    LaunchedEffect(isBookmarked) {
        val becameBookmarked = isBookmarked && !wasBookmarked
        wasBookmarked = isBookmarked
        if (becameBookmarked) {
            haptic(PixivHaptics.HapticCue.SUCCESS)
            favoriteBounce.snapTo(0.6f)
            favoriteBounce.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }
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
            activeIconTint = MaterialTheme.extendedColors.favorite,
            accent = MaterialTheme.extendedColors.favorite,
            iconModifier = Modifier.graphicsLayer {
                scaleX = favoriteBounce.value
                scaleY = favoriteBounce.value
            },
        )
        VerticalActionButton(
            icon = Icons.Filled.Fullscreen,
            label = strings.fullscreen,
            active = false,
            enabled = true,
            onClick = onOpenViewer,
            modifier = Modifier.weight(1f),
            accent = SettingAccentViolet,
        )
        VerticalActionButton(
            icon = Icons.Filled.Download,
            label = strings.download,
            active = false,
            enabled = true,
            onClick = onDownload,
            modifier = Modifier.weight(1f),
            accent = SettingAccentBlue,
        )
        VerticalActionButton(
            icon = Icons.Filled.ModeComment,
            label = strings.comments,
            active = false,
            enabled = true,
            onClick = onOpenComments,
            modifier = Modifier.weight(1f),
            accent = SettingAccentTeal,
        )
    }
}

@Composable
private fun StatBlock(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    iconAccent: Color = Color.Unspecified,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (iconAccent == Color.Unspecified) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    resolveAccent(iconAccent)
                },
                modifier = Modifier.size(Sizes.s20),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.xsPlus),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun AuthorFollowPill(
    isFollowed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    strings: IllustDetailStrings,
) {
    if (isFollowed) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = AppShapes.pill,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            Text(
                text = strings.followed,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = AppShapes.pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            Text(
                text = strings.follow,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun IllustRelatedSection(
    items: List<Illust>,
    strings: IllustDetailStrings,
    onOpenIllust: (Long) -> Unit,
) {
    // item lambda 非组合上下文，须在组合期提前捕获 motionScheme
    val motionScheme = MaterialTheme.motionScheme
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
                        .animateItem(
                            fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                            placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                        )
                        .clip(AppShapes.cardSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onOpenIllust(related.id) },
                ) {
                    PixivImage(
                        url = related.image_urls?.medium ?: related.image_urls?.square_medium,
                        contentDescription = related.title,
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
