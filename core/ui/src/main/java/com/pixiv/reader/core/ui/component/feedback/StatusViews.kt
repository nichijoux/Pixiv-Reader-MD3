package com.pixiv.reader.core.ui.component.feedback

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.component.input.AccentIconBadge
import com.pixiv.reader.core.ui.theme.SettingAccentOrange
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 加载中占位：全屏居中 Expressive `ContainedLoadingIndicator`（形状形变加载指示器）。
 *
 * ## UI 设计方式
 * `Box` + `fillMaxSize` + `contentAlignment.Center`，带容器底的加载指示器居中。
 *
 * @param modifier 外部传入的 Modifier
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator()
    }
}

/**
 * 三态占位的大图标（错误 / 空态共用）：默认 48dp 线性图标 + effects 快档淡入；
 * 传入点缀色时改为 64dp 图标徽标（淡底 + 同色图标）。
 *
 * ## UI 设计方式
 * - 未传点缀色（[Color.Unspecified]）：次级色 40% 透明度（`onSurfaceVariant.copy(alpha = 0.4f)`）裸图标，
 *   弱化到「氛围插画」而非信息层级（既有 26 处调用的观感保持不变）。
 * - 传入点缀色：交给 [AccentIconBadge] 渲染 64dp 容器（16dp 圆角、32dp 图标），
 *   容器淡底与图标色同源且经 `resolveAccent` 解析（彩色风格关闭时自动回退主题单色）。
 * - 两种形态都由 [Animatable] 从 0 → 1 淡入（透明度挂在外层 Box），避免三态切换硬闪。
 *
 * @param icon 图标（错误态 CloudOff / 空态 Inbox）
 * @param accent 点缀色；[Color.Unspecified] 保持中性裸图标观感
 * @return 无返回值（Composable 渲染图标）
 */
@Composable
private fun StateIllustration(icon: ImageVector, accent: Color = Color.Unspecified) {
    // 组合期捕获动效规格：Animatable 的 animateTo 调用点不在组合上下文
    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // 进场淡入：单次播放，重组不重播（LaunchedEffect 以 Unit 为 key）
        alpha.animateTo(1f, animationSpec = fadeSpec)
    }
    Box(
        modifier = Modifier
            .padding(bottom = Spacing.md)
            .graphicsLayer { this.alpha = alpha.value },
        contentAlignment = Alignment.Center,
    ) {
        if (accent == Color.Unspecified) {
            // 中性形态：保持与既有一致的 48dp 弱化裸图标（无容器底）
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp),
            )
        } else {
            // 彩色形态：淡底 + 同色图标的统一徽标语汇（accent 由 AccentIconBadge 内 resolveAccent 解析）
            AccentIconBadge(
                icon = icon,
                accent = accent,
                size = 64.dp,
                cornerRadius = 16.dp,
                iconSize = 32.dp,
            )
        }
    }
}

/**
 * 错误占位：居中显示错误信息 + 重试按钮。
 *
 * ## UI 设计方式
 * `Column` 垂直居中：64dp 橙色图标徽标（CloudOff，淡底 + 同色图标，淡入）+ 错误文本（`error` 色，居中）
 * + "重试" `Button`（带刷新图标）。
 *
 * @param message 错误信息
 * @param onRetry 重试回调（重新发起加载）
 * @param modifier 外部传入的 Modifier
 */
@Composable
fun ErrorBox(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // message 为空/空白时回退到本地化兜底文案（i18n）
    val text = message.takeUnless { it.isNullOrBlank() } ?: stringResource(R.string.load_failed)
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 氛围图标：橙色徽标提示「网络/请求异常」，文案仍用 error 色保持错误语义
        StateIllustration(Icons.Outlined.CloudOff, accent = SettingAccentOrange)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = Spacing.lg)) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text(stringResource(R.string.retry), modifier = Modifier.padding(start = Spacing.xsPlus))
        }
    }
}

/**
 * 空态占位：居中显示收件箱图标 + 提示文本（次级色）。
 *
 * ## UI 设计方式
 * `Column` 垂直居中（`fillMaxSize`）：图标（淡入）+ 单行提示文本。
 * 未传 [accent] 时为 48dp 次级色 40% 裸图标（既有调用观感不变）；传入后改为 64dp 图标徽标。
 *
 * @param text 空态提示文案（如"暂无收藏"）
 * @param modifier 外部传入的 Modifier
 * @param accent 可选点缀色；[Color.Unspecified] 保持中性裸图标观感
 */
@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier, accent: Color = Color.Unspecified) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 氛围图标：与错误态同规格，保持三态视觉语言一致
        StateIllustration(Icons.Outlined.Inbox, accent = accent)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
