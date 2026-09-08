package com.pixiv.reader.core.network.action

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** 离线队列失败分流策略单测（网络/服务端/明确拒绝三类异常的处置决策）。 */
class QueuePolicyTest {

    // ── 异常分类 ─────────────────────────────────────────────────────────────

    @Test
    fun `IOException 族识别为网络错误`() {
        val samples = listOf<IOException>(
            UnknownHostException("dns"),
            ConnectException("refused"),
            SocketTimeoutException("timeout"),
            IOException("token refresh failed"),
        )
        samples.forEach { assertTrue("$it 应为网络错误", QueuePolicy.isNetworkError(it)) }
    }

    @Test
    fun `HttpException 与普通异常不是网络错误`() {
        assertFalse(QueuePolicy.isNetworkError(httpException(500)))
        assertFalse(QueuePolicy.isNetworkError(IllegalStateException("x")))
    }

    @Test
    fun `429 与 5xx 识别为服务端暂不可用`() {
        assertTrue(QueuePolicy.isServerError(httpException(429)))
        assertTrue(QueuePolicy.isServerError(httpException(500)))
        assertTrue(QueuePolicy.isServerError(httpException(503)))
    }

    @Test
    fun `其他 4xx 不是服务端暂不可用`() {
        listOf(400, 401, 403, 404, 422, 428).forEach { code ->
            assertFalse("$code 不应视为服务端暂不可用", QueuePolicy.isServerError(httpException(code)))
        }
        assertFalse(QueuePolicy.isServerError(UnknownHostException("dns")))
    }

    // ── 直连失败处置 ──────────────────────────────────────────────────────────

    @Test
    fun `直连时网络与服务端失败转离线队列`() {
        assertEquals(QueuePolicy.Direct.ENQUEUE, QueuePolicy.onDirectFailure(SocketTimeoutException("t")))
        assertEquals(QueuePolicy.Direct.ENQUEUE, QueuePolicy.onDirectFailure(httpException(503)))
        assertEquals(QueuePolicy.Direct.ENQUEUE, QueuePolicy.onDirectFailure(httpException(429)))
    }

    @Test
    fun `直连时明确拒绝传播失败`() {
        assertEquals(QueuePolicy.Direct.FAIL, QueuePolicy.onDirectFailure(httpException(400)))
        assertEquals(QueuePolicy.Direct.FAIL, QueuePolicy.onDirectFailure(IllegalStateException("bad")))
    }

    // ── 补发失败处置 ──────────────────────────────────────────────────────────

    @Test
    fun `补发时网络与服务端失败稍后重试`() {
        assertEquals(QueuePolicy.Drain.RETRY_LATER, QueuePolicy.onDrainFailure(UnknownHostException("dns")))
        assertEquals(QueuePolicy.Drain.RETRY_LATER, QueuePolicy.onDrainFailure(httpException(500)))
    }

    @Test
    fun `补发时明确拒绝标记失败`() {
        assertEquals(QueuePolicy.Drain.MARK_FAILED, QueuePolicy.onDrainFailure(httpException(403)))
        assertEquals(QueuePolicy.Drain.MARK_FAILED, QueuePolicy.onDrainFailure(NullPointerException("npe")))
    }

    /** 构造指定状态码的 HttpException（retrofit Response 不可为 null body，用空 body 占位）。 */
    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, okhttp3.ResponseBody.create(null, "")))
}
