package com.pixiv.reader.core.network.fanbox

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.pixiv.reader.core.network.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 无屏（不上屏）WebView 桥：专门负责从 WebView 里发 `post.info`——该端点被 Cloudflare
 * 单独封锁非浏览器客户端（OkHttp / curl / headless Chrome 全 403，真机与 App WebView 可过）。
 *
 * 机制（对齐 Pixiv-Shaft classic `FanboxWebBridge`，已真机验证）：
 * 1. 懒建 WebView，先加载一次 `https://www.fanbox.cc/` 壳页拿到正经 origin（顺带让 CF
 *    自己种 cookie）——断网时 onPageFinished 渲染的是错误页、origin 不对，**认 URL 才算就绪**；
 * 2. 之后所有请求都在这张页面里 `fetch(credentials:'include')`，结果经
 *    `@JavascriptInterface` 桥（token 防并发串扰）回传；非 200 或超时一律返 null；
 * 3. `shouldInterceptRequest` 把 fanbox.cc 以外的子资源全掐成空响应——首屏那 5MB SPA
 *    （s.pxpx.net）一点用没有，实际只下 8KB 左右的壳；
 * 4. UA **保持 WebView 默认**，禁止换成 `WEB_USER_AGENT` 常量——它钉死在 Chrome/131，
 *    与设备 WebView 真实 TLS/HTTP2 指纹不符正是 CF 判机器人的典型信号（PRD D7）。
 *
 * 生命周期：进程单例；构造不建 WebView，首次 [get] 才懒建；空闲 [IDLE_RELEASE_MS] 后或
 * `onTrimMemory`（经 [Context.registerComponentCallbacks] 自注册）时 [release] 销毁，
 * 下次再用重建。所有 WebView 操作都在主线程（创建 / 销毁 / loadUrl / evaluateJavascript）。
 */
