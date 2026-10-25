package com.pixiv.api.network

import com.pixiv.api.PixivConstants
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RequestNonce] 时间格式契约回归：
 * - 服务端要求 `yyyy-MM-dd'T'HH:mm:ss±HH:MM`（零偏移必须是 `+00:00`，不能是 `Z`）
 * - hash 恒为 `md5(time + CLIENT_TIME_SECRET)`
 *
 * 时区相关用例通过切换 JVM 默认时区模拟不同设备环境，finally 中恢复原值避免污染其他用例。
 */
class RequestNonceTest {

    /** 时间串格式：秒级时间 + 带冒号的两位偏移（不接受 `Z` 形式）。 */
    private val timePattern = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+-]\d{2}:\d{2}""")

    @Test
    fun `默认时区时间串符合服务端格式且不以 Z 结尾`() {
        val nonce = RequestNonce.build()
        assertTrue("时间串格式不符: ${nonce.time}", timePattern.matches(nonce.time))
        assertFalse("零偏移不得输出 Z: ${nonce.time}", nonce.time.endsWith("Z"))
    }

    @Test
    fun `UTC 零偏移输出加号形式的 +00 00`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val nonce = RequestNonce.build()
            assertTrue("UTC 应为 +00:00: ${nonce.time}", nonce.time.endsWith("+00:00"))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `东京时区输出 +09 00 且 hash 为 md5 时间加密钥`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val nonce = RequestNonce.build()
            assertTrue("东京应为 +09:00: ${nonce.time}", nonce.time.endsWith("+09:00"))
            assertEquals(md5(nonce.time + PixivConstants.CLIENT_TIME_SECRET), nonce.hash)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
