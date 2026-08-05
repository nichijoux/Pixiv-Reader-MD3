# 代码流程地图 — Pixiv Reader（供 AI 理解）

> 本文件是「全项目代码流程地图」，帮助新会话 AI 在动手前快速建立心智模型。
> 与 `AGENTS.md`（构建/规则/陷阱速查）、`agent.md`（历史轮次决策）互补：**先读本文件建立流程认知 → 再读 AGENTS.md 查规则 → 改老功能再去 agent.md 查历史**。
> 所有路径为相对仓库根（`F:\pixiv-mateiral3`）。

---

## 0. 三分钟心智模型

- **Pixiv Reader** = Android（Kotlin + Jetpack Compose + Hilt + Room）的 pixiv 客户端，主打「插画/小说双阅读」。
- 包结构按 **多模块 Clean 分层**：`app → feature/* → core/* → lib:pixivapi`，依赖单向，feature 之间禁止互依。
- 网络分两条腿：**app-api**（`getRecommendedIllusts` 这类官方 OAuth 接口）和 **webApi**（`pixiv.net` 网页接口，补 app-api 缺失的能力：每 P 真实宽高、isBlocking、拉黑、正文嵌入图映射、评论树等）。两者都封装在 `lib:pixivapi`（vendor 副本，只读勿改；feature 改 API 只能在 `lib/pixivapi/`）。
- 图片 loader 用 **Coil**，全局 `SingletonImageLoader`（`PixivApp`）注入带 pixiv Referer 的 `OkHttpClient`，所以代码里所有 `PixivImage` / `AsyncImage` 自动带 Referer，不 403。
- 状态管理统一 **StateFlow** + `collectAsStateWithLifecycle`；分页统一 `PagedState<T>`（基于 `next_url` 游标）。
- 持久化三套：**Room**（进度/历史/下载索引/搜索历史）、**DataStore Preferences**（阅读偏好/主题/屏蔽标签/热门缓存）、**MMKV**（会话 token / OAuth verifier）。
- 导航：**单一 Activity + Compose Navigation**。顶层全屏页路由在 `PixivNavGraph`，底部 5 Tab 在 `MainShell` 内层 NavHost。内层 Tab 不能直达顶层路由，靠**回调链**上抛。

---

## 1. 模块依赖图（硬约束）

```
app ──▶ feature/* ──▶ core/ui ──┐
                               ├─▶ core/network ──▶ lib:pixivapi
                               ├─▶ core/database
                               ├─▶ core/datastore
                               ├─▶ core/model ──▶ core/common
                               └─▶ core/novel ──▶ core/common
```

- `feature/*` 之间 **禁止互相依赖**。共享逻辑下沉到 core。
- `lib:pixivapi` 是 `pixiv-api-kotlin/` 的 vendor 副本，**只读勿改 feature 需要的 API**；要加 API 只在 `lib/pixivapi/` 改。
- 所有 `com.example.pixivapi.*` 的 import 解析到 `lib/pixivapi` 副本。
- `feature/download` 是空壳（未使用），下载管理实际在 `feature:user`。

模块清单（`settings.gradle.kts`）：
```
:app :lib:pixivapi
:core:common :core:model :core:network :core:database :core:datastore :core:ui :core:novel
:feature:auth :feature:home :feature:discover :feature:illust :feature:viewer
:feature:novel :feature:reader :feature:user :feature:bookmark :feature:watchlist
:feature:manga :feature:download :feature:settings
```

---

## 2. 应用启动流程

