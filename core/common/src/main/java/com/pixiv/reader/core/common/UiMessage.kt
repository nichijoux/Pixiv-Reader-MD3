package com.pixiv.reader.core.common

import androidx.annotation.StringRes

/** 一次性消息的提示类型（UI 侧映射到 core:ui 的 NotificationType）。 */
enum class MessageType { INFO, SUCCESS, ERROR }

/**
 * 一次性 UI 事件（ViewModel → UI）。
 *
 * 文案资源支持 i18n；UI 侧 `context.getString(msg.res, *msg.args)` 解析后交给
 * NotificationHost，并按 [type] 映射通知类型（成功/失败用对应图标与语义色）。
 */
data class UiMessage(
    @param:StringRes val res: Int,
    val args: List<Any> = emptyList(),
    val type: MessageType = MessageType.INFO,
)

/**
 * 加载失败消息映射：异常带原因（`message` 非空）发 [reasonRes] 并附原因，否则发 [fallbackRes]。
 * 收敛详情页/阅读器 VM 逐文件复制的同体 `message?.let ?: fallback` 样板。
 */
fun loadFailureMessage(
    failure: Throwable,
    @StringRes reasonRes: Int,
    @StringRes fallbackRes: Int,
): UiMessage = failure.message?.let { UiMessage(reasonRes, listOf(it)) } ?: UiMessage(fallbackRes)
