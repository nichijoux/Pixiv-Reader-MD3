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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.R
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
 * 三态占位的大图标（错误 / 空态共用）：48dp 线性图标 + effects 快档淡入。
 *
 * ## UI 设计方式
 * 次级色 40% 透明度（`onSurfaceVariant.copy(alpha = 0.4f)`），弱化到「氛围插画」而非信息层级；
 * 组合期捕获 MotionScheme 的 `fastEffectsSpec`，由 [Animatable] 从 0 → 1 淡入，避免三态切换硬闪。
 *
 * @param icon 图标（错误态 CloudOff / 空态 Inbox）
 * @return 无返回值（Composable 渲染图标）
 */
@Composable
private fun StateIllustration(icon: ImageVector) {
    // 组合期捕获动效规格：Animatable 的 animateTo 调用点不在组合上下文
    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // 进场淡入：单次播放，重组不重播（LaunchedEffect 以 Unit 为 key）
        alpha.animateTo(1f, animationSpec = fadeSpec)
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .padding(bottom = Spacing.md)
            .size(48.dp)
            .graphicsLayer { this.alpha = alpha.value },
    )
}

/**
 * 错误占位：居中显示错误信息 + 重试按钮。
 *
 * ## UI 设计方式
 * `Column` 垂直居中：48dp 云断开图标（次级色 40%，淡入）+ 错误文本（`error` 色，居中）
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
        // 氛围图标：弱化视觉重量，仅作情绪提示
        StateIllustration(Icons.Outlined.CloudOff)
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
 * `Column` 垂直居中（`fillMaxSize`）：48dp 收件箱图标（次级色 40%，淡入）+ 单行提示文本。
 *
 * @param text 空态提示文案（如"暂无收藏"）
 * @param modifier 外部传入的 Modifier
 */
@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 氛围图标：与错误态同规格，保持三态视觉语言一致
        StateIllustration(Icons.Outlined.Inbox)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