```
PixivApp（@HiltAndroidApp）
  ├─ Hilt 装配：PixivApi / Session / NetworkMonitor / 各 Repository / DAO
  ├─ WorkManager Configuration（HiltWorkerFactory）→ 支持注入式 Worker（如小说离线下载）
  └─ newImageLoader()：ImageLoader.Builder.okHttpClient(pixivRepository.imageClient) → Coil 全局带 Referer
         ↓
MainActivity（@AndroidEntryPoint）
  ├─ onCreate：
  │   ├─ enableEdgeToEdge（导航栏透明，沉浸式由各页 padding 自处理）
  │   ├─ handleIntent(intent)  ← OAuth 深链冷启动转发
  │   └─ setContent：
  │        ├─ 收集 userPreferences.themeMode（0跟随/1浅/2深）+ dynamicColor → isDark
  │        ├─ 收集 networkMonitor.isOnline → 离线 Snackbar
  │        ├─ PixivReaderTheme(darkTheme, dynamicColor) { ... }  ← core:ui/theme
  │        └─ Scaffold（contentWindowInsets=0）{ PixivNavGraph(...) }
  ├─ onNewIntent：handleIntent → 转发热启动 OAuth 回调
  └─ handleIntent：intent.data 若为 pixiv://account/login → sessionRepository.onOAuthCallback(uri)
```

关键文件：
- `app/.../app/PixivApp.kt`、`app/.../app/MainActivity.kt`
- `app/.../app/navigation/PixivNavGraph.kt`、`app/.../app/navigation/MainShell.kt`
- `core/ui/.../theme/Theme.kt`：`PixivReaderTheme(darkTheme, dynamicColor)` 按 `dynamicColor && SDK>=S` → 系统动态取色；否则 dark? `DarkColors` : `LightColors`（种子 #0096FA 派生）。

---

## 3. 登录流程（OAuth 授权码 + PKCE）

```
AuthRoute（feature:auth）
  └─ AuthViewModel.startLogin()
       └─ sessionRepository.buildLoginUrl()
            └─ pixivApi.oauth.startLoginUrl()  ← 库内生成 PKCE verifier 并持久化到 MMKV
                 └─ AuthEvent.OpenLoginPage(url) → UI 打开网页
```

**回调（冷热两种）**：用户网页授权后跳回 `pixiv://account/login?code=…`：
```
MainActivity.handleIntent / onNewIntent
  └─ sessionRepository.onOAuthCallback(uri)  ← 写入 pendingOAuthUri: StateFlow<Uri?>
       └─ AuthViewModel 监听 pendingOAuthUri → processCallback(uri)
            ├─ code 去重（processedCode，防止旋转/重复回调二次提交）
            └─ sessionRepository.processLogin(uri)
                 └─ pixivApi.oauth.handleCallback(uri) → 换 token
                      ├─ PixivAuthResult.Success → session.saveSession + isLoggedIn=true → AuthEvent.LoginSuccess
                      └─ PixivAuthResult.Failure.* → uiState.error 文案
```

登录成功 → `navController.navigate(ROUTE_MAIN) { popUpTo(ROUTE_AUTH){inclusive=true} }`。

登出：`sessionRepository.logout()` → 清会话 + `isLoggedIn=false` → MainShell 回调链 → 导航回 `ROUTE_AUTH`。

关键文件：
- `feature/auth/.../AuthRoute.kt`、`AuthViewModel.kt`
- `core/network/.../session/SessionRepository.kt`（会话桥 + 登录态可观察）
- `core/network/.../session/MmkvSessionStore.kt` / `MmkvVerifierStore.kt`（token / verifier 持久化）
- `core/network/.../di/NetworkModule.kt`：装配 `MMKV → SessionStore → SessionManager(.restore()) → VerifierStore → PixivApi.create(PixivOAuthConfig.PIXIV_ANDROID)`

---

## 4. 网络层与统一出口

```
feature ViewModel
  └─ 注入 PixivRepository（@Singleton @Inject constructor，构造由 Hilt 自动装配）
       ├─ .api        : AppApi       ← app-api（推荐流/详情/评论/收藏/排行/marker…）
       ├─ .webApi     : PixivWebApi  ← 网页接口（每 P 真实宽高、isBlocking、拉黑、Novel 正文嵌入图…）
       ├─ .imageClient: OkHttpClient  ← 带 pixiv Referer 的图片 client，Coil 全局注入
       └─ .pixivApi.session / .pixivApi.oauth  ← 经直取，或经 SessionRepository
```

