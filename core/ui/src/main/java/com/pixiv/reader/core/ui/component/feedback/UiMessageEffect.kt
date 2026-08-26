package com.pixiv.reader.core.ui.component.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.pixiv.reader.core.common.UiMessage
import kotlinx.coroutines.flow.Flow

/**
 * 订阅 [UiMessage] 一次性通知流并显示到 [NotificationHostState]（core 共享）。
 *
 * 收敛各 Route 重复的 `LaunchedEffect + collect + getString + toNotificationType` 样板，
 * 一行接入：`UiMessageEffect(viewModel.message, notificationHostState)`。
 *
 * 文案在协程内用 `context.getString` 解析——LaunchedEffect 内不可调 stringResource
 * （应用上下文未随应用内语言切换重建，组合期解析会拿到错误语言）。
 */
@Composable
fun UiMessageEffect(
    messages: Flow<UiMessage>,
    hostState: NotificationHostState,
) {
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { msg ->
            hostState.show(
                context.getString(msg.res, *msg.args.toTypedArray()),
                type = msg.type.toNotificationType(),
            )
        }
    }
}
