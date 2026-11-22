package com.pixiv.reader.feature.fanbox.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.pixiv.reader.core.network.fanbox.FanboxHeaderInterceptor
import com.pixiv.reader.feature.fanbox.R
import java.net.URI
import org.json.JSONObject

private const val PIXIV_ACCOUNTS_HOST = "accounts.pixiv.net"
private const val FANBOX_API_HOST = "api.fanbox.cc"

/**
 * FANBOX 移动版侧边栏修复脚本（onPageFinished 注入）。
 *
 * 实测（CDP）：汉堡点击的事件链与 DOM 开合状态均正常，但打开态容器高度为 0——
 * 站点样式把 overlay/wrapper 压成 0 高（inline `height:100vh !important` 也被压制），
 * 侧边栏「不可见、不可点」。布局层唯一可靠的解法是 `min-height`（盒模型下限，
 * 任何 height:0 都无法覆盖）：
 * - 打开信号 = overlay 计算样式 visibility:visible（关闭态为 hidden + translateX(-260px)；
 *   注意 visibility 带过渡延迟，class 变更瞬间读到的是旧值，故变更后延迟多次重试）；
 * - 打开且高度 < 100px 时以 inline `min-height` 强制视口高度（px 值，绕开 vh 解析）；
 * - 关闭时移除，避免隐藏遮罩层挡住页面点击。
 */
private const val FANBOX_SIDEBAR_FIX_JS = """(function(){
  if (window.__fanboxFixInstalled) return;
  window.__fanboxFixInstalled = true;
  var apply = function(){
    var o = document.querySelector('[class*="MenuModal__Overlay"]');
    var w = document.querySelector('[class*="MenuModal__MenuWrapper"]');
    if (!o || !w) return;
    var open = getComputedStyle(o).visibility === 'visible';
    var target = Math.round(window.innerHeight * 0.9) + 'px';
    if (open) {
      if (o.getBoundingClientRect().height < 100) o.style.setProperty('min-height', target, 'important');
      if (w.getBoundingClientRect().height < 100) w.style.setProperty('min-height', target, 'important');
    } else {
      o.style.removeProperty('min-height');
      w.style.removeProperty('min-height');
    }
  };
  var schedule = function(){ [0, 150, 500, 1200].forEach(function(d){ setTimeout(apply, d); }); };
  new MutationObserver(schedule).observe(document.body, {attributes:true, subtree:true, attributeFilter:['class','style']});
  schedule();
})();"""

/**
 * 解析可在 WebView 或浏览器中打开的网页地址。
 *
 * @param url 待解析的原始地址
 * @return 具有有效主机名且使用 HTTP 或 HTTPS 的地址，否则为 null
 */
private fun parseWebUrl(url: String?): URI? {
    if (url == null) return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    return uri.takeIf { (scheme == "http" || scheme == "https") && it.host != null && it.userInfo == null }
}

/**
 * 检查是否可在内置 WebView 中加载网页。
 *
 * @param url 已解析的网页地址
 * @param authFlow 是否为 FANBOX 登录流程
 * @return 地址匹配 HTTPS 白名单时为 true
 */
private fun isAllowedWebUrl(url: URI, authFlow: Boolean): Boolean {
    if (!url.scheme.equals("https", ignoreCase = true) || (url.port != -1 && url.port != 443)) return false
    val host = url.host?.lowercase() ?: return false
    // 登录流程会跳转第三方 SSO 或反爬验证域名，放行标准 HTTPS 站点以免中断登录回调。
    return if (authFlow) true else FanboxHeaderInterceptor.isAllowedWebHost(host)
}

/**
 * 通过系统浏览器打开合法的网页地址。
 *
 * @param context 用于发起跳转的上下文
 * @param url 待打开的原始地址
 * @return 无返回值
 */
private fun openExternalWebUrl(context: Context, url: String?) {
    // Intent 仅接收校验过的 HTTP(S) 地址，避免把其他协议交给外部应用。
    val target = parseWebUrl(url) ?: return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.toString()))) }
}

