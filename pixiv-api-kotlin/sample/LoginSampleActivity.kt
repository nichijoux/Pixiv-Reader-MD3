package com.example.pixivapi.sample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.example.pixivapi.PixivApi
import com.example.pixivapi.auth.PixivAuthResult
import kotlinx.coroutines.launch

/**
 * 登录示例 Demo Activity
 *
 * 完整复刻 Pixiv-Shaft 的 PKCE 登录链路（`FragmentLogin` → `OutWakeActivity`）：
 *
 * ```
 * 点登录 → startLoginUrl() → Chrome Custom Tab → pixiv://account/login?code=... → 本 Activity
 *        → isOAuthCallback 判断 → code 单次性去重 → handleCallback() 换 token
 *        → saveSession() 保存登录态
 * ```
 *
 * 需要：
 * - 依赖 `androidx.browser:browser`（Custom Tabs）与 `androidx.appcompat`
 * - AndroidManifest.xml 为回调 scheme 注册 intent-filter：
 * ```xml
 * <activity android:name=".LoginSampleActivity"
 *           android:launchMode="singleTask">
 *     <intent-filter>
 *         <action android:name="android.intent.action.VIEW" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *         <category android:name="android.intent.category.BROWSABLE" />
 *         <data android:scheme="pixiv" />
 *     </intent-filter>
 * </activity>
 * ```
 * - 通过 `(application as SampleApp).pixiv` 拿到 [PixivApi] 实例
 */
class LoginSampleActivity : AppCompatActivity() {

    private lateinit var pixiv: PixivApi
    private lateinit var loginButton: Button
    private lateinit var statusText: TextView

    /**
     * code 单次性去重（对齐 Shaft OutWakeActivity.sHandledLoginCode）：
     * OAuth 授权码是单次性的，配置变化(旋转/深色模式)会用同一个回调 intent
     * 重跑 onNewIntent，二次提交 code 必被 Pixiv 拒成「不正确的请求」。
     */
    private var handledLoginCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 示例：自己搭一个简单的界面（正式项目用 XML 布局即可）
        loginButton = Button(this).apply { text = "登录" }
        statusText = TextView(this).apply {
            text = "未登录"
            textSize = 16f
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusText)
            addView(loginButton)
        })

        // 从 Application / DI 容器获取 PixivApi（见 PixivApi.create 示例）
        pixiv = (application as SampleApp).pixiv

        loginButton.setOnClickListener { openLoginPage() }

        // 冷启动路径：应用被杀后系统可能直接把回调 intent 带给新 Activity
        handleLoginIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 热启动路径：Chrome Custom Tab 回调
        handleLoginIntent(intent)
    }

    // ── 登录 ─────────────────────────────────────────────────────────────

    private fun openLoginPage() {
        val url = pixiv.oauth.startLoginUrl()
        CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
    }

    private fun handleLoginIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (!pixiv.oauth.isOAuthCallback(uri)) return

        val code = uri.getQueryParameter("code")
        if (code != null && code == handledLoginCode) {
            // 已处理过的 code：直接判断登录态，避免重复提交
            if (pixiv.session.isLoggedIn) {
                goMain()
            } else {
                statusText.text = "登录已过期，请重新点击登录"
            }
            return
        }
        handledLoginCode = code

        lifecycleScope.launch {
            statusText.text = "登录中…"
            when (val result = pixiv.oauth.handleCallback(uri)) {
                is PixivAuthResult.Success -> {
                    pixiv.session.saveSession(result.account)
                    statusText.text = "登录成功：${result.account.user?.name ?: result.account.user?.account ?: ""}"
                    goMain()
                }
                is PixivAuthResult.Failure.NetworkError ->
                    statusText.text = "登录失败：网络连接不上，请检查网络或代理后重试"
                is PixivAuthResult.Failure.MissingVerifier ->
                    statusText.text = "登录已过期，请重新点击登录"
                is PixivAuthResult.Failure.MissingCode ->
                    statusText.text = "登录被取消或回调异常，请重新登录"
                is PixivAuthResult.Failure.ServerRejected ->
                    statusText.text = "Pixiv 拒绝了登录请求(HTTP ${result.httpCode})，请重新登录（换节点无效）"
            }
        }
    }

    private fun goMain() {
        // TODO: 跳转主界面
        // startActivity(Intent(this, MainActivity::class.java))
    }
}

/** 示例 Application：持有全局 PixivApi 实例 */
class SampleApp : android.app.Application() {
    lateinit var pixiv: PixivApi
        private set

    override fun onCreate() {
        super.onCreate()
        val session = com.example.pixivapi.auth.SimpleSessionManager(YourSessionStore()).also { it.restore() }
        pixiv = PixivApi.create(
            session = session,
            verifierStore = YourVerifierStore(),   // 实现 ceui.pixiv.login.VerifierStore
            debug = BuildConfig.DEBUG,
        )
    }
}

// ── 以下为占位实现，正式项目按需替换 ────────────────────────────────────────

/** 会话持久化：建议用 MMKV / SharedPreferences / DataStore 实现 */
private class YourSessionStore : com.example.pixivapi.auth.SessionStore {
    override fun loadAccount(): com.example.pixivapi.model.AccountResponse? = null
    override fun saveAccount(account: com.example.pixivapi.model.AccountResponse) {}
    override fun getCookie(): String = ""
    override fun clear() {}
}

/** PKCE verifier 持久化：应对登录页往返期间进程被杀（对齐 Shaft MmkvVerifierStore） */
private class YourVerifierStore : ceui.pixiv.login.VerifierStore {
    override fun save(verifier: String) {}
    override fun load(): String? = null
    override fun clear() {}
}
