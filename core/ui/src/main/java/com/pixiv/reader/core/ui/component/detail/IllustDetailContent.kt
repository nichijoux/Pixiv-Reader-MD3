package com.pixiv.reader.core.ui.component.detail

import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.format.formatCount
import com.pixiv.reader.core.network.model.IllustPageInfo
import com.pixiv.reader.core.network.ugoira.UgoiraFrame
import com.pixiv.reader.core.ui.component.card.TagChip
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.image.UgoiraPlayer
import com.pixiv.reader.core.ui.component.input.VerticalActionButton
import com.pixiv.reader.core.ui.component.text.HtmlCaptionText
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.FavoriteRed
import com.pixiv.reader.core.ui.theme.Spacing
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
 *
 * 传入 [seed] 时即使 [pages] 尚未就绪也会渲染 pager 容器（占位封面），hero 共享元素
 * 自进入详情起即存在——详情路由据此不再叠加独立的 loader 分支。
 *
 * @param illust 作品数据；null 时信息区不渲染（骨架阶段）
 * @param pages 各 P 的图片元数据
 * @param ugoiraFrames 动图帧
 * @param ugoiraProgress 动图 zip 下载进度 0..1；null 表示未在下载
 * @param relatedItems 相关作品列表
 * @param strings 详情页文案（feature 侧注入）
 * @param onPageChange 当前页变化回调
 * @param onOpenViewer 全屏入口回调（参数为页索引）
 * @param onOpenUser 打开作者主页回调
 * @param onOpenIllust 简介内打开其他插画回调
 * @param onSearchTag 点击标签搜索回调
 * @param isAuthorFollowed 作者是否已关注
 * @param isAuthorFollowing 关注操作是否进行中
 * @param onToggleFollowAuthor 切换作者关注回调
 * @param isBookmarked 是否已收藏
 * @param isBookmarking 收藏操作是否进行中
 * @param onToggleBookmark 切换收藏回调
 * @param onDownload 下载回调
 * @param onOpenComments 打开评论回调
 * @param expandableIntro 简介是否可展开/收起
 * @param showActionRow 是否在标签下渲染操作行（详情页走底栏时为 false）
 * @param modifier 外部 Modifier
 * @param onOpenNovel 简介内打开小说回调
 * @param seed hero 种子（pages 未就绪时的封面占位），透传给 [IllustPagePager]
 * @param heroIllustId hero 共享元素 key 的回退作品 id（路由参数，[illust] 未到达时用于定位 key，
 *   保证占位封面在数据加载期即参与 morph）；null 时仅用 [illust] 的 id
 * @return 无返回值（渲染 Composable）
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
    seed: HeroSeed? = null,
    heroIllustId: Long? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 有种子时 pages 为空也渲染 pager：hero 元素从进入详情起唯一存在
        if (pages.isNotEmpty() || seed != null) {
            IllustPagePager(
                pages = pages,
                ugoiraFrames = ugoiraFrames,
                ugoiraProgress = ugoiraProgress,
                onPageChange = onPageChange,
                onOpenViewer = onOpenViewer,
                strings = strings,
                // hero：详情大图与列表卡片封面同 key（illust 可空：回退路由参数 id）
                illustId = illust?.id ?: heroIllustId,
                // 垫底 medium：与 hero/卡片封面同源，原图未就绪时先顶上
                placeholderUrl = illust?.image_urls?.medium,
                // 首页兜底比例取 illust 宽高（与卡片 aspectRatio 同源）：pages 宽高未就绪时
                // 首帧高度即与 hero 占位一致，避免接棒后从 1.5f 修正到大图真实比例的两连跳
                fallbackRatio = illust
                    ?.takeIf { it.width > 0 && it.height > 0 }
                    ?.let { it.width.toFloat() / it.height.toFloat() },
                seed = seed,
            )
        }
        // 数据未到（仅 seed 占位阶段）：信息区用骨架占位，避免大图下方直接空出
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
        // 沉浸式底部收尾：内容直通系统导航栏的页面（平板 Master-Detail pane）尾部留出
        // 导航栏高度，让最底部内容能滚出导航栏区域；inset 已被上层消费时高度为 0（手机）
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * 多 P Pager：容器高度按「图片真实宽高比」自适应（无黑边、无闪变）。
 * 用 AsyncImage 的 onSuccess 拿到 drawable 真实尺寸计算比例；
 * spinner 仅在真正加载中显示，加载完成或失败即隐藏。
 *
 * 高度变化按来源分流：**换页**时用 Expressive spatial 弹簧吸附（不同比例页过渡自然）；
 * **同页内**的比例修正（图片加载完成、hero 占位接棒）直接落地不做动画——
 * 否则 hero morph 结束后弹簧重播，观感是「大图又跳一下」。
 *
 * 比例兜底链：图片真实比例 → 页面元数据宽高 → [fallbackRatio]（首页用 illust 宽高比）
 * → 1.5f 通用兜底。多页作品实时翻页时第 2+ 页在 web 接口补齐宽高前，用首页比例兜底
 * （严格优于 1.5f 的通用值）；补齐后翻页高度弹簧照常播放。
 *
 * 占位态（`pages` 为空但持有 [seed]）：容器不经 HorizontalPager，直接渲染种子封面，
 * 比例取 `seed.aspectRatio → fallbackRatio → 1.5f`，且不渲染页码胶囊 / 全屏按钮 / spinner /
 * 动图分支 / 失败重试层。容器本身始终挂 hero 共享边界，数据到达后同一个容器原地转为真实
 * pager——hero 元素全程唯一，避免 loading→content 硬切导致共享元素「消失后重现」重播动画。
 *
 * 容器底色：占位态与当前页图片尚未就绪时用 `colorScheme.surface`（占位图 / 加载中不露黑块）；
 * 图片真正加载完成后才切 `Color.Black`，用于压住图片 letterbox 区域的黑帧观感。
 *
 * @param pages 各 P 的图片元数据（宽高/URL）
 * @param onPageChange 当前页变化回调
 * @param onOpenViewer 点击全屏入口回调（参数为页索引）
 * @param ugoiraFrames 动图帧（ugoira 作品由 ViewModel 加载）；非空时首页（动图作品仅 1 页）
 *   叠加 [UgoiraPlayer] 播放动画——静态封面 AsyncImage 保留（量比例 + 帧未就绪兜底显示）
 * @param ugoiraProgress 动图 zip 下载进度 0..1；非 null 且帧未就绪时首页叠加转圈 + 百分比
 * @param strings 详情页文案（core:ui 不依赖 feature 的 R）
 * @param illustId 作品 id（hero 共享元素 key 用；null 时（无作品的骨架/预览场景）不挂共享边界）
 * @param placeholderUrl 当前页原图加载完成前的垫底图 URL（通常为已缓存的 medium，
 *   与 hero 占位同源）；null 时不垫底
 * @param fallbackRatio 首页兜底宽高比（调用方传 illust 的 `width / height`）；用于修正
 *   pages 宽高未就绪（web 接口补尺寸前的数百毫秒）时落到 1.5f 通用兜底、与 hero 占位
 *   （封面比例）高度不一致导致的接棒跳变。null 时仍走 1.5f 兜底
 * @param seed hero 种子（卡片点击时快照的封面）。pages 未就绪时作为封面占位渲染，
 *   保证 hero 共享元素自详情进入起即存在、跨 loading→content 切换不重建；null 时
 *   pages 为空则不渲染任何内容（深链兜底由调用方走完整骨架）
 * @return 无返回值（渲染 Composable）
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IllustPagePager(
    pages: List<IllustPageInfo>,
    onPageChange: (Int) -> Unit,
    onOpenViewer: (Int) -> Unit,
    ugoiraFrames: List<UgoiraFrame> = emptyList(),
    ugoiraProgress: Float? = null,
    strings: IllustDetailStrings,
    illustId: Long? = null,
    placeholderUrl: String? = null,
    fallbackRatio: Float? = null,
    seed: HeroSeed? = null,
) {
    // 占位态：无页面数据但持有种子封面（数据到达前 hero 元素必须存在）
    val placeholderOnly = pages.isEmpty() && seed != null
    // 占位态比例：种子封面比例 → 调用方兜底 → 通用兜底
    val placeholderRatio = seed?.aspectRatio ?: fallbackRatio ?: 1.5f
    // lambda 每次调用重读最新 pages / seed，占位态 1 页、正常态按页数
    val pagerState = rememberPagerState(
        pageCount = { if (pages.isEmpty() && seed != null) 1 else pages.size },
    )
    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) { onPageChange(currentPage) }

    // 占位态不读 pages（pageCount=1 时索引无意义），用种子比例合成当前页关键参数
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

    // 优先图片真实比例，其次网页宽高，再次调用方传入的首页兜底比例，最后通用兜底；
    // 占位态直接用种子封面比例，保证与卡片封面高度一致（接棒无跳变）
    val webRatio = current
        ?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { it.width.toFloat() / it.height.toFloat() }
    val effectiveRatio = if (placeholderOnly) {
        placeholderRatio
    } else {
        imageRatio ?: webRatio ?: fallbackRatio ?: 1.5f
    }
    // spinner 只反映「当前页图片是否已有尺寸/已停止加载」：fallbackRatio 是静态估值，
    // 不代表图片已就绪，不计入 ready（否则首帧误隐藏转圈）
    val ready = imageRatio != null || webRatio != null
    // 底色：占位态与图片未就绪时不露黑块；真实图片渲染后才用黑底压 letterbox 黑帧
    val containerColor = if (placeholderOnly || (!ready && !loadDone)) {
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
        // 高度吸附弹簧：在组合期先取好（LaunchedEffect 的 body 不是组合上下文，
        // 不能在其中读 MaterialTheme.motionScheme），与 theme/Motion.kt 同一做法
        val heightSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
        // 容器高度改为手控 Animatable：只有换页才走弹簧，同页修正直接落地
        val heightAnim = remember { Animatable(targetHeight, Dp.VectorConverter) }
        // 首组合标记：首个 LaunchedEffect 只做就位，不播放入场高度动画
        var heightSettled by remember { mutableStateOf(false) }
        // 上次参与高度判定的页索引：用于区分「换页」与「同页比例修正」
        var lastPageForHeight by remember { mutableIntStateOf(currentPage) }
        LaunchedEffect(currentPage, targetHeight) {
            // 三态判定：首组合就位 / 换页吸附 / 同页修正落地
            val firstComposition = !heightSettled
            val pageChanged = !firstComposition && lastPageForHeight != currentPage
            // 先落标记再启动动画：弹簧被后续 targetHeight 变化打断时，
            // 新的一次判定不会被重复当成「换页」而重播弹簧
            heightSettled = true
            lastPageForHeight = currentPage
            when {
                // 首组合：容器直接落到目标高度（与 animateDpAsState 首帧即终值语义一致）
                firstComposition -> heightAnim.snapTo(targetHeight)
                // 换页：不同比例页之间保留 spatial 弹簧吸附
                pageChanged -> heightAnim.animateTo(targetHeight, heightSpec)
                // 同页修正（原图加载完成 / hero 占位接棒）：立即落地，消除二次跳动
                else -> heightAnim.snapTo(targetHeight)
            }
        }
        // 嵌套 lambda（pager item）无法隐式访问 BoxWithConstraints receiver：提前捕获
        val pagerMaxWidth = maxWidth
        // hero：大图容器与卡片封面同 key 衔接 morph（容器高度换页时才有弹簧吸附，
        // morph 期间 boundsTransform 由 sharedBounds 接管，静态态下即为当前显示高度）
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
            // 占位态：pager 的 pageCount 为 1 而 pages 为空（取 pages[index] 会越界），
            // 故不经过 HorizontalPager，直接在同一 hero 容器内渲染种子封面
            if (placeholderOnly) {
                // 封面空串走 null：PixivImage 会画主题占位色块（与容器 surface 底一致）
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
                    // 预组合相邻页：翻页前邻图已在加载，滑动即显
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    val p = pages[index]
                    if (index == currentPage) {
                        Box {
                            // 垫底：原图（original/large，首访无缓存）加载完成前显示已在缓存中的
                            // medium（与 hero 占位同源）。AsyncImage 成功后自然覆盖；非 hero 直接
                            // 打开详情页同样受益（避免接棒瞬间空窗）。null 时不渲染——
                            // PixivImage 对 null 会画主题占位色块，会盖掉容器的黑色底
                            if (placeholderUrl != null) {
                                PixivImage(
                                    url = placeholderUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
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
                                                LoadingIndicator(color = Color.White)
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
            }
            // 占位态：不渲染 spinner 与页码/全屏入口（尚无页面数据可操作）
            if (!placeholderOnly) {
                if (!ready && !loadDone) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator(color = Color.White)
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
                        // 统一胶囊 chip（与小说详情一致）；点击跳搜索该标签
                        TagChip(
                            text = "#$tagName",
                            onClick = { onSearchTag(tagName) },
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

/**
 * 作者关注 / 取关按钮（作者行顶右）：全圆 pill + Expressive 按压形变。
 * 未关注 = 实心主色 [Button]；已关注 = 主色描边 + 浅底 [OutlinedButton]。
 */
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