/**
 * FANBOX 可见 WebView 页：登录引导、创作者主页、方案页、详情网页兜底等统一入口。
 *
 * 普通浏览允许 FANBOX 站内页和 Pixiv 账号域；登录模式按精确主机名放行
 * FANBOX、Pixiv 账号页与 Pixiv 会话中转页，站外 HTTP(S) 链接交给系统浏览器。
 * WebView 启用 JS / DOM storage / 第三方 cookie（登录流程跨域种 cookie 必需）。
 * 登录完成（走过 accounts 登录域 + 回到 fanbox 域 + FANBOXSESSID 存在）时**自动关闭
 * 本页**，由首页 ON_RESUME 的自动重试刷出原生内容，用户无需手动返回。
 *
 * @param url 目标地址（非白名单域时转系统浏览器，本页仅留空白）
 * @param title 顶栏标题（null 用默认文案）
 * @param onBack 返回上一页
 * @return 无返回值
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FanboxWebRoute(
    url: String?,
    title: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.fanbox_web_title)

    // 按实际主机与路径识别登录入口，再用同一白名单校验初始地址和后续导航。
    val initialUrl = remember(url) { parseWebUrl(url) }
    val authFlow = initialUrl?.let {
        it.host.equals("www.fanbox.cc", ignoreCase = true) && it.rawPath.trimEnd('/') == "/login"
    } == true
    val allowed = initialUrl?.let { isAllowedWebUrl(it, authFlow) } == true
    LaunchedEffect(url) {
        if (initialUrl != null && !allowed) openExternalWebUrl(context, url)
    }

    // 由 WebView 的历史回调更新状态，BackHandler 随导航重新判断是否可回退。
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(resolvedTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.fanbox_cd_back))
                    }
                },
                actions = {
                    if (initialUrl != null) {
                        IconButton(onClick = {
                            openExternalWebUrl(context, url)
                        }) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = stringResource(R.string.fanbox_web_open_external))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        if (url != null && allowed) {
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(padding),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            // 视口与窗口相关（缺 WebChromeClient / 视口设置时，页面 JS 对话框、
                            // window.open、target=_blank 会静默失效——侧边栏「登录」即此类）
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            javaScriptCanOpenWindowsAutomatically = true
                            mediaPlaybackRequiresUserGesture = false
                        }
                        // this = WebView；CookieManager 是 setAcceptThirdPartyCookies 的调用方
                        CookieManager.getInstance().let { cm ->
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                        }
                        // 不启用多窗口：target=_blank 在当前视图内导航（仍走白名单分流）
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            private var sawAccountsLogin = false
                            private var loginDone = false

                            /**
                             * 按来源分流页内导航和站外链接。
                             *
                             * @param view 发起导航的 WebView
                             * @param request 待加载的导航请求
                             * @return 本页已拦截或交由系统浏览器处理时为 true
                             */
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val url = request.url.toString()
                                val target = parseWebUrl(url) ?: return true
                                if (isAllowedWebUrl(target, authFlow)) return false
                                // 子框架的站外跳转不应主动启动外部浏览器。
                                if (request.isForMainFrame) {
                                    openExternalWebUrl(ctx, url)
                                }
                                return true
                            }

                            /**
                             * 复核未经过导航回调的主文档加载。
                             *
                             * @param view 开始加载的 WebView
                             * @param url 主文档地址
                             * @param favicon 页面图标，可空
                             * @return 无返回值
                             */
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                val target = parseWebUrl(url)
                                if (target != null && isAllowedWebUrl(target, authFlow)) return
                                // 表单提交等导航可能绕过 shouldOverrideUrlLoading。
                                view.stopLoading()
                                if (target != null) openExternalWebUrl(ctx, url)
                            }

                            /**
                             * 将 WebView 的历史变更同步给系统返回键处理器。
                             *
                             * @param view 更新历史的 WebView
                             * @param url 历史记录对应的地址
                             * @param isReload 是否是当前页重载
                             * @return 无返回值
                             */
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view.canGoBack()
                            }

                            /**
                             * 在 FANBOX 网页修复侧边栏并检查 SSO 是否完成。
                             *
                             * @param view 完成导航的 WebView
                             * @param finishedUrl 回调报告的页面地址
                             * @return 无返回值
                             */
                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                super.onPageFinished(view, finishedUrl)
                                val finished = parseWebUrl(finishedUrl) ?: return
                                val page = view?.takeIf { it.url == finishedUrl } ?: return
                                if (!isAllowedWebUrl(finished, authFlow)) return
                                val finishedHost = finished.host?.lowercase() ?: return
                                if (finishedHost == PIXIV_ACCOUNTS_HOST) sawAccountsLogin = true
                                val onFanbox = finishedHost == "fanbox.cc" || finishedHost.endsWith(".fanbox.cc")
                                if (!onFanbox || finishedHost == FANBOX_API_HOST) return

                                // JS 执行时也确认来源，防止回调与下一次导航竞争时注入其他页面。
                                val origin = JSONObject.quote("https://$finishedHost")
                                page.evaluateJavascript(
                                    "if (location.origin === $origin) $FANBOX_SIDEBAR_FIX_JS",
                                    null,
                                )
                                // 登录完成检测：走过 accounts 登录域 + 已回到 fanbox 域 + FANBOXSESSID 存在。
                                // 三条件缺一不可：游客 cookie 在 fanbox 首页与选号页上都存在，只看 cookie
                                // 会在选号页提前误关
                                if (!sawAccountsLogin || loginDone) return
                                val has = runCatching {
                                    CookieManager.getInstance()
                                        .getCookie(FanboxHeaderInterceptor.FANBOX_URL)
                                        ?.contains("${FanboxHeaderInterceptor.SESSION_COOKIE}=") == true
                                }.getOrDefault(false)
                                if (has) {
                                    loginDone = true
                                    // 登录完成后立即将 WebView Cookie 刷盘，确保后续请求立即可见。
                                    android.webkit.CookieManager.getInstance().flush()
                                    // 登录完成：清掉 SSO 链历史并自动关闭本页；
                                    // 首页 ON_RESUME 自动重试即刷出原生内容，无需用户手动返回
                                    page.clearHistory()
                                    canGoBack = false
                                    onBack()
                                }
                            }
                        }
                        webView = this
                        loadUrl(url)
                    }
                },
                onRelease = {
                    webView = null
                    canGoBack = false
                    // 离开前停止加载并移出父容器，避免后台继续执行脚本或泄漏视图。
                    it.stopLoading()
                    it.webChromeClient = null
                    (it.parent as? android.view.ViewGroup)?.removeView(it)
                    it.destroy()
                },
            )
            // 随页面生命周期暂停/恢复 WebView，避免退到后台后继续计时或耗电。
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                        androidx.lifecycle.Lifecycle.Event.ON_RESUME -> webView?.onResume()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
        }
    }
}
