package com.pixiv.reader.core.ui.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.theme.resolveAccent

/**
 * 用户头像（通用组件）。
 *
 * ## UI 设计方式
 * - 有 [avatarUrl]：`PixivImage` 按传入 [shape]（默认圆形）裁剪加载
 * - URL 缺失（pixiv 各接口头像尺寸字段组合不固定）：显示**用户名首字母圆形兜底**
 *   （默认 `primaryContainer` 底色 + 首字符 + `onPrimaryContainer` 文字；传入 [accent] 时
 *   改为该色 12% 淡底 + 同色首字母，与全 App 彩色图标徽标语汇一致）
 * 颜色取自 `MaterialTheme`，支持深浅色主题。
 *
 * @param name 用户名（用于兜底显示首字符与无障碍描述）
 * @param avatarUrl 头像 URL，建议传 `profile_image_urls?.best()`（自动 fallback 各尺寸）
 * @param modifier 外部传入的 Modifier（通常指定尺寸 `Modifier.size(...)`）
 * @param shape 头像形状（默认圆形，可传圆角）
 * @param onClick 非 null 时头像可点击（如点击跳用户主页）
 * @param accent 首字母兜底圈点缀色；[Color.Unspecified] 保持 primaryContainer 现状，
 *   否则经 [resolveAccent] 取色（关闭彩色风格时回退 primary）
 * @return 无返回值
 */
@Composable
fun UserAvatar(
    name: String?,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.CircleShape,
    onClick: (() -> Unit)? = null,
    accent: Color = Color.Unspecified,
) {
    val clickModifier = if (onClick != null) modifier.clip(shape).clickable(onClick = onClick) else modifier
    if (!avatarUrl.isNullOrBlank()) {
        PixivImage(
            url = avatarUrl,
            contentDescription = name,
            modifier = clickModifier.clip(shape),
        )
    } else {
        // 未指定点缀色时保持原 primaryContainer 兜底；指定后走「淡底 + 同色字」徽标语汇
        val custom = accent != Color.Unspecified
        val accentColor = if (custom) resolveAccent(accent) else MaterialTheme.colorScheme.primaryContainer
        // 深色下提高淡底透明度，避免色块不可见
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val containerColor = if (custom) {
            accentColor.copy(alpha = if (dark) 0.18f else 0.12f)
        } else {
            accentColor
        }
        val contentColor = if (custom) accentColor else MaterialTheme.colorScheme.onPrimaryContainer
        Box(
            modifier = clickModifier
                .clip(shape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name?.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
