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
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
import org.json.JSONObject

/**
 * 无屏 WebView 桥：绕过 Cloudflare 对非浏览器客户端访问 `post.info` 的 403 封锁。
 * 懒建后加载 `www.fanbox.cc/` 壳页获取真实 origin（拦截无关子资源、保持默认 UA 防指纹冲突），
 * 再页内 `fetch(credentials:'include')` 经 JS 桥回传；仅主线程操作，空闲 [IDLE_RELEASE_MS] 或内存吃紧时销毁。
 */
@Singleton
class FanboxWebBridge @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val appContext: Context = context.applicationContext

    // 单 WebView 单页面，串行化避免并发请求互相踩壳页就绪状态
    private val requestMutex = Mutex()
    private val pending = ConcurrentHashMap<String, CancellableContinuation<String?>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val idleRelease = Runnable { release() }

    // 仅主线程访问
    private var webView: WebView? = null
    private var pageLoaded = false
    private var pageWaiter: CancellableContinuation<Unit>? = null

    init {
        // WebView 渲染进程占内存高，内存吃紧时主动释放
        appContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) release()
            }

            override fun onLowMemory() = release()
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
        })
    }

    /** 经壳页 GET 指定 `api.fanbox.cc` HTTPS 接口；地址非法、非 200、超时或 WebView 不可用返回 null。 */
    suspend fun get(url: String): String? = withTimeoutOrNull(TIMEOUT_MS) {
        val apiUri = secureFanboxUri(url, API_HOST) ?: return@withTimeoutOrNull null
        // 规范化路径避免双斜杠依赖服务端重定向
        val apiUrl = apiUri.normalize().toASCIIString()
        requestMutex.withLock {
            withContext(Dispatchers.Main) {
                mainHandler.removeCallbacks(idleRelease)
                try {
                    val view = ensureWebView() ?: return@withContext null
                    if (!ensurePageLoaded(view)) return@withContext null
                    fetchInPage(view, apiUrl)
                } finally {
                    mainHandler.postDelayed(idleRelease, IDLE_RELEASE_MS)
                }
            }
        }
    }

    /** 销毁 WebView 并将所有等待中的请求以 null 唤醒；可跨线程重复调用，实际在主线程执行。 */
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
        // 先摘 bridge 再 destroy，避免销毁后残留 JS 回调命中已清空的表
        view.removeJavascriptInterface(BRIDGE_NAME)
        view.stopLoading()
        view.webViewClient = WebViewClient()
        view.destroy()
        Log.i(TAG, "released")
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun ensureWebView(): WebView? {
        webView?.let { return it }
        val view = runCatching { WebView(appContext) }.getOrElse {
            Log.w(TAG, "WebView 不可用: $it")
            return null
        }
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            // 必须保持 WebView 默认 UA，禁换 WEB_USER_AGENT 以免 TLS 指纹不符触发 CF 风控
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
        view.addJavascriptInterface(JsBridge(), BRIDGE_NAME)
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                secureFanboxUri(request.url.toString(), SHELL_HOST) == null

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 断网错误页同样触发 onPageFinished，须双向校验来源才算壳页就绪
                pageLoaded = secureFanboxUri(url, SHELL_HOST) != null &&
                    secureFanboxUri(view?.url, SHELL_HOST) != null
                pageWaiter?.let { waiter ->
                    pageWaiter = null
                    if (waiter.isActive) waiter.resume(Unit)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                // 主文档失败立即唤醒等待方走兜底，不等超时
                if (request?.isForMainFrame == true) {
                    pageWaiter?.let { waiter ->
                        pageWaiter = null
                        if (waiter.isActive) waiter.resume(Unit)
                    }
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url?.toString()
                // 仅放行壳页与 API 子资源，拦截无关 SPA 静态资源减少流量和开销
                val allowed = secureFanboxUri(url, SHELL_HOST) != null ||
                    (request?.isForMainFrame == false && secureFanboxUri(url, API_HOST) != null)
                if (!allowed) return EMPTY_RESPONSE
                return super.shouldInterceptRequest(view, request)
            }
        }
        webView = view
        return view
    }

    private suspend fun ensurePageLoaded(view: WebView): Boolean {
        if (pageLoaded && secureFanboxUri(view.url, SHELL_HOST) != null) return true
        pageLoaded = false
        suspendCancellableCoroutine { cont ->
            pageWaiter = cont
            cont.invokeOnCancellation {
                if (pageWaiter === cont) pageWaiter = null
            }
            view.loadUrl(ORIGIN)
        }
        return pageLoaded
    }

    private suspend fun fetchInPage(view: WebView, url: String): String? {
        val token = UUID.randomUUID().toString()
        return suspendCancellableCoroutine { cont ->
            pending[token] = cont
            cont.invokeOnCancellation { pending.remove(token) }
            view.evaluateJavascript(fetchScript(token, url), null)
        }
    }

    private fun fetchScript(token: String, url: String): String {
        val quotedToken = JSONObject.quote(token)
        val quotedUrl = JSONObject.quote(url)
        val quotedOrigin = JSONObject.quote(FanboxHeaderInterceptor.FANBOX_ORIGIN)
        // 执行时复核壳页来源，并阻止带凭证 fetch 跟随重定向
        return """
            (function(){
                if (location.origin !== $quotedOrigin) return;
                var t = $quotedToken;
                fetch($quotedUrl, { credentials: 'include', redirect: 'error', headers: { 'Accept': 'application/json' } })
                    .then(function(r){
                        return r.text().then(function(body){ $BRIDGE_NAME.onResult(t, r.status, body); });
                    })
                    .catch(function(e){ $BRIDGE_NAME.onResult(t, -1, String(e)); });
            })();
        """.trimIndent()
    }

    private fun secureFanboxUri(url: String?, host: String): URI? {
        if (url == null) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        return uri.takeIf {
            it.scheme.equals("https", ignoreCase = true) && it.host.equals(host, ignoreCase = true) &&
                it.userInfo == null && (it.port == -1 || it.port == 443)
        }
    }

    private inner class JsBridge {
        // JS 线程回调；CancellableContinuation.resume 线程安全
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
        const val SHELL_HOST = "www.fanbox.cc"
        const val API_HOST = "api.fanbox.cc"
        const val BRIDGE_NAME = "FanboxNativeBridge"
        const val TIMEOUT_MS = 25_000L
        const val IDLE_RELEASE_MS = 60_000L

        // 返回 200 空响应而非错误码，避免部分机型刷 console 噪音
        val EMPTY_RESPONSE: WebResourceResponse
            get() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}
