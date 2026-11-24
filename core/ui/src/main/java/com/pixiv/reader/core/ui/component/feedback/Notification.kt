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

/** 通知类型：决定图标与强调色。 */
enum class NotificationType { Info, Success, Error }

/** 一条待展示的通知数据（带单调递增 [id]，保证同文案重推也能触发计时重置与入场）。 */
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

/**
 * 全局应用内通知宿主状态：由根 Activity 向下提供。
 *
 * 子页面调用 [rememberNotificationHostState] 时会直接复用该根状态，
 * 其局部放置的 [NotificationHost] 自动退让为空实现，使全局通知与页面通知
 * 统一在右上角堆叠展示并跨页面保持连续。
 */
val LocalNotificationHostState = staticCompositionLocalOf<NotificationHostState?> { null }

/**
 * 自定义 Material3 风格可堆叠通知宿主状态（替代 Material `Snackbar`）。
 *
 * ## 设计说明
 * - 支持最多 [MAX_VISIBLE] 条通知在右上角纵向堆叠，新通知插在顶部并顺推旧通知；
 * - 文案、类型与操作标签均相同的活跃通知会被去重合并并重置到顶部；
 * - 视觉遵循 M3 `inverseSurface` 反色胶囊容器 + 16dp 圆角 + 微边框 + 阴影；
 * - 常规 2.6s 自动消失（带操作按钮延长至 4.2s），支持点击或向右滑动关闭。
 */
class NotificationHostState internal constructor() {
    private val _items = MutableStateFlow<List<NotificationData>>(emptyList())

    /** 当前活跃通知列表（按最新在前排序，最多 [MAX_VISIBLE] 条）。 */
    val items: StateFlow<List<NotificationData>> = _items.asStateFlow()

    /**
     * 展示一条通知；新通知插在栈顶，相同内容的活跃通知会合并置顶并刷新计时。
     *
     * @param text 通知文案
     * @param type 通知语义类型
     * @param actionText 可选操作按钮文案（非空且配合 [onAction] 时展示）
     * @param onAction 可选操作回调（点击后自动关闭该条通知）
     * @return 无返回值
     */
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

    /**
     * 按 [id] 关闭指定通知。
     *
     * @param id 目标通知唯一编号
     * @return 无返回值
     */
    fun dismiss(id: Long) {
        _items.update { current -> current.filterNot { it.id == id } }
    }

    /**
     * 立即清空全部活跃通知。
     *
     * @return 无返回值
     */
    fun dismiss() {
        _items.value = emptyList()
    }

    companion object {
        /** 同时展示的最大通知堆叠条数，超出时移除最早条。 */
        const val MAX_VISIBLE = 3
    }
}

/**
 * 获取通知宿主状态：优先复用根部通过 [LocalNotificationHostState] 下发的统一实例，
 * 独立预览或脱离根壳的场景回退为局部实例。
 *
 * @return 可用的 [NotificationHostState]
 */
@Composable
fun rememberNotificationHostState(): NotificationHostState =
    LocalNotificationHostState.current ?: remember { NotificationHostState() }

/**
 * 可堆叠右上角通知宿主：紧贴右上角显示紧凑胶囊通知栈，仅占用实际卡片区域，不拦截其余屏幕触摸。
 *
 * 若当前 [state] 与上级 [LocalNotificationHostState] 相同，说明根宿主已在右上角接管渲染，
 * 本次调用直接返回，使各子路由既有 `NotificationHost(...)` 零占位退让。
 *
 * @param state 通知状态（[rememberNotificationHostState]）
 * @param modifier 外部定位与避让修饰符（根宿主通过 `align(Alignment.TopEnd)` + insets 定位）
 * @param contentModifier 仅在存在可见卡片时应用的修饰符（空闲时不占高度）
 * @return 无返回值
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun NotificationHost(
    state: NotificationHostState,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
) {
    // 根部已挂载统一宿主的子树直接让位，避免局部 Scaffold 与根部重复渲染或占用底部空白
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
        // 紧凑浮层上限：手机取屏宽 78% 且封顶 320dp，平板封顶 360dp，避免遮挡左侧标题
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

/**
 * 单条右上角紧凑通知卡片：类型图标徽标 + 文案 + 可选动作 + 关闭图标。
 * 支持自动定时消失、点击关闭及向右滑动关闭。
 */
@Composable
private fun NotificationCard(
    notification: NotificationData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasAction = notification.actionText != null && notification.onAction != null
    // 带动作按钮的通知给予更充裕的操作时间窗口
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

/** 反色通知容器上的类型语义色：Info 用 inversePrimary、Success/Error 走扩展色反色角色。 */
@Composable
private fun notificationTypeColor(type: NotificationType): Color = when (type) {
    NotificationType.Info -> MaterialTheme.colorScheme.inversePrimary
    NotificationType.Success -> MaterialTheme.extendedColors.successOnInverse
    NotificationType.Error -> MaterialTheme.extendedColors.errorOnInverse
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
