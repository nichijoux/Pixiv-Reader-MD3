package com.pixiv.reader.core.ui.component.card
import android.annotation.SuppressLint
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.image.UgoiraCardPlayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Illust
import com.pixiv.api.model.User
import com.pixiv.reader.core.common.format.formatCount
import com.pixiv.reader.core.network.ugoira.UgoiraLoader
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.FavoriteRed
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 排行榜大图卡片（插画/漫画通用，作为 [RankingList] 的 itemContent 渲染）。
 *
 * ## UI 设计方式
 * 参考瀑布流 [IllustCard] 的信息组织，但封面左上角叠**排名徽标**（排行榜的灵魂）：
 * - **封面区**（Box）：图片按 `width/height` 完整显示（`aspectRatio`），无宽高回退 [coverHeight]。
 *   左上角浮层 **排名徽标 + AI 标识 + 页码**；右上角收藏切换按钮；右下角收藏数角标。
 * - **信息区**（Column，10dp 内边距）：标题（2 行省略）+ 作者行（20dp 头像 + 名称）。
 *
 * 与 [IllustCard] 差异：左上角多了排名徽标（1金/2橙/3灰 + 斜体加粗）；封面用 `medium` 保证清晰。
 *
 * @param rank 排名序号（从 1 开始，1金/2橙/3灰）
 * @param illust 作品数据（`width/height` 用于完整显示、`is_bookmarked` 初始化收藏态）
 * @param onClick 整卡点击回调（通常打开作品详情）
 * @param modifier 外部传入的 Modifier（列表行通常传 `fillMaxWidth`）
 * @param coverHeight 无宽高数据时的回退封面高度
 * @param onToggleFavorite 收藏切换回调，参数为切换后的目标状态（true=收藏）；null 隐藏按钮
 * @param onOpenAuthor 作者行点击回调（打开作者主页；user 为 null 时不可点）
 * @param ugoiraLoader 动图加载器；非空且作品为 ugoira 时封面播放动图动画（帧未就绪露出静态封面）；null 恒静态
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun RankingIllustCard(
    rank: Int,
    illust: Illust,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverHeight: Dp = 220.dp,
    onToggleFavorite: ((Boolean) -> Unit)? = null,
    onOpenAuthor: () -> Unit = {},
    ugoiraLoader: UgoiraLoader? = null,
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
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 封面宽度（px）：动图帧采样解码上限（避免解码 zip 原图尺寸浪费内存）
            val coverMaxSize = with(LocalDensity.current) { maxWidth.roundToPx() }
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
            // 动图：ugoira 卡片播放（zip 帧动画覆盖静态封面；帧未就绪透明露出封面）
            if (ugoiraLoader != null && illust.isGif()) {
                UgoiraCardPlayer(
                    loader = ugoiraLoader,
                    illustId = illust.id,
                    maxDecodeSize = coverMaxSize,
                    modifier = Modifier.matchParentSize(),
                )
            }
            // 左上角：排名徽标 + AI 标识 + 页码（多 P），中性黑底白字
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 排名徽标：1金/2橙/3灰，斜体加粗大号（排行榜灵魂）
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = AppShapes.small,
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
                if (illust.isAi()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
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
                        color = Color.Black.copy(alpha = 0.45f),
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
            }
            // 收藏切换按钮：右上角自绘浮层（28dp 圆，避免 IconButton 强制 40dp 溢出边界）
            if (onToggleFavorite != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.sm)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable {
                            favorite = !favorite
                            onToggleFavorite(favorite)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (favorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite),
                        tint = if (favorite) FavoriteRed else Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // 收藏数角标：右下角
            val bookmarkCount = (illust.total_bookmarks ?: 0).toLong()
            if (bookmarkCount > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = AppShapes.small,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
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
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(AppShapes.small)
                        .clickable(onClick = onOpenAuthor)
                        .padding(end = 4.dp),
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
