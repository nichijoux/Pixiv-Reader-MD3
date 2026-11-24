package com.pixiv.reader.core.ui.component.card
import android.annotation.SuppressLint
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.image.UgoiraCardPlayer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.pixiv.api.model.Illust
import com.pixiv.api.model.User
import com.pixiv.reader.core.common.format.formatCount
import com.pixiv.reader.core.network.ugoira.UgoiraLoader
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.component.actions.BlockedOverlay
import com.pixiv.reader.core.ui.component.actions.BlockedRevealBadge
import com.pixiv.reader.core.ui.component.actions.LocalFavoriteActions
import com.pixiv.reader.core.ui.component.actions.rememberCardBlockGesture
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.pixiv.reader.core.ui.component.detail.HeroSeed
import com.pixiv.reader.core.ui.component.detail.HeroSeedStore
import com.pixiv.reader.core.ui.component.detail.heroBoundsModifier
import com.pixiv.reader.core.ui.component.detail.illustCoverKey
import com.pixiv.reader.core.ui.component.input.pressScale
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.ImageScrim
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.core.ui.theme.extendedColors
import kotlin.math.roundToInt

/**
 * 封面按 `illust.width/height` 计算 `aspectRatio` 完整显示；无宽高时回退 [coverHeight] 固定高度 + Crop
 * 可能裁剪中间，调用方应尽量提供宽高。hero 共享边界仅在宿主提供双 CompositionLocal 时生效，否则退化。
 */
