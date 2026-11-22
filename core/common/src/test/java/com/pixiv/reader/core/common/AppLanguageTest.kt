package com.pixiv.reader.core.common

import com.pixiv.reader.core.common.config.pixivLanguageCode
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    /**
     * 验证 [pixivLanguageCode] 按语言/脚本/地区正确映射为 pixiv 接口语言码与英文兜底。
     *
     * @return 无返回值
     */
    @Test
    fun `pixivLanguageCode 正确映射简繁中日英与兜底语言`() {
        assertEquals("zh-CN", pixivLanguageCode(Locale.forLanguageTag("zh-CN")))
        assertEquals("zh-TW", pixivLanguageCode(Locale.forLanguageTag("zh-TW")))
        assertEquals("zh-TW", pixivLanguageCode(Locale.forLanguageTag("zh-HK")))
        assertEquals("zh-TW", pixivLanguageCode(Locale.forLanguageTag("zh-MO")))
        assertEquals("zh-TW", pixivLanguageCode(Locale.forLanguageTag("zh-Hant")))
        assertEquals("ja", pixivLanguageCode(Locale.forLanguageTag("ja")))
        assertEquals("en", pixivLanguageCode(Locale.forLanguageTag("en")))
        assertEquals("en", pixivLanguageCode(Locale.forLanguageTag("fr")))
    }
}