**何时用 webApi**（重要决策点）：
- 插画详情：`webApi.getIllustPages(id)` 补每 P 真实宽高（app-api 不给宽高，`IllustCard` 无宽高会固定高度 + Crop 裁剪中间）。
- 用户主页：`webApi.getUserDetail(id)` 含 `isBlocking`（我是否拉黑对方），初始化拉黑态。
- 拉黑/取关：`webApi.saveBlock(...)` 需要 `x-csrf-token`（从网页 Cookie 解析，见 `UserViewModel.csrfTokenFromCookies`）。
- 小说正文：`webApi.getNovelWeb(id)` 拿 `textEmbeddedImages`（`uploadedimage:file → urls` 映射），喂给 `NovelParser`。

关键文件：
- `core/network/.../session/PixivRepository.kt`（统一出口，类级注释已说明三者分工）
- `lib/pixivapi/...`（API 定义，只读）

---

## 5. 通用分页 `PagedState<T>`

`core/network/.../paging/PagedState.kt` —— 几乎所有列表 ViewModel 复用：

```
ViewModel 持有         val paged = PagedState<Illust>()
首次加载   suspend paged.loadInitial(
              fetch     = { api.getRecommendedIllusts(true) },
              fetchNext = { url -> api.getNextIllusts(url) })
              → 拉 first page + 缓存 fetch 函数 + 记 next_url 游标
触底加载   suspend paged.loadMore()
              → 用 next_url 游标拉下一页，items 追加；无游标/加载中忽略
暴露       items / isLoading / isLoadingMore / hasMore / error（均 StateFlow）
重置/报错  paged.reset() / paged.setError(msg)
```

特性：加载中防重入；失败仅报错不清空已有数据；`loadInitial` 失败时 `hasMore=false` 停用加载更多。

UI 侧：`LazyColumn` 监听 `listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index >= items.size - 3` → `viewModel.loadMore()`。

---

## 6. 持久化三套

### 6.1 Room（`core/database`，db name `pixiv_reader.db`，**version=3**）

实体（4 个）：
| 实体 | 表 | 主键 | 用途 |
|---|---|---|---|
| `ReadingProgressEntity` | reading_progress | novelId | 字符级阅读进度（charOffset/percentage/seriesId/title/coverUrl） |
| `BrowseHistoryEntity` | browse_history | id(auto) | 浏览历史（targetType/targetId + **payloadJson** 完整卡片快照） |
| `DownloadEntryEntity` | download_entry | targetId | 下载索引（illust/ugoira/novel/novel_offline + width/height + status + localPath） |
| `SearchHistoryEntity` | search_history | id(auto) | 搜索历史关键词 |

DAO 模式要点：**`deleteByX` 先删旧再 `upsert` = 去重置顶**（BrowseHistory/SearchHistory 用）。

Migration：`MIGRATION_1_2`（建 search_history）、`MIGRATION_2_3`（download_entry 加 width/height）。**加字段/实体必须升 version + 写 Migration**；`fallbackToDestructiveMigration()` 兜底（开发期避免卡迁移，但加列时仍要显式 Migration 保数据）。

### 6.2 DataStore Preferences（`core/datastore`，name `pixiv_prefs`）

`UserPreferences` 暴露的 Flow（默认值见文件）：
- 阅读偏好：`readerFontSize/readerLineHeight/readerFontFamily/readerTheme/readerPageMode/readerBrightness/readerFollowSystem/readerCustomFontPath`
- 全局：`imageQuality/dynamicColor/themeMode/autoUpdate`
- 缓存/过滤：`hotTags/hotTagsUpdatedAt/mutedTags`

全部 `suspend setXxx(value)` 写入。ViewModel 用 `stateIn(scope, WhileSubscribed(5000), default)` 派生 `StateFlow`。

