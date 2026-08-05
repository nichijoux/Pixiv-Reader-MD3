package com.pixiv.reader.core.common

import androidx.annotation.StringRes

/**
 * 一次性 UI 文案事件（ViewModel → UI）。
 *
 * 资源化以支持 i18n：UI 层用 `stringResource(msg.res, *msg.args)` 解析。
 * 动态内容（如服务端异常文案）作为 [args] 传入对应模板占位符 `%1$s`。
 */
data class UiMessage(@StringRes val res: Int, val args: List<Any> = emptyList())