package com.pixiv.reader.core.ui.component.card
import com.pixiv.reader.core.ui.component.image.PixivImage

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.format.formatCountForNovel
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.component.input.pressScale
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.ImageScrim
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

data class SeriesCardData(
    val title: String,
    val caption: String? = null,
    val coverUrl: String? = null,
    val partsCount: Int = 0,
    val totalChars: Int = 0,
    val isConcluded: Boolean? = null,
    val watchlisted: Boolean = false,
    val authorName: String? = null,
    val authorAvatarUrl: String? = null,
    val updatedAt: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesCard(
    data: SeriesCardData,
    onClick: () -> Unit,
    onOpenAuthor: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 共享 interactionSource：pressScale 须与 clickable 共用 source 才能收到 pressed 态
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .height(IntrinsicSize.Min)
            .clip(AppShapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(Spacing.mdPlus),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 128.dp)
                .clip(AppShapes.card),
        ) {
            if (!data.coverUrl.isNullOrBlank()) {
                PixivImage(
                    url = data.coverUrl,
                    contentDescription = data.title,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SeriesBookCover(
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 48.dp,
                )
            }
            // 用 ImageScrim（70% 黑）而非更淡遮罩，确保浅色封面上白字对比度 ≥8:1
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(ImageScrim)
                    .padding(horizontal = Spacing.xsPlus, vertical = 3.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    IconCount(
                        icon = Icons.Filled.Collections,
                        text = stringResource(R.string.series_parts, data.partsCount),
                        tint = Color.White,
                        iconSize = 12.dp,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (data.totalChars > 0) {
                        IconCount(
                            icon = Icons.AutoMirrored.Filled.Notes,
                            text = stringResource(
                                R.string.series_chars,
                                formatCountForNovel(data.totalChars),
                            ),
                            tint = Color.White,
                            iconSize = 12.dp,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        // 依赖外层 Row 的 IntrinsicSize.Min 定高，使作者行通过 Spacer 抵底对齐封面
        Column(
            modifier = Modifier
                .padding(start = Spacing.mdPlus)
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Text(
                text = data.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // 简介限 1 行防总高度超出 128dp 封面，保证作者行恒抵底
            val caption = data.caption
            if (!caption.isNullOrBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                val concluded = data.isConcluded
                if (concluded != null) {
                    StatusPill(
                        text = stringResource(
                            if (concluded) R.string.series_concluded else R.string.series_ongoing,
                        ),
                        container = if (concluded) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        content = if (concluded) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
                if (data.watchlisted) {
                    StatusPill(
                        text = stringResource(R.string.series_watchlisted),
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (!data.authorName.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .clip(AppShapes.small)
                        .clickable(onClick = onOpenAuthor),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(
                        name = data.authorName,
                        avatarUrl = data.authorAvatarUrl,
                        modifier = Modifier.size(Sizes.s24),
                    )
                    Text(
                        text = data.authorName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = Spacing.sm)
                            .weight(1f),
                    )
                    if (!data.updatedAt.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Filled.Update,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = data.updatedAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = Spacing.xxs),
                        )
                    }
                }
            }
        }
    }
}