@Singleton
class FanboxWebBridge @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val appContext: Context = context.applicationContext

    /** 串行化请求：单 WebView 单页面，并发 fetch 会互相踩壳页就绪状态。 */
    private val requestMutex = Mutex()
    private val sequence = AtomicLong(0L)
    private val pending = ConcurrentHashMap<String, CancellableContinuation<String?>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val idleRelease = Runnable { release() }

    /** 只在主线程碰。null = 尚未建过、上次建失败（WebView 组件缺失）或已被 [release]。 */
    private var webView: WebView? = null
    private var pageLoaded = false
    private var pageWaiter: CancellableContinuation<Unit>? = null

    init {
        // 内存吃紧时主动释放：WebView 是全 App 最重的对象之一（几十 MB 独立渲染进程）
        appContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) release()
            }

            override fun onLowMemory() = release()
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
        })
    }

    /**
     * GET 一个 api.fanbox.cc 上的接口，返回响应体。
     *
     * @param url 完整接口地址（须为 fanbox.cc 域，否则不保证行为）
     * @return 响应体字符串；非 200（含 CF 拦截页）/ 超时 / WebView 不可用一律 null，
     *         调用方自行决定是否退回 OkHttp 只读元数据接口
     */
    suspend fun get(url: String): String? = withTimeoutOrNull(TIMEOUT_MS) {
        requestMutex.withLock {
            withContext(Dispatchers.Main) {
                mainHandler.removeCallbacks(idleRelease)
                try {
                    val view = ensureWebView() ?: return@withContext null
                    if (!ensurePageLoaded(view)) return@withContext null
                    fetchInPage(view, url)
                } finally {
                    // 无论成败都重起空闲倒计时；下一次 get 进来会先取消它
                    mainHandler.postDelayed(idleRelease, IDLE_RELEASE_MS)
                }
            }
        }
    }

    /**
     * 销毁 WebView 并放掉所有等待中的调用方（以 null 结束，触发 post.get 兜底）。
     * 可重复调用；释放后下一次 [get] 会重新懒建并重载壳页。仅主线程。
     */
    fun release() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { release() }
            return
        }
        mainHandler.removeCallbacks(idleRelease)
        pageWaiter?.let { waiter -> pageWaiter = null; if (waiter.isActive) waiter.resume(Unit) }
        pending.keys.toList().forEach { token ->
            pending.remove(token)?.let { if (it.isActive) it.resume(null) }
        }
        pageLoaded = false
        val view = webView ?: return
        webView = null
        // 先摘 bridge 再 destroy：destroy 之后 JS 仍可能回调一次，别让它碰到已清空的表
        view.removeJavascriptInterface(BRIDGE_NAME)
        view.stopLoading()
        view.webViewClient = WebViewClient()
        view.destroy()
        Log.i(TAG, "released")
    }

    /**
     * 懒建 WebView（仅主线程）。WebView 在少数机型上会因组件正在升级直接抛——不能让
     * FANBOX 页跟着崩。
     *
     * @return 就绪的 WebView；创建失败返回 null
     */
    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun ensureWebView(): WebView? {
        webView?.let { return it }
        val view = runCatching { WebView(appContext) }.getOrElse {
            Log.w(TAG, "WebView 不可用: $it")
            return null
        }
        if (BuildConfig.DEBUG) {
            // 这张页面不上屏，排查只能靠 chrome://inspect——正文链路整个依赖 CF 认不认这个 WebView
            WebView.setWebContentsDebuggingEnabled(true)
        }
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // UA 保持 WebView 默认（见类 KDoc 第 4 点，PRD D7）
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
        view.addJavascriptInterface(JsBridge(), BRIDGE_NAME)
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 断网时 WebView 也会 onPageFinished（渲染错误页），认 URL 才算壳页就绪，
                // 否则后面每次 fetch 都吃 CORS
                pageLoaded = url.orEmpty().startsWith(ORIGIN)
                pageWaiter?.let { waiter -> pageWaiter = null; waiter.resume(Unit) }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                // 主文档挂了就别让调用方干等超时，立即放行走兜底
                if (request?.isForMainFrame == true) {
                    pageWaiter?.let { waiter -> pageWaiter = null; waiter.resume(Unit) }
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val host = request?.url?.host
                // 放行整个 fanbox.cc——api.fanbox.cc 是这张页面存在的理由，掐掉的话 fetch
                // 拿到本地伪造空响应、报 CORS 错误，与被 CF 挡了一模一样、极难分辨。
                // 其余全掐：s.pximg.net 的 SPA、GA、Twitter widget 一个都用不上。
                if (host != null && host != FANBOX_DOMAIN && !host.endsWith(".$FANBOX_DOMAIN")) {
                    return EMPTY_RESPONSE
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        webView = view
        return view
    }

    /**
     * 确保壳页已加载（首次调用真的去加载一次；之后复用同一张页面）。
     *
     * @param view 就绪的 WebView
     * @return 壳页 origin 校验通过为 true；加载失败 / 超时 false
     */
    private suspend fun ensurePageLoaded(view: WebView): Boolean {
        if (pageLoaded) return true
        suspendCancellableCoroutine { cont ->
            pageWaiter = cont
            cont.invokeOnCancellation { pageWaiter = null }
            view.loadUrl(ORIGIN)
        }
        return pageLoaded
    }

    /**
     * 在壳页上下文里执行 fetch 并等待 JS 桥回传。
     *
     * @param view 壳页就绪的 WebView
     * @param url 目标接口地址
     * @return 响应体；非 200 / 异常 null
     */
    private suspend fun fetchInPage(view: WebView, url: String): String? {
        val token = "fb-${sequence.incrementAndGet()}"
        return suspendCancellableCoroutine { cont ->
            pending[token] = cont
            cont.invokeOnCancellation { pending.remove(token) }
            view.evaluateJavascript(fetchScript(token, url), null)
        }
    }

    /**
     * 生成页内 fetch 脚本（token 防并发串扰；URL 做引号转义）。
     *
     * @param token 本次请求的回传令牌
     * @param url 目标接口地址
     * @return 可执行 JS 片段
     */
    private fun fetchScript(token: String, url: String): String {
        val safeUrl = url.replace("\\", "\\\\").replace("'", "\\'")
        return """
            (function(){
                var t = '$token';
                fetch('$safeUrl', { credentials: 'include', headers: { 'Accept': 'application/json' } })
                    .then(function(r){
                        return r.text().then(function(body){ $BRIDGE_NAME.onResult(t, r.status, body); });
                    })
                    .catch(function(e){ $BRIDGE_NAME.onResult(t, -1, String(e)); });
            })();
        """.trimIndent()
    }

    /** JS 回调落点：非 static 内部类，回调落回本 bridge 实例的 pending 表。 */
    private inner class JsBridge {
        /** 页内 fetch 结果回传（JS 线程调用；resume 线程安全）。 */
        @JavascriptInterface
        fun onResult(token: String, status: Int, body: String) {
            val cont = pending.remove(token) ?: return
            if (status != 200) Log.w(TAG, "request failed, status=$status")
            if (cont.isActive) cont.resume(body.takeIf { status == 200 })
        }
    }

    private companion object {
        const val TAG = "FanboxWebBridge"
        const val ORIGIN = FanboxHeaderInterceptor.FANBOX_URL
        const val FANBOX_DOMAIN = "fanbox.cc"
        const val BRIDGE_NAME = "FanboxNativeBridge"
        const val TIMEOUT_MS = 25_000L

        /**
         * 空闲多久后销毁 WebView：60s 覆盖「看完一篇回列表点下一篇」的间隔，
         * 又不让渲染进程在后台白占几十 MB。
         */
        const val IDLE_RELEASE_MS = 60_000L

        /** 掐子资源用的空响应（给 200 而非错误码，避免个别机型打 console 噪音）。 */
        val EMPTY_RESPONSE: WebResourceResponse
            get() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}
