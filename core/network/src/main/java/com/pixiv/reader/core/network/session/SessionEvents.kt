package com.pixiv.reader.core.network.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 会话失效事件总线（refresh_token 被吊销）。
 *
 * 会话失效发生在 OkHttp 拦截器线程（[com.pixiv.api.network.TokenInterceptor] 的
 * 「Invalid refresh token」分支与 [com.pixiv.api.auth.AuthRefresher] 的刷新失败分支），
 * 那里只能同步清空 [com.pixiv.api.auth.SessionManager]，无法直接更新 UI 可观察的
 * [SessionRepository.isLoggedIn]。本总线把「已失效」这一事实桥接到可观察层：
 * - 拦截器侧调用 [notifyInvalidated]（非挂起，可在任意线程）
 * - [SessionRepository] 收集 [invalidations] 后把登录态同步为 false，UI 才会退回登录页
 *
 * 用 `extraBufferCapacity = 1` + `DROP_OLDEST`：事件只是「已经是登出态」的提醒，
 * 重复触发无副作用，也不会因无订阅者而阻塞拦截器。
 */
@Singleton
class SessionEvents @Inject constructor() {

    private val _invalidations = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 会话失效事件流（每次 refresh_token 被吊销发一次）。 */
    val invalidations: SharedFlow<Unit> = _invalidations.asSharedFlow()

    /**
     * 标记会话已失效（底层 SessionManager 已被清空）。
     *
     * @return 无返回值
     */
    fun notifyInvalidated() {
        // 非挂起发射：拦截器线程不允许阻塞，用 tryEmit + DROP_OLDEST 保证不卡请求
        _invalidations.tryEmit(Unit)
    }
}
