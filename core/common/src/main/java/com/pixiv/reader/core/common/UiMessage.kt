package com.pixiv.reader.core.common

import androidx.annotation.StringRes

enum class MessageType { INFO, SUCCESS, ERROR }

data class UiMessage(
    @param:StringRes val res: Int,
    val args: List<Any> = emptyList(),
    val type: MessageType = MessageType.INFO,
)

fun loadFailureMessage(
    failure: Throwable,
    @StringRes reasonRes: Int,
    @StringRes fallbackRes: Int,
): UiMessage = failure.message?.let { UiMessage(reasonRes, listOf(it)) } ?: UiMessage(fallbackRes)