// scope 实际用到了（maxWidth 作为 ugoira 解码上限），IDE 误报"scope 未使用"
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun IllustCard(
    illust: Illust,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverHeight: Dp = 150.dp,
    rank: Int? = null,
    onToggleFavorite: ((Boolean) -> Unit)? = null,
    onOpenAuthor: () -> Unit = {},
    onTagClick: ((String) -> Unit)? = null,
    ugoiraLoader: UgoiraLoader? = null,
    download: DownloadBadge = DownloadBadge.None,
) {
    // 按 ID 去重订阅全局收藏覆盖态，避免其他作品变动引发全页重组并同步失败回滚
    val favoriteActions = LocalFavoriteActions.current
    val favoriteOverride by if (favoriteActions != null) {
        remember(favoriteActions, illust.id) {
            favoriteActions.illustBookmarks
                .map { it[illust.id] }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = favoriteActions.illustBookmarks.value[illust.id])
    } else {
        remember { mutableStateOf<Boolean?>(null) }
    }
    var localFavorite by remember(illust.id, illust.is_bookmarked) {
        mutableStateOf(illust.is_bookmarked == true)
    }
    val favorite = favoriteOverride ?: localFavorite
    val tagNames = remember(illust.tags) {
        illust.tags.orEmpty().mapNotNull { it.name?.trim() }.filter { it.isNotEmpty() }
    }
    val gson = PAYLOAD_GSON
    val block = rememberCardBlockGesture(
        targetType = "illust",
        targetId = illust.id,
        title = { illust.title },
        payload = { gson.toJson(illust) },
        onClick = {
            // 点击瞬间写入封面快照供详情加载期占位衔接 morph，避免数据到达前的飞入空窗
            HeroSeedStore.seed(
                key = illustCoverKey(illust.id),
                value = HeroSeed(
                    coverUrl = illust.image_urls?.medium
                        ?: illust.image_urls?.square_medium
                        ?: "",
                    aspectRatio = if (illust.width > 0 && illust.height > 0) {
                        illust.width.toFloat() / illust.height.toFloat()
                    } else {
                        null
                    },
                ),
            )
            onClick()
        },
        tags = tagNames,
        serverMuted = illust.is_muted == true,
    )
    // 共享 interactionSource：pressScale 须与 combinedClickable 共用 source 才能收到 pressed 态
    val cardInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(cardInteraction)
            .clip(AppShapes.cardLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                interactionSource = cardInteraction,
                indication = LocalIndication.current,
                onClick = block.onClick,
                onLongClick = block.onLongClick,
            ),
    ) {
        Column(
            modifier = Modifier.then(if (block.state.isMasked) Modifier.blur(16.dp) else Modifier),
        ) {
        // 共享边界挂在封面容器且须显式补顶部圆角：sharedBounds 在 overlay 中渲染会绕过父级裁剪
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heroBoundsModifier(illustCoverKey(illust.id))
                .clip(AppShapes.cardLargeTop),
        ) {
            // 以封面实宽作动图解码上限，避免按 zip 原图尺寸解码浪费内存
            val coverWidth = maxWidth
            val coverMaxSize = with(LocalDensity.current) { coverWidth.roundToPx() }
            val ratio = if (illust.width > 0 && illust.height > 0) {
                illust.width.toFloat() / illust.height.toFloat()
            } else {
                null
            }
            PixivImage(
                url = illust.image_urls?.medium ?: illust.image_urls?.square_medium,
                contentDescription = illust.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (ratio != null) Modifier.aspectRatio(ratio)
                        else Modifier.height(coverHeight),
                    ),
                contentScale = ContentScale.Crop,
            )
            if (ugoiraLoader != null && illust.isGif()) {
                UgoiraCardPlayer(
                    loader = ugoiraLoader,
                    illustId = illust.id,
                    maxDecodeSize = coverMaxSize,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
            ) {
                if (rank != null) {
                    Surface(
                        color = ImageScrim,
                        shape = rankBadgeShape(rank),
                    ) {
                        Text(
                            text = "$rank",
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            color = rankColor(rank) ?: Color.White,
                        )
                    }
                }
                if (illust.isAi()) {
                    Surface(
                        color = ImageScrim,
                        shape = AppShapes.small,
                    ) {
                        Text(
                            text = "AI",
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
                if (illust.page_count > 1) {
                    Surface(
                        color = ImageScrim,
                        shape = AppShapes.small,
                    ) {
                        Text(
                            text = "${illust.page_count}P",
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
                if (block.state.isRevealed) {
                    BlockedRevealBadge()
                }
            }
            // 外层 48dp 隐形热区满足 M3 最小触控目标，内层保持 32dp 视觉圆底
            if (onToggleFavorite != null) {
                val favoriteInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(Sizes.s48)
                        .toggleable(
                            value = favorite,
                            role = Role.Checkbox,
                            interactionSource = favoriteInteraction,
                            indication = null,
                        ) { next ->
                            if (favoriteActions == null) {
                                localFavorite = next
                            }
                            onToggleFavorite(next)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(Sizes.s32)
                            .clip(CircleShape)
                            .background(ImageScrim)
                            .indication(favoriteInteraction, ripple(bounded = true, color = Color.White)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (favorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite),
                            tint = if (favorite) MaterialTheme.extendedColors.favoriteOnScrim else Color.White,
                            modifier = Modifier.size(Sizes.s18),
                        )
                    }
                }
            }
            val bookmarkCount = (illust.total_bookmarks ?: 0).toLong()
            if (bookmarkCount > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xsPlus),
                    color = ImageScrim,
                    shape = AppShapes.small,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = formatCount(bookmarkCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(Spacing.smPlus)) {
            when (val badge = download) {
                is DownloadBadge.Running -> {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        LinearWavyProgressIndicator(
                            progress = { badge.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.download_progress, (badge.progress * 100).roundToInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                DownloadBadge.Failed -> {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        LinearWavyProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.download_failed_short),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                DownloadBadge.None -> Text(
                    text = illust.title.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val user: User? = illust.user
            if (user != null) {
                Row(
                    modifier = Modifier
                        .padding(top = Spacing.xsPlus)
                        .clip(AppShapes.small)
                        .clickable(onClick = onOpenAuthor)
                        .padding(end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                ) {
                    UserAvatar(
                        name = user.name,
                        avatarUrl = user.profile_image_urls?.best(),
                        modifier = Modifier.size(Sizes.s20),
                    )
                    Text(
                        text = user.name.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onTagClick != null && tagNames.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                ) {
                    tagNames.take(3).forEach { tag ->
                        TagChip(text = "#$tag", onClick = { onTagClick(tag) }, compact = true)
                    }
                    if (tagNames.size > 3) {
                        TagChip(text = "+${tagNames.size - 3}", onClick = null, compact = true)
                    }
                }
            }
        }
        }
        // 遮罩置于最上层接管手势，屏蔽期间拦截下层作者行/收藏/标签点击
        if (block.state.isMasked) {
            BlockedOverlay(
                modifier = Modifier.matchParentSize(),
                onClick = block.onClick,
                onLongClick = block.onLongClick,
            )
        }
    }
}

// Gson 线程安全，共享单例避免每张卡片组合重复创建
private val PAYLOAD_GSON = Gson()

sealed interface DownloadBadge {
    data object None : DownloadBadge

    data class Running(val progress: Float) : DownloadBadge

    data object Failed : DownloadBadge
}
