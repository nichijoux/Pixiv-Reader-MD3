package com.pixiv.reader.core.ui.component.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.NotificationType
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import kotlinx.coroutines.flow.StateFlow

/**
 * 卡片长按动作目标：哪个作品的本地动作菜单（稍后再看 / 屏蔽）。
 *
 * @param targetType 目标类型："illust" / "novel"
 * @param targetId 目标 id
 * @param title 卡片标题（菜单副标题展示；可空）
 * @param payloadJson 完整卡片快照 JSON（加入稍后再看时落库，离线还原卡片；可空）
 */
data class CardActionTarget(
    val targetType: String,
    val targetId: Long,
    val title: String? = null,
    val payloadJson: String? = null,
)

/**
 * 卡片本地动作实现（app 层注入：稍后再看走 Room、屏蔽走 DataStore；
 * 两组集合以进程级 StateFlow 暴露，卡片收集 [blockedIds] 做封面模糊）。
 */
interface CardActions {
    /** 已加入稍后再看的目标集合（`"illust:1"` 形式键）。 */
    val readLaterIds: StateFlow<Set<String>>

    /** 本地屏蔽目标集合（`"illust:1"` 形式键）。 */
    val blockedIds: StateFlow<Set<String>>

    /**
     * 加入 / 移出稍后再看（按目标当前状态取反）。
     * @param target 动作目标（payloadJson 用于加入时落快照）
     */
    fun toggleReadLater(target: CardActionTarget)

    /**
     * 屏蔽 / 取消屏蔽（按目标当前状态取反）。
     * @param targetType 目标类型；@param targetId 目标 id
     */
    fun toggleBlock(targetType: String, targetId: Long)
}

/**
 * 卡片长按动作控制器（[CardActionsHost] 经 CompositionLocal 下发；卡片侧唯一入口）。
 * [show] 打开全局唯一长按菜单；`readLaterIds` / `blockedIds` 供菜单动态文案与卡片模糊收集。
 */
class CardActionsController internal constructor(
    private val impl: CardActions,
    private val openMenu: (CardActionTarget) -> Unit,
) {
    /** 已加入稍后再看的目标集合。 */
    val readLaterIds: StateFlow<Set<String>> get() = impl.readLaterIds

    /** 本地屏蔽目标集合。 */
    val blockedIds: StateFlow<Set<String>> get() = impl.blockedIds

    /** 打开长按动作菜单（卡片 onLongClick 调用）。 */
    fun show(target: CardActionTarget) = openMenu(target)

    /** 加入 / 移出稍后再看（菜单行调用）。 */
    fun toggleReadLater(target: CardActionTarget) = impl.toggleReadLater(target)

    /** 屏蔽 / 取消屏蔽（菜单行调用）。 */
    fun toggleBlock(targetType: String, targetId: Long) = impl.toggleBlock(targetType, targetId)

    /** 目标键（`"illust:1"` 形式）。 */
    fun keyOf(type: String, id: Long): String = "$type:$id"
}

/** 长按动作宿主 CompositionLocal（app 根 [CardActionsHost] 提供；无宿主环境为 null，卡片长按静默）。 */
val LocalCardActionsHost = staticCompositionLocalOf<CardActionsController?> { null }

/**
 * 卡片长按动作全局宿主：组合本地化下发 [CardActionsController] + 全局唯一长按菜单弹层。
 * app 根部包一层（`CardActionsHost(actions) { NavHost() }`），所有列表卡片自动获得长按能力，
 * 无需逐页透传回调；[actions] 为 null 时仅下发 null（长按无响应，预览/测试环境安全）。
 *
 * @param actions 本地动作实现（app 层注入；null = 无动作环境）
 * @param modifier 外层 Modifier（默认撑满）
 * @param content 宿主内容（通常为 NavHost）
 * @return 无返回值
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardActionsHost(
    actions: CardActions?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 当前菜单目标（全局唯一：长按另一卡片时顶替前序菜单）
    var menuTarget by remember { mutableStateOf<CardActionTarget?>(null) }
    // 动作成功提示（稍后再看/屏蔽 的加入与移除均有反馈）
    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current
    val controller = remember(actions) {
        actions?.let { CardActionsController(it) { menuTarget = it } }
    }

    CompositionLocalProvider(LocalCardActionsHost provides controller) {
        Box(modifier = modifier) {
            content()
            // 长按动作菜单：稍后再看 / 屏蔽（按当前状态动态文案）
            val target = menuTarget
            if (target != null && actions != null) {
                val readLaterIds by actions.readLaterIds.collectAsStateWithLifecycle()
                val blockedIds by actions.blockedIds.collectAsStateWithLifecycle()
                val inReadLater = controller!!.keyOf(target.targetType, target.targetId) in readLaterIds
                val isBlocked = controller.keyOf(target.targetType, target.targetId) in blockedIds
                ModalBottomSheet(onDismissRequest = { menuTarget = null }) {
                    Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
                        Text(
                            text = target.title.orEmpty(),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            modifier = Modifier
                                .padding(horizontal = Spacing.lg)
                                .padding(bottom = Spacing.sm),
                        )
                        // 稍后再看：加入（时钟）/ 移出（取消圆）
                        MenuActionRow(
                            icon = if (inReadLater) Icons.Filled.Cancel else Icons.Filled.Schedule,
                            label = stringResource(
                                if (inReadLater) R.string.card_action_read_later_remove else R.string.card_action_read_later,
                            ),
                            onClick = {
                                controller.toggleReadLater(target)
                                // 成功提示（菜单关闭后浮出）
                                notificationHostState.show(
                                    context.getString(
                                        if (inReadLater) R.string.card_msg_read_later_removed
                                        else R.string.card_msg_read_later_added
                                    ),
                                    type = NotificationType.Success,
                                )
                                menuTarget = null
                            },
                        )
                        // 屏蔽：就地模糊（屏蔽用禁止图标，取消屏蔽用可见图标）
                        MenuActionRow(
                            icon = if (isBlocked) Icons.Filled.Visibility else Icons.Filled.Block,
                            label = stringResource(
                                if (isBlocked) R.string.card_action_unblock else R.string.card_action_block,
                            ),
                            onClick = {
                                controller.toggleBlock(target.targetType, target.targetId)
                                notificationHostState.show(
                                    context.getString(
                                        if (isBlocked) R.string.card_msg_unblocked
                                        else R.string.card_msg_blocked
                                    ),
                                    type = NotificationType.Success,
                                )
                                menuTarget = null
                            },
                        )
                    }
                }
            }
            // 动作结果通知：浮在宿主内容之上（底部滑入）
            NotificationHost(
                state = notificationHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = Spacing.lg),
            )
        }
    }
}

/** 菜单动作行（图标 + 文案，整行点击）。 */
@Composable
private fun MenuActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.smPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Sizes.s20),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = Spacing.md),
        )
    }
}
