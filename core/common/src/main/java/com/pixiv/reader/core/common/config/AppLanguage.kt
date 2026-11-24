package com.pixiv.reader.core.common.config

import java.util.Locale

object AppLanguage {
    const val SYSTEM = "system"
    const val ZH = "zh"
    const val ZH_TW = "zh-TW"
    const val EN = "en"
}

/** [AppLanguage.SYSTEM] 返回 null，表示沿用系统 Locale 不强制覆盖。 */
fun localeFor(appLanguage: String): Locale? = when (appLanguage) {
    AppLanguage.ZH -> Locale.forLanguageTag("zh")
    AppLanguage.ZH_TW -> Locale.forLanguageTag("zh-TW")
    AppLanguage.EN -> Locale.forLanguageTag("en")
    else -> null
}

/** 映射为 pixiv 接口的 accept-language / lang 值，未匹配回退 en。 */
fun pixivLanguageCode(locale: Locale): String = when {
    locale.language.equals("zh", ignoreCase = true) && (
        locale.script.equals("Hant", ignoreCase = true) ||
            locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")
        ) -> "zh-TW"
    locale.language.equals("zh", ignoreCase = true) -> "zh-CN"
    locale.language.equals("ja", ignoreCase = true) -> "ja"
    else -> "en"
}