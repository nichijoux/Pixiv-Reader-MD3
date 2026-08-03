package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pixivapi.model.Illust
import com.example.pixivapi.model.User
import com.pixiv.reader.core.common.formatCount

/**
 * 瀑布流卡片：封面 + 收藏角标 + AI 标识 + 标题 + 作者。
 */
@Composable
fun IllustCard(
    illust: Illust,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverHeight: Dp = 150.dp,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            PixivImage(
                url = illust.image_urls?.medium ?: illust.image_urls?.square_medium,
                contentDescription = illust.title,
                modifier = Modifier.fillMaxWidth().height(coverHeight),
                contentScale = ContentScale.Crop,
            )
            val bookmarkCount = (illust.total_bookmarks ?: 0).toLong()
            if (bookmarkCount > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = formatCount(bookmarkCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }
            if (illust.isAi()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    color = Color(0xFF7C4DFF).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = "AI",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = illust.title.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val user: User? = illust.user
            if (user != null) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PixivImage(
                        url = user.profile_image_urls?.px_50x50 ?: user.profile_image_urls?.px_16x16,
                        contentDescription = user.name,
                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)),
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
        }
    }
}
