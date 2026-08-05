package com.pixiv.reader.core.common

import java.util.Locale

/**
 * 应用语言常量与映射工具。
 *
 * 存储值（[com.pixiv.reader.core.datastore.UserPreferences.appLanguage]）：
 * - [SYSTEM] 跟随系统
 * - [ZH] 简体中文
 * - [EN] 英语
 *
 * 网络语言由 [pixivLanguageCode] 映射为 pixiv 接口所需的 `accept-language`/`lang` 值。
 * 数字格式化等纯函数读取 [Locale.getDefault]（在 MainActivity.attachBaseContext 中一并设置）。
 */
object AppLanguage {
    const val SYSTEM = "system"
    const val ZH = "zh"
    const val EN = "en"
}

/**
 * 将存储值转为 [Locale]，[AppLanguage.SYSTEM] 返回 null（表示沿用系统配置，不强制覆盖）。
 */
fun localeFor(appLanguage: String): Locale? = when (appLanguage) {
    AppLanguage.ZH -> Locale("zh")
    AppLanguage.EN -> Locale("en")
    else -> null
}

/**
 * 将 [Locale] 映射为 pixiv 接口的语言代码（accept-language 头 / lang 查询参数）。
 * 未匹配的语言回退为 英语，保证接口可用。
 */
fun pixivLanguageCode(locale: Locale): String = when {
    locale.language.equals("zh", ignoreCase = true) -> "zh-CN"
    locale.language.equals("ja", ignoreCase = true) -> "ja"
    else -> "en"
}