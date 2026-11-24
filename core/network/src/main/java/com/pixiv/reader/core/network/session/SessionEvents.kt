package com.pixiv.reader.core.network.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 会话失效事件总线（refresh_token 被吊销）：拦截器线程（TokenInterceptor / AuthRefresher）只能同步
 * 清空 SessionManager，无法更新可观察的 [SessionRepository.isLoggedIn]，经本总线桥接到可观察层。
 *
 * extraBufferCapacity=1 + DROP_OLDEST：事件只是「已是登出态」的提醒，重复触发无副作用也不阻塞拦截器。
 */
@Singleton
class SessionEvents @Inject constructor() {

    private val _invalidations = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val invalidations: SharedFlow<Unit> = _invalidations.asSharedFlow()

    /** 标记会话已失效（底层 SessionManager 已被清空）。 */
    fun notifyInvalidated() {
        // 非挂起发射：拦截器线程不允许阻塞，用 tryEmit + DROP_OLDEST 保证不卡请求
        _invalidations.tryEmit(Unit)
    }
}
