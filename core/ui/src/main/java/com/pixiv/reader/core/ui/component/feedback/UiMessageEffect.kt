package com.pixiv.reader.core.ui.component.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.pixiv.reader.core.common.UiMessage
import kotlinx.coroutines.flow.Flow

/** 订阅 [UiMessage] 一次性通知流并展示到 [hostState]；协程内用 `context.getString` 解析文案（LaunchedEffect 内不可调 stringResource）。 */
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
