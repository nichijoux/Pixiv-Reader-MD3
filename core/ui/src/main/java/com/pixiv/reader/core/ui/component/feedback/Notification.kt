package com.pixiv.reader.core.ui.component.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.theme.Durations
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.milliseconds

/** 通知类型：决定图标与强调色。 */
enum class NotificationType { Info, Success, Error }

/** 一条待展示的通知数据。 */
data class NotificationData(
    val text: String,
    val type: NotificationType,
    val actionText: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * 自定义 Material3 风格通知宿主状态（替代 Material `Snackbar`）。
 *
 * ## 设计说明
 * 视觉遵循 Material 3 规范并做定制：`surfaceContainerHigh` 卡片底（跟随 app 主题明暗，非固定黑色）
 * + 阴影浮起 + `onSurface` 文字 + 类型图标徽标（Info=primary / Success=绿 / Error=error 语义色）
 * + 关闭按钮；宽度**自适应内容**，上限按设备区分——手机约屏宽 88%、平板封顶 420dp
 * （短文案显示为紧凑胶囊，不占满屏幕）。
 * 行为与 MD 一致：新消息**顶替当前**并重置计时、约 2.6s 自动消失、点击可关闭。
 * 动画为底部滑入 + 淡入（[NotificationHost] 内 `AnimatedVisibility`）。
 *
 * 用法：
 * ```
 * val notificationState = rememberNotificationHostState()
 * Scaffold(snackbarHost = { NotificationHost(notificationState) }) { ... }
 * // 触发：notificationState.show(context.getString(R.string.xxx))
 * ```
 */
class NotificationHostState internal constructor() {
    private val _queue = MutableStateFlow<NotificationData?>(null)

    /** 当前应展示的通知（null = 无，用于驱动显隐动画）。 */
    val queue: StateFlow<NotificationData?> = _queue.asStateFlow()

    /** 展示一条通知；若已有展示中的通知则直接顶替并重置计时。
     *  @param actionText 非空时展示操作按钮（点击执行 [onAction] 后自动关闭通知） */
    fun show(
        text: String,
        type: NotificationType = NotificationType.Info,
        actionText: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        _queue.value = NotificationData(text, type, actionText, onAction)
    }

    /** 立即关闭当前通知（触发退出动画）。 */
    fun dismiss() {
        _queue.value = null
    }
}

/** 创建可组合内持有的 [NotificationHostState]。 */
@Composable
fun rememberNotificationHostState(): NotificationHostState = remember { NotificationHostState() }

/**
 * 自定义通知宿主：底部居中展示当前通知，进入/退出带滑入 + 淡入淡出动画。
 * 可直接放入 `Scaffold(snackbarHost = ...)`，或放入任意 [Box] 用 `Modifier.align(BottomCenter)` 定位。
 *
 * 卡片宽度自适应内容，上限按设备区分：手机（< 600dp）约屏宽 88%（留边距不贴满），
 * 平板（>= 600dp）封顶 420dp。
 *
 * @param state 通知状态（[rememberNotificationHostState]）
 * @param modifier 外部定位/边距 Modifier（内部已 `fillMaxWidth` + 底部居中）
 * @param contentModifier 仅作用于通知卡片内容的 Modifier（如 `navigationBarsPadding()` 避让系统导航栏）。
 *   注意：必须作用于卡片而不是宿主——若加在 [modifier] 上，即使没有通知（内容收起）宿主也会占位
 *   非零高度，在 Scaffold snackbar 槽中会残留底部空白。
 */
@Composable
fun NotificationHost(
    state: NotificationHostState,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
) {
    val notification by state.queue.collectAsStateWithLifecycle()
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 手机屏宽通常 < 420dp：固定 420dp 上限会撑满屏，改用比例留白；平板才封顶 420dp
        val cardMaxWidth = if (maxWidth >= 600.dp) 420.dp else maxWidth * 0.88f
        // 退出动画期间通知已为 null：记住最近一次非 null 值，让退出动画仍能渲染旧卡片
        var last by remember { mutableStateOf(notification) }
        notification?.let { last = it }
        AnimatedVisibility(
            visible = notification != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(280)) +
                fadeIn(animationSpec = tween(280)),
            exit = slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(220)) +
                fadeOut(animationSpec = tween(220)),
        ) {
            // 卡片外包一层：contentModifier 只随卡片存在/消失而占位，空闲时宿主高度为 0，
            // 避免在沉浸式外层 Scaffold（contentWindowInsets=0）的 snackbar 槽残留底部空白
            Box(
                modifier = contentModifier,
                contentAlignment = Alignment.BottomCenter,
            ) {
                last?.let { n ->
                    NotificationCard(
                        notification = n,
                        onDismiss = state::dismiss,
                        modifier = Modifier.widthIn(max = cardMaxWidth),
                    )
                }
            }
        }
    }
}

/** 单条通知卡片：类型图标徽标 + 文本 + 关闭按钮；自动消失 + 整卡点击关闭。 */
@Composable
private fun NotificationCard(
    notification: NotificationData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 自动消失：消息变化时重启计时（新消息顶替旧消息）
    LaunchedEffect(notification) {
        delay(Durations.NOTIFICATION_TIMEOUT.milliseconds)
        onDismiss()
    }
    Surface(
        modifier = modifier.clickable(onClick = onDismiss),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 类型图标徽标：语义色 15% alpha 圆底 + 实色图标
            val accent = notificationTypeColor(notification.type)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = notificationTypeIcon(notification.type),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = notification.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 操作按钮（可选）：点击执行动作并关闭通知；卡片其余区域点击仍为关闭
            if (notification.actionText != null && notification.onAction != null) {
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = notification.actionText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            notification.onAction.invoke()
                            onDismiss()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onDismiss),
            )
        }
    }
}

/** 类型语义色：Info 用主题 primary、Success 固定绿、Error 用主题 error（明暗主题均对比清晰）。 */
@Composable
private fun notificationTypeColor(type: NotificationType): Color = when (type) {
    NotificationType.Info -> MaterialTheme.colorScheme.primary
    NotificationType.Success -> SuccessGreen
    NotificationType.Error -> MaterialTheme.colorScheme.error
}

/** 类型图标。 */
private fun notificationTypeIcon(type: NotificationType): ImageVector = when (type) {
    NotificationType.Info -> Icons.Filled.Info
    NotificationType.Success -> Icons.Filled.CheckCircle
    NotificationType.Error -> Icons.Filled.ErrorOutline
}

/** core:common [MessageType] → 通知类型映射（ViewModel 一次性事件统一走此转换）。 */
fun com.pixiv.reader.core.common.MessageType.toNotificationType(): NotificationType = when (this) {
    com.pixiv.reader.core.common.MessageType.INFO -> NotificationType.Info
    com.pixiv.reader.core.common.MessageType.SUCCESS -> NotificationType.Success
    com.pixiv.reader.core.common.MessageType.ERROR -> NotificationType.Error
}
