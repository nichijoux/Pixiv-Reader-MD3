package com.pixiv.reader.core.ui.component.actions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.ImageScrim
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 屏蔽遮罩三态，互斥（勿拆成双 Boolean）；MASKED = 模糊 + 全遮罩，REVEALED = 内容可见 + 已屏蔽角标。
 */
enum class CardMaskState(val isMasked: Boolean, val isRevealed: Boolean) {
    NONE(false, false),

    MASKED(true, false),

    REVEALED(false, true),
}

/** [rememberCardBlockGesture] 产物，三卡共用。 */
class CardBlockGesture(
    val state: CardMaskState,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)?,
)

/**
 * 屏蔽态首次点击改为「临时显示」且不透传原点击；已临时显示后再点击只弹提示，因为卡片仍在屏蔽名单内，
 * 直开详情会被拦下（需先长按菜单取消屏蔽）。长按则弹全局动作菜单（稍后再看/屏蔽）。
 */
@Composable
internal fun rememberCardBlockGesture(
    targetType: String,
    targetId: Long,
    title: () -> String?,
    payload: () -> String?,
    onClick: () -> Unit,
    tags: List<String> = emptyList(),
    serverMuted: Boolean = false,
): CardBlockGesture {
    val controller = LocalCardActionsHost.current
    val context = LocalContext.current
    val targetKey = remember(controller, targetType, targetId) {
        controller?.keyOf(targetType, targetId)
    }
    // 基于根宿主单次采集的 Snapshot State 做 O(1) derivedStateOf，零协程且仅命中卡片重组
    val isBlockedTarget by remember(controller, targetKey) {
        derivedStateOf {
            controller != null && targetKey != null && targetKey in controller.blockedIdsState.value
        }
    }
    val hasMutedTag by remember(controller, tags) {
        derivedStateOf {
            if (controller == null || tags.isEmpty()) {
                false
            } else {
                val muted = controller.mutedTagsState.value
                muted.isNotEmpty() && tags.any { it.trim().lowercase() in muted }
            }
        }
    }
    val rawBlocked = serverMuted || isBlockedTarget || hasMutedTag
    // 屏蔽态变化时自然重置临时显示标记，避免重新屏蔽后沿用上次 revealed=true（且无需起协程）
    var revealed by remember(targetType, targetId, rawBlocked) { mutableStateOf(false) }
    val state = when {
        !rawBlocked -> CardMaskState.NONE
        revealed -> CardMaskState.REVEALED
        else -> CardMaskState.MASKED
    }
    return CardBlockGesture(
        state = state,
        onClick = {
            when {
                !rawBlocked -> onClick()
                revealed -> controller?.showMessage(
                    context.getString(R.string.card_msg_blocked_no_detail),
                )
                else -> revealed = true
            }
        },
        onLongClick = controller?.let { c ->
            { c.show(CardActionTarget(targetType, targetId, title(), payload())) }
        },
    )
}

/**
 * 三卡共用遮罩：ImageScrim 深色底保证白字对比度不随封面亮度变化。
 *
 * 遮罩绘制在最上层并持有全部点击手势（Compose 命中测试最上层优先），模糊内容层里的标签 chip / 作者行 /
 * 收藏按钮等 clickable 在屏蔽期间收不到事件，防止「透过遮罩点标签跳搜索 / 进作者主页」。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BlockedOverlay(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .background(ImageScrim)
            .let { base ->
                if (onClick != null) {
                    base.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    base
                }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Block,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(Sizes.s28),
        )
        Text(
            text = stringResource(R.string.card_blocked_badge),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = stringResource(R.string.card_blocked_reveal_hint),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

/** 临时显示态角标：与 AI / 页码角标同风格，持续提示该卡片仍在屏蔽名单内（点击不打开详情）。 */
@Composable
internal fun BlockedRevealBadge() {
    Surface(
        color = ImageScrim,
        shape = AppShapes.small,
    ) {
        Text(
            text = stringResource(R.string.card_blocked_badge),
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}
