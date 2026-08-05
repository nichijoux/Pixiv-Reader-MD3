package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.formatCount
import com.pixiv.reader.core.ui.R

/** 排名徽标配色：1 金 / 2 橙 / 3 灰，其余用主题次级色（[NovelCard] 排名徽标复用）。 */
internal fun rankColor(rank: Int): Color? = when (rank) {
    1 -> Color(0xFFE8A33D)
    2 -> Color(0xFFB45309)
    3 -> Color(0xFF6B7280)
    else -> null
}

/**
 * 排行榜条目行（插画/漫画通用，作为 [RankingList] 的默认 [RankingList] itemContent 渲染）。
 *
 * ## UI 设计方式
 * 横向 `Row`：排名徽标（28dp 斜体加粗，1金/2橙/3灰）+ 88dp 圆角封面 + 文本列
 * （标题 2 行省略 + 作者 + 收藏数），整行点击打开详情，行 hover 背景 `surfaceContainer`。
 *
 * @param rank 排名序号（从 1 开始）
 * @param illust 作品数据（封面/标题/作者/收藏）
 * @param onClick 点击行（通常打开作品详情）
 */
@Composable
fun RankingRow(
    rank: Int,
    illust: Illust,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            color = rankColor(rank) ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        PixivImage(
            url = illust.image_urls?.square_medium ?: illust.image_urls?.medium,
            contentDescription = illust.title,
            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = illust.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = illust.user?.name.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            val bookmarks = (illust.total_bookmarks ?: 0).toLong()
            if (bookmarks > 0) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = stringResource(R.string.ranking_bookmarks, formatCount(bookmarks)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}