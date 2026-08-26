package com.pixiv.reader.core.network.message

import androidx.lifecycle.ViewModel
import com.pixiv.reader.core.common.UiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 带操作通知通道的 ViewModel 基类（core 共享）。
 *
 * 收敛此前在 10+ 个 VM 间逐文件复制的 `Channel<UiMessage>(BUFFERED) + receiveAsFlow` 样板；
 * UI 侧配合 core:ui 的 `UiMessageEffect` 一行收集显示到 NotificationHost。
 *
 * 子类发通知：[sendMessage]（挂起，缓冲 BUFFERED）/ [trySendMessage]（非挂起，无法挂起的
 * 回调里用，缓冲满时丢弃）。
 */
abstract class MessageViewModel : ViewModel() {

    private val _message = Channel<UiMessage>(Channel.BUFFERED)

    /** 一次性操作通知流：UI 侧收集显示 NotificationHost。 */
    val message: Flow<UiMessage> = _message.receiveAsFlow()

    /** 发送通知（挂起直至进入缓冲）。 */
    protected suspend fun sendMessage(message: UiMessage) = _message.send(message)

    /** 发送通知（非挂起；缓冲满时丢弃，用于不可挂起的上下文）。 */
    protected fun trySendMessage(message: UiMessage) = _message.trySend(message)
}
