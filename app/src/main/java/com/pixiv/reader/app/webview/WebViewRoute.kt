package com.pixiv.reader.app.webview

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.pixiv.api.PixivConstants

/**
 * 通用内嵌 WebView 全屏页（路由 `webview?url={url}&title={title}`）：
 * 加载指定 URL（pixivision 文章原文 / pixiv COMIC / FANBOX 网页等）。
 *
 * ## 约束
 * UA 必须用 [PixivConstants.WEB_USER_AGENT]——pixiv 网页域的 cf_clearance cookie 绑定该 UA，
 * WebView 与 OkHttp 网络层保持一致才能通过人机校验；cookie 由进程内
 * `android.webkit.CookieManager` 自持（COMIC / FANBOX 的 WebView 内登录即存于此）。
 *
 * @param url 目标网页地址
 * @param title 顶栏标题（可空；空则不显示标题文字）
 * @param onBack 返回（WebView 无历史可退时触发）
 * @return 无返回值
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewRoute(
    url: String,
    title: String?,
    onBack: () -> Unit,
) {
    // 页面加载进度（0~100；100 = 完成，隐藏进度条）
    var progress by remember { mutableIntStateOf(0) }
    // WebView 实例：BackHandler 判断历史栈用
    val webView = remember { WebViewHolder() }

    BackHandler {
        val wv = webView.view
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title.orEmpty(),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (progress < 100) {
                LinearWavyProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webView.view = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // cf_clearance cookie 绑定 UA：必须与 lib:pixivapi 网络层一致
                        settings.userAgentString = PixivConstants.WEB_USER_AGENT
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                progress = 100
                            }
                        }
                        // 新窗口接管：目标网页的外链（target=_blank / window.open）加载回当前
                        // WebView；未处理时点击会被静默丢弃
                        setWebChromeClient(object : android.webkit.WebChromeClient() {
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
                                        webView.view?.loadUrl(request.url.toString())
                                        return true
                                    }
                                }
                                (resultMsg.obj as WebView.WebViewTransport).webView = transient
                                resultMsg.sendToTarget()
                                return true
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        })
                        loadUrl(url)
                    }
                },
                onRelease = { view ->
                    view.destroy()
                    webView.view = null
                },
            )
        }
    }
}

/** WebView 持有器（AndroidView factory 与 BackHandler 间共享实例，可空占位）。 */
private class WebViewHolder {
    var view: WebView? = null
}
