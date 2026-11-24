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
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.NotificationType
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import kotlinx.coroutines.flow.StateFlow

/** payloadJson 是完整卡片快照，加入稍后再看时落库，用于离线还原卡片。 */
data class CardActionTarget(
    val targetType: String,
    val targetId: Long,
    val title: String? = null,
    val payloadJson: String? = null,
)

/**
 * 集合以进程级 StateFlow 暴露，键为 `"illust:1"` 形式；卡片收集 [blockedIds] 做封面模糊。
 * mutedTags 已转小写并去首尾空白，便于与作品标签实时匹配。
 */
interface CardActions {
    val readLaterIds: StateFlow<Set<String>>

    val blockedIds: StateFlow<Set<String>>

    val mutedTags: StateFlow<Set<String>>

    fun toggleReadLater(target: CardActionTarget)

    fun toggleBlock(targetType: String, targetId: Long)
}

/** [CardActionsHost] 经 CompositionLocal 下发，卡片侧唯一入口；showNotice 默认空操作，预览/测试环境安全。 */
class CardActionsController internal constructor(
    private val impl: CardActions,
    private val openMenu: (CardActionTarget) -> Unit,
    private val showNotice: (String) -> Unit = {},
) {
    val readLaterIds: StateFlow<Set<String>> get() = impl.readLaterIds

    val blockedIds: StateFlow<Set<String>> get() = impl.blockedIds

    val mutedTags: StateFlow<Set<String>> get() = impl.mutedTags

    fun show(target: CardActionTarget) = openMenu(target)

    fun toggleReadLater(target: CardActionTarget) = impl.toggleReadLater(target)

    fun toggleBlock(targetType: String, targetId: Long) = impl.toggleBlock(targetType, targetId)

    fun showMessage(text: String) = showNotice(text)

    fun keyOf(type: String, id: Long): String = "$type:$id"
}

/** app 根 [CardActionsHost] 提供；无宿主环境为 null，卡片长按静默。 */
val LocalCardActionsHost = staticCompositionLocalOf<CardActionsController?> { null }

/** 供卡片按 ID 实时订阅最新收藏与关注覆盖状态。 */
val LocalFavoriteActions = staticCompositionLocalOf<FavoriteActions?> { null }

/**
 * app 根部包一层（`CardActionsHost(actions) { NavHost() }`），所有列表卡片自动获得长按能力，无需逐页透传回调。
 * actions 为 null 时仅下发 null（长按无响应，预览/测试环境安全）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardActionsHost(
    actions: CardActions?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 全局唯一：长按另一卡片时顶替前序菜单
    var menuTarget by remember { mutableStateOf<CardActionTarget?>(null) }
    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current
    val controller = remember(actions) {
        actions?.let {
            CardActionsController(
                impl = it,
                openMenu = { menuTarget = it },
                showNotice = { text ->
                    notificationHostState.show(text, type = NotificationType.Info)
                },
            )
        }
    }

    CompositionLocalProvider(LocalCardActionsHost provides controller) {
        Box(modifier = modifier) {
            content()
            val target = menuTarget
            if (target != null) {
                val c = controller ?: return@Box
                val impl = actions ?: return@Box
                val readLaterIds by impl.readLaterIds.collectAsStateWithLifecycle()
                val blockedIds by impl.blockedIds.collectAsStateWithLifecycle()
                val inReadLater = c.keyOf(target.targetType, target.targetId) in readLaterIds
                val isBlocked = c.keyOf(target.targetType, target.targetId) in blockedIds
                ModalBottomSheet(onDismissRequest = { menuTarget = null }) {
                    Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
                        if (!target.title.isNullOrBlank()) {
                            Text(
                                text = target.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                modifier = Modifier
                                    .padding(horizontal = Spacing.lg)
                                    .padding(bottom = Spacing.sm),
                            )
                        }
                        MenuActionRow(
                            icon = if (inReadLater) Icons.Filled.Cancel else Icons.Filled.Schedule,
                            label = stringResource(
                                if (inReadLater) R.string.card_action_read_later_remove else R.string.card_action_read_later,
                            ),
                            onClick = {
                                c.toggleReadLater(target)
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
                        MenuActionRow(
                            icon = if (isBlocked) Icons.Filled.Visibility else Icons.Filled.Block,
                            label = stringResource(
                                if (isBlocked) R.string.card_action_unblock else R.string.card_action_block,
                            ),
                            onClick = {
                                c.toggleBlock(target.targetType, target.targetId)
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
