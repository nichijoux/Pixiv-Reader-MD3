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
 * 带操作通知通道的 ViewModel 基类（core 共享）。
 *
 * 收敛此前在 10+ 个 VM 间逐文件复制的 `Channel<UiMessage>(BUFFERED) + receiveAsFlow` 样板；
 * UI 侧配合 core:ui 的 `UiMessageEffect` 一行收集显示到 NotificationHost。
 *
 * 子类发通知：[sendMessage]（挂起，缓冲 BUFFERED）/ [trySendMessage]（非挂起，无法挂起的
 * 回调里用，缓冲满时丢弃）。收藏/关注/追更类布尔开关动作用 [runToggle]（[ToggleUiState]
 * 状态机骨架）；非开关型单次动作用 [runActionNotified]。
 */
abstract class MessageViewModel : ViewModel() {

    private val _message = Channel<UiMessage>(Channel.BUFFERED)

    /** 一次性操作通知流：UI 侧收集显示 NotificationHost。 */
    val message: Flow<UiMessage> = _message.receiveAsFlow()

    /** 发送通知（挂起直至进入缓冲）。 */
    protected suspend fun sendMessage(message: UiMessage) = _message.send(message)

    /** 发送通知（非挂起；缓冲满时丢弃，用于不可挂起的上下文）。 */
    protected fun trySendMessage(message: UiMessage) = _message.trySend(message)

    /**
     * 单次动作 + 成功文案的通用骨架：防连点 → 调用动作 → 成功执行 [onSuccess] 钩子并发
     * [successRes] 文案（null = 静默成功，如详情页已有视觉反馈）、失败发 action_failed + 原因；
     * 无论成败结束都复位防连点标志。
     *
     * @param inFlight 防连点标志流；调用时已为 true 则忽略本次
     * @param successRes 成功文案资源；null 表示成功时不发通知
     * @param onSuccess 成功后的附带动作（状态回写/关闭弹层等）；默认无
     * @param action 动作本体；失败原因附在 action_failed 文案里
     * @return 无返回值（操作完成后结束的协程）
     */
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
     * 布尔开关动作状态机骨架（[ToggleUiState] 驱动）：防连点 → 写入 TURNING_* → 以目标态调用
     * 动作 → 成功写终态（ON/OFF）并按方向发置位/复位文案（null = 静默成功）、失败回滚原终态并
     * 发 [failedMessage]（null = 默认 action_failed + 原因）；进行中状态保留旧展示值（[ToggleUiState.isOn]）。
     *
     * @param state 开关状态流（VM 持有的唯一事实源，取代「状态 + 进行中」两个布尔）
     * @param addedRes 置位成功文案（OFF → ON）；null 静默
     * @param removedRes 复位成功文案（ON → OFF）；null 静默
     * @param failedMessage 失败文案构造器（参数为失败异常；null = 默认 action_failed + 原因。
     *   需按失败种类分文案的场景——如 CSRF 不可用专用提示——传自定义构造器）
     * @param action 动作本体，参数为目标状态
     * @return 无返回值（操作完成后结束的协程）
     */
    protected fun runToggle(
        state: MutableStateFlow<ToggleUiState>,
        addedRes: Int?,
        removedRes: Int?,
        failedMessage: ((Throwable) -> UiMessage)? = null,
        action: suspend (target: Boolean) -> Result<Unit>,
    ) {
        // 防连点：进行中（TURNING_*）忽略本次
        if (state.value.inFlight) return
        viewModelScope.launch {
            // 目标态取反；先写 TURNING_*（UI 保留旧文案、禁用按钮）
            val target = !state.value.isOn
            state.value = if (target) ToggleUiState.TURNING_ON else ToggleUiState.TURNING_OFF
            action(target)
                .onSuccess {
                    // 成功：落终态 + 按方向提示
                    state.value = if (target) ToggleUiState.ON else ToggleUiState.OFF
                    val successRes = if (target) addedRes else removedRes
                    successRes?.let { sendMessage(UiMessage(it)) }
                }
                .onFailure {
                    // 失败：回滚原终态（UI 无感）+ 专用或默认失败文案
                    state.value = if (target) ToggleUiState.OFF else ToggleUiState.ON
                    sendMessage(
                        failedMessage?.invoke(it)
                            ?: UiMessage(CoreR.string.core_msg_action_failed, listOf(it.message ?: "")),
                    )
                }
        }
    }
}
