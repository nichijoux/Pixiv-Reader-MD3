package com.pixiv.reader.core.ui.component.feedback

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Durations
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.extendedColors
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotificationType { Info, Success, Error }

/** 单条通知数据；单调递增 [id] 保证同文案重推也能重置计时与入场动画。 */
data class NotificationData(
    val text: String,
    val type: NotificationType,
    val actionText: String? = null,
    val onAction: (() -> Unit)? = null,
    val id: Long = nextId.incrementAndGet(),
) {
    companion object {
        private val nextId = AtomicLong(0L)
    }
}

/** 根 Activity 提供的全局通知宿主状态；子页 [rememberNotificationHostState] 复用该实例，其局部 [NotificationHost] 自动退让。 */
val LocalNotificationHostState = staticCompositionLocalOf<NotificationHostState?> { null }

/**
 * 可堆叠通知宿主状态（替代 M3 Snackbar）：最多 [MAX_VISIBLE] 条，
 * 常规 2.6s / 带动作 4.2s 自动消失，同内容活跃通知合并置顶并刷新计时。
 */
class NotificationHostState internal constructor() {
    private val _items = MutableStateFlow<List<NotificationData>>(emptyList())

    val items: StateFlow<List<NotificationData>> = _items.asStateFlow()

    fun show(
        text: String,
        type: NotificationType = NotificationType.Info,
        actionText: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        val next = NotificationData(text, type, actionText, onAction)
        _items.update { current ->
            // 同文案/类型/动作的活跃条目直接合并置顶，避免连点同一操作刷屏堆叠
            val deduped = current.filterNot {
                it.text == text && it.type == type && it.actionText == actionText
            }
            (listOf(next) + deduped).take(MAX_VISIBLE)
        }
    }

    fun dismiss(id: Long) {
        _items.update { current -> current.filterNot { it.id == id } }
    }

    fun dismiss() {
        _items.value = emptyList()
    }

    companion object {
        const val MAX_VISIBLE = 3
    }
}

@Composable
fun rememberNotificationHostState(): NotificationHostState =
    LocalNotificationHostState.current ?: remember { NotificationHostState() }

