package com.pixiv.reader.core.ui.component.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.pixiv.reader.core.common.format.formatCountForNovel
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
import com.pixiv.reader.core.ui.component.detail.novelCoverKey
import com.pixiv.reader.core.ui.component.input.pressScale
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.ImageScrim
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.core.ui.theme.extendedColors

/** 兼容 core:ui 既有引用方的 typealias，本体已下沉 core:common。 */
typealias NovelCardData = com.pixiv.reader.core.common.model.NovelCardData

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun NovelCard(
    novel: NovelCardData,
    onClick: () -> Unit,
    onOpenAuthor: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onTagClick: (String) -> Unit,
    onSeriesClick: (Long) -> Unit = {},
    rank: Int? = null,
    showFavoriteCount: Boolean = true,
    coverBadge: (@Composable () -> Unit)? = null,
    coverWidth: Dp = 104.dp,
    modifier: Modifier = Modifier,
) {
    // 按 ID 去重订阅全局收藏覆盖态，避免其他作品变动引发全页重组并同步失败回滚
    val favoriteActions = LocalFavoriteActions.current
    val favoriteOverride by if (favoriteActions != null) {
        remember(favoriteActions, novel.id) {
            favoriteActions.novelBookmarks
                .map { it[novel.id] }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = favoriteActions.novelBookmarks.value[novel.id])
    } else {
        remember { mutableStateOf<Boolean?>(null) }
    }
    var localFavorite by remember(novel.id, novel.isFavorite) { mutableStateOf(novel.isFavorite) }
    val favorite = favoriteOverride ?: localFavorite

    val badgeStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
    )

    val gson = PAYLOAD_GSON
    val block = rememberCardBlockGesture(
        targetType = "novel",
        targetId = novel.id,
        title = { novel.title },
        payload = { gson.toJson(novel) },
        onClick = {
            // 点击瞬间写入 3:4 封面快照供详情加载期占位衔接 morph
            HeroSeedStore.seed(
                key = novelCoverKey(novel.id),
                value = HeroSeed(coverUrl = novel.coverUrl.orEmpty(), aspectRatio = 3f / 4f),
            )
            onClick()
        },
        tags = novel.tags,
        serverMuted = novel.serverMuted,
    )

    // 共享 interactionSource：pressScale 须与 combinedClickable 共用 source 才能收到 pressed 态
    val cardInteraction = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(cardInteraction)
            .combinedClickable(
                interactionSource = cardInteraction,
                indication = LocalIndication.current,
                onClick = block.onClick,
                onLongClick = block.onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = AppShapes.large,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .padding(Spacing.mdPlus)
                    .then(if (block.state.isMasked) Modifier.blur(12.dp) else Modifier),
            ) {
            // IntrinsicSize.Min 让 Row 取封面固有高度，右侧 fillMaxHeight 才有确定高度使作者行抵底
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(coverWidth)
                        .aspectRatio(3f / 4f)
                        .heroBoundsModifier(novelCoverKey(novel.id))
                        .clip(AppShapes.card)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    AsyncImage(
                        model = novel.coverUrl,
                        contentDescription = novel.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 封面仅 104dp 宽，纵列堆叠排名与已屏蔽角标防横向重叠裁剪
                    Column(
                        modifier = Modifier.align(Alignment.TopStart),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                    ) {
                        if (rank != null) {
                            Box(
                                modifier = Modifier
                                    .padding(Spacing.xsPlus)
                                    .clip(rankBadgeShape(rank))
                                    .background(ImageScrim)
                                    .padding(horizontal = Spacing.sm, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "$rank",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
                                    color = rankColor(rank) ?: Color.White,
                                )
                            }
                        }
                        if (block.state.isRevealed) {
                            Box(modifier = Modifier.padding(horizontal = Spacing.xsPlus)) {
                                BlockedRevealBadge()
                            }
                        }
                    }
                    if (coverBadge != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(Spacing.xsPlus),
                        ) {
                            coverBadge()
                        }
                    }
                    // 底端渐变遮罩确保纯白封面上白字对比度 ≥4.5:1
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.35f to Color.Black.copy(alpha = 0.55f),
                                    1f to ImageScrim,
                                ),
                            )
                            .padding(top = Spacing.md, bottom = Spacing.sm, start = Spacing.xs, end = Spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (showFavoriteCount) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.extendedColors.favoriteOnScrim,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = formatCountForNovel(novel.favoriteCount),
                                    style = badgeStyle,
                                    modifier = Modifier.padding(start = 3.dp),
                                )
                            }
                        }
                        Text(
                            text = formatCountForNovel(novel.wordCount),
                            style = badgeStyle,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(start = Spacing.mdPlus)
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = novel.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconToggleButton(
                            checked = favorite,
                            onCheckedChange = { next ->
                                if (favoriteActions == null) {
                                    localFavorite = next
                                }
                                onToggleFavorite(next)
                            },
                        ) {
                            Icon(
                                imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (favorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite),
                                tint = if (favorite) MaterialTheme.extendedColors.favorite else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Sizes.s24),
                            )
                        }
                    }
                    val seriesId = novel.seriesId
                    if (!novel.seriesTitle.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .padding(top = Spacing.xsPlus)
                                .clip(AppShapes.tiny)
                                .clickable(enabled = seriesId != null, onClick = { seriesId?.let(onSeriesClick) })
                                .padding(end = Spacing.xs, bottom = Spacing.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = novel.seriesTitle ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(AppShapes.small)
                            .clickable(onClick = onOpenAuthor),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(
                            name = novel.authorName,
                            avatarUrl = novel.authorAvatarUrl,
                            modifier = Modifier.size(Sizes.s28),
                        )
                        Text(
                            text = novel.authorName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = Spacing.sm)
                                .weight(1f),
                        )
                        if (!novel.publishDate.isNullOrBlank()) {
                            Text(
                                text = novel.publishDate?.take(10) ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }

            if (novel.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = Spacing.smPlus),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                ) {
                    novel.tags.take(5).forEach { tag ->
                        TagChip(text = "#$tag", onClick = { onTagClick(tag) }, compact = true)
                    }
                    if (novel.tags.size > 5) {
                        TagChip(text = "+${novel.tags.size - 5}", onClick = null, compact = true)
                    }
                }
            }
            }
            // 遮罩置于最上层接管手势，屏蔽期间拦截下层标签/作者/收藏点击
            if (block.state.isMasked) {
                BlockedOverlay(
                    modifier = Modifier.matchParentSize(),
                    onClick = block.onClick,
                    onLongClick = block.onLongClick,
                )
            }
        }
    }
}

// Gson 线程安全，共享单例避免每张卡片组合重复创建
private val PAYLOAD_GSON = Gson()
