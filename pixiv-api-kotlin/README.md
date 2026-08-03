# Pixiv API Kotlin 客户端

基于 [Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft) 项目 API 层分析，用 Kotlin 重写的专用 Pixiv API 客户端。
供新 Pixiv Android 应用直接使用。

完整接口文档见 [docs/PIXIV_API_DOCUMENTATION.md](docs/PIXIV_API_DOCUMENTATION.md)。

---

## 功能特性

- ✅ 官方 App API 全量封装（插画 / 小说 / 用户 / 搜索 / 收藏 / 关注 / 评论 / 系列 / 追更 / 通知）
- ✅ 网页 API 封装（作品宽高 / 用户详情 / 首页 / 拉黑 / tag 筛选 / Street）
- ✅ OAuth PKCE 登录 + access_token 自动刷新（失败重放原请求）
- ✅ OAuth 完全使用 **pixiv-login 库**（clientId / clientSecret / 签名密钥取自 `PixivOAuthConfig.PIXIV_ANDROID`）
- ✅ 官方请求签名 `x-client-time` / `x-client-hash`
- ✅ iOS 官方客户端身份（与 Shaft 新版一致，可拿到更多字段）
- ✅ 图片 CDN 拦截器（自动带 Referer）
- ✅ 分页封装（next_url 游标）

## 目录结构

```
pixiv-api-kotlin/
├── docs/
│   └── PIXIV_API_DOCUMENTATION.md   # 完整 API 文档
├── sample/
│   └── LoginSampleActivity.kt       # 登录示例（含 code 去重 / 失败提示 / Application 装配）
└── src/main/java/com/example/pixivapi/
    ├── PixivApi.kt                  # 顶层门面（create 入口）
    ├── Constants.kt                 # 常量 / 请求头 / 签名密钥
    ├── api/
    │   ├── AppApi.kt                # app-api.pixiv.net 全部接口
    │   └── PixivWebApi.kt           # www.pixiv.net 网页接口
    ├── model/
    │   ├── Models.kt                # App API DTO
    │   └── WebModels.kt             # Web API DTO
    ├── network/
    │   ├── Interceptors.kt          # 请求头 / 签名 / Cookie
    │   ├── TokenInterceptor.kt      # token 自动刷新
    │   └── PixivClient.kt           # OkHttp + Retrofit 构建
    ├── auth/
    │   ├── SessionManager.kt        # 会话抽象 + 默认实现
    │   └── PixivOAuth.kt            # OAuth 门面（委托 pixiv-login 库）
    └── util/
        └── Pagination.kt            # 分页封装
```

## 快速开始

### 1. 依赖

```kotlin
// build.gradle.kts (app)
dependencies {
    // pixiv-login 库：OAuth 密钥 + PKCE 登录 + token 刷新
    implementation("com.github.SoxiaLiSA:pixiv-login:1.2.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

> 若用 `settings.gradle.kts` 的 dependencyResolutionManagement 需要添加 JitPack 仓库：
> `maven { url = uri("https://jitpack.io") }`

### 2. 实现 SessionStore（持久化）

```kotlin
class MmkvSessionStore : SessionStore {
    override fun loadAccount(): AccountResponse? { ... }
    override fun saveAccount(account: AccountResponse) { ... }
    override fun getCookie(): String { ... }   // 返回网页 Cookie
    override fun clear() { ... }
}
```

同时实现 pixiv-login 库的 `VerifierStore`（建议持久化以应对进程被杀）：

```kotlin
class MmkvVerifierStore(private val mmkv: MMKV) : ceui.pixiv.login.VerifierStore {
    override fun save(verifier: String) { mmkv.encode("pkce_verifier", verifier) }
    override fun load(): String? = mmkv.decodeString("pkce_verifier")
    override fun clear() { mmkv.removeValueForKey("pkce_verifier") }
}
```

### 3. 创建客户端

> 密钥已内置：clientId / clientSecret / 签名密钥默认取自 **pixiv-login 库**的
> `PixivOAuthConfig.PIXIV_ANDROID`，**无需手动填写**。

```kotlin
val session = SimpleSessionManager(MmkvSessionStore()).also { it.restore() }

val pixiv = PixivApi.create(
    session = session,
    verifierStore = MmkvVerifierStore(mmkv),  // pixiv-login 库的 VerifierStore
    // config = PixivOAuthConfig.PIXIV_ANDROID,  // 默认已用，可省略
    debug = BuildConfig.DEBUG,
)
```

### 4. 登录（PKCE）

> 需在 AndroidManifest.xml 为回调 scheme `pixiv` 注册 intent-filter
> （`pixiv://account/login`），并打开登录页时使用 Chrome Custom Tab。

```kotlin
// 打开登录页
val loginUrl = pixiv.oauth.startLoginUrl()
CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(loginUrl))

// 回调处理（Activity 的 onCreate / onNewIntent）
val uri: Uri = intent.data
if (uri != null && pixiv.oauth.isOAuthCallback(uri)) {
    when (val result = pixiv.oauth.handleCallback(uri)) {
        is PixivAuthResult.Success -> {
            pixiv.session.saveSession(result.account)
            // 登录成功，跳主页面
        }
        is PixivAuthResult.Failure.MissingCode -> { /* 缺 code */ }
        is PixivAuthResult.Failure.MissingVerifier -> { /* 缺 verifier */ }
        is PixivAuthResult.Failure.ServerRejected -> { /* 服务器拒绝 */ }
        is PixivAuthResult.Failure.NetworkError -> { /* 网络错误 */ }
    }
}
```