### 6.3 MMKV（`core/network/.../session/`）
- `MmkvSessionStore`：token / currentUser。
- `MmkvVerifierStore`：OAuth PKCE verifier。

### 6.4 文件系统约定
- 离线小说：`{filesDir}/offline/novels/{novelId}.json`（`NovelDocumentCodec.encode`）+ `{novelId}_meta.json`（最小元数据），由 `OfflineNovelRepository` 管理。
- 导出文件：`{filesDir}/Downloads/`（插画原图、小说 TXT/EPUB 导出）。
- 调试：`{filesDir}/novel_debug/`（解析结果，设置页可清）。
- 自定义阅读字体：导入到私有目录后存绝对路径到 `readerCustomFontPath`。

---

## 7. 核心 feature 流程

### 7.1 首页（feature:home）
`HomeViewModel` 持 `recommendPaged`/`followingPaged`（`PagedState<Illust>`）+ `trendingTags`。Tab 切换懒加载（空才拉）。触底 `loadMore`。`HomeRoute` 顶部搜索框点按 → 切到 discover_tab。

### 7.2 发现（feature:discover）
`DiscoverViewModel`（最复杂的列表 VM）：
- 搜索类型三类（插画/小说/用户）× 模式两档（最新 sort=date_desc / 热门 popular_desc）。
- 热门搜索缓存：`hotTags` + `hotTagsUpdatedAt`，**24h TTL** 优先 DataStore，过期才 `api.getHotTags` 网络刷新并回写。
- 搜索历史：`SearchHistoryDao`，`recordHistory` 先 `deleteByKeyword` 再 `upsert` = 去重置顶。
- 筛选：`loadOptions` 拉 `/v1/search/options`（绘制工具/题材）；`applyFilters` 后重新搜索。
- `initialQuery` —— 由 `MainShell.pendingSearch` 单次消费传入（跨 Tab 搜索通道，见 §8.2）。
- 副作用：收藏/关注由组件回调 `toggleIllustFavorite`/`toggleNovelFavorite`/`toggleFollow`（`nowFavorite`/`nowFollowed` 为目标态）。

### 7.3 漫画 Tab + 排行榜（feature:manga）
- **MangaRoute（漫画 Tab，底部第 3 位）**：TopBar「漫画」+ 排行榜入口 banner（纯 tertiaryContainer，点击进漫画排行榜）+ 推荐漫画瀑布流（`api.getRecommendedManga` → `PagedState<Illust>` + `IllustWaterfallGrid`）。
- **MangaRankingRoute（全屏页 `manga_ranking`）**：复用通用 `core:ui RankingList<T>`——`ScrollableTabRow` + `HorizontalPager` 左右滑动切段，点 Tab `animateScrollToPage`；5 段（日 `day_manga` / 周 `week` / 月 `month` / 新人 `week_rookie` / R18 `day_r18`）由 `MangaRankingViewModel.modes`（`RankingModeInfo(@StringRes, value)`）配置；每段 `api.getRanking(mode)` + `PagedState` 分页，行渲染 `RankingRow`（排名徽标 1金/2橙/3灰 + 封面 + 标题/作者/收藏）。
- 说明：pixiv 漫画专属榜仅 `day_manga`；周/月/新人/R18 为通用 mode（会混入插画）。未来小说/插画排行榜复用 `RankingList` + 各自 `RankingModeInfo` 列表与 itemContent。

### 7.4 插画详情（feature:illust）
```
IllustDetailRoute
  └─ IllustViewModel.load()
       ├─ api.getIllust(id) → illust + is_bookmarked + toPages()
       ├─ recordHistory(illust)  ← BrowseHistory payloadJson 存 id/title/coverUrl/width/height/bookmarks/pageCount/isBookmarked
       ├─ loadRelated()  → relatedPaged
       ├─ loadComments() → commentsPaged
       └─ loadRealSizes() ← webApi.getIllustPages(id) 补每 P 真实宽高（关键）
```
- 下载：`download()` → `imageClient` 下载原图到 `filesDir/Downloads` → `recordDownload` 写 `DownloadEntryEntity`（`BitmapFactory` 解析真实宽高写 width/height）。
- 双栏：`WindowSizeClass != Compact` → `TwoPaneContent`（左主内容 + 右评论，输入框固底）。
- 多 P Pager：容器高度按图真实宽高比自适应（`AsyncImage.onSuccess` 拿 drawable.intrinsic 算比例，无则用网页宽高，再无兜底 1.5）。

