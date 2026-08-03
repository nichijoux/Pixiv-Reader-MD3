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
- 测试：`HtmlToPlainTextTest` 4 + `NovelParserTest` 新增 4（div 段落/纯 div 兜底/script 排除/React JSON 兜底），总计 35 用例
