package com.pixiv.reader.core.network.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.common.ToggleUiState
import com.pixiv.reader.core.common.UiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 带操作通知通道的 ViewModel 基类：Channel(BUFFERED) + receiveAsFlow。
 * UI 侧配合 core:ui 的 `UiMessageEffect` 收集显示到 NotificationHost。
 */
abstract class MessageViewModel : ViewModel() {

    private val _message = Channel<UiMessage>(Channel.BUFFERED)

    val message: Flow<UiMessage> = _message.receiveAsFlow()

    /** 缓冲满时挂起等待。 */
    protected suspend fun sendMessage(message: UiMessage) = _message.send(message)

    /** 非挂起，缓冲满则丢弃（不可挂起的回调里用）。 */
    protected fun trySendMessage(message: UiMessage) = _message.trySend(message)

    /** 单次动作骨架：防连点 → 动作 → 成功钩子与文案（null 静默）、失败发 action_failed（附原因）。 */
    protected fun runActionNotified(
        inFlight: MutableStateFlow<Boolean>,
        successRes: Int?,
        onSuccess: () -> Unit = {},
        action: suspend () -> Result<Unit>,
    ) {
        if (inFlight.value) return
        viewModelScope.launch {
            inFlight.value = true
            action()
                .onSuccess {
                    onSuccess()
                    successRes?.let { sendMessage(UiMessage(it)) }
                }
                .onFailure {
                    sendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(it.message ?: "")))
                }
            inFlight.value = false
        }
    }

    /**
     * 布尔开关骨架（[ToggleUiState]）：进行中忽略连点并保留旧展示值，成功写终态并按方向发文案，
     * 失败回滚原终态并发送 [failedMessage]（null = 默认 action_failed + 原因）。
     */
    protected fun runToggle(
        state: MutableStateFlow<ToggleUiState>,
        addedRes: Int?,
        removedRes: Int?,
        failedMessage: ((Throwable) -> UiMessage)? = null,
        action: suspend (target: Boolean) -> Result<Unit>,
    ) {
        if (state.value.inFlight) return
        viewModelScope.launch {
            // 先写 TURNING_*：UI 保留旧文案并禁用按钮
            val target = !state.value.isOn
            state.value = if (target) ToggleUiState.TURNING_ON else ToggleUiState.TURNING_OFF
            action(target)
                .onSuccess {
                    state.value = if (target) ToggleUiState.ON else ToggleUiState.OFF
                    val successRes = if (target) addedRes else removedRes
                    successRes?.let { sendMessage(UiMessage(it)) }
                }
                .onFailure {
                    // 回滚原终态，UI 无感
                    state.value = if (target) ToggleUiState.OFF else ToggleUiState.ON
                    sendMessage(
                        failedMessage?.invoke(it)
                            ?: UiMessage(CoreR.string.core_msg_action_failed, listOf(it.message ?: "")),
                    )
                }
        }
    }
}