### 7.5 全屏查看器（feature:viewer）
```
ViewerRoute(novelId, page?)  ← ROUTE_VIEWER = "viewer/{illustId}?page={page}"
  └─ ViewerViewModel
       ├─ api.getIllust(id) → illust + pages + isGif(illust)
       ├─ UgoiraLoader（动图 zip 解帧）— isGif 时
       ├─ toggleBookmark / download(当前页原图) / wallpaper(设壁纸) / toggleOriginal(预览↔原图)
       └─ recordDownload（targetType=illust/ugoira，写真实宽高）
```
底部圆形操作条（第47轮）：收藏/下载/壁纸/原图。设壁纸用 `WallpaperManager.setBitmap`（minSdk 26 无需运行时权限，manifest 声明 `SET_WALLPAPER` normal 权限）。

### 7.6 小说详情（feature:novel）
```
NovelDetailRoute(novelId)
  └─ NovelViewModel
       ├─ load() → api.getNovel + seriesNovels(api.getNovelSeries 分页 20 页防御)
       ├─ recordHistory(payloadJson 存 NovelCardData 完整：作者/头像/日期/系列/收藏/字数/标签)
       ├─ loadComments() + postComment()
       ├─ toggleBookmark / toggleWatchlist（series 才能追更）
       └─ 下载对话框：
            ├─ exportNovel(TXT/EPUB)   → NovelExporter 导出单本到 filesDir/Downloads
            ├─ exportSeries(TXT/EPUB)  → 逐章下载合并
            ├─ downloadOfflineCurrent()/Series() → 入队 NovelOfflineDownloadWorker（见下）
```
沉浸式 banner：`PixivImage` 高度 `NOVEL_BANNER_HEIGHT+PARALLAX`，`graphicsLayer.translationY = scrollOffset*0.45f` 视差。返回按钮浮层（半透明圆底）。系列分册点击 → `onOpenNovel(id)`。

### 7.7 小说离线下载（WorkManager）
```
NovelViewModel.downloadOfflineCurrent/Series
  └─ WorkManager.enqueue(NovelOfflineDownloadWorker, inputData novelId/seriesId)
       └─ doWork：
            seriesId>0 → fetchSeriesNovels（循环分页 last_order，防御 20 页）→ 逐本 downloadOne
            downloadOne(id)：
              ├─ downloadEntryDao.upsert(status="downloading")
              ├─ novelContentLoader.load(id) → (Novel, NovelDocument)
              │     ├─ api.getNovel(id)
              │     ├─ api.getNovelHtml(id).string()
              │     ├─ webApi.getNovelWeb(id).textEmbeddedImages → uploadedimage 映射
              │     └─ NovelParser.parse(html, imageUrls) → resolvePixivImages（[pixivimage:ID] → 首图 URL）
              ├─ offlineNovelRepository.save(novel, doc) → 写 {novelId}.json + _meta.json
              └─ upsert(status="done")  ← 失败 Result.retry() 自动重试
```
`NovelContentLoader` 注释明确：与 `ReaderViewModel.load` 的加载链路保持一致（TODO 抽到共享层消除重复）。

