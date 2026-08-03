# Agent 工作说明 · Pixiv Android 客户端（Pixiv Reader）

> 本文件是 opencode 代理执行本项目时的工作手册。每次开始新会话应先读本文件，遵循其中
> 的技术决策、目录规范、构建命令与验收标准。

---

## 1. 项目概述

基于本地 `pixiv-api-kotlin` 库开发一个 Pixiv Android 客户端（代号 **Pixiv Reader**），
核心体验：**人性化小说阅读** + **多图/动图插画查看**，UI 采用 **Material Design 3**
（原型见 `design/ui-mockup.html`，含 10 个界面：首页瀑布流 / 小说推荐流 / 搜索+筛选 /
插画详情 / 全屏查看器 / 小说阅读器(核心) / 追更 / 排行榜 / 用户主页 / 我的）。

已确认的设计取向：
- 多模块分层（app + core/* + feature/*）
- MVVM + 单向数据流 + Hilt 注入（最优架构）
- 分页：自研游标分页（基于 API 库 next_url），**不引入 Paging3**
- 国内网络：**不做代理 UI**，视为可直连；失败仅弹提示（Snackbar/对话框）
- 零 emoji，全部图标使用 SVG（与原型一致）

---

## 2. 环境与工具链（已实测）

| 项 | 值 / 位置 |
|---|---|
| JDK | `C:\Users\nichijoux\.jdks\jbr-21.0.11`（JDK 21，需手动加入 PATH 或配置 org.gradle.java.home） |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk`（platforms: android-36, android-37.0；build-tools: 34/35/36） |
| Gradle | 发行版已缓存 8.14.3 / 8.9（offline 可用） |
| Android Studio | 未安装；无 cmdline-tools → 只能用命令行构建，真机/模拟器由用户处理 |
| pixiv-api-kotlin | 位于 `pixiv-api-kotlin/`，**无 Gradle 构建文件**，需在 P0 补齐并作为模块引入 |

### 关键命令（Windows / PowerShell）
```powershell
# 编译前确保 JDK 可用（若未配置 PATH）
$env:JAVA_HOME = "C:\Users\nichijoux\.jdks\jbr-21.0.11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 构建（本机 gradle 发行版已缓存）
& .\gradlew.bat :app:assembleDebug
```

### 版本选型（已锁定）
- Gradle 8.14.3 · AGP 8.9 · Kotlin 2.1（启用 Compose Compiler Gradle 插件）
- Compose BOM 2025.x · compileSdk 36 · minSdk 26 · targetSdk 36 · Java 21 工具链

---

## 3. 技术决策（已锁定，不要偏离）

1. **分层**：`feature → core →（无反向依赖）`，严禁循环依赖。
2. **架构**：MVVM + Clean 分层（数据层 Repository / 领域逻辑 / 表现层 ViewModel）。
3. **状态管理**：ViewModel 暴露 `StateFlow<UiState>`，一次性事件走 `SharedFlow`。
4. **DI**：Hilt（`@HiltViewModel` + `@Inject` 构造注入），Application 中装配 `PixivApi`。
5. **存储**：
   - MMKV：Session / OAuth Verifier（对齐 API 库 `SessionStore` / `VerifierStore` 示例）
   - Room：阅读进度（字符级）、浏览历史、下载索引
   - DataStore Preferences：设置 / 阅读偏好 / 主题种子
6. **分页**：`PagedState<T>` 封装 `PagedLoader`（loadInitial / loadMore + next_url 游标），
   供 `LazyVerticalStaggeredGrid` / `LazyColumn` 驱动。
7. **图片**：Coil 3，注入 API 库提供的 `imageClient`（自动带 `Referer: https://app-api.pixiv.net/`）。
8. **网络策略**：不实现代理/直连加速；网络错误统一收敛为全局提示（中文文案）。

---

## 4. 模块结构

```
F:\pixiv-mateiral3\
├── agent.md                     # 本文件
├── design/ui-mockup.html        # UI 设计原稿（10 屏）
├── pixiv-api-kotlin/            # vendor API 库源码（只读，勿改）
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── lib/pixivapi/                # vendor 库构建模块（P0 补 build.gradle.kts）
│   └── src/main/java/...        # 指向/复制自 pixiv-api-kotlin/src
├── core/
│   ├── common/                  # 工具、常量、扩展（无业务依赖）
│   ├── model/                   # 领域模型（映射自 API DTO）
│   ├── network/                 # PixivApi 封装、游标分页、会话、Coil client
│   ├── database/                # Room：进度/历史/下载
│   ├── datastore/               # DataStore：偏好/设置
│   └── ui/                      # 通用 Compose 组件（瀑布流/空态/错误态/Chip）
├── feature/
│   ├── auth/                    # 登录/会话
│   ├── home/                    # 首页推荐（瀑布流+热门标签+关注流）
│   ├── discover/                # 搜索（联想/热门/V3 筛选）+ 排行榜
│   ├── illust/                  # 插画详情（多 P/评论/收藏打标签）
│   ├── viewer/                  # 全屏查看器（zoomable + ugoira 动图）
│   ├── novel/                   # 小说列表/详情/系列
│   ├── reader/                  # 小说阅读器（核心自研：HTML 解析+排版引擎）
│   ├── user/                    # 用户主页/关注/拉黑
│   ├── bookmark/                # 收藏夹/标签管理
│   ├── watchlist/               # 追更
│   ├── download/                # 下载管理 + 离线阅读
│   └── settings/                # 设置
└── app/                         # 壳工程：Application、导航根、主题、深链 pixiv://
```

### 依赖方向
```
app → feature/* → core/ui → core/network → core/database · core/datastore · core/model → core/common
                                          ↘ lib/pixivapi（Retrofit/OkHttp/Gson/pixiv-login）
```

---

## 5. 第三方库清单

### 已由 pixiv-api-kotlin 依赖（沿用）
`com.github.SoxiaLiSA:pixiv-login`、Retrofit 2.9、OkHttp 4.12、Gson、kotlinx-coroutines-android

### 新增
| 类别 | 库 |
|---|---|
| UI | Compose BOM + Material3 + Material Icons Extended |
| 导航 | androidx.navigation:navigation-compose（类型安全路由 + `pixiv://` 深链） |
| DI | dagger:hilt-android + hilt-compiler（ksp） |
| 图片 | io.coil-kt.coil3（coil-compose / coil-network-okhttp） |
| 缩放 | me.skydoves:zoomable |
| 本地库 | androidx.room（runtime/ktx/compiler） |
| 偏好/会话 | androidx.datastore:datastore-preferences + com.tencent:mmkv |
| 解析 | org.jsoup:jsoup |
| 后台 | androidx.work:work-runtime |
| 分页 | 自研（不引入 Paging3） |
| 测试 | JUnit / MockK / Turbine / Compose UI Test |

### 自研模块（无现成库）
- `UgoiraPlayer`：ugoira 动图（zip + frames[].delay 逐帧播放，注意内存）
- `NovelParser` + `NovelTypography`：webview HTML 解析为结构化段落 + 自绘排版引擎

---

## 6. 实施阶段与验收标准

> 顺序执行，每阶段结束必须能独立编译/验收，再进入下一阶段。

| 阶段 | 交付物 | 验收 |
|---|---|---|
| **P0 工程骨架** | 根 Gradle + `libs.versions.toml` + 全部模块；vendor `lib:pixivapi` 补构建文件；M3 主题+深色+动态取色；Hilt 装配 `PixivApi` | `:app:assembleDebug` 通过 |
| **P1 数据层+登录** | Repository 层、Room/DataStore/MMKV；PKCE 登录（Custom Tab + `pixiv://` 回调 + code 去重 + token 自动刷新 + 登出）；`PagedState` | 登录→token 持久化→刷新不中断请求 |
| **P2 浏览** | 首页瀑布流+热门标签+关注流；发现搜索（联想/热门/V3 筛选）；排行榜分段 | 三个 Tab 真实数据可滚动分页 |
| **P3 插画查看** | 详情多 P Pager + 真实宽高（`webApi.getIllustPages`）+ 相关 + 评论贴纸；全屏查看器（zoomable）+ 收藏/下载/壁纸；`UgoiraPlayer` | 多 P 滑页、捏合缩放、动图可播 |
| **P4 小说阅读（核心）** | `NovelParser`(Jsoup) → 结构化段落；自绘排版（字号/行距/字体/缩进/两端对齐）；4 主题+亮度+3 翻页模式；字符级进度落库+官方 marker 同步；系列/章节导航、书签、追更 | 长文流畅、进度重启恢复 |
| **P5 用户社交** | 用户主页（统计/分区/关注/拉黑）；收藏夹+标签管理；我的（历史/屏蔽） | 关注/收藏操作即时反馈 |
| **P6 下载+设置** | WorkManager 下载队列 + Room 索引 + 离线阅读；设置页（图片质量/阅读默认/屏蔽/清缓存）；网络错误提示框 | 断网可读已下载作品 |
| **P7 打磨** | 动效/无障碍/文案/错误空态；单测 + Compose UI 测试 + 真机联调 | 测试通过、无崩溃 |

---

## 7. 关键 API 对照（pixiv-api-kotlin）

> 详细文档见 `pixiv-api-kotlin/docs/PIXIV_API_DOCUMENTATION.md`。

| 需求 | 调用 |
|---|---|
| 登录 | `pixiv.oauth.startLoginUrl()` / `handleCallback(uri)`（PKCE，回调 scheme `pixiv`） |
| 首页推荐 | `api.getRecommendedIllusts(true)`（含 ranking_illusts） |
| 搜索 | `api.searchIllusts(word, sort, searchTarget, startDate, endDate, bookmarkNumMin, tool, ratioPattern, searchAiType, content_type, 尺寸…)` |
| 搜索联想 | `api.searchAutocomplete(word)` |
| 排行榜 | `api.getRanking(mode, date)`（day/week/month/day_male/day_female/week_rookie/day_manga/day_r18…） |
| 小说正文 | `api.getNovelHtml(id)` → ResponseBody（HTML，需 Jsoup 解析） |
| 小说详情 | `api.getNovel(id)` |
| 系列 | `api.getNovelSeries(seriesId)`、`api.getUserNovelSeries(uid)` |
| 追更 | `api.getWatchlistNovel()` / add / remove |
| 阅读书签 | `api.addNovelMarker(novelId, page)` / `getNovelMarkers()` |
| 作品详情 | `api.getIllust(id)` |
| 真实宽高 | `pixiv.webApi.getIllustPages(id)`（app-api 不提供） |
| 动图元数据 | `api.getUgoiraMetadata(id)`（zip + frames） |
| 收藏 | `api.bookmarkIllust(id, restrict, tags)` / `unbookmarkIllust`；novel 同理 |
| 评论 | `api.getIllustComments(id)`、`postIllustComment`、`getStamps()` |
| 关注 | `api.followUser` / `unfollowUser` / `getFollowingIllusts` |
| 拉黑 | `pixiv.webApi.saveBlock(csrfToken, BlockSaveRequest)` |
| 用户详情 | `api.getUserDetail(uid)`（v2，需 filter=for_ios） |
| 分页 | 所有列表响应带 `next_url`；`api.getNextIllusts/Novels/Users/Comments…` |

**注意事项**
- 图片 URL 必须走 `imageClient`（Referer），否则 403。
- 小说正文仅 HTML，无结构化文本 —— 阅读器依赖 P4 的解析引擎。
- OAuth 密钥来自 pixiv-login 库（`PixivOAuthConfig.PIXIV_ANDROID`），**禁止硬编码**。
- 不要在搜索请求里传 `include_potential_violation_works=false`（会漏结果）。

---

## 8. 编码规范（约束）

- **禁止修改** `pixiv-api-kotlin/` 源码；如需改由 lib 模块内适配。
- 文本类文件（.md/.json/.xml/.toml/.yml）禁止用 shell 改写，必须用 edit/write 工具。
- Compose 组件集中在 `core/ui` 复用；feature 模块内只放页面私有组件。
- 命名：模块内按 `数据层 / 状态 / UI` 分包；函数/类注释用中文，代码注释简洁。
- 提交前自查：`./gradlew :app:assembleDebug` 与相关模块单测必须通过。

---

## 9. 已知风险与对策

| 风险 | 对策 |
|---|---|
| 小说 HTML 结构多变 | `NovelParser` 多选择器回退 + 文本兜底 + 测试夹具 |
| ugoira 动图无现成库 | 自研 `UgoiraPlayer`（zip 解码 + delay 逐帧），注意 Bitmap 复用 |
| token 刷新并发 | 复用 API 库 `TokenInterceptor` 的锁机制，联调复测 |
| 无 Android Studio | 命令行构建；需要真机由用户安装调试 |
| 平板适配遗漏 | 布局一律按 `WindowSizeClass` 分流；宽屏内容用 `AdaptiveContentBox` 限宽 |

## 9.5 平板 / 自适应设计规范（横切需求）

> 所有界面必须同时考虑手机与平板，禁止只按手机尺寸设计。

| 尺寸类 | 宽度 | 导航 | 内容 |
|---|---|---|---|
| Compact | <600dp | 底部 NavigationBar | 单列/双列网格 |
| Medium | 600–839dp | 左侧 NavigationRail | 2–3 列网格；内容限宽 |
| Expanded | ≥840dp | 左侧 NavigationRail | 3–4 列网格；内容限宽 ≤760dp |

- **实现**：`core:common.classifyWindowWidth` + `core:ui.AdaptiveNavScaffold` / `AdaptiveContentBox`
- **网格**：瀑布流/作品网格用 `StaggeredGridCells.Adaptive(minColumnWidth)`，不要 `Fixed(2)`
- **阅读器**（P4）：正文容器 `AdaptiveContentBox(maxWidth=760dp)`，行宽恒定，平板两侧留白
- **查看器**（P3）：图片区域自适应居中，操作条在平板横屏时可改为底部或侧边
- **详情/列表双栏**（可选进阶）：Expanded 下浏览列表 + 详情两栏（list-detail pane），纳入后续规划

---

## 10. 当前进度

- [x] UI 原型 `design/ui-mockup.html`（已确认，整体合理，部分页面二期补）
- [x] **P0 工程骨架**（编译通过 `:app:assembleDebug`，单测 1/1 通过）
- [x] **P1 数据层 + 登录**（编译通过，单测 5/5 通过）
- [x] **P2 浏览**（编译通过，单测 12/12 通过）
- [x] **平板/自适应基础**（窗口尺寸分类 + 自适应导航壳 + 自适应瀑布流列数，单测覆盖）
- [x] **P3 插画查看**（编译通过，单测 15/15 通过）
- [x] **P4 小说阅读（核心）**（编译通过，单测 27/27 通过）
- [ ] P5 用户社交
- [ ] P6 下载 + 设置
- [ ] P7 打磨

### P0 完成要点（2026-08-03）
- 根 Gradle 工程 + `libs.versions.toml` 版本矩阵（AGP 8.13.0 / Kotlin 2.1.20 / KSP 2.1.20-1.0.31 / Compose BOM 2024.09.00）
- 模块：`app` + `lib:pixivapi` + `core/{common,model,network,database,datastore,ui}` + `feature/*`(12)
- **vendor 适配**：`lib:pixivapi` 持有 pixiv-api-kotlin 源码**副本**（原仓库只读不动），修复两处编译问题：
  1. `Models.kt` KDoc 中 `models/*.java` 的 `/*` 触发 Kotlin 嵌套注释未闭合
  2. `Pagination.kt` 中 `resp.nextUrl` 应为 DTO 字段 `resp.next_url`
- Hilt 装配 `PixivApi` 完成（`NetworkModule`：MMKV → SessionStore/VerifierStore → PixivApi）
- 构建环境：`local.properties` 指向本机 SDK；`gradle.properties` 含 `org.gradle.java.home`（jbr-21）与 `preferIPv4Stack`（规避 TLS 握手失败）
- 产出：`app/build/outputs/apk/debug/app-debug.apk`

### P1 完成要点（2026-08-03）
- **core:database**：Room `PixivDatabase` v1（ReadingProgress / BrowseHistory / DownloadEntry）+ DAO + `DatabaseModule`
- **core:datastore**：`UserPreferences`（阅读字号/行距/字体/主题/翻页/亮度、图片质量、动态取色、屏蔽标签）
- **core:network**：
  - `SessionRepository`（登录态可观察化、OAuth 回调桥接、登出/强制登出）
  - `PixivRepository`（api / webApi / imageClient 统一出口）
  - `PagedState<T>` 游标分页（loadInitial / loadMore / reset / error）
- **feature:auth**：`AuthViewModel`（code 去重、错误分类、Custom Tab）+ `LoginScreen` + `AuthRoute`（`hiltViewModel`）
- **app**：`MainActivity` 深链转发（onCreate/onNewIntent → SessionRepository）+ `PixivNavGraph`（auth/home 按登录态切换）
- 修复：Hilt 升至 **2.56.1**（2.51.1 无法读 Kotlin 2.1.20 元数据）；`core:network` hilt-compiler 误用 `implementation` 改为 `ksp`；CustomTabs 构件更名 `androidx.browser:browser:1.8.0`
- 测试：`PagedStateTest` 4 用例（首屏/翻页/错误/重置）+ `ExampleUnitTest` 1 用例，全部通过
- 产出：`app-debug.apk`（20.5 MB）

### P2 完成要点（2026-08-03）
- **core:common**：`formatCount`（万/亿格式化）+ `NumberFormatTest`（3 用例）
- **core:ui**：`PixivImage`（Coil AsyncImage）、`IllustCard`（封面/收藏角标/AI 标识）、`StatusViews`（Loading/Error/Empty）、`IllustWaterfallGrid`（双列瀑布 + 触底加载）
- **feature:home**：`HomeViewModel`（推荐/关注双 PagedState + 热门标签）+ `HomeRoute`（TopBar + 分区 Chip + 瀑布流）
- **feature:discover**：`DiscoverViewModel`（防抖联想 + 热门 + 三类型搜索 + V3 筛选）+ `DiscoverScreen`/`Results`/`FilterSheet`（ModalBottomSheet 筛选：排序/匹配/时间/收藏数/AI）；`RankingViewModel` + `RankingScreen`（7 种模式分段 + 排名列表）
- **feature:novel** `NovelRoute` 占位；**feature:user** `MeRoute` 占位（含登出）
- **app**：`MainShell`（Scaffold + NavigationBar 五 Tab + 内层 NavHost）；`PixivApp` 实现 `coil.ImageLoaderFactory`（注入 imageClient 带 Referer）；`PixivNavGraph` 升级为 auth / main 两级
- 修复：Coil 2 全局配置接口为 `ImageLoaderFactory`（非 SingletonImageLoader）；`total_bookmarks ?: 0L` 类型（Int? 与 Long → Number）统一 `(x ?: 0).toLong()`；瀑布流 `items` 改用单参重载 + `illust.id % size` 高度循环；MenuBook 用 AutoMirrored 版本
- 测试：`NumberFormatTest` 3 + `PagedStateTest` 4 + `ExampleUnitTest` 1，共 8 用例全通过
- 产出：`app-debug.apk`（20.6 MB）

### 平板 / 自适应基础（2026-08-03）
- **core:common**：`WindowSizeClass`（Compact/Medium/Expanded）+ `classifyWindowWidth(dp)`（<600 / 600~839 / ≥840）+ `useRail()` + `MAX_CONTENT_WIDTH_DP=760`；`AdaptiveTest` 4 用例
- **core:ui**：`AdaptiveNavScaffold`（手机底部 NavigationBar / 平板左侧 NavigationRail，按窗口尺寸类切换）、`AdaptiveContentBox`（内容宽度上限，供详情页/阅读器用）
- **app MainShell**：改用 `AdaptiveNavScaffold`
- **core:ui `IllustWaterfallGrid`**：`StaggeredGridCells.Fixed(2)` → `Adaptive(minColumnWidth=140.dp)`（手机 2 列，平板自动 3~4 列）
- 约定：详情页/阅读器/查看器在平板端必须用 `AdaptiveContentBox` 限制行宽；列表页用自适应列；后续页面一律走窗口尺寸类，禁止写死手机尺寸

### P3 完成要点（2026-08-03）
- **core:model**：`Illust.toPages()`（单图/多图 → 页面列表，displayUrl/originalUrl）+ `IllustExtTest` 3 用例
- **core:ui**：`ZoomableImage`（自研缩放：捏合 1~6x / 双击 / 平移钳制 / 缩放时禁用 Pager 滑动）
- **feature:illust**：`IllustViewModel`（详情 + 网页真实宽高补齐 + 相关 + 评论 + 收藏/取消 + 发评论 + 下载原图）+ `IllustDetailRoute`（多 P Pager + 页码/全屏入口 + 作者/统计/标签/简介 + 相关横滑 + 评论区）
- **feature:viewer**：`ViewerViewModel` + `ViewerRoute`（深色全屏、HorizontalPager + ZoomableImage、页码、底部收藏/下载）+ `UgoiraLoader`（zip 下载解压到 cache）+ `UgoiraPlayer`（按 delay 逐帧）+ `ImageSaver`
- **导航**：顶层新增 `illust/{illustId}` 与 `viewer/{illustId}?page={page}` 全屏路由；Home/Discover/Ranking 卡片 → 详情 → 全屏查看
- 修复：`getIllust` 返回 `SingleIllustResponse`（取 `.illust`）；`GifFrame.file` 可空处理；跨模块 caption 智能转换；图标 AutoMirrored
- 测试：`IllustExtTest` 3 新增，总计 15 用例全通过
- 产出：`app-debug.apk`（20.8 MB）

### Hero（共享元素）过渡特效（已回滚，2026-08-03）
- **结论**：尝试实现「卡片封面 → 详情大图」Hero 过渡，先后评估并实现了两版，均存在视觉/稳定性问题，**已按用户要求回滚，恢复 Hero 前的原样**（`illust/viewer` 路由回外层 `PixivNavGraph`，`MainShell`/`IllustCard`/`PagePager`/`AdaptiveNavScaffold` 等全部还原，删除 `core:ui Hero.kt`）。
- **调研记录（供后续参考）**：
  - 自研浮层方案（HeroRequestStore + HeroOverlay 矩形插值）：用户反馈 bug 多，弃用。
  - 官方 API 方案（`SharedTransitionLayout` + `Modifier.sharedElement`，Compose 1.7 / Navigation 2.8 可用：`AnimatedContentScope` 不继承 `SharedTransitionScope`，需外层 scope + 内层 `AnimatedVisibilityScope` 作参数）：Hero 目标加在 Pager item 内 AsyncImage 上会导致详情图片不加载（黑底+灰占位）；移到图片区域容器 Box 可修复，但最终仍被回滚。
- 若未来重做，建议直接升级 Navigation 2.9+ 使用官方 `composable` 内 `sharedElement`，或等 Compose 稳定后再评估。
- 当前状态：无 Hero，编译通过，单测 15/15，APK `app-debug.apk`（20.8 MB）。

### P4 完成要点（2026-08-03）
- **core:novel**（新模块）：`NovelBlock`（段落/标题/引用/插图/分隔线，带全文字符区间）+ `NovelParser`（Jsoup 多选择器回退 + 文本兜底，段落自动全角缩进）+ `NovelDocument`（`fullText` 纯文本 + `percentageAt`/`blockContaining`）；单测 8 用例
- **feature:reader**（新模块）：
  - `ReaderPaginator` 自研排版引擎：`TextMeasurer` 逐块测行 → 按页高切页 → 行级样式拼 AnnotatedString；段落两端对齐、标题 1.25x、引用降透明度、分隔线居中、插图独立成页（页高 = 宽×0.75）
  - `ReaderViewModel`：加载详情+HTML → 解析；DataStore 阅读偏好（字号/行距/字体/主题/翻页/亮度）；字符级进度 Room 防抖落库 + `flushProgress` 离场落库 + 官方 marker 比例换算低频同步；进度恢复优先级 本地 Room → 官方 marker → 开头；`progressRestored` 门闩防止 UI 抢先上报
  - `ReaderRoute`：3 翻页模式（滑动 LazyColumn / 翻页 HorizontalPager / 仿真 3D rotationY 翻页）、4 主题（日间/纸张/夜间/深黑）+ 亮度遮罩、顶栏（收藏/更多菜单：阅读书签+追更/设置）+ 底栏（百分比+页码/字数）；`ReaderSettingsSheet` 分段按钮+滑杆
  - `ReaderPageMapping` 纯函数（字符偏移↔页/官方页码），单测 4 用例
- **feature:novel**（替换占位）：`NovelFeedViewModel` 推荐流（游标分页 + 触底自动加载）；`NovelDetailRoute`（标题/作者/统计/标签/简介 + 开始或继续阅读 + 收藏/追更 + 系列章节列表高亮当前章）；`NovelCard`
- **app**：顶层新增 `novel/{novelId}` 与 `reader/{novelId}` 全屏路由 + 深链 `pixiv://novel/{id}`、`pixiv://reader/{id}`；`MainShell` 小说 Tab 接推荐流
- 修复：`core:database` / `core:datastore` 补 Hilt Gradle 插件 + `hilt-compiler`（此前库内 `@Module`/`@Inject` 未聚合，P4 首次注入 DAO 才暴露）；Channel 消息流用 `receiveAsFlow`；跨模块 nullable 属性 smart cast 需 `checkNotNull`
- 测试：`NovelParserTest` 8 + `ReaderPageMappingTest` 4，总计 27 用例全通过
- 产出：`app-debug.apk`

### P4 修复（2026-08-03）
- **简介富文本**：新增 `htmlToPlainText`（任意 HTML → 纯文本，`<br>`/块级标签转换行，`<a>` 等行内标签只留文字），小说详情简介不再显示原始 `<br/>`、`<a href>` 等标签；单测 4 用例
- **解析兜底强化**：`NovelParser` 增加保留换行的全文提取（`extractAllText`：遍历节点、块级/`<br>` 转换行、排除 script/style/iframe）；支持 `<div class="novel-paragraph">` 与纯 div 段落结构；移除兜底选择器中的裸 `div`（避免整段合并/重复）；`textFallback` 改为按换行切段（原 `body.text()` 会把全页压成一段）
- **解析策略升级（第二次修复）**：`parse` 改为「逐个候选容器尝试（`div.novel-content`/`.novel-view`/`.novel-body`/`#novel-body`/`section.novel-body`/`main`/`article`）→ 整页 `body` 全文提取 → `<script>` 内嵌 JSON 兜底（`content`/`text`/`description` 字段长文本，CJK 比例过滤 + `\n`/`\uXXXX` 去转义）」，解决部分真实页面正文在 React `__INITIAL_STATE__` 里导致"没有正文内容"的问题
- **阅读器交互修正（第二次修复）**：移除"点击正文切换工具栏"的动画效果；**点击正文任意处直接弹出阅读设置面板**；顶/底栏保持常显
- **防闪退加固（第三次修复）**：`ReaderViewModel` 所有协程体（偏好收集 ×6 / load / restoreProgress / loadServerState / saveProgress / 偏好写入 ×6）全面包 try-catch 或 runCatching，杜绝 `viewModelScope` 未捕获异常直接崩 App；`load()` 的 `isLoading=false` 移入 finally；进度恢复异常不再影响正文展示；`ReaderPaginator` 分页包 runCatching（失败返回空页而非崩溃）；`NovelParser` 超长段落（>3000 字符）自动切分，避免单个 Text 过大；异常均打 `Log.w`（tag: ReaderViewModel / ReaderPaginator / 新增 `loadServerState` 用 try/catch）
- **调试日志（第四轮）**：`ReaderViewModel` 注入 `@ApplicationContext Context`，`load()` 中打印并保存原始 HTML 到 `cacheDir/novel_debug/{id}.html`，并打印解析结果（块数 / 全文长度 / 各块类型 / 全文开头），便于排查"没有正文内容"是否来自网络返回异常或解析器未匹配结构
- **点击交互再修正（第四轮）**：原"点击正文任意处弹设置"区域过大挡住翻页 → 改为**左 1/3 上一页、右 1/3 下一页、中间 1/3 弹设置**（翻页/仿真模式）；`pagerState` 提到外层供点击翻页共用；滑动翻页由 HorizontalPager 自身处理、不受影响
- **仿真翻页重写（第六轮，直接移植 legado-with-MD3）**：`SimulationPageContent.kt` 忠实移植 `SimulationPageDelegate`——贝塞尔曲线卷页路径 mPath0（两条 quadraticTo 构造真实纸页卷曲）、当前页 = 整页 − 卷页区域（ClipOp.Difference）、下一页在卷页区域内绘制 + 柔光阴影、**纸背先铺背景色再沿折痕镜像当前页内容并灰化**（彻底解决文字黑影重叠）；拖拽点位置驱动卷页，松手回弹/翻过动画；翻下一页卷右下角、上一页卷左下角；点击三区翻页/设置
- **仿真翻页修正（第七轮）**：① 角落改为**按触摸点象限动态选择**（点页面哪个区就掀哪个角，对齐 legado `calcCornerXY`；右上/右下=下一页，左上/左下=上一页）；② 纸背**不再绘制镜像文字**——只填充纸色 + 边缘渐变阴影，彻底消除文字黑影重叠
- **仿真翻页修正（第八轮）**：下一页**只在"下一页露出三角"（mPath0 ∩ nextTri）内绘制**，卷页区域其余部分保持背景色被遮挡（不再把下一页整页铺底层导致提前透出文字，对齐 legado `drawNextPageAreaAndShadow` 只在 mPath1 内画 bitmap）
- **阅读器增强（目录/搜索/自定义字体/跟随系统）**：
  - **目录**：从 `NovelBlock.Heading` 提取（无标题时用长段落分节），底栏"目录"按钮 → ModalBottomSheet 列表 → 点击按 `jumpToChar` 跳转（滑动/翻页/仿真三模式均支持）
  - **文本搜索**：全文忽略大小写搜索，`searchResults` + 上一条/下一条跳转 + 匹配上下文列表；底栏"搜索"按钮 → 底部面板
  - **自定义字体**：`UserPreferences.readerCustomFontPath` + 系统文件选择器导入（复制到 filesDir/fonts/），字体选项新增"自定义"（`FontFamily(Font(File(path)))`，损坏回退衬线）
  - **主题跟随系统**：`readerFollowSystem` 偏好 + `isSystemInDarkTheme()`，开启后按系统深色自动切「夜间/纸张」，设置面板加 Switch（开启时主题分段禁用）
- **阅读器 UI 沉浸化（第九轮）**：默认只显示正文（无顶栏/底栏）；点击正文中间 1/3 切换工具栏显隐；顶栏 = 返回 + 标题 + 竖排三点（更多菜单：收藏/阅读书签/追更）；底栏 `ReaderToolBar` = 目录 + 百分比 + 搜索 + 设置；翻页/仿真模式左右边缘点击仍可翻页（仿真内部只处理左右边缘，中间交外层）
- **工具栏点击修复（第十轮）**：中间点击原由父层 `detectTapGestures` 处理，但子层手势（仿真 tap / pager / 滚动）消费指针事件导致父层收不到 up → 改为**内容上方叠加中间 1/3 透明覆盖层**（`pointerInput` 专属处理工具栏切换，不消费拖动、滑动穿透）；父层仅处理翻页模式左右边缘翻页，仿真模式父层不注册手势；左右边缘翻页/拖动均不受影响
- **工具栏浮层化（第十一轮）**：去掉 Scaffold 的 topBar/bottomBar（会挤压正文）→ 改为 **Box 浮层**：正文始终全屏（`statusBarsPadding`/`navigationBarsPadding` 避开系统栏），顶栏（返回/标题/三点更多）与底栏 `ReaderToolBar`（目录/搜索/设置）`align(TopCenter/BottomCenter)` 叠加在正文之上，弹出时**不挤压正文**；**阅读百分比移出工具栏**（进度仍按字符落库恢复）
- **工具栏沉浸 + 点击交互（第十二轮）**：顶/底栏背景 `themeColors.topBar.copy(alpha = 0.92f)` 半透明融入正文；**工具栏显示时点击正文左右边缘不再翻页，而是关闭工具栏**（翻页模式由正文 Box `pointerInput(pageMode, barsVisible)` 处理、仿真模式由 `SimulationPageContent` 新增 `barsVisible`/`onCloseBars` 处理，`pointerInput` key 含 `barsVisible` 保证状态变化后重启捕获最新值）
- **工具栏实色沉浸（第十三轮）**：用户要求**不透明** + **背景覆盖系统栏** → 顶/底栏浮层 Box 背景改为**实色 `themeColors.topBar`**（去掉 0.92 半透明），Box 不再 `statusBarsPadding`/`navigationBarsPadding`（背景延伸覆盖时间栏/小白条），内部内容改用 `Modifier.statusBarsPadding()` / 内层 `navigationBarsPadding()` 包裹避让；`TopAppBar` containerColor 设 `Color.Transparent`（背景由 Box 统一承担），`ReaderToolBar` Row 恢复实色背景
- **修复部分小说无法显示（第十四轮）**：pixiv isV2 页面（`isV2:true`）正文**不在 DOM**，而在 `<script>` 内 `window.pixiv.novel.text`（`\uXXXX` 转义、几十万字符）；原 `extractFromScripts` 用正则 `"text"\s*:\s*"...((?:[^"\\]|\\.)*)"` 匹配 → 超长文本**正则回溯触发 StackOverflowError**（Pattern.java:4106），Android 上表现不同导致静默返回空 → blocks=0。修复：**弃用正则匹配字段值**，改 `indexOf` 定位键 + `readJsonStringValue` 逐字符解析 JSON 字符串字面量（O(n)，处理 `\uXXXX`/`\n`/`\"`/`\/` 等转义），字段名扩展 `content/text/description/body`。真实样本加入回归测试（`DebugRealHtmlTest` + `test/resources/debug/26256802.html`）：blocks=2532, textLength=91654
- **小说插图支持（第十五轮）**：pixiv 正文插图以标记内嵌（`[pixivimage:ID]` 引用画作 / `[uploadedimage:ID]` 上传图）。`NovelParser` 新增 `parse(html, imageUrls)`，`splitEmbeddedImages` 把标记切分为 `NovelBlock.Image`；`PixivWebApi` 新增 `getNovelWeb`（`ajax/novel/{id}`）+ `WebNovel`/`WebEmbeddedImage`；`ReaderViewModel.resolvePixivImages` 用 `ajax/illust/{id}` 解析 pixivimage 首图。三种模式均已有图片渲染（Coil 默认 loader 带 Referer）
- **插图白屏修复（第十六轮）**：`textEmbeddedImages` 实际结构为 `{novelImageId: {novelImageId, sl, urls:{240mw,480mw,1200x1200,128x128,original}}}`——key 是 novelImageId、value 是 **urls 映射而非 url**；原 `WebEmbeddedImage(url)` 取不到值 → URL 保留协议串 → Coil 加载失败显示白屏。修复：`WebEmbeddedImage` 增加 `urls` 映射，URL 按 `1200x1200 > 480mw > 240mw > original > url` 择优；`resolvePixivImages` 与 `NovelParser.parse` 挪入 `Dispatchers.IO`（修复主线程卡顿 33 帧）
- **滑动模式误触工具栏 + 图片独占页修复（第十七轮）**：
  - **滑动模式**：中间 1/3 透明覆盖层**不再叠加**（滚动时误触频繁弹工具栏），工具栏改由**顶部 28dp 窄条**点按触发；翻页/仿真模式仍为中间点击切换
  - **图片参与分页**：`ReaderPage` 从 `Text/Image` 密封类改为**混合页**（`ReaderPage(startChar,endChar,elements)`），`PageElement` = 文本行/图片，图片**按顺序插入文本流**不再独占整页；图片高度自适应 `min(内容宽×0.75, 页高×55%)`（`IMAGE_MAX_HEIGHT_RATIO=0.55f`），图片后剩余不足一行即换页；`RenderPage`/`SimulationPageContent.renderPage` 改为 Column 逐元素渲染（空行按行高占位）；滑动模式图片仍保持宽×0.75
- **仿真翻页松手动画修复（第十八轮）**：翻过动画被绘制计算中 `coerceIn(0,w/h)` 截断——动画中 touch 出页但被夹回页边 → touch 回到 corner → 卷页几何消失 → 视觉"掀起到一半放回去再闪切"。修复：**移除 touch 坐标夹取**（允许出页，卷页几何随 touch 扩展覆盖整页 → 完整翻页动画），`settle` **去掉 `cancel` 强制回弹**（onDragCancel 手指滑出屏幕也按距离判定），阈值 `0.28f→0.22f`
- **翻过路径修正（第十九轮）**：日志定位 `settle` 翻过目标 `corner+(corner-touch)*3` 的直线会**精确经过 corner**（参数 t=0.25），touch 经过 corner 时卷页缩到 0、页面放平 → 视觉"弹回"。改为 `touch+(touch-corner)*scale`（**远离 corner 方向**，距离单调递增），scale 保证目标距离 ≥1.2 对角线（覆盖整页）
- **点击翻页末尾停顿修复（第二十轮）**：`turnTo` 原目标 `cx±w` 只让 touch 到页面边缘 → 卷页停在底部三角**未覆盖整页** → 动画结束闪切、停顿感。改为 touch 沿翻过方向**出页**（`(±w,-h)` 方向，目标距离 ≥1.3 对角线），卷页持续扩大覆盖整页后自然切页；动画时长/插值保持不动
- **目录按系列重构（第二十一轮）**：目录不再按当前小说标题/长段落"第 N 节"分章。`ReaderTocItem` 扩展 `(title, novelId=-1, charOffset)`（novelId=-1=当前小说页内跳转，否则=系列内目标小说）；`buildToc` 改 suspend：**非系列**（`series?.id==null`）→ 单条本小说；**系列** → `getNovelSeries` 循环分页拉全（`next_url` 解析 `last_order`，上限 20 页）列出**系列内各本**；新增 `tocLoading` 状态；目录面板加载中/空态区分，当前在读条目高亮加粗；点击其他分册 → `onOpenNovel(id)` 直接打开该本阅读器（`reader/$id`）
- **小说详情页 UI 优化（第二十二轮）**：
  - **系列分册点击 → 跳对应小说详情**：`NovelDetailRoute` 新增 `onOpenNovel` 回调（`navigate("novel/$id")`），`ChapterRow` 从 `onOpenReader` 改为 `onOpenNovel`；标题改"系列分册（N）"
  - **沉浸式封面 banner + 视差**：去掉 Scaffold/TopAppBar，改 Box + LazyColumn；首 item 为封面 banner（`image_urls.medium`，高 280dp 延伸到状态栏），底部 `verticalGradient`（透明→surface）过渡；封面图比容器高 160dp、`graphicsLayer.translationY = scrollOffset × 0.45`（上滑视差，移动慢于列表）；返回按钮浮在 banner 左上（半透明圆底白图标）
  - **评论区**：`NovelViewModel` 新增 `comments`/`commentsLoading`，`loadComments()` 调 `getNovelComments`（第一页）；详情页底部"评论（N）"区块：圆形头像 + 用户名 + 日期（`date.take(10)`）+ 内容；加载中/空态（点击重试）
- **头像显示修复 + 平板适配（第二十三轮）**：
  - **头像不显示根因**：pixiv 各接口返回的 `profile_image_urls` 尺寸字段组合不固定，`px_50x50` 常缺失；有 fallback 的地方（IllustCard/Discover）能显示，无 fallback 的（NovelRoute/IllustDetailRoute 作者+评论头像）URL null → 灰块
  - **修复**：core:ui 新增 `UserAvatar`（URL 用 `profile_image_urls?.best()` 自动 fallback 尺寸；URL 仍缺失时显示**用户名首字母圆形**兜底）；NovelRoute 作者/评论、IllustDetailRoute 作者/评论、IllustCard、DiscoverResults 全部改用 `UserAvatar`
  - **平板适配**：阅读器原本已由 `AdaptiveContentBox`（`MAX_CONTENT_WIDTH_DP=760dp`）限宽居中；小说推荐流补上 `AdaptiveContentBox` 限宽；小说详情页 banner 保持全宽沉浸、正文（header/操作/系列/评论）新增 `NovelCenteredBox`（`widthIn(max=760)` 居中）
  - **评论重叠 bug 修复（补充）**：`CommentsSection` 原无统一根容器（直接 emit 标题 + 各评论行多个并列 composable），被包在 `NovelCenteredBox` 的 **Box** 里 → Box 子项默认堆叠 → 评论内容/用户名/多个用户全部重叠。修复：`CommentsSection` 根改为 `Column` 统一包裹
- **评论区树形化 + 去 divider（第二十四轮）**：
  - **数据**：`Comment` 模型（Models.kt）已含 `replies: List<Comment>?` / `parent_comment`，`v3/{type}/comments` 接口内嵌回复数组 → 零 API/ViewModel 改动，直接渲染 `comment.replies`
  - **Novel 详情**：`CommentsSection` 移除 `HorizontalDivider`，评论间改 `Spacer(8.dp)`；`CommentRow` 父评论内容下方渲染子层：缩进浅色圆角块 `surfaceContainerLow` + `clip(12.dp)`，内部每条 `ReplyRow`（小头像 28dp + "回复 @被回复者：内容" + 日期），最多 20 条，行间 spacing 8dp
  - **Illust 详情**：`CommentRow` 同款子层（`IllustReplyRow`，头像 24dp，无日期，保持该页原有无 divider 风格）
  - **视觉**：区分父层（无背景、36dp 头像）与子层（缩进 + 浅色块、小头像），符合 Material Design 列表分组规范；评论与评论之间无分割线
- **小说下载/导出 TXT·EPUB（第二十五轮）**：
  - **能力**：详情页可将小说导出为文件——**TXT**（纯文本，跳过正文插图）/ **EPUB**（EPUB3 标准 zip，内嵌正文插图 + 封面）；范围支持**当前单本**或**整个系列**（`getNovelSeries` 循环分页拉全 → 逐章抓取串行下载）
  - **新增** `feature:novel` 内：`NovelContentLoader`（@Singleton，正文管线 getNovel→getNovelHtml→getNovelWeb 插图映射→parse→resolvePixivImages，与 ReaderViewModel.load 同链路，TODO 后续去重）；`NovelExporter`（@Singleton，TXT/EPUB 生成；手写 EPUB3 zip：mimetype STORED 首位 + container.xml + content.opf + nav.xhtml + 章节 xhtml + images；图片下载失败即跳过不内嵌）；纯函数 `buildTxt/buildEpub/buildOpf/buildNav/buildChapterXhtml/escapeXml/sanitizeFileName` 可单测
  - **存储**：`filesDir/Downloads/novels/{清洗标题}_{id}.txt|.epub`（与插画下载同目录体系，novels 子目录）
  - **UI**：`NovelActions` 新增第三个"下载"按钮（下载中禁用 + 进度文案"第 x/y 章"）；点击弹 `DownloadDialog`（本文 TXT / 本文 EPUB / 系列 TXT / 系列 EPUB，系列项仅 `series?.id != null` 显示）；完成后 Snackbar 提示文件名；`NovelViewModel` 注入 exporter，新增 `downloading`/`downloadProgress` StateFlow + `exportNovel`/`exportSeries`
  - **TODO（后续）**：①"我的下载"管理页（复用 `DownloadEntryDao.observeAll`）+ 删除；②离线阅读（`ReaderViewModel` 本地数据源优先，阅读器只消费 `NovelDocument` 已具备条件）；③SAF/MediaStore 导出公共 Downloads；④WorkManager 后台队列（`work-runtime-ktx 2.9.1` 已声明）+ 中断恢复；⑤`NovelContentLoader` 与阅读器去重
  - **测试**：feature:novel 新建 `NovelExporterTest`（6 用例：sanitizeFileName/escapeXml/buildTxt 单章与系列/EPUB zip 结构/章节图片引用）；feature:novel 补 `testImplementation(libs.junit)`
- **P5 用户社交 · 批次1（第二十六轮）：用户主页 + 我的页 + 阅读历史**
  - **feature:user**（补 Hilt/ksp/`:core:network`/`:core:database` 依赖）：新增 `UserViewModel`（`getUserDetail` 统计 + `is_followed`；三区 `PagedState`——插画/漫画 `getUserIllusts(type)`→`getNextIllusts`、小说 `getUserNovels`→`getNextNovels`；`toggleFollow` 即时反馈）+ `UserRoute`（用户主页：头部头像/名称/@account/简介 `user.comment` + 统计格 插画/小说/收藏/关注 + 关注按钮 + 分区 FilterChip + `IllustWaterfallGrid`/`NovelCard` 列表 + 触底分页 + `AdaptiveContentBox` 平板适配 + Snackbar）
  - **MeRoute 升级**：`MeViewModel`（`SessionRepository.currentUser`）+ 个人头部（`UserAvatar` 64dp + 名称/@account）+ 功能入口列表（阅读历史可跳；我的收藏/追更/屏蔽管理批次2占位"功能开发中"）+ 登出；`MeEntry` 列表项组件
  - **阅读历史落地（横切）**：`BrowseHistoryDao` 新增 `deleteByTarget`（先删旧再插入避免重复）；`IllustViewModel`/`NovelViewModel` load 成功时 `upsert`（illust/novel + title + coverUrl，`feature:illust` 补 `:core:database` 依赖）
  - **HistoryRoute**：`HistoryViewModel`（`observeRecent(100)` stateIn）+ 历史列表（封面 + 标题 + 类型标签 + 时间 `MM-dd HH:mm` + 删除），点击按类型跳 illust/novel 详情，支持单项删除/清空，空态
  - **NovelCard 上移**：`NovelCard` 移至 `core:ui`（供 user/bookmark/watchlist 复用），`formatCountForNovel` 移至 `core:common`；`feature:novel` 删除旧文件改 import
  - **导航**：`PixivNavGraph` 新增 `user/{userId}`（深链 `pixiv://user/{id}`）与 `history` 路由；`MainShell` 加 `onOpenHistory`；`IllustDetailRoute`/`NovelDetailRoute` 新增 `onOpenUser`——作者头像/名称可点击跳用户主页
  - **TODO（批次2，下一步）**：feature:bookmark 收藏夹+标签筛选、feature:watchlist 追更、屏蔽（`saveBlock` 需从 `session.cookie()` 解析 `csrf_token=`，有 403 风险）；MeRoute 收藏/追更/屏蔽入口接入
  - 测试：全部相关模块回归通过（core:novel / feature:reader / core:network / feature:novel / feature:user / core:common）
- **P5 用户社交 · 批次2（第二十七轮）：收藏夹 + 追更 + 屏蔽 → P5 完成**
  - **feature:bookmark**（补 Hilt/ksp/`:core:network`）：`BookmarkViewModel`（当前用户 uid=`session.loggedInUid`；类型 Tab 插画/小说；`getIllustBookmarkTags`/`getNovelBookmarkTags` 标签列表 + "全部"；`getUserBookmarkedIllusts/Novels(uid,"public",tag)` 按标签筛选，切标签 `loadInitial` 自动覆盖；`PagedState` 分页）+ `BookmarkRoute`（TopAppBar + 类型 FilterChip + 标签 LazyRow + `IllustWaterfallGrid`/`NovelCard` 列表 + 触底分页）
  - **feature:watchlist**（补依赖）：`WatchlistViewModel`（`getWatchlistNovel`→`getNextWatchlist` 分页）+ `WatchlistRoute`（系列行：作者头像/标题/章节数，点击打开 `latest_content_id` 小说详情；`isMasked` 显示"已隐藏的系列"）
  - **屏蔽**：`UserViewModel.toggleBlock`（网页 `saveBlock(BlockSaveRequest(user_id, block/unblock))`，`x-csrf-token` 从 `session.cookie()` 解析 `csrf_token=`；拉黑态用 `webApi.getWebUserDetail.isBlocking` 初始化）+ `UserRoute` 头部更多菜单（拉黑/取消拉黑）；`BlockedViewModel`（`getMutedHistory` 用户+标签）+ `BlockedRoute`（屏蔽管理：已屏蔽用户可取消屏蔽 `saveBlock(unblock)`，标签取消无 API 暂"开发中"）
  - **导航**：`PixivNavGraph` 新增 `bookmarks`/`watchlist`/`blocked` 路由；`MainShell` 传 `onOpenBookmarks`/`onOpenWatchlist`/`onOpenBlocked`；`MeRoute` 三个入口接入（移除"功能开发中"占位）
  - **风险记录**：拉黑依赖网页 Cookie 中的 `csrf_token`，若登录会话无该 Cookie 则提示"无法获取 CSRF Token，拉黑暂不可用"（不崩溃）
  - 测试：全部相关模块回归通过
  - **至此 P5 用户社交完成**（用户主页/关注/拉黑 + 收藏夹标签 + 追更 + 我的页 + 阅读历史 + 屏蔽管理）
- 测试：`HtmlToPlainTextTest` 4 + `NovelParserTest` 15 + `DebugRealHtmlTest` 1，core:novel 共 20 用例；feature:novel `NovelExporterTest` 6 用例