/** 右上角可堆叠通知宿主；若 [state] 与上级 [LocalNotificationHostState] 为同一实例则零占位退让。 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun NotificationHost(
    state: NotificationHostState,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
) {
    if (LocalNotificationHostState.current === state) return

    val activeItems by state.items.collectAsStateWithLifecycle()
    val displayItems = remember { mutableStateListOf<NotificationData>() }
    val transitionStates = remember { mutableStateMapOf<Long, MutableTransitionState<Boolean>>() }

    LaunchedEffect(activeItems) {
        val activeIds = activeItems.mapTo(HashSet(activeItems.size)) { it.id }
        val merged = activeItems.toMutableList()
        // 保留正在播放退场动画的旧卡片原相对位置，待动画完成后再从 displayItems 移除
        displayItems.forEachIndexed { index, existing ->
            if (existing.id !in activeIds) {
                val prevId = if (index > 0) displayItems[index - 1].id else null
                val prevIdx = if (prevId != null) merged.indexOfFirst { it.id == prevId } else -1
                val insertAt = if (prevIdx >= 0) prevIdx + 1 else 0
                merged.add(insertAt, existing)
            }
        }
        activeItems.forEach { item ->
            val ts = transitionStates.getOrPut(item.id) { MutableTransitionState(false) }
            ts.targetState = true
        }
        transitionStates.forEach { (id, ts) ->
            if (id !in activeIds) ts.targetState = false
        }
        if (displayItems.map { it.id } != merged.map { it.id }) {
            displayItems.clear()
            displayItems.addAll(merged)
        }
    }

    val slideInSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val slideOutSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val expandSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val shrinkSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopEnd,
    ) {
        // 手机限宽 78% 且封顶 320dp、平板 360dp，避免遮挡左侧标题
        val cardMaxWidth = if (maxWidth >= 600.dp) 360.dp else (maxWidth * 0.78f).coerceAtMost(320.dp)
        Column(
            modifier = if (displayItems.isNotEmpty()) contentModifier else Modifier,
            horizontalAlignment = Alignment.End,
        ) {
            displayItems.forEach { item ->
                key(item.id) {
                    val ts = transitionStates.getOrPut(item.id) {
                        MutableTransitionState(item in activeItems)
                    }
                    // 退场动画完成后回收列表与状态项，避免内存泄漏
                    LaunchedEffect(ts.isIdle, ts.currentState) {
                        if (ts.isIdle && !ts.currentState && activeItems.none { it.id == item.id }) {
                            displayItems.removeAll { it.id == item.id }
                            transitionStates.remove(item.id)
                        }
                    }
                    AnimatedVisibility(
                        visibleState = ts,
                        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = slideInSpec) +
                            expandVertically(expandFrom = Alignment.Top, animationSpec = expandSpec) +
                            fadeIn(animationSpec = fadeSpec),
                        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = slideOutSpec) +
                            shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = shrinkSpec) +
                            fadeOut(animationSpec = fadeSpec),
                    ) {
                        // 卡片间距放在 AnimatedVisibility 内部，退场收折时高度连同间距平滑归零
                        Box(modifier = Modifier.padding(bottom = Spacing.sm)) {
                            NotificationCard(
                                notification = item,
                                onDismiss = { state.dismiss(item.id) },
                                modifier = Modifier.widthIn(max = cardMaxWidth),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasAction = notification.actionText != null && notification.onAction != null
    LaunchedEffect(notification.id) {
        val timeout = if (hasAction) {
            Durations.NOTIFICATION_ACTION_TIMEOUT
        } else {
            Durations.NOTIFICATION_TIMEOUT
        }
        delay(timeout.milliseconds)
        onDismiss()
    }

    val density = LocalDensity.current
    val dismissThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    val dragOffsetX = remember(notification.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val snapBackSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier
            .graphicsLayer {
                translationX = dragOffsetX.value
                val progress = (dragOffsetX.value / (dismissThresholdPx * 1.5f)).coerceIn(0f, 1f)
                alpha = 1f - progress * 0.65f
            }
            .pointerInput(notification.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val next = (dragOffsetX.value + dragAmount).coerceAtLeast(0f)
                        scope.launch { dragOffsetX.snapTo(next) }
                    },
                    onDragEnd = {
                        if (dragOffsetX.value >= dismissThresholdPx) {
                            onDismiss()
                        } else {
                            scope.launch { dragOffsetX.animateTo(0f, snapBackSpec) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffsetX.animateTo(0f, snapBackSpec) }
                    },
                )
            }
            .clickable(
                onClickLabel = stringResource(R.string.notification_dismiss),
                onClick = onDismiss,
            ),
        shape = AppShapes.large,
        color = scheme.inverseSurface,
        border = BorderStroke(0.5.dp, scheme.inverseOnSurface.copy(alpha = 0.12f)),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.smPlus),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val accent = notificationTypeColor(notification.type)
            val badgeBgAlpha = if (scheme.inverseSurface.luminance() < 0.5f) 0.16f else 0.14f
            Box(
                modifier = Modifier
                    .size(Sizes.s24)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = badgeBgAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = notificationTypeIcon(notification.type),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(Sizes.s16),
                )
            }
            Spacer(Modifier.width(Spacing.smPlus))
            // fill = false：短文案保持紧凑胶囊，长文案才在最大宽度内换行
            Text(
                text = notification.text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.inverseOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (hasAction) {
                Spacer(Modifier.width(Spacing.xsPlus))
                Text(
                    text = notification.actionText!!,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.inversePrimary,
                    modifier = Modifier
                        .clip(AppShapes.tiny)
                        .clickable {
                            notification.onAction?.invoke()
                            onDismiss()
                        }
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
            Spacer(Modifier.width(Spacing.xsPlus))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = scheme.inverseOnSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(Sizes.s16),
            )
        }
    }
}

@Composable
private fun notificationTypeColor(type: NotificationType): Color = when (type) {
    NotificationType.Info -> MaterialTheme.colorScheme.inversePrimary
    NotificationType.Success -> MaterialTheme.extendedColors.successOnInverse
    NotificationType.Error -> MaterialTheme.extendedColors.errorOnInverse
}

private fun notificationTypeIcon(type: NotificationType): ImageVector = when (type) {
    NotificationType.Info -> Icons.Filled.Info
    NotificationType.Success -> Icons.Filled.CheckCircle
    NotificationType.Error -> Icons.Filled.ErrorOutline
}

fun com.pixiv.reader.core.common.MessageType.toNotificationType(): NotificationType = when (this) {
    com.pixiv.reader.core.common.MessageType.INFO -> NotificationType.Info
    com.pixiv.reader.core.common.MessageType.SUCCESS -> NotificationType.Success
    com.pixiv.reader.core.common.MessageType.ERROR -> NotificationType.Error
}