### 7.8 阅读器（feature:reader）—— 全项目最复杂
```
ReaderRoute(novelId, localDocument?, localTitle?)
  └─ ReaderViewModel
       ├─ init：collectPreferences（DataStore 7 项阅读偏好镜像到内存 StateFlow，每项独立 runCatching 防闪退） + load()
       ├─ useLocalDocument(doc, title)  ← local_reader 路由入口（TXT/EPUB 解析后注入，跳过网络/离线）
       └─ load()：
            ├─ 离线优先：offlineNovelRepository.exists(novelId) → loadDocument + loadNovel（断网可读）
            └─ 否则在线：getNovel + getNovelHtml + webApi.getNovelWeb(textEmbeddedImages) → NovelParser.parse → resolvePixivImages
```

**翻页模式**（`pageMode`：0滑动/1翻页/2仿真）：
- 滑动（`ScrollReaderContent`）：`LazyColumn` 按块渲染（Paragraph/Heading/Quote/Separator/Image），滚动进度 = 首可见块字符偏移 + 块内滚动比例。
- 翻页（`PagerReaderContent`）：`rememberReaderPages` 预分页 → `HorizontalPager`。左右边缘点击翻页（与中间 1/3 透明覆盖层切换工具栏配合）。
- 仿真（`SimulationPageContent`/`BookPageTurnContent`）：位置驱动贝塞尔卷页（legado 移植）。折痕 = 角落↔手指的垂直平分线，被卷起部分实时绘制"纸背"（内容沿折痕反射 + 灰色遮罩）。

**进度流（字符级 + 官方 marker 双轨）**：
```
UI 翻页 → reportPage(startChar, total)        UI 滚动 → reportScrollOffset(offset)
  ↓（progressRestored 完成后才接收）            ↓（<50 字符防抖）
charOffset + percentage = document.percentageAt(offset)
  ├─ scheduleProgressSave（防抖 800ms）→ readingProgressDao.upsert(ReadingProgressEntity)
  └─ scheduleMarkerSync（防抖 3000ms）→ api.addNovelMarker(novelId, (ratio*pageCount)页)  ← 仅翻页模式
离开 → flushProgress()（onDispose 立即落库）
恢复 → restoreProgress()：本地 Room 优先 → 官方 marker 次之 → 开头兜底；progressRestored=true 后 UI 定位
```

**目录/搜索**：`buildToc` 系列小说展示系列内各本（非系列只一条）；`searchText` 全文忽略大小_WRONLY → `searchResults: List<Int>`（字符偏移）→ `jumpToChar` 由内容组件响应。

**主题**：`readerThemeColors(effectiveTheme)` 4 套（日间/纸张/夜间/深黑），`followSystem` 开启按系统深色选夜间/纸张。

### 7.9 本地 TXT/EPUB 阅读
```
DownloadsViewModel.parseLocal(uri)  ← 文件选择器
  ├─ TxtNovelParser.parse / EpubNovelParser.parse（core:novel）→ NovelDocument
  ├─ LocalReaderStore.set(document, title)  ← 暂存（单例 object，覆盖前次）
  └─ onReady(novelId) → 导航 local_reader/{novelId}
       └─ PixivNavGraph ROUTE_LOCAL_READER → LocalReaderStore.consume()（一次性取走）→ ReaderRoute(localDocument=, localTitle=)
            └─ ReaderViewModel.useLocalDocument → _isLocalMode=true, _isOffline=true，跳过网络直接渲染
```

### 7.10 浏览历史（feature:user/HistoryRoute）
TabRow 三类（插画/小说/用户）+ HorizontalPager。`BrowseHistoryDao.observeByType` 分流。三类转换：
- 插画：`toIllust()` 优先 `Gson().fromJson(payloadJson)` 恢复宽高（无宽高则 IllustCard 裁剪中间）。
- 小说：`toNovelCardData()` 同样优先 payloadJson 完整字段（作者/系列/字数…）。
- 用户：`CreatorProfileCard`。

### 7.11 下载管理（feature:user/DownloadsRoute）
TabRow 三类（插画/小说/本地文件）+ 右上角删除 overlay。`DownloadEntryDao.observeAll` 分流。删除：`deleteByType` + 清对应本地资源（导出文件删文件 / 离线缓存 `offlineNovelRepository.delete`）。本地文件项点击 → `local_reader` 路由。

