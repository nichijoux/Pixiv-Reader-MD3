package com.pixiv.reader.feature.fanbox.ui

import android.content.Intent
import android.util.Log
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

private const val TAG = "FanboxWebRoute"

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
 * FANBOX 可见 WebView 页：登录引导、创作者主页、方案页、详情网页兜底等统一入口。
 *
 * 约束（PRD FR-8）：加载前做 host 白名单校验——仅 `*.fanbox.cc` 与 `accounts.pixiv.net`
 * 在本页加载，站外链接一律转系统浏览器；**登录模式**（入口为 [FanboxHeaderInterceptor.FANBOX_LOGIN_URL]）
 * 额外放行 `*.pixiv.net`——选号「继续使用此账号」可能经 www.pixiv.net 做会话中转，
 * 拦下转系统浏览器会导致 SSO 凭证落进浏览器而非 App CookieManager，登录必然失败。
 * WebView 启用 JS / DOM storage / 第三方 cookie（登录流程跨域种 cookie 必需）。
 * 登录完成（走过 accounts 登录域 + 回到 fanbox 域 + FANBOXSESSID 存在）时**自动关闭
 * 本页**，由首页 ON_RESUME 的自动重试刷出原生内容，用户无需手动返回。
 *
 * @param url 目标地址（非白名单域时转系统浏览器，本页仅留空白）
 * @param title 顶栏标题（null 用默认文案）
 * @param onBack 返回上一页
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

    // host 白名单：非白名单域不进 WebView，转系统浏览器（本页留空白）
    val host = url?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }
    val allowed = FanboxHeaderInterceptor.isAllowedWebHost(host)
    // 登录模式：整个 pixiv 域族放行（SSO 会话中转可能经过 www.pixiv.net 等）
    val authFlow = url?.startsWith(FanboxHeaderInterceptor.FANBOX_LOGIN_URL) == true
    LaunchedEffect(url) {
        if (url != null && !allowed) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        }
    }

    // WebView 引用：BackHandler 据此决定回退历史还是退出页面
    var webView by remember { mutableStateOf<WebView?>(null) }
    // 登录完成检测：直接访问 fanbox.cc 也会种下游客 FANBOXSESSID（非登录态），且选号页
    // （accounts 域）上游客 cookie 同样存在——必须「走过 accounts 登录域 + 已回到 fanbox 域 +
    // cookie 存在」三条件同时满足才判定完成，随后自动关闭本页回到原生（首页自动重试刷新）
    var sawAccountsLogin by remember { mutableStateOf(false) }
    var loginDone by remember { mutableStateOf(false) }

    BackHandler(enabled = webView?.canGoBack() == true) {
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
                    if (url != null) {
                        IconButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
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
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val target = request.url
                                // 登录模式：照搬 Shaft 的通用 WebView——零拦截，任何 http/https
                                // 都在页内加载。SSO 链会经过无法穷举的中间域，踢去系统浏览器 =
                                // 凭证落进浏览器 cookie 库而 App 的 CookieManager 拿不到，登录必败
                                if (authFlow) {
                                    if (target.scheme == "http" || target.scheme == "https") {
                                        Log.d(TAG, "fanbox login nav: $target")
                                        return false
                                    }
                                    return true
                                }
                                // 普通模式：只对 http/https 做白名单分流；其余 scheme（javascript:/
                                // 页内 # 锚点等）交给 WebView 自己处理，避免误拦页内导航
                                if (target.scheme != "http" && target.scheme != "https") return true
                                if (FanboxHeaderInterceptor.isAllowedWebHost(target.host)) return false
                                // 站外 http/https：转系统浏览器（留痕便于排查误踢）
                                Log.w(TAG, "fanbox web → browser: $target")
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, target)) }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                super.onPageFinished(view, finishedUrl)
                                // 移动版侧边栏修复：菜单容器在本 WebView 内打开态高度为 0（不可见），
                                // 打开时以 inline !important 强制视口高度、关闭时撤除（防止遮罩挡住页面点击）
                                view?.evaluateJavascript(FANBOX_SIDEBAR_FIX_JS, null)
                                // 登录完成检测：走过 accounts 登录域 + 已回到 fanbox 域 + FANBOXSESSID 存在。
                                // 三条件缺一不可：游客 cookie 在 fanbox 首页与选号页上都存在，只看 cookie
                                // 会在选号页提前误关
                                val finishedHost = finishedUrl?.let {
                                    runCatching { android.net.Uri.parse(it).host }.getOrNull()
                                }
                                if (finishedHost == "accounts.pixiv.net") sawAccountsLogin = true
                                val onFanbox = finishedHost != null &&
                                    (finishedHost == "fanbox.cc" || finishedHost.endsWith(".fanbox.cc"))
                                val has = runCatching {
                                    CookieManager.getInstance()
                                        .getCookie(FanboxHeaderInterceptor.FANBOX_URL)
                                        ?.contains("${FanboxHeaderInterceptor.SESSION_COOKIE}=") == true
                                }.getOrDefault(false)
                                if (has && sawAccountsLogin && onFanbox && !loginDone) {
                                    loginDone = true
                                    // 登录完成：清掉 SSO 链历史并自动关闭本页；
                                    // 首页 ON_RESUME 自动重试即刷出原生内容，无需用户手动返回
                                    view?.clearHistory()
                                    onBack()
                                }
                            }
                        }
                        webView = this
                        loadUrl(url)
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
