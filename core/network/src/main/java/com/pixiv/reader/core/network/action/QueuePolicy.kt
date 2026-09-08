package com.pixiv.reader.core.network.action

import java.io.IOException
import retrofit2.HttpException

/**
 * 离线队列失败分流策略（纯函数，可单测）：
 * 区分「网络不通 / 服务端暂不可用」（应入队或稍后重试）与「服务端明确拒绝」（应直接失败）。
 *
 * - 网络类异常 = `IOException` 族（UnknownHost / SocketTimeout / Connect 等 OkHttp 层抛出）；
 * - 服务端类 = HTTP 429 / 5xx（限流或服务端故障，稍后重试有意义）；
 * - 其余 `HttpException`（4xx 等）为服务端明确拒绝（参数无效 / 权限变化等），重试无意义。
 */
internal object QueuePolicy {

    /** 直连（在线即时执行）失败后的处置。 */
    enum class Direct {
        /** 网络类 / 服务端类失败 → 转入离线队列（对调用方表现为成功，UI 乐观翻转）。 */
        ENQUEUE,

        /** 服务端明确拒绝 → 向调用方传播失败（VM 弹错误提示，状态不翻转）。 */
        FAIL,
    }

    /** 补发（drain 泵）失败后的处置。 */
    enum class Drain {
        /** 网络断开 / 服务端暂不可用 → 中断本轮补发，保留队列等待下次触发。 */
        RETRY_LATER,

        /** 服务端明确拒绝 → 标记该条 failed（保留待用户在管理页重试 / 删除），继续下一条。 */
        MARK_FAILED,
    }

    /** 是否网络类异常（断网 / 超时 / 连接失败）。 */
    fun isNetworkError(e: Throwable): Boolean = e is IOException

    /** 是否服务端暂不可用（429 限流 / 5xx 故障）。 */
    fun isServerError(e: Throwable): Boolean =
        e is HttpException && (e.code() == 429 || e.code() in 500..599)

    /**
     * 直连失败的处置决策。
     *
     * @param e 直连执行抛出的异常
     * @return ENQUEUE=转离线队列；FAIL=向调用方传播失败
     */
    fun onDirectFailure(e: Throwable): Direct = if (isNetworkError(e) || isServerError(e)) Direct.ENQUEUE else Direct.FAIL

    /**
     * 补发失败的处置决策。
     *
     * @param e 补发执行抛出的异常
     * @return RETRY_LATER=中断本轮等待下次；MARK_FAILED=标记失败并继续处理后续条目
     */
    fun onDrainFailure(e: Throwable): Drain = when {
        isNetworkError(e) || isServerError(e) -> Drain.RETRY_LATER
        else -> Drain.MARK_FAILED
    }
}