### 7.12 屏蔽（feature:user/BlockedRoute）
两块：「屏蔽过滤标签」（推荐/搜索过滤，写 `UserPreferences.mutedTags`）+ 「屏蔽用户」（取关，`webApi.saveBlock action=unblock`）。Material 卡片 + pill 标签（? 删除）。

### 7.13 追更 / 收藏 / 标签 / 设置
- 追更（`feature:watchlist`）：`api.getWatchlistNovel` + `PagedState<WatchlistSeries>`。
- 收藏（`feature:bookmark`）：`bookmarks?type={type}&tag={tag}`，`api.getBookmarks`。
- 收藏标签（`feature:user/TagsRoute`）：本地枚举类型（避免跨 feature 依赖）→ 点击跳 `bookmarks?type=&tag=`。
- 设置（`feature:user` MeRoute 内嵌，无独立设置页）：外观（主题模式/动态取色/语言，MeViewModel 读写 `UserPreferences`）+ 系统（自动更新开关、缓存清理 `filesDir/offline` + `novel_debug` + `cacheDir` + Coil diskCache 并删 `downloadEntryDao.deleteByType("novel_offline")`）+ 关于（版本/描述/检查更新占位/开源链接）。`feature:settings` 为空壳模块（同 `feature:download`，未使用）。

---

## 8. 导航约定（易踩坑）

### 8.1 两级 NavHost
- **顶层** `PixivNavGraph`（`app/.../navigation/PixivNavGraph.kt`）：`auth` / `main?search=` / `illust` / `viewer` / `novel` / `reader` / `user` / `history` / `bookmarks?type=&tag=` / `watchlist` / `blocked` / `downloads` / `local_reader` / `tags` / `manga_ranking`。
- **内层** `MainShell`：`home_tab` / `discover_tab` / `ranking_tab` / `novel_tab` / `me_tab`，自适应导航（手机底部 NavigationBar / 平板 NavigationRail，由 `WindowSizeClass.useRail()` 决定）。

### 8.2 内层 Tab 无法直达顶层路由
内层 feature 想开插画详情/阅读器/用户主页时，**不能**直接 `navController.navigate("illust/...")`（拿不到顶层 navController）。约定用回调链：
```
PixivNavGraph.onOpenIllust ─┐
  → MainShell(onOpenIllust=...) ─┤
    → feature(onOpenIllust=...) ─┘
      → navController.navigate("illust/$id")
```
新增全屏页 = 顶层路由 + ROUTE_ 常量 + `PixivNavGraph` 接线 + MainShell 回调上抛。

### 8.3 跨 Tab 搜索
`main?search={search}` 顶层参数 → `MainShell.initialSearch` → 写入 `pendingSearch` → 切 `discover_tab` → `DiscoverRoute(initialQuery = pendingSearch?.also{pendingSearch=null})`（**一次性消费**）。小说 Tab 标签点击、用户主页标签点击同样走 `pendingSearch` 通道。`popUpTo(ROUTE_MAIN){inclusive=true} launchSingleTop=true` 避免栈堆积。

### 8.4 深链
`pixiv://` scheme：`illust/{id}` / `novel/{id}` / `reader/{id}` / `user/{id}` 在 `PixivNavGraph` 用 `navDeepLink` 注册。OAuth 回调 `pixiv://account/login` 由 `MainActivity.handleIntent` 转发到 `SessionRepository`。

---

## 9. 通用组件（core:ui，优先复用）

