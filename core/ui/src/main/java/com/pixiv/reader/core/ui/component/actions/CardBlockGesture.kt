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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.theme.Sizes
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 卡片就地屏蔽手势包装结果（[rememberCardBlockGesture] 产物，三卡共用）。
 *
 * @param isBlocked 是否处于「屏蔽且未临时显示」态（true 时封面模糊 + 遮罩）
 * @param onClick 包装后的点击回调（屏蔽态首次点击改为临时显示，不透传原点击）
 * @param onLongClick 包装后的长按回调（弹全局动作菜单；无宿主环境为 null）
 */
class CardBlockGesture(
    val isBlocked: Boolean,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)?,
)

/**
 * 卡片就地屏蔽手势助手（IllustCard / RankingIllustCard / NovelCard 共用）：
 * 收集全局屏蔽集合（[LocalCardActionsHost]）判断本卡是否被屏蔽，并包装点击/长按行为——
 * 屏蔽态下首次点击改为「临时显示」（不透传原点击），长按弹全局动作菜单（稍后再看/屏蔽）。
 *
 * @param targetType 目标类型（"illust" / "novel"）
 * @param targetId 目标 id
 * @param title 卡片标题供给函数（长按菜单副标题；惰性求值避免组合期取值）
 * @param payload 卡片快照 JSON 供给函数（加入稍后再看时落库；惰性求值避免组合期序列化）
 * @param onClick 原点击回调（非屏蔽态透传）
 * @return [CardBlockGesture]（含 isBlocked 与包装后的点击/长按）
 */
@Composable
internal fun rememberCardBlockGesture(
    targetType: String,
    targetId: Long,
    title: () -> String?,
    payload: () -> String?,
    onClick: () -> Unit,
): CardBlockGesture {
    val controller = LocalCardActionsHost.current
    // 收集全局屏蔽集合（无宿主环境恒空集）
    val blockedIds: Set<String> = if (controller != null) {
        val ids by controller.blockedIds.collectAsStateWithLifecycle()
        ids
    } else {
        emptySet()
    }
    // 本卡「已临时显示」标记：屏蔽态点击一次后解除模糊（remember(id) 切卡重置）
    var revealed by remember(targetId) { mutableStateOf(false) }
    val rawBlocked = controller != null && controller.keyOf(targetType, targetId) in blockedIds
    // 取消屏蔽后再次屏蔽：重置临时显示标记——否则上一次点开留下的 revealed=true
    // 会让 isBlocked 恒为 false，重新屏蔽的遮罩永不恢复
    LaunchedEffect(rawBlocked) {
        if (rawBlocked) revealed = false
    }
    return CardBlockGesture(
        isBlocked = rawBlocked && !revealed,
        onClick = {
            // 屏蔽态首次点击 = 临时显示；非屏蔽态透传原点击
            if (rawBlocked) revealed = true else onClick()
        },
        onLongClick = controller?.let { c ->
            { c.show(CardActionTarget(targetType, targetId, title(), payload())) }
        },
    )
}

/**
 * 屏蔽遮罩（三卡共用）：半透明黑底 + 禁止图标 +「已屏蔽 · 轻点显示」文案。
 *
 * ## 手势拦截（关键）
 * 遮罩绘制在最上层并**持有全部点击手势**——Compose 命中测试最上层优先，
 * 模糊内容层里的标签 chip / 作者行 / 收藏按钮等 clickable 在屏蔽期间全部收不到事件，
 * 防止「透过遮罩点标签跳搜索 / 进作者主页」。点击 = 临时显示，长按 = 动作菜单。
 *
 * @param modifier 遮罩范围（调用方传 `matchParentSize()` / `fillMaxSize()`）
 * @param onClick 点击回调（通常传 [CardBlockGesture.onClick]，即临时显示路径）；null 纯展示无手势
 * @param onLongClick 长按回调（传 [CardBlockGesture.onLongClick] 弹动作菜单）
 * @return 无返回值
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
            .background(Color.Black.copy(alpha = 0.5f))
            // 手势挂遮罩（最上层）而非内容层：屏蔽期间下层所有 clickable 一并失效
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
