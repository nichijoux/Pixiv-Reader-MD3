package com.pixiv.reader.feature.novel.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.format.formatCount
import com.pixiv.reader.core.common.format.formatCountForNovel
import com.pixiv.reader.core.ui.component.card.TagChip
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.text.HtmlCaptionText
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.novel.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NovelHeader(
    novel: Novel,
    onOpenUser: (Long) -> Unit,
    expandableIntro: Boolean,
    isAuthorFollowed: Boolean = false,
    isAuthorFollowing: Boolean = false,
    onToggleFollowAuthor: () -> Unit = {},
    onOpenNovel: (Long) -> Unit = {},
    onOpenIllust: (Long) -> Unit = {},
    onSearchTag: (String) -> Unit = {},
) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
        Text(
            text = novel.title.orEmpty(),
            style = novelTitleStyle(),
        )
        val publishDate = novel.create_date?.take(10)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { novel.user?.id?.let(onOpenUser) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus),
            ) {
                UserAvatar(
                    name = novel.user?.name,
                    avatarUrl = novel.user?.profile_image_urls?.best(),
                    modifier = Modifier.size(Sizes.s36),
                )
                Text(
                    text = novel.user?.name.orEmpty(),
                    style = novelAuthorStyle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // fill=false 短名不拉伸，超长省略，保证关注胶囊不被挤出
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.s16),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AuthorFollowPill(
                isFollowed = isAuthorFollowed,
                enabled = !isAuthorFollowing,
                onClick = onToggleFollowAuthor,
            )
        }
        if (!publishDate.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.novel_publish_date, publishDate),
                    style = novelMetaStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            NovelStatBlock(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                value = formatCountForNovel(novel.text_length ?: 0),
                label = stringResource(R.string.novel_stat_word),
                modifier = Modifier.weight(1f),
            )
            NovelStatBlock(
                icon = Icons.Filled.FavoriteBorder,
                value = formatCount((novel.total_bookmarks ?: 0).toLong()),
                label = stringResource(R.string.novel_stat_bookmark),
                modifier = Modifier.weight(1f),
            )
            NovelStatBlock(
                icon = Icons.Filled.Visibility,
                value = formatCount((novel.total_view ?: 0).toLong()),
                label = stringResource(R.string.novel_stat_view),
                modifier = Modifier.weight(1f),
            )
        }
        val tags = novel.tags.orEmpty().take(8)
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = Spacing.md),
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
        // 手机 6 行截断 + 展开全文，平板完整显示；按钮仅在真的截断时出现
        val caption = novel.caption
        if (!caption.isNullOrBlank()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            var truncated by remember { mutableStateOf(false) }
            val clamped = expandableIntro && !expanded
            HtmlCaptionText(
                html = caption,
                style = novelIntroStyle().copy(
                    textIndent = TextIndent(firstLine = 2.em),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (clamped) 6 else Int.MAX_VALUE,
                overflow = if (clamped) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = Modifier
                    .padding(top = Spacing.mdPlus)
                    .animateContentSize(),
                onTextLayout = { layout ->
                    // 展开后 maxLines 无限，溢出恒 false，只在 clamped 时检测
                    if (clamped) truncated = layout.hasVisualOverflow
                },
                onOpenNovel = onOpenNovel,
                onOpenIllust = onOpenIllust,
                onOpenUser = onOpenUser,
            )
            if (expandableIntro && (truncated || expanded)) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (expanded) stringResource(R.string.novel_intro_collapse) else stringResource(
                            R.string.novel_intro_expand
                        ),
                        style = novelMetaStyle().copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 紧凑作者关注胶囊（系列页作者行复用）。 */
@Composable
internal fun AuthorFollowPill(
    isFollowed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val container = if (isFollowed) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val content = if (isFollowed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }
    Text(
        text = stringResource(if (isFollowed) R.string.novel_following else R.string.novel_follow),
        style = novelSmallLabelStyle().copy(fontWeight = FontWeight.SemiBold),
        color = content,
        maxLines = 1,
        modifier = Modifier
            .clip(AppShapes.pill)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}

/** 图标 + 数值 + 标签；weight(1f) 均分整行。 */
@Composable
internal fun NovelStatBlock(
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
                // 16dp 在整页比例下偏小，与数值同档提到 20dp
                modifier = Modifier.size(Sizes.s20),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = value,
                style = novelStatValueStyle(),
                modifier = Modifier.padding(start = Spacing.xsPlus),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
}