`core/ui/.../component/`：
- **IllustCard**：瀑布流卡，按 `illust.width/height` aspectRatio 显示，含收藏按钮/AI角标/页码。**无宽高会固定高度 + Crop 裁剪中间**（数据源必须带宽高）。
- **IllustWaterfallGrid**：瀑布流容器（自适应列数）。
- **NovelCard** + **NovelCardData**：小说通用卡（封面/作者/收藏/字数/系列/标签）。
- **UserAvatar**：URL null 用首字母圆头像。
- **SettingsCard** + **SettingsCardItem**：数据驱动设置卡（icon/title/description/trailingIcon/onClick）。
- **ProfileHeader**：用户主页头部。
- **CreatorProfileCard**：创作者卡（历史/屏蔽用）。
- **CommentInput**：评论输入框。
- **AdaptiveContentBox**：平板限宽（`MAX_CONTENT_WIDTH_DP=760`）居中容器。
- **AdaptiveNavScaffold**：自适应导航脚手架（手机底栏 / 平板左栏）。
- **PixivImage**：Coil AsyncImage 封装，url=null 渲染色块，默认 ContentScale.Crop。Referer 靠全局 `SingletonImageLoader` 自动带。
- **StatusViews**：`LoadingBox` / `ErrorBox(onRetry)` / `EmptyBox`。

---

## 10. 易踩坑速查

| 坑 | 规避 |
|---|---|
| 图片 403 | 必须走 `PixivRepository.imageClient`（PixivImage/AsyncImage 自动带 Referer） |
| org.json 单测失败 | 本地 JVM 单测无 org.json，需 `testImplementation("org.json:json:20240303")`（core:novel 已配） |
| 数据库加列丢数据 | version 必须 +1 + 写 Migration；只在兜底用 `fallbackToDestructiveMigration` |
| 历史/下载卡片信息不全 | `BrowseHistoryEntity.payloadJson` 必须存完整卡片数据（宽高/作者/系列/字数），否则通用组件展示不全/图片裁剪 |
| IllustCard 裁剪中间 | 数据源带宽高（`width/height`），无宽高走固定高度 + Crop |
| 内层 Tab 想跳顶层 | 用回调链，不能直接 navigate |
| Gson 跨模块 | 经 lib:pixivapi 传递，feature 可直接 `Gson()`（如历史 payloadJson） |
| 改 API | 只在 `lib/pixivapi/` 改，`pixiv-api-kotlin/` 只读 |
| 提交命令 | `git add` 与 `git commit` 必须分开执行（争 index.lock） |
| 阅读进度闪回 | `progressRestored` 必须为 true 才接收 UI 上报，否则初始翻页会覆盖恢复位置 |
| 离线阅读读不到 | ReaderViewModel.load 离线优先（`offlineNovelRepository.exists` 先查），下载未完成（status≠done）不进入离线分支 |

---

## 11. 构建/测试（必须用此 JDK）

```powershell
$env:JAVA_HOME = "C:\Users\nichijoux\.jdks\jbr-21.0.11"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& .\gradlew.bat :app:compileDebugKotlin --console=plain    # 快速验证
# 单测（按改动模块跑）
& .\gradlew.bat :core:novel:testDebugUnitTest :feature:reader:testDebugUnitTest :core:network:testDebugUnitTest :feature:novel:testDebugUnitTest :feature:user:testDebugUnitTest :core:common:testDebugUnitTest --console=plain
```

每次改代码先 `:app:compileDebugKotlin`，涉及数据层再加对应模块单测。Windows 无 Android Studio；编译错误看 `^e: ` 行。

---

## 12. 改动前自查清单

- [ ] 先读本文件建立流程认知，确认改动落在哪个模块/层。
- [ ] 模块依赖方向对吗？（feature 不依赖 feature；core 不反向依赖）
- [ ] 涉及图片？走 `PixivRepository.imageClient` / `PixivImage`。
- [ ] 涉及数据层？DAO `deleteByX 先删旧再 upsert`；DB 加列升 version + Migration。
- [ ] 涉及历史/下载卡片？`payloadJson` 存完整字段（含 width/height）。
- [ ] 涉及导航？顶层路由 + 回调链；内层 Tab 不直接 navigate 顶层。
- [ ] 改完 `:app:compileDebugKotlin`；涉及模块跑对应单测。
- [ ] 想了解历史决策 → `agent.md`（按轮次记录，第 48 轮为注释补充）。