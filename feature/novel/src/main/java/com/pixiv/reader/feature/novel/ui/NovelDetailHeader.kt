package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
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
import com.pixiv.reader.core.common.formatCount
import com.pixiv.reader.core.common.formatCountForNovel
import com.pixiv.reader.core.novel.htmlToPlainText
import com.pixiv.reader.core.ui.component.UserAvatar
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.novel.R

/** 标题信息卡：标题 / 作者（关注按钮）/ 发布时间 / 统计（均分撑满整行）/ 标签 / 简介（首行缩进 + 展开全文）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NovelHeader(
    novel: Novel,
    onOpenUser: (Long) -> Unit,
    expandableIntro: Boolean,
    isAuthorFollowed: Boolean = false,
    isAuthorFollowing: Boolean = false,
    onToggleFollowAuthor: () -> Unit = {},
) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
        // 标题：titleLarge 派生 + 22sp Bold
        Text(
            text = novel.title.orEmpty(),
            style = novelTitleStyle(),
        )
        // 作者 + 发布时间同行：头像/昵称 › 撑左（点击进用户主页）+ 关注胶囊，发布时间靠右
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                UserAvatar(
                    name = novel.user?.name,
                    avatarUrl = novel.user?.profile_image_urls?.best(),
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = novel.user?.name.orEmpty(),
                    style = novelAuthorStyle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 关注 / 取关胶囊（嵌套 clickable 自身消费事件，不会触发外层进用户页）
                AuthorFollowPill(
                    isFollowed = isAuthorFollowed,
                    enabled = !isAuthorFollowing,
                    onClick = onToggleFollowAuthor,
                )
            }
            // 发布时间：同行靠右（不可点击）
            if (!publishDate.isNullOrBlank()) {
                Row(
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
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        // 统计：三块均分撑满整行（icon + 值 15sp Bold + 标签 11sp）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            NovelStatBlock(
                icon = Icons.Filled.MenuBook,
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
        // 标签胶囊
        val tags = novel.tags.orEmpty().take(8)
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    Text(
                        text = "#${tag.displayName ?: tag.name.orEmpty()}",
                        style = novelSmallLabelStyle(),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(AppShapes.pill)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        // 简介：13.5sp + 首行缩进两格；手机 6 行截断 + 展开全文，平板完整显示
        // 展开/收起按钮仅在 clamped 截断（内容超 6 行）时出现，短简介不显示按钮
        val caption = novel.caption
        if (!caption.isNullOrBlank()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            var truncated by remember { mutableStateOf(false) }
            val clamped = expandableIntro && !expanded
            Text(
                text = htmlToPlainText(caption),
                style = novelIntroStyle().copy(
                    textIndent = TextIndent(firstLine = 2.em),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (clamped) 6 else Int.MAX_VALUE,
                overflow = if (clamped) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = Modifier.padding(top = 14.dp),
                onTextLayout = { layout ->
                    // 仅在 clamped 时检测是否真的溢出（展开后 maxLines 无限，溢出恒 false）
                    if (clamped) truncated = layout.hasVisualOverflow
                },
            )
            if (expandableIntro && (truncated || expanded)) {
                // 展开 / 收起：居中按钮（未截断时不出现）
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

/** 作者关注 / 取关胶囊（紧凑，作者名旁）。 */
@Composable
private fun AuthorFollowPill(
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
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** 统计块：图标 + 数值 + 标签（weight(1f) 均分整行，块内水平居中保证左右边距对称）。 */
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
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = value,
                style = novelStatValueStyle(),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = label,
            style = novelSmallLabelStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
