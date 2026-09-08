package com.pixiv.reader.feature.fanbox.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pixiv.api.PixivConstants
import com.pixiv.reader.feature.fanbox.R
import com.pixiv.reader.feature.fanbox.state.FanboxLoginViewModel

/**
 * FANBOX 内嵌登录页：WebView 加载 fanbox（未登录自动跳 pixiv 登录），
 * 登录成功（cookie 出现 FANBOX_SESSIONID）后持久化 FANBOX 域 cookie 并回调进入列表。
 *
 * cookie 由进程内 CookieManager 自持；UA 与网络层网页 UA 一致（cf_clearance 绑定）。
 *
 * @param onLoginSuccess 登录成功并已持久化 cookie
 * @param onBack 用户主动返回
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FanboxLoginRoute(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: FanboxLoginViewModel = hiltViewModel(),
) {
    // WebView 历史栈（返回键优先页内回退）
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else onBack()
    }

    // 轮询登录态：cookie 出现 FANBOX_SESSIONID 即视为登录成功
    LaunchedEffect(Unit) {
        while (true) {
            if (viewModel.checkAndSave()) {
                onLoginSuccess()
                return@LaunchedEffect
            }
            viewModel.waitNextPoll()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fanbox_login_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.fanbox_cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = PixivConstants.WEB_USER_AGENT
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            canGoBack = view?.canGoBack() == true
                        }
                    }
                    // 新窗口接管：fanbox 网页的菜单/链接多为 target=_blank / window.open，
                    // 未处理时点击会被静默丢弃（表现为毫无反应）
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message,
                        ): Boolean {
                            val transient = WebView(view.context)
                            transient.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: android.webkit.WebResourceRequest,
                                ): Boolean {
                                    // 把新窗口的目标 URL 加载回当前 WebView
                                    webView?.loadUrl(request.url.toString())
                                    return true
                                }
                            }
                            (resultMsg.obj as WebView.WebViewTransport).webView = transient
                            resultMsg.sendToTarget()
                            return true
                        }
                    }
                    loadUrl("https://www.fanbox.cc/")
                }
            },
            onRelease = { view ->
                CookieManager.getInstance().flush()
                view.destroy()
                webView = null
            },
            update = { view ->
                webView = view
            },
        )
    }
}
