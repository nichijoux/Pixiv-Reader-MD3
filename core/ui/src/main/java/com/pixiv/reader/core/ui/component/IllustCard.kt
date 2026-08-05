package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Illust
import com.pixiv.api.model.User
import com.pixiv.reader.core.common.formatCount
import com.pixiv.reader.core.ui.R

/**
 * 插画瀑布流卡片（通用组件，首页 / 搜索结果 / 收藏 / 下载 / 浏览历史共用）。
 *
 * ## UI 设计方式
 * 纵向 Column 分两段：
 * - **封面区**（Box，撑满宽度）：图片按作品 `width/height` 计算 `aspectRatio` 完整显示
 *   （`ContentScale.Crop` + 比例匹配即不裁剪）；无宽高时回退 [coverHeight] 固定高度，
 *   可能只显示中间部分——调用方应尽量提供宽高（历史/下载实体已存）。
 *   浮层统一用 `Modifier.align` 定位，黑底白字中性风：
 *   - 左上角：AI 标识 + 页码（多 P 时 `xP`）
 *   - 右上角：收藏切换按钮（[onToggleFavorite] 非空才显示）
 *   - 右下角：收藏数角标
 * - **信息区**（Column，10dp 内边距）：标题（最多 2 行省略号）+ 作者行（20dp 小头像 + 名称）。
 *
 * ## 交互
 * 整卡 [onClick] 打开详情；收藏按钮点击**先翻转本地状态再回调**外部执行 API
 * （成功与否由外部负责，组件仅维护 UI 态 [favorite]）。
 *
 * @param illust 作品数据（`width/height` 用于完整显示、`is_bookmarked` 初始化收藏态）
 * @param onClick 整卡点击回调（通常打开作品详情）
 * @param modifier 外部传入的 Modifier（瀑布流网格通常传 `fillMaxWidth`）
 * @param coverHeight 无宽高数据时的回退封面高度
 * @param onToggleFavorite 收藏切换回调，参数为切换后的目标状态（true=收藏）；null 隐藏按钮
 */
@Composable
fun IllustCard(
    illust: Illust,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverHeight: Dp = 150.dp,
    onToggleFavorite: ((Boolean) -> Unit)? = null,
) {
    // 收藏态：以作品初始收藏态初始化，点击切换（仅 UI 态，API 由外部回调处理）
    var favorite by remember(illust.id) { mutableStateOf(illust.is_bookmarked == true) }
    // 卡片根容器：圆角 + 卡片底色 + 整卡点击
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        // ── 封面区（Box 内浮层用 align 定位） ──
        Box(modifier = Modifier.fillMaxWidth()) {
            // 封面：有宽高比按原比例完整显示，否则回退固定高度
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
            // 左上角：AI 标识 + 页码（多 P），中性黑底白字（与收藏角标统一，不鲜艳）
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (illust.isAi()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "AI",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
                if ((illust.page_count ?: 0) > 1) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "${illust.page_count}P",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }
            // 收藏切换按钮：右上角（外面即可点击收藏/取消）
            if (onToggleFavorite != null) {
                IconButton(
                    onClick = {
                        favorite = !favorite
                        onToggleFavorite(favorite)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                ) {
                    Icon(
                        imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (favorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite),
                        tint = if (favorite) Color(0xFFFF5252) else Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            // 收藏数角标：右下角
            val bookmarkCount = (illust.total_bookmarks ?: 0).toLong()
            if (bookmarkCount > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                    UserAvatar(
                        name = user.name,
                        avatarUrl = user.profile_image_urls?.best(),
                        modifier = Modifier.size(20.dp),
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