#### 完整登录时序（对齐 Pixiv-Shaft）

```
用户点"登录"
  └─ pixiv.oauth.startLoginUrl()                 // pixiv-login 库生成 PKCE verifier
  └─ Chrome Custom Tab 打开登录页，用户授权
  └─ Pixiv 重定向 → pixiv://account/login?code=XXX&via=login
  └─ Activity.onNewIntent 收到回调
       ├─ isOAuthCallback(uri) == true           // scheme 匹配 "pixiv"
       ├─ code 单次性去重（防止旋转屏幕重复提交）
       └─ handleCallback(uri)                    // 交换 code → token
            ├─ Success(account) → session.saveSession(account)
            └─ Failure → 按类型给用户提示
```

> **code 单次性去重**：OAuth 授权码是单次性的，配置变化（旋转/深色模式）会用同一个
> 回调 intent 重跑 `onNewIntent`，二次提交必被 Pixiv 拒成「不正确的请求」。
> 建议在 Activity 里记录已处理过的 code（对齐 Shaft `OutWakeActivity.sHandledLoginCode`）。

完整示例见 [sample/LoginSampleActivity.kt](sample/LoginSampleActivity.kt)（含 code 去重 + 失败提示文案，与 Shaft 一致）。

### 5. 请求数据

```kotlin
// 作品详情
val illust = pixiv.api.getIllust(illustId)

// 排行榜
val ranking = pixiv.api.getRanking(PixivConstants.RANK_DAY)
ranking.illusts.forEach { ... }

// 搜索
val results = pixiv.api.searchIllusts(word = "初音ミク", sort = PixivConstants.SORT_DATE_DESC)

// 收藏
pixiv.api.bookmarkIllust(illustId, PixivConstants.RESTRICT_PUBLIC, listOf("标签1"))

// 网页接口（拉黑）
pixiv.webApi.saveBlock(csrfToken = "...", request = BlockSaveRequest("123", "block"))
```

### 6. 分页

```kotlin
val loader = IllustPagedLoader(pixiv.api) {
    pixiv.api.getRanking(PixivConstants.RANK_DAY)
}
val firstPage = loader.loadInitial()
// 上拉加载
if (loader.hasMore) {
    loader.loadMore()
}
```

## 重要说明

### 客户端密钥（直接使用 pixiv-login 库，不硬编码）
OAuth 相关密钥**全部来自 pixiv-login 库**（`com.github.SoxiaLiSA:pixiv-login:1.2.0`）——
本项目不再硬编码 `clientId` / `clientSecret`，登录、PKCE、token 交换/刷新均由库的
`PixivOAuthClient` 处理，配置取自库的 `PixivOAuthConfig.PIXIV_ANDROID`：

| 项 | 值 | 库内位置 |
|----|----|---------|
| `clientId` | `MOBrBDS8blbauoSck0ZfDbtuzpyT` | `PixivOAuthConfig.PIXIV_ANDROID.clientId` |
| `clientSecret` | `lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj` | `PixivOAuthConfig.PIXIV_ANDROID.clientSecret` |
| 签名密钥 `HASH_SECRET` | `28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c` | `PixivOAuthClient.HASH_SECRET` |
| 登录 URL | `https://app-api.pixiv.net/web/v1/login` | `PixivOAuthConfig.PIXIV_ANDROID.loginUrl` |
| `client` 参数 | `pixiv-android` | `PixivOAuthConfig.PIXIV_ANDROID.clientParam` |
| 回调 scheme | `pixiv`（`pixiv://account/login?code=…`） | `PixivOAuthConfig.PIXIV_ANDROID.callbackScheme` |

> 唯一保留在 `Constants.kt` 的 `CLIENT_TIME_SECRET`（app-api 业务请求的
> `x-client-hash` 签名密钥）与库内 `HASH_SECRET` 值相同，供 `HeaderInterceptor`
> 使用。若官方更新导致密钥失效，**升级 pixiv-login 库版本**即可，无需改本仓库。

> 如需换用 Pixiv Comic 客户端，传 `config = PixivOAuthConfig.PIXIV_COMIC`。

### 请求头（与 Shaft 新版一致）
- `User-Agent: PixivIOSApp/8.6.10 (iOS 26.5; iPhone16,2)`
- `app-os: ios` / `app-os-version` / `app-version`
- `x-client-time` + `x-client-hash`（MD5 签名）

### 网页 Cookie
Web API 需要 Cookie（`PHPSESSID` 等）。Shaft 通过设置页让用户手动同步。
`cf_clearance` 绑定 UA，WebView 与 OkHttp 必须使用同一 UA。

### 图片加载
i.pximg.net 必须带 `Referer: https://app-api.pixiv.net/`，否则 403。
使用 `pixiv.imageClient` 或自行在图片加载库（Glide/Coil）中配置 Referer 拦截器。

## 国内网络

Pixiv 在中国大陆无法直连。Shaft 的解决方案：
1. **代理**：系统 HTTP 代理（Clash 等）
2. **直连加速**：Cronet（QUIC/HTTP3）走 Cloudflare CDN IP（`i.pximg.net` → `104.16.90.12`）
   - 需要在 OkHttp 中接入 CronetEngine（见 Shaft 的 `CronetInterceptor`）

新应用如需直连能力，可参考 Shaft 的 `CronetInterceptor.java` 与 `HttpDns.java` 自行实现。
