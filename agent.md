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
- **我的页 Material 卡片重构 + 下载/标签/历史/设置（第二十八轮）**：
  - **MeRoute 重构**：Material3 Card 分组——用户卡片（头像/名称/@account）+ "内容"卡片组（我的收藏/浏览历史/下载管理/收藏标签/屏蔽管理）+ "系统"卡片组（设置）+ 登出；`verticalScroll`
  - **浏览历史筛选**：`BrowseHistoryDao` 新增 `observeByType`；`HistoryViewModel` 加 `HistoryFilter`（全部/插画/小说/用户）`flatMapLatest` 切换查询；`HistoryRoute` 顶部 FilterChip；点击用户记录跳用户主页（新增 `onOpenUser`）；`UserViewModel` 打开主页时写 user 历史（`deleteByTarget` + upsert）
  - **下载索引体系**：现有下载点全部接入 `DownloadEntryDao`（`targetType`=illust/novel，`status`=done/failed，含 localPath/title/coverUrl/pageCount）——`IllustViewModel.download`、`ViewerViewModel.download`（feature:viewer 补 `:core:database`）、`NovelExporter.exportNovel/exportSeries`（系列导出不再二次拉列表，返回 `(file, chapterCount)`）
  - **下载管理页**：`DownloadsViewModel`（`observeAll` + 分类 filter 全部/插画/小说 + `delete` 删文件仅限 filesDir 内 + 删索引）+ `DownloadsRoute`（分类 Chip + Card 列表：类型图标/标题/大小/时间/状态标记/删除）
  - **收藏标签页**：`TagsViewModel`（`getIllustBookmarkTags`/`getNovelBookmarkTags`）+ `TagsRoute`（Tab + 标签卡片含计数）→ 点击跳 `bookmarks?type={t}&tag={tag}`；`bookmarks` 路由加可选 `type`/`tag` 参数，`BookmarkViewModel` 从 `SavedStateHandle` 读取预选中
  - **设置页（feature:settings 填充）**：补 Hilt/ksp/`:core:datastore` 依赖；`UserPreferences` 新增 `themeMode`（0跟随/1浅色/2深色）、`autoUpdate`；`SettingsViewModel`（读写 + 版本号 PackageManager）+ `SettingsRoute`（外观卡片：主题模式 FilterChip 三选 + 动态取色 Switch；通用卡片：自动更新 Switch；关于卡片：App 名/版本/App 描述）
  - **主题真实生效**：`MainActivity` 收集 `themeMode` + `dynamicColor` → `PixivReaderTheme(darkTheme = 按模式计算, dynamicColor)`
  - **导航**：新增 `downloads`/`tags`/`settings` 路由；`MainShell` 加 `onOpenDownloads`/`onOpenTags`/`onOpenSettings`；`history` 接 `onOpenUser`
  - TODO（P6）：自动更新实际逻辑、WorkManager 下载队列/重试、下载状态实时跟踪
  - 测试：全部相关模块回归通过
- **P6 离线阅读闭环（第二十九轮）：下载到应用 → 断网可读**（验收：断网可读已下载作品）
  - **序列化**：core:novel 新增 `NovelDocumentCodec`（org.json 编解码 NovelDocument：paragraph/heading/quote/image/separator 五类块 + fullText/textLength，图片保留最终 URL）；`NovelDocumentCodecTest` 4 用例（还原全类型/非法 JSON 返回 null/空文档/caption null）；core:novel 测试补 `org.json:json`（Android 内置类本地 JVM 单测不可用）
  - **离线缓存仓库**：core:network 新增 `OfflineNovelRepository`（@Singleton，依赖新增 `:core:novel`）——`filesDir/offline/novels/{id}.json`（文档）+ `{id}_meta.json`（最小元数据：id/title/cover/seriesId/seriesTitle/userId/userName/textLength/pageCount/isBookmarked，org.json 手写）；`save/loadDocument/loadNovel/exists/delete`
  - **下载到应用**：`NovelViewModel` 注入 `NovelContentLoader`+`OfflineNovelRepository`+`DownloadEntryDao`；`downloadOfflineCurrent()`/`downloadOfflineSeries()`（系列循环分页拉全逐章串行，进度"第 x/y 章"）；缓存保存 + `DownloadEntryDao` 写索引（`targetType="novel_offline"` 区分文件导出）；`fetchSeriesNovels` 复制
  - **下载对话框**：`DownloadDialog` 分两组——"导出文件"（本文/系列 TXT·EPUB）+ "离线阅读"（下载到应用/下载整个系列）
  - **离线阅读**：`ReaderViewModel` 注入 `OfflineNovelRepository`；`load()` **离线优先**——`exists(novelId)` 则读缓存文档+元数据（跳过网络与重新解析），`_isOffline` 状态；`ReaderRoute` 顶栏标题旁显示"离线"小字；目录/进度照常（本地 Room 恢复）
  - **下载管理**：`DownloadFilter` 加 `OFFLINE`；`DownloadsViewModel.delete` 区分——`novel_offline` 清离线缓存 + 索引，illust/novel 删文件 + 索引；`DownloadsRoute` 离线分类 Chip + 行图标（Save/Description/Image）+ "离线阅读"标识
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过（core:novel 现 24 用例）
  - TODO（P6 收尾）：WorkManager 后台队列/重试、下载状态实时跟踪、自动更新逻辑、网络错误提示框、图片质量接入、清缓存
- **P6 收尾（第三十轮）：清缓存 + 下载状态 + 下载管理直达**
  - **设置页"存储"卡片**：`DownloadEntryDao` 新增 `deleteByType`；`SettingsViewModel` 注入 `DownloadEntryDao`（feature:settings 补 `:core:database`）+ 缓存大小估算（offline/novel_debug/cacheDir 递归求和，`formatSize`）+ `clearCache()`（删 `filesDir/offline` + `novel_offline` 索引 + `novel_debug` + Coil `diskCache.clear()`）；`SettingsRoute` 增加"存储"卡片（当前占用 + 清除按钮，Snackbar 反馈）；修复 `File(cacheDir)` 无单参构造（cacheDir 已是 File）
  - **离线下载状态**：`upsertOfflineIndex(novel, status)` 支持 `downloading`/`done`——系列逐章先标"下载中"再标"完成"，下载管理页实时可见
  - **下载管理直达**：`DownloadsRoute` 新增 `onOpenIllust`/`onOpenNovel`/`onOpenReader`，条目可点击——**离线小说 → `reader/{id}`（离线优先直读）**、小说 → 详情、插画 → 详情；`DownloadRow` Card 加 clickable；`PixivNavGraph` downloads 路由接入回调
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
  - 剩余 TODO：WorkManager 后台队列/重试、自动更新实际逻辑、网络错误提示框、图片质量接入（P7 打磨候选）
- **小说评论发布（第三十一轮）**：
  - **core:ui** 新增 `CommentInput`（文本输入 + 发送按钮，插画/小说评论区共用）；`IllustDetailRoute` 删除私有 `CommentInput` 改 import 共用
  - **feature:novel**：`NovelViewModel` 新增 `commentDraft` + `onCommentDraftChange` + `postComment`（`postNovelComment(novelId, text)`，成功清空 + 刷新评论区 + Snackbar）；`NovelRoute` 传参链（NovelDetailRoute → NovelDetailContent → CommentsSection）接入，评论区底部显示评论输入框
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
  - 剩余 TODO：WorkManager 后台队列/重试、自动更新实际逻辑、网络错误提示框、图片质量接入（P7 打磨候选）
- **P6 TODO 完成（第三十二轮）：网络错误提示 + 检查更新 + WorkManager 离线下载**
  - **网络错误提示框**：core:network 新增 `NetworkMonitor`（@Singleton，ConnectivityManager `registerDefaultNetworkCallback` → `isOnline` StateFlow）；`MainActivity` 包全局 `Scaffold` + `SnackbarHost`，`LaunchedEffect(isOnline)` 断网时 Snackbar"网络连接已断开"
  - **检查更新**：`SettingsViewModel.checkUpdate()` + 设置页关于卡片"检查更新"入口（无发布渠道，提示"当前已是最新版本"；TODO 接入远程版本检查）
  - **WorkManager 离线下载后台化**：`libs.versions.toml` 加 `androidx-hilt-work`；feature:novel / app 补 `work-runtime-ktx` + `hilt-work`；`PixivApp` 实现 `Configuration.Provider`（HiltWorkerFactory）；新增 `NovelOfflineDownloadWorker`（@HiltWorker + @AssistedInject，注入 NovelContentLoader/OfflineNovelRepository/DownloadEntryDao/PixivRepository，inputData `novelId`+`seriesId`，单本或系列逐章下载，索引 downloading→done，失败 `Result.retry()`，系列分页复制）；`NovelViewModel` 注入 `@ApplicationContext`，`downloadOfflineCurrent/Series` 改为 `WorkManager.getInstance(context).enqueue`（移除协程版与私有 fetchSeriesNovels/upsertOfflineIndex，`_message.trySend`）
  - **图片质量接入说明**：pixiv 图床各尺寸 URL 为接口字段组合（medium/original 路径不同不可推导），全局接入需改所有调用点传不同字段——维持设置 `imageQuality` 偏好 + TODO
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
  - 至此 P6 剩余 TODO 全部完成（WorkManager 队列/重试 ✓、自动更新占位 ✓、网络错误提示 ✓、清缓存 ✓、下载状态 ✓）；仅剩图片质量接入（技术限制）+ P7 打磨
- **发现页搜索重构（第三十三轮）：滑动切换类型 + 全量筛选 + 搜索历史 + 热门预览**
  - **搜索历史（Room，与现有 BrowseHistoryDao 模式一致）**：core:database 新增 `SearchHistoryEntity`（keyword/searchedAt）+ `SearchHistoryDao`（`observeRecent(20)`/`deleteByKeyword` 先删旧再插去重/upsert/delete/clearAll）；`PixivDatabase` version 1→2 加 `MIGRATION_1_2`（建表保数据）；`DatabaseModule` 提供 DAO
  - **DiscoverViewModel 重构**：`SearchFilters` 全量扩展（sort 修正为 `popular_desc`、补 `exact_match_for_tags`；插画 tool/ratioPattern/contentType/width/height 区间；小说 genre/isOriginalOnly/isReplaceableOnly/textLength/wordCount/readingTime 区间）；`loadOptions()`（`searchOptions(word="")` → toolOptions/genreOptions）；`search()` 按类型传全量参数 + `recordHistory` 写历史 + `loadPopular`（`popularPreview`/`popularNovelPreview` 热门预览，插画/小说各取 10）；`clearSearch()`（清空回初始态）；`removeHistory`/`clearHistory`
  - **DiscoverScreen 三态 UI**（对照 HTML 预览）：搜索框（聚焦高亮 + ✕ 清除 + 搜索按钮）；初始态 = 热门搜索 + **搜索历史**（可单删/清空/点击重搜）；有输入 = 联想列表；结果态 = `TabRow` + `HorizontalPager` 滑动切换 插画/小说/用户、下划线跟随、点 Tab 平滑滚动；`LaunchedEffect(currentPage)` 同步 `setType`（切类型重搜）；顶部**热门预览横滑区**
  - **FilterSheet 按类型动态渲染**：插画（通用+专属：比例/内容类型/工具下拉/宽高区间）/小说（通用+专属：题材下拉/仅原创/仅可转载/文字数/字数/阅读时长）/用户（提示无筛选）；FilterChip 互斥、Switch、`LazyRow` 选项滚动；**宽度约束**——时间/区间输入行用 `Row` + 两端 `weight(1f)` + 固定"~"分隔，不超宽；重置/应用
  - **结果行可点击**：`NovelSearchResults`/`UserSearchResults` 加 `onOpenNovel`/`onOpenUser`（NovelRow/UserRow clickable）；`DiscoverRoute` 签名扩展；`MainShell`/`PixivNavGraph` 补 `onOpenUser` 传参
  - 修复：`searchOptions` 需必填 `word`；feature:discover 补 `:core:database` 依赖；RangeInputRow 可空参数；genre `it?.toIntOrNull()`
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **搜索结果 UI 修正（第三十四轮，对照 search-ui-mockup）**：
  - **小说条目卡片化**：`NovelRow` 改为 `Card`（surfaceContainer 圆角 14dp，行间距 10dp 去掉 divider）；封面 68×90 圆角 10dp；标题 bodyLarge SemiBold（2 行）+ 作者 + "字数/收藏"（labelMedium，间距 14dp）；**移除右侧 KeyboardArrowRight 箭头**
  - **用户条目卡片化**：`UserRow` 改为 `Card`：头像 52dp + 名称 titleMedium + @account + "代表作 N"；**代表作缩略图放大到 76dp ×3、间距 8dp**（清晰可辨）；布局宽松（padding 14dp）
  - **列表容器**：小说/用户搜索结果 `LazyColumn` 改 `verticalArrangement.spacedBy(10.dp)`，去掉分隔线；清理未使用 imports（Icons/Favorite/HorizontalDivider/Icon）
  - 说明：真实 Novel 数据无"连载中"等状态字段，未加状态标签（mockup 为演示）
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **搜索体验优化（第三十五轮）：热门缓存 + 历史胶囊长按删除 + 用户更多信息**
  - **热门搜索缓存**：`UserPreferences` 新增 `hotTags`（`\n` 分隔 List<String>）+ `hotTagsUpdatedAt`（`longPreferencesKey`）与 setters；`DiscoverViewModel` 注入 `UserPreferences`（feature:discover 补 `:core:datastore`），`loadHotTags()` **24 小时 TTL 缓存优先**（`first()` 读缓存 → 新鲜直接用 `TrendingTag(tag=name)` 构造；过期才网络刷新并回写缓存），避免每次打开发现页都请求
  - **搜索历史胶囊化**：`IdlePanel` 历史区从列表行改为 **FlowRow 胶囊**（圆角 16dp + surfaceContainerHigh 背景），`HistoryChip` 用 `combinedClickable`——**单击搜索、长按删除单条**（`ExperimentalFoundationApi`/`ExperimentalLayoutApi`），保留"清空"
  - **用户搜索更多信息**：`UserRow` 增加 **简介**（`user.comment`，最多 2 行）+ **代表作最多 6 张**（`chunked(3)` 两行 × 3，76dp 圆角 10dp），不再只显示 3 张
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **CreatorProfileCard 用户卡片（第三十六轮，按规格组件化）**：
  - **core:ui 新增 `CreatorProfileCard`**（+`CreatorProfile` data class：id/name/avatarUrl/covers/isFollowed）——深色圆角卡片（32dp 圆角，固定 `0xFF1C1B1F`）；顶部 **3 封面横排**（高 180dp，`ContentScale.Crop` 边缘到边缘）；底部 **96dp 圆形头像**（3dp 白边框 + `offset(y=-40dp)` 负垂直偏移重叠封面）+ **白色 20sp 粗体用户名** + **药丸关注按钮**（150×56dp、透明背景、紫色描边/文字）；关注状态 `remember` 组件内维护，切换回调 `onToggleFollow(Boolean)`；Coil `AsyncImage`（走 app ImageLoader 带 Referer）
  - **应用**：`DiscoverViewModel` 加 `toggleFollowUser(userId, nowFollowed)`（follow/unfollow）；`DiscoverResults.UserRow` 改用 `CreatorProfileCard`（covers 取 `preview.illusts.take(3)` 封面、`isFollowed=user.is_followed`）——回到 3 代表作但按新规格展示；用户搜索结果列表卡片化
  - **主题化修正（补充）**：按反馈去除固定深色卡/紫色——改用 `MaterialTheme.colorScheme`（`surfaceContainer` 卡片、`surface` 头像边框、`onSurface` 用户名、`OutlinedButton` 关注按钮）；尺寸缩小——封面 180→120dp、头像 96→64dp（边框 3→2dp、负偏移 -40→-24dp）、用户名 20sp→titleMedium、按钮 150×56→40dp 高 OutlinedButton、圆角 32→16dp，与 NovelCard 等其余组件风格一致
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **搜索图片卡片修正（第三十八轮）：插画卡片收藏按钮 + 图片完整显示**：
  - **`IllustCard`**：封面按作品 `width/height` 比例 `aspectRatio` 完整显示（无尺寸回退 `coverHeight`），修复瀑布流固定高度 `Crop` 导致"只显示中间部分"；新增 `onToggleFavorite: ((Boolean) -> Unit)?` 参数（null 隐藏）——右上角半透明圆底收藏按钮（`remember` 状态 + `Favorite/FavoriteBorder` 红/白，点击切换回调）；AI 标识移到左上角避免冲突；收藏数角标保留右下
  - **`IllustWaterfallGrid`**：新增 `onToggleFavorite: ((Long, Boolean) -> Unit)? = null` 透传（`cb(illust.id, fav)`）
  - **搜索页接入**：`DiscoverViewModel.toggleIllustFavorite`（bookmark/unbookmarkIllust）；`IllustSearchResults` 传 `onToggleFavorite`（搜索结果卡片外面即可收藏/取消）
  - **微调（补充）**：收藏按钮 padding 4→8dp 避开卡片圆角裁剪（修复右上角被遮挡）；`NovelSearchResultCard` 放大——封面 88→104dp（圆角 10→12dp）、卡片 padding 12→14dp、作者头像 24→28dp、收藏按钮 34→36dp、行距 8→10dp，小说卡片不再偏小
  - **插画卡片封面浮层四项调整（补充2）**：① 右下收藏数角标放大（图标 11→14dp、`labelSmall`→`labelMedium`、内边距 8×3、圆角 8dp）；② AI 标签放大 + **Material 配色**（固定紫 → `tertiaryContainer`+`onTertiaryContainer`，`labelMedium`，圆角 8dp）；③ 右上收藏按钮缩小（30→24dp、图标 16→14dp、黑底 0.4→0.35）；④ 左上角新增**页码标识**（`page_count > 1` 显示 `"xP"`，`secondaryContainer` 配色，单张不显示）——左上角改为 Row 并排 AI + 页码
  - **AI/页码标签中性化（补充3）**：按反馈去除鲜艳色——AI 标签与页码标识均改为 **中性黑底白字**（`Color.Black.copy(alpha=0.45f)` + `Color.White`，`labelMedium`，圆角 8dp），与收藏数角标风格统一，不鲜艳
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **热门/最新 作为搜索模式（第三十九轮）**：
  - **模式化**：`DiscoverViewModel` 新增 `SearchMode { LATEST, HOT }`，`SearchFilters.mode` 字段（默认 LATEST）；`search()` 按模式分支——`HOT`（插画/小说）只调 `loadPopular()` 拉 `popularIllusts/popularNovels` 一次性；`LATEST` 走常规 `illustPaged/novelPaged` 分页；用户类型忽略 HOT
  - **筛选面板入口**：`FilterSheet` 通用区最上方加 **"模式"** 单选行（热门/最新，FilterChip），随"应用筛选"生效
  - **结果页**：`SearchResultPager` 按 `mode` 分支——HOT 显示**热门一次性完整列表**（插画 `IllustWaterfallGrid` / 小说 `LazyColumn`+`NovelSearchResultCard`，空态"暂无热门作品"）；LATEST 显示常规结果；**移除顶部固定热门横滑区**（`PopularIllustRow/PopularNovelRow/PopularCard/PopularNovelCard`/`ResultPage` 删除）——不再突兀/挡结果
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **筛选面板两级展示（第四十轮）：公共条件 + 完整条件**：
  - `FilterBottomSheet` 新增 `detailed: Boolean`：**通用区**（模式/排序/匹配/时间/收藏/AI）始终平铺显示（公共条件，与类型无关）；`detailed=true` 时追加当前类型专属区（插画比例/内容类型/工具/宽高、小说题材/仅原创/仅可转载/文字数/字数/阅读时长）；标题分级（未搜索"高级筛选"、搜索后"高级筛选 · 插画/小说"）
  - `DiscoverScreen` 传 `detailed = hasSearched`——**进入发现页默认筛选面板 = 公共通用条件**（不再"像小说条件"）；**搜索出结果后 = 完整条件**（保留全量筛选）；用户类型始终"无筛选"
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **底部导航沉浸式修复（第四十一轮）**：
  - **根因**：`MainActivity` 外层 `Scaffold` 默认 `contentWindowInsets`（含系统栏）+ `Box.padding(padding)` 把内层 `MainShell` 的 `NavigationBar` 抬离系统导航栏 → 底部不沉浸
  - **修复**：外层 `Scaffold` 设 `contentWindowInsets = WindowInsets(0,0,0,0)`、`Box` 不再 padding（`{ _ -> }`）——`MainShell` 的 `NavigationBar`（默认 insets 自带沉浸背景）延伸到系统导航栏；`enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT))` 去掉系统导航栏暗色 scrim
  - **不影响小说界面**：阅读器（ReaderRoute）为顶层沉浸浮层路由，自行处理 `statusBarsPadding`/`navigationBarsPadding`（第十三四轮实现），不依赖外层 padding；其他顶层页（illust/novel/user 详情）用 Scaffold+TopAppBar 默认 insets 自处理，均不受影响
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **小说列表 item 统一 + 组件更名（第四十三轮）**：
  - **三处统一**：小说 Tab 推荐流（`NovelRoute`）、用户主页小说分区（`UserRoute.SectionNovel`）、收藏夹小说（`BookmarkRoute.BookmarkNovelList`）全部改用通用小说卡片（原 `NovelSearchResultCard`）——样式与搜索结果完全一致；各 ViewModel（`NovelFeedViewModel`/`UserViewModel`/`BookmarkViewModel`）新增 `toggleNovelFavorite`
  - **交互完整接通**：封面→阅读器（`onOpenReader`）、作者→主页（`onOpenUser`）、收藏切换、标签→搜索（`onSearchTag`）——`MainShell`/`PixivNavGraph` 补 `onOpenReader`/`onOpenUser` 传参
  - **标签搜索跨 Tab**：`ROUTE_MAIN` 加可选 `search` 参数（`main?search={search}`）；`MainShell` `initialSearch` + `pendingSearch`（顶层路由标签 → `navigate("main?search=xxx")`，小说 Tab 标签 → 直接切 `discover_tab`）；`DiscoverRoute` 加 `initialQuery`（进入自动 `onQueryChange`+`search`）
  - **组件更名（通用化）**：`NovelSearchResultCard` → **`NovelCard`**（core:ui，文件更名），`NovelSearchResult` → **`NovelCardData`**；全部引用更新（DiscoverResults/DiscoverScreen/NovelRoute/UserRoute/BookmarkRoute）；删除旧 `NovelCard`（第三十三轮的上移版，已无引用）；`formatCountForNovel` 保留（详情页仍用）
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **个人中心/设置界面重构（第四十四轮）**：
  - **可复用组件（core:ui）**：`SettingsCard`（数据驱动 `SettingsCardItem(icon/title/description/trailingIcon/onClick)`，Card + Row[leadingIcon + 标题/描述 + trailing]，全 Material 主题色/typography）；`ProfileHeader`（`ProfileHeaderData` + 头像可点击 + 名称/@account + 可选操作按钮）；`UserAvatar` 加 `onClick` 支持
  - **MeRoute 重构**：ProfileHeader（头像/名称/@account + "个人主页"按钮，点击进 `user/{ownUid}`）+ 6 个分组数据驱动卡片：
    - 用户内容管理：收藏/浏览历史/下载管理/收藏标签/屏蔽管理（现有回调）
    - 账户管理：账户信息（@account·UID，点击进主页）+ 退出登录
    - 外观与阅读设置：主题与外观 → SettingsRoute
    - 推荐与过滤：**屏蔽标签**（内嵌 `MutedTagsDialog`：FlowRow chips 点击删除 + 输入添加，读写 `UserPreferences.mutedTags`）
    - 系统设置：缓存与更新 → SettingsRoute
    - 关于信息：logo + 名称 + 版本 + **开源链接 `https://github.com/nichijoux/Pixiv-Material`**（点击 `Intent.ACTION_VIEW` 打开）+ 许可文本
  - **MeViewModel 增强**：`ownUid`/`versionName`/`mutedTags`（stateIn）+ `addMutedTag`/`removeMutedTag`（feature:user 补 `:core:datastore`）
  - **导航**：`MeRoute` 加 `onOpenUser`，MainShell 已有 onOpenUser 直接传入
  - **屏蔽标签独立设置页（补充）**：新增 `MutedTagsRoute`（TopAppBar"屏蔽标签" + 返回/清空；添加输入框 + `InputChip` 列表点击/✕删除 + 空态）+ `MutedTagsViewModel`（读写 `UserPreferences.mutedTags`，`addTag/removeTag/clear`）；`MeRoute` 删除内嵌 `MutedTagsDialog`，"屏蔽标签"卡片改跳 `ROUTE_MUTED_TAGS`（`onOpenMutedTags`，MainShell/PixivNavGraph 接线）；**ProfileHeader 操作按钮由"个人主页"改为"退出登录"**，删除"账户管理"section 的"账户信息"卡片（保留退出登录卡片）
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **首页图片卡片收藏按钮（补充）**：`HomeRoute` 推荐流与关注流 `IllustWaterfallGrid` 传 `onToggleFavorite`（`HomeViewModel.toggleIllustFavorite` bookmark/unbookmarkIllust）——首页图片卡片与搜索结果一致显示右上角收藏按钮，可直接收藏/取消
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **屏蔽功能合并（第四十五轮，方案 A）**：解决"屏蔽管理"与"屏蔽标签"功能重合——统一为单一**屏蔽管理**入口：
  - `BlockedRoute` 重构为三区块：**本地过滤标签**（推荐/搜索过滤，顶部添加输入框 + `InputChip` 点击/✕删除 + TopAppBar 清空，读写 `UserPreferences.mutedTags`）+ **服务端已屏蔽标签**（展示）+ **已屏蔽用户**（可取消）
  - `BlockedViewModel` 注入 `UserPreferences`，新增 `localTags`（stateIn）+ `addLocalTag/removeLocalTag/clearLocalTags`
  - `MeRoute` **删除"推荐与过滤" section**（"屏蔽标签"卡片移除）——"用户内容管理"的"屏蔽管理"成为唯一屏蔽入口；移除 `onOpenMutedTags` 参数
  - 清理：删除 `MutedTagsRoute`/`MutedTagsViewModel`/`ROUTE_MUTED_TAGS`/MainShell·PixivNavGraph 接线/`Refresh` import
  - **空态修复（补充）**：原 `EmptyBox("暂无屏蔽")` 在本地标签也空时整页隐藏管理入口 → 移除整体空态；`LazyColumn` 始终渲染——**本地过滤标签区块始终显示**（添加输入框 + chips / "暂无本地过滤标签"），服务端部分按数据显示、全空时显示"暂无服务端屏蔽"
  - **UI 重新设计（补充2）**：屏蔽管理页改为 **Material 卡片分组 + pill 标签**——本地过滤标签卡片（标题 + 说明 + 输入框 + `FilledIconButton` 添加 + `FlowRow` pill 标签带 ✕ 删除）；服务端屏蔽卡片（标签 pill 展示 + 用户行头像/取消 + 分割线）；`LazyColumn` 卡片间距 12dp、统一 `surfaceContainer` 圆角卡片，移除裸露 SectionTitle 列表
  - **外观设置内嵌（补充3）**："主题与外观"不再跳 `SettingsRoute`（避免无关内容）——`MeViewModel` 增加 `themeMode`/`dynamicColor`（stateIn）+ `setThemeMode`/`setDynamicColor`；`MeRoute` "外观设置" section 内嵌 Card（主题模式 FilterChip 三选：跟随系统/浅色/深色 + 动态取色 Switch）；`SettingsRoute` 仅由"系统设置（缓存与更新）"访问
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **浏览历史界面重构（第四十六轮）**：`HistoryRoute` 改为 **TabRow（插画/小说/用户）+ HorizontalPager 滑动切换**（滑动切页同步 `HistoryFilter`、点击 Tab 平滑滚动、下划线跟随）：
  - 插画：`IllustWaterfallGrid` + `IllustCard`（首页同款，含收藏按钮，`toggleIllustFavorite`）
  - 小说：`LazyColumn` + `NovelCard`
  - 用户：`LazyColumn` + `CreatorProfileCard`
  - TopAppBar 保留"清空"；`HistoryFilter` 去 `ALL` 仅三类、默认 `ILLUST`；删除 `AllHistoryList`/`HistoryRow`/`typeLabel`
  - **小说历史完整信息（补充）**：历史快照只有 title/coverUrl 导致 `NovelCard` 信息不全 → 利用 `BrowseHistoryEntity.payloadJson`——`NovelViewModel.recordHistory` 存完整 `NovelCardData` JSON（作者/头像/日期/系列/收藏/字数/标签/是否收藏）；`HistoryRoute.toNovelCardData` 优先 `Gson().fromJson(payloadJson)` 完整展示、旧记录/失败回退最小数据；小说历史卡 `onOpenAuthor`（payloadJson 提供 authorId）→ 用户主页、`onToggleFavorite` → `toggleNovelFavorite`；`HistoryViewModel` 注入 `PixivRepository` + `toggleIllustFavorite`/`toggleNovelFavorite`
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **历史插画完整显示修复（补充）**：根因——历史 `toIllust()` 构造的 `Illust` 无 `width/height` → `IllustCard` 走固定高度 + `Crop` 裁剪中间；修复——`IllustViewModel.recordHistory` 存 `payloadJson`（id/title/coverUrl/**width/height**/bookmarks/pageCount/isBookmarked，org.json）；`HistoryRoute.toIllust` 优先解析 payloadJson 恢复宽高（`IllustCard` aspectRatio 完整显示），旧记录回退最小数据；修复 `optInt` 平台类型（`?: 0`）与 `page_count` 非空类型
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- **全屏查看器底部操作条（第四十七轮）**：
  - `ViewerRoute` 新增**底部圆形操作条**（`navigationBarsPadding` + 底部渐变，与顶部对称）：4 个 52dp 圆形按钮（黑底 45%/白 icon，收藏时红 `0xFFFF5252`，原图选中高亮 28% 白底）——**收藏**（`Favorite`/`FavoriteBorder`，`toggleBookmark`）/ **下载**（`Download`，非 GIF 下载当前页原图+索引，GIF `downloadGifStub`）/ **壁纸**（`Wallpaper`，`wallpaper()` 设为手机壁纸；GIF 禁用）/ **原图**（`HighQuality`，`toggleOriginal` 切换预览↔原图；GIF 禁用）
  - `ViewerViewModel`：注入 `@ApplicationContext`；新增 `isOriginal`（默认 false）+ `toggleOriginal`（Snackbar"已加载原图/已切换预览"）；`wallpaper()`——下载原图字节 → `BitmapFactory.decode` → `WallpaperManager.setBitmap`（minSdk 26 无需权限）；图片 URL 按 `isOriginal` 选 `displayUrl`（预览，默认）或 `originalUrl`（原图）
  - `SnackbarHost` 底部 padding 调至 100dp 避开操作条；顶栏"更多"菜单保留（收藏/下载/举报，与底部一致）
  - 说明：pixiv `displayUrl(large)` 通常 1200px 上限，多数作品与原图差距小；原图按钮提供超清查看
  - **壁纸权限修复（补充）**：设置壁纸报 "Access denied ... must have permission android.permission.SET_WALLPAPER" → `AndroidManifest.xml` 声明 `<uses-permission android:name="android.permission.SET_WALLPAPER" />`（normal 权限，安装即授予，无需运行时请求）
  - 验证：`compileDebugKotlin` + 全部相关模块测试通过
- 测试：`HtmlToPlainTextTest` 4 + `NovelParserTest` 15 + `DebugRealHtmlTest` 1 + `NovelDocumentCodecTest` 4，core:novel 共 24 用例；feature:novel `NovelExporterTest` 6 用例

### i18n 国际化（第四十八轮，全量）
- **决策**：默认中文（`values/`）+ 英文（`values-en/`）；应用内语言切换（DataStore `appLanguage` system/zh/en）+ 跟随系统；lib:pixivapi 网络语言（`accept-language`/`lang`）随应用语言动态化。
- **基础设施**：
  - `core:common`：`AppLanguage` 常量/`localeFor`/`pixivLanguageCode`；`UiMessage(@StringRes, args)`；`NumberFormat` 改 locale-aware（zh 万/亿，en K/M/B，`formatCountForNovel` 与 `formatCount` 统一并修复去尾零"1.0万"），`NumberFormatTest` 增 en 用例。
  - `core:datastore`：`UserPreferences.appLanguage` + `readAppLanguageSync`（runBlocking 同步读，供 attachBaseContext）。
  - `app MainActivity.attachBaseContext`：同步读语言 → `createConfigurationContext` 覆盖 + `Locale.setDefault` + `PixivLang.code`。
  - `lib:pixivapi`：`PixivLang` holder + `HeaderInterceptor`/`WebHeaderInterceptor` 读它（accept-language 动态 + webApi `lang` 查询参数重写）。
  - `core:ui ErrorBox(message: String?)` 空白时回退 `R.string.load_failed`；`core:network PagedState.error` 存原始 message（null 由 UI 兜底）。
- **文案迁移**：全部模块新建 `res/values/strings.xml` + `values-en/strings.xml`（app/core:ui + 11 个 feature，共 13 组、~400 key，中英 key 完全一致）；硬编码中文 → `stringResource`；VM Snackbar/error → `UiMessage`；枚举显示名（RankingMode/SearchType/HomeTab/HistoryFilter/DownloadFilter/BookmarkType/ReaderTheme/PageMode/FontFamily）→ `@StringRes`。
- **语言切换 UI**：我的页外观设置内嵌语言卡（跟随系统/中文/English），切换 `activity.recreate()` 生效（无独立设置页）。
- **已知取舍**：`@ApplicationContext.getString` 不跟随应用内语言覆盖（应用上下文未重建），VM 持续展示型文案（如下载进度）仅在应用内切换语言后偶发不一致——可接受；`NovelExporter` 导出文件内嵌标签（作者：/目录/插图等）暂保持中文（TODO，未动测试）。
- 验证：`:app:compileDebugKotlin` + 六模块单测全过 + `assembleDebug` 产出 `app-debug.apk`（21.0 MB）。

### 设置并入我的页（第四十八轮补充）
- 用户反馈：系统设置不要独立页面 → 删除 `feature:settings` 源码（SettingsRoute/SettingsViewModel + res），模块变空壳（同 feature:download）；`ROUTE_SETTINGS`/PixivNavGraph composable/MainShell `onOpenSettings` 回调链全部移除。
- **MeRoute 内嵌**：外观卡（主题模式 + 动态取色 + **语言** FilterChip 三选，切换 `activity.recreate()`）；系统设置卡（自动更新 Switch + 存储清理：缓存占用 + 清除按钮）；关于卡（版本/描述/开源链接 + 检查更新占位）。
- **MeViewModel** 承接设置逻辑：`appLanguage`/`autoUpdate`/`cacheSize`/`setAppLanguage`/`setAutoUpdate`/`checkUpdate`/`clearCache`/`refreshCacheSize`（注入 `DownloadEntryDao`，`:core:database` 已有依赖）；`message` 改 `Channel<UiMessage>`；新增 `me_` 前缀字符串 key（语言/自动更新/存储/检查更新等，中英齐）。
- **语言切换竞态修复（补充）**：原"点击 → setAppLanguage（异步写 DataStore）→ 立即 recreate"存在 bug——recreate 销毁旧 ViewModel 会取消写协程，导致语言未落盘而 `attachBaseContext` 读到旧值（"点了中文却显示英文"）。修复：`setAppLanguage(value, onDone)` **落盘完成后**才回调 `recreate()`；MeRoute 加 `switchingLanguage` 防抖（切换期间忽略重复点击）+ 已选语言不重复触发。
- **我的页卡片化（补充）**：用户要求设置区不用 `HorizontalDivider` 分隔——外观设置拆为三张独立卡片（主题模式 / 动态取色 / 语言），系统设置拆为两张独立卡片（自动更新 / 存储），每项一个 Card + 8dp 间距，与"用户内容管理"的 SettingsCard 风格一致；移除 MeRoute 中 `HorizontalDivider` import。
- 验证：`:app:compileDebugKotlin` + 单测 + `assembleDebug` 产出 `app-debug.apk`（21.0 MB，20:41）。
- **全项目注释补充（第四十八轮，纯注释无逻辑变更）**：
  - 目标：为代码补齐 KDoc（参数含义 + 函数含义 + UI 设计方式），分批推进
  - 第一批（core:ui + core 数据层）：`IllustCard`/`NovelCard`+`NovelCardData`/`SettingsCard`+`SettingsCardItem`/`ProfileHeader`/`UserAvatar`/`CreatorProfileCard`/`CommentInput`/`IllustWaterfallGrid`/`AdaptiveScaffold`/`StatusViews`/`PixivImage` 全部通用组件（每处含 UI 设计方式 + @param）；`NovelParser.parse`/`NovelDocumentCodec.encode`；`BrowseHistoryDao`/`SearchHistoryDao`/`DownloadEntryDao`/`ReadingProgressDao`（含"先删旧再 upsert 去重置顶"语义）；`OfflineNovelRepository` 方法注释
  - 第二批：`DiscoverViewModel` 类级 KDoc（职责/状态）+ 全部方法注释（联想防抖/热门缓存 24h TTL/搜索历史去重置顶/屏蔽标签过滤等）
  - 第三批：`PixivNavGraph` 14 个路由常量逐个 KDoc + 各 composable 块注释（深链 scheme `pixiv://`、登录清栈、`main?search` 跨 Tab、全屏路由、`local_reader` 单次消费、系列分册跳转语义）；补 `IllustViewModel`/`HomeViewModel`/`AuthViewModel`/`RankingViewModel`/`ViewerViewModel` 类级注释（含枚举与状态/事件注释）
  - 第四批：`PagedState` 方法注释（游标语义/失败处理/防重入）；`MainShell` TABS 注释 + 类级注释补 `pendingSearch` 跨 Tab 搜索机制；`PixivRepository` 类级增强（api/webApi/imageClient 分工 + Referer 防 403）；`SessionRepository` 补 isOAuthCallback/logout
- 每批验证：`:app:compileDebugKotlin` BUILD SUCCESSFUL
- 备注：feature Route 文件注释多为各开发轮次自带；`lib:pixivapi` vendor 副本只读未动；agent.md 本文件即为会话记忆与轮次记录（后续会话先读本文件 + AGENTS.md）

### 漫画 Tab + 通用排行榜（第四十九轮）
- **HTML 原型**：`design/manga-ui.html`（可交互：漫画 Tab 排行榜入口 banner + 推荐瀑布流 + 排行榜全屏页左右滑动切段；纯 M3 色无渐变——用户要求去掉渐变、排行榜点击按钮改为滑动切换）。
- **通用排行榜组件**（为未来小说/插画复用）：
  - `core:common RankingModeInfo(@StringRes labelRes, value)`（分段配置数据）
  - `core:ui RankingList<T>`：ScrollableTabRow + HorizontalPager 滑动切段（点 Tab animateScrollToPage、滑动后回调 onModeSelect）+ 每页三态（复用 StatusViews）+ 触底加载；`itemContent(T, rank)` slot 由调用方提供
  - `core:ui RankingRow(rank, illust, onClick)`：徽标 1金 #E8A33D / 2 #B45309 / 3 #6B7280 / 其余 onSurfaceVariant（titleMedium bold 斜体）+ 64dp 封面 + 标题/作者/收藏（新增 `ranking_bookmarks` 资源）
- **feature:manga（新模块）**：`MangaViewModel`（推荐流 `getRecommendedManga` 分页 + 收藏）、`MangaRoute`（TopBar「漫画」+ 排行榜 banner 纯 tertiaryContainer + IllustWaterfallGrid）、`MangaRankingViewModel`（5 段 modes：日 day_manga / 周 week / 月 month / 新人 week_rookie / R18 day_r18，`getRanking(mode)` 分页）、`MangaRankingRoute`（全屏页复用 RankingList + RankingRow）；中英 strings 双份。
- **app 接线**：底部第 3 Tab「排行」→「漫画」（icon `Collections`，`main_tab_manga`）；新增顶层路由 `ROUTE_MANGA_RANKING = "manga_ranking"`（`MainShell.onOpenMangaRanking` 回调链 + PixivNavGraph composable，点击排名行开插画详情）；`settings.gradle`/`app build.gradle` 加 `:feature:manga`。
- **删除**：`feature:discover` 的 `RankingScreen.kt`/`RankingViewModel.kt`（原 `RankingMode` 7 模式）与 discover strings 的 `ranking_*` key（中英）。
- **API 取舍**：pixiv 漫画专属榜仅 `day_manga`；周/月/新人/R18 用通用 mode（会混入插画），用户已确认接受。
- **滑动切换突兀优化（方案 A，补充）**：原 RankingList 的 HorizontalPager 每页共享同一 `items`，滑动到邻页时邻页复用当前内容造成"内容跟手滑动后又闪换"。修复：新增 `dataKey`（数据就绪标识，VM `loadInitial` 完成后递增）→ RankingList 内部按 mode 缓存每页数据快照（`mutableStateMapOf`），每页只渲染自己 mode 的快照（已加载段即时显示、未加载段 LoadingBox 占位），数据就绪后 `AnimatedContent` 淡入 + 上移过渡；`MangaRankingViewModel` 新增 `dataVersion`，`MangaRankingRoute` 传 `dataKey=dataVersion`。
- **排行榜配色统一（补充）**：用户反馈排行榜颜色突兀 → **修正理解**：排名徽标保持 1金 `#E8A33D`/2橙 `#B45309`/3灰 `#6B7280`（设计如此，不随主题改）；突兀的是**漫画 Tab 顶部排行榜入口 banner 的紫色**——由 tertiaryContainer 紫色改为 primaryContainer 主色系（图标块 onPrimaryContainer 底 + onPrimary 白图标，文字 onPrimaryContainer/primary）；HTML 原型同步。
- **排行榜封面与文本尺寸（补充）**：用户反馈 item 图片太小 → `RankingRow` 封面 64dp → 88dp（HTML 同步 60px→88px）；随后用户反馈"其他字体和图片大小不够匹配" → 文本规格同步调大：标题 `bodyLarge`→`titleMedium`（16sp Medium）、作者间距 4→6dp、收藏 `labelSmall`→`labelMedium`（12sp）、间距 2→4dp。
- **排行榜行配色层级（补充）**：用户反馈标题/收藏/作者全黑 → 拉开层次：标题 `onSurface`；作者 `onSurfaceVariant`；**收藏数改为 secondaryContainer 胶囊 + primary 蓝字 + Favorite 心形图标**（对齐 HTML 原型 `.rtt .n` 样式）。
- 验证：`:app:compileDebugKotlin` + 六模块单测 + `assembleDebug` 产出 `app-debug.apk`（21.1 MB，8/5 08:26）。

### 排行榜滑动跳变修复 + 每段独立分页重构（第五十轮）
- **Bug**：漫画排行榜从其他列滑动到已有列时整页"向上跳一下"——原 RankingList 的 `AnimatedContent(targetState=dataKey)` 用**全局**递增 dataKey，任意段加载完成 dataKey 变化，滑回已就绪页时重播 `fadeIn + slideInVertically{it/10}`（从下方 1/10 高度上滑）。
- **第一版修复（readyKey 快照）**：快照改 `RankSnapshot(items, readyKey)`（首次就绪的 dataKey 固定），AnimatedContent targetState 用该页 readyKey——首次到位淡入、切回已就绪页不重播。但"切回已加载段仍会重新拉取该段数据（浪费 1 次请求）"，且快照在组合层（remember，旋转丢）。
- **最终重构（每段独立分页，用户确认）**：
  - `MangaRankingViewModel`：移除单例 `paged`/`dataVersion`/`selectedValue`；新增 `pages: Map<mode, PagedState>`（`stateFor(mode)` 惰性 `getOrPut`、数据驻留 VM、旋转不丢）+ `initialized` 集合（段首次进入才加载，`onPageSelected` 幂等）；`retry(mode)`/`loadMore(mode)` 按段；loadInitial 的 fetch 用**局部捕获的 mode**（避免快速切段时 `_selectedValue` 竞态）。
  - `RankingList<T>`：参数改为 `onModeSelect`/`stateFor: (String) -> PagedState<T>`/`onRetry(String)`/`onLoadMore(String)`，移除 `selectedValue`/`items`/`isLoading`/`hasMore`/`error`/`dataKey` 与内部快照 `RankSnapshot`；每页 `remember(mode.value) { stateFor(mode.value) }` 独立 collect，`AnimatedContent(targetState = 该页内容三态 Loading/Error/Content)`——首次到位淡入、已就绪页切回不重播；错误/触底均作用于**该页自己的** PagedState（无状态错配）。
  - `MangaRankingRoute`：只传 `modes`/`onPageSelected`/`stateFor`/`retry`/`loadMore`/`emptyText` + itemContent。
  - `core:ui/build.gradle.kts` 新增 `api(project(":core:network"))`（RankingList 依赖 PagedState）。
- **骨架屏加载占位（补充）**：用户要求排行榜刷新用骨架占位而非跳动动效 → `RankingList` 新增私有 `RankingSkeleton`（仿 `RankingRow` 布局：28dp 序号位 + 88dp 封面 + 标题/作者/收藏文本条 × 9 行，`surfaceVariant` 呼吸 alpha 0.35↔0.75 脉冲）；加载态由全屏转圈 `LoadingBox` 改为骨架；`AnimatedContent` 过渡由 `fadeIn + slideInVertically` 改为**纯 `fadeIn`/`fadeOut`**（无位移跳动）。
- **发现页搜索骨架（补充）**：`feature/discover/DiscoverResults.kt` 三个搜索结果加载态由 `LoadingBox`（全屏转圈）改为骨架：`IllustSearchSkeleton`（`LazyVerticalStaggeredGrid` + `Adaptive(140.dp)` 2 列瀑布流，8 张占位卡仿 `IllustCard`：交替高度封面块 + 标题 2 行 + 20dp 圆头像作者行）、`NovelSearchSkeleton`（`LazyColumn` 6 张仿 `NovelCard`：104dp 3/4 封面块 + 标题/作者条）、`UserSearchSkeleton`（`LazyColumn` 5 张仿 `CreatorProfileCard`：120dp 三封面横排 + 64dp 圆头像重叠 + 关注按钮块）；共享 `skeletonPulseColor`（呼吸 alpha）+ `SkeletonBlock`（占位块）。验证 `:app:compileDebugKotlin` 通过。

## 第五十一轮：自定义通知组件替代 Snackbar（P7 补充）

- **需求**：用户要求自定义一个符合 Material Design 的通知组件，替换现在的 Material `Snackbar`。
- **实现**：`core/ui/.../component/Notification.kt` 新增自研 `NotificationHost` + `NotificationHostState` + `rememberNotificationHostState()` + `NotificationType(Info/Success/Error)`：
  - 视觉遵循 MD3 规范并定制：`inverseSurface` 深底胶囊 + `inverseOnSurface` 文字（圆角 14dp）+ 类型图标徽标（语义色 200 系浅色调在深底高对比：蓝 #90CAF9 / 绿 #A5D6A7 / 红 #EF9A9A，22% alpha 圆底）+ 关闭按钮。
  - 行为：新消息**顶替当前**并重置计时、2.6s 自动消失（`LaunchedEffect(notification)` + delay）、整卡/关闭按钮点击 dismiss；进入 `slideInVertically{it/2} + fadeIn`，退出反向（280ms/220ms）；`AnimatedVisibility` content 用 `last?.let` 记住最近非空通知，保证退出动画期间仍渲染旧卡片。
  - 退出动画期间 notification 已为 null 的处理：`NotificationHost` 内 `var last by remember` + `notification?.let { last = it }`。
- **替换（10 文件 13 处）**：`MainActivity`（顶层 Scaffold + 离线 `show(type = Error)`）、`ReaderRoute`/`NovelRoute`/`ViewerRoute`（Box 内 `align(BottomCenter)`，Viewer 保留 bottom=100dp 避开操作条）、`IllustDetailRoute`/`WatchlistRoute`/`BookmarkRoute`/`UserRoute`/`MeRoute`/`BlockedRoute`（Scaffold `snackbarHost` slot）；调用 `snackbarHostState.showSnackbar(ctx.getString(...))` → `notificationHostState.show(ctx.getString(...))`；`UiMessage` 机制不变。
- 踩坑：`last` 经 `by remember` 委托后编译器不窄化 → `NotificationCard(notification = last)` 类型不匹配，改 `last?.let { NotificationCard(it) }`。
- 文档：AGENTS.md「通用组件」新增 NotificationHost 说明 + i18n 段改「通知/error 发 UiMessage」；CODEFLOW.md「离线 Snackbar」→「离线通知」。
- 验证：`:app:compileDebugKotlin` 通过；grep 确认无 `SnackbarHostState/showSnackbar/SnackbarHost(` 残留。

### 第五十一轮补充（通知组件样式迭代 + 测试按钮）
- 用户反馈三点：① 通知太宽 ② 黑色不匹配 app 配色 ③ 我的页加一组测试按钮。
- `Notification.kt`：卡片宽度由 `fillMaxWidth` 改为**自适应内容 + `widthIn(max = 420.dp)`**（短文案显示紧凑胶囊）；容器 `inverseSurface` 深底 → **`surfaceContainerHigh` + `shadowElevation = 4.dp`**（跟随主题明暗）、文字 `inverseOnSurface` → `onSurface`、关闭 `inverseOnSurface` → `onSurfaceVariant`；类型色改为主题感知：Info=`colorScheme.primary`、Success=`Color(0xFF4CAF50)`（固定绿）、Error=`colorScheme.error`，徽标底 15% alpha（`notificationTypeColor` 改 @Composable）。
- `MeRoute.kt`：在「关于信息」前新增「通知测试」Card——Info/Success/Error 三个 `Button`（weight 均分）触发 `notificationHostState.show(...)`；**踩坑**：`stringResource` 不能在 onClick 内调用，需先提到 Composable 作用域存变量。
- strings：feature/user 新增 `me_test_notification_*` 8 个 key（zh + en）。
- **宽度上限设备区分（补充）**：用户反馈 420dp 上限对手机无效（手机屏宽 < 420dp → 卡片撑满屏）→ `NotificationHost` 由 `Box` 改 `BoxWithConstraints`，`cardMaxWidth = if (maxWidth >= 600.dp) 420.dp else maxWidth * 0.88f`，`NotificationCard` 增 `modifier` 参数接收 `widthIn(max = cardMaxWidth)`——手机留白约 12%、平板封顶 420dp。
- 验证：`:app:compileDebugKotlin` 通过。

### 第五十一轮补充 2（主题/语言选择器：胶囊按钮 + 删通知测试）
- 用户要求：① 主题模式/语言下的三选一改成更圆的按钮（颜色适配 app）且撑满父级 ② 删除通知测试按钮。
- 第一次用 `FilterChip` + `shape = RoundedCornerShape(50)` + `weight(1f)` + `filterChipColors(selectedContainerColor = primaryContainer)`；用户反馈：文字不居中（选中时 FilterChip 自带勾选 leadingIcon 导致偏移）+ 太扁（默认 32dp）。
- 改用**自定义 `PillSelectButton`**（MeRoute 私有）：`Box` + `height(44.dp)` + `clip(RoundedCornerShape(50))` + `background(container)` + `clickable(enabled)`，`contentAlignment = Center` 文字**绝对居中**；选中 `primaryContainer`/`onPrimaryContainer` + SemiBold，未选中 `surfaceContainerHighest`/`onSurfaceVariant`；三按钮 `weight(1f)` 均分撑满。
- 删除「通知测试」卡片 + `me_test_notification_*` strings（zh/en）+ `Button`/`NotificationType`/`FilterChip`/`FilterChipDefaults` imports；新增 `background`/`clickable`/`clip`/`Box` imports。
- 踩坑：清理 import 时误删 `Icon`/`MaterialTheme`/`Scaffold`/`Switch`/`Text`（仍在用），已恢复；`Box` 未导入导致 `Unresolved reference`，已补。
- **选中色改浅（补充）**：用户反馈 `primaryContainer`/`surfaceContainerHighest` 太深 → `PillSelectButton` 选中容器改为 `primary.copy(alpha = 0.12f)` 浅色半透明底 + `primary` 文字，未选中降为 `surfaceContainerHigh`。
- 验证：`:app:compileDebugKotlin` 通过。

### 第五十二轮：lib:pixivapi 包名迁移 `com.example.pixivapi` → `com.pixiv.api`
- **需求**：用户不接受 `com.example` 前缀包名；选择新包名 `com.pixiv.api`。
- **优雅方案**：不仅字符串替换，还把 `api` 子包并入 `network` 子包，消除 `com.pixiv.api.api` 自我重复（Retrofit 接口与 OkHttp 客户端同属网络层）。
- **目录迁移**（git mv）：`lib/pixivapi/src/main/java/com/example/pixivapi/` → `com/pixiv/api/`；其下 `api/` → `network/`（AppApi/PixivWebApi 直接进 network 根，不要 `network/api`）。踩坑：git mv 前需先建目标父目录 `com/pixiv`；首次 mv 目录后再单独移 AppApi/PixivWebApi 到 `network/` 根。
- **内容替换**（UTF-8 无 BOM，PowerShell `Replace` 双 pass）：先 `com.example.pixivapi.api` → `com.pixiv.api.network`（package/import 都覆盖），再 `com.example.pixivapi` → `com.pixiv.api`。lib 内 13 文件 + app/core/feature 引用方 47 文件。
- **配置**：`build.gradle.kts` `namespace = "com.pixiv.api"`；`consumer-rules.pro` `-keep class com.pixiv.api.model.**` + `com.pixiv.api.network.**`（原 keep api.** 需改为 network.** 覆盖迁入接口）。
- **最终结构**：根包 `com.pixiv.api`（PixivApi/Constants+Pageable）+ 子包 `network`（AppApi/PixivWebApi/PixivClient/Interceptors/TokenInterceptor/PixivLang）、`auth`、`model`、`util`。
- **不动**：上游 `pixiv-api-kotlin/`（只读，仍保持 com.example 52 处）、模块名 `:lib:pixivapi`、`applicationId`。
- 验证：`:app:compileDebugKotlin` 通过；`:core:network/:core:model/:feature:novel` 单测通过；全库 grep `com.example.pixivapi` 0 残留（排除 build 与上游）。AGENTS.md/CODEFLOW.md 同步改 `com.pixiv.api.*`。

### 第五十二轮补充（删除「账户管理-退出登录」）
- 用户要求删除「我的页-账户管理」分组下的退出登录项（该分组仅此一项）→ 整个分组删除（`SectionSpacer + SectionTitle(me_section_account) + SettingsCard(Logout)`），并清理 `Icons.AutoMirrored.Filled.Logout` import 与 `me_section_account` string（zh/en）。
- **保留**：头部 `ProfileHeader` 的 `actionLabel`（`me_logout`）退出登录快捷按钮仍在，`me_logout` string 保留。
- 验证：`:app:compileDebugKotlin` 通过。

### 第五十三轮：小说页改造——推荐/关注 Tab + 小说排行榜
- **需求**：小说页增加排行榜，并分为推荐/关注两个页面。
- **API 分析（lib:pixivapi 零改动，全部已就绪）**：排行榜 `GET v1/novel/ranking?mode=`、推荐 `GET v1/novel/recommended`、关注 `GET v1/novel/follow?restrict=public`——三者响应均带 `next_url`（`NovelResponse`/`NovelRecommendResponse`），分页统一走 `getNextNovels`。小说排行榜 mode 与插画通用（`day/week/day_male/day_female/week_rookie/day_r18`，无小说专属 mode；`day_manga` 是漫画专属不适用）。用户选定 6 段：日榜/周榜/男性向/女性向/新人/R18。
- **交互（用户选定）**：排行榜 = 推荐页顶部 banner 入口 → 全屏排行榜页（同漫画 Tab 模式）；推荐/关注 = 小说 Tab 内 `PrimaryTabRow + HorizontalPager`（2 页滑动切换）。
- **新增 core:ui `NovelRankingRow.kt`**：小说排行行（与 `RankingRow` 布局一致的小说版）——28dp 斜体加粗序号（复用 `RankingRow` 内 `rankColor`，已从 private 改 `internal`）+ 88dp 圆角封面 + 标题2行/作者/收藏徽标/字数；core:ui strings 新增 `ranking_word_count`（%1$s 字 / %1$s words）。
- **新增 `NovelRankingViewModel.kt`**（仿 MangaRankingViewModel）：6 段 `RankingModeInfo` + `pages` map + `initialized` set，每段独立 `PagedState` 惰性创建驻留 VM；`fetch = getRankingNovels(mode)`、`fetchNext = getNextNovels(it)`。
- **新增 `NovelRankingRoute.kt`**：全屏页 `Scaffold + TopAppBar（返回）+ RankingList(modes, stateFor, onRetry, onLoadMore, emptyText)`，itemContent = `NovelRankingRow` → `onOpenNovel`。
- **`NovelFeedViewModel` 扩展**：新增 `follow = PagedState<Novel>()` + `ensureFollowLoaded()`（幂等，关注 Tab 首进加载）/`refreshFollow()`/`loadMoreFollow()`，数据源 `getFollowingNovels("public")`。
- **`NovelRoute` 重构**：`PrimaryTabRow`（推荐/关注）+ `HorizontalPager`（2 页）；推荐页 = `NovelRankingBanner`（仿漫画入口卡，primaryContainer + Leaderboard 图标）+ 推荐流；关注页 = 关注流；抽出私有 `NovelPagedList`（三态 + 触底自动加载 + NovelCard 渲染，推荐/关注共用）。**坑**：`PrimaryTabRow` 是 experimental M3 API，`NovelRoute` 需 `@OptIn(ExperimentalMaterial3Api::class)`。
- **导航**：`MainShell` 加 `onOpenNovelRanking` 参数透传；`PixivNavGraph` 新增 `ROUTE_NOVEL_RANKING = "novel_ranking"` + 注册 `NovelRankingRoute`（onOpenNovel → `novel/{id}`）。
- **strings（feature:novel，zh/en）**：`novel_tab_recommend`/`novel_tab_follow`、`novel_follow_empty`、`novel_ranking_title/empty/banner(+desc)`、6 个 mode 标签（日榜/周榜/男性向/女性向/新人/R18）。
- 验证：`:app:compileDebugKotlin` 通过；`:feature:novel:testDebugUnitTest :core:ui:testDebugUnitTest` 通过。**未提交**（含上一轮删除退出登录 + 本轮全部改动）。

### 第五十四轮：小说页顶部对齐漫画 + 下拉刷新（小说/漫画）+ 排行榜随滚动 + 默认页自定义
- **需求**：① 小说页顶部边距与漫画不一致（顶得高）；② 小说刷新按钮改排行榜（与漫画一致）+ 小说/漫画下拉刷新；③ 小说 Tab 默认页（推荐/关注）可自定义，选项放"我的"页。
- **根因**：`AdaptiveNavScaffold`/`MainActivity` 外层 Scaffold 均 `contentWindowInsets = WindowInsets(0,0,0,0)`（顶栏 inset 由各页自行处理）。漫画用 `Scaffold + TopAppBar`（自带状态栏 inset）而小说用 `AdaptiveContentBox{ Column{ Row(标题) } }` 无状态栏 inset → 标题顶到状态栏、位置与漫画不一致。
- **`NovelRoute` 顶部重构**：改 `Scaffold + TopAppBar(title=小说, actions={ Leaderboard → onOpenNovelRanking })`（移除 AdaptiveContentBox 与 Refresh 按钮），TopAppBar 自带状态栏 inset → 与漫画完全一致；`PrimaryTabRow`+`HorizontalPager` 保留。
- **下拉刷新**：material3 1.3.0 `PullToRefreshBox`（`androidx.compose.material3.pulltorefresh`，experimental，BOM 2024.09 自带无新依赖）。小说 `NovelPagedList` 包 PullToRefreshBox（`isRefreshing`/`onRefresh` 参数，空/错误态用 `Modifier.verticalScroll(rememberScrollState())` 包裹保证可下拉）；漫画 `MangaRoute` 同样包裹。VM 各自加 `isRefreshing`/`isFollowRefreshing`（`MutableStateFlow`）+ `pullRefresh()`/`pullRefreshFollow()`（防重入 → true → reset+loadInitial → finally false）。**坑**：`PullToRefreshBox` 需 `@OptIn(ExperimentalMaterial3Api::class)`。
- **排行榜随滚动**：`IllustWaterfallGrid` 新增可选 `header: (@Composable () -> Unit)?` slot，网格头部用 `item(span = StaggeredGridItemSpan.FullLine)` 整行渲染（漫画 banner 移入 header，随列表滚动/下拉）。**坑**：`LazyStaggeredGridScope.item` 是作用域成员函数，**不要** import `androidx.compose.foundation.lazy.staggeredgrid.item`（不存在该顶层函数）。小说推荐页 banner 同样改为 `NovelPagedList` 的 `header`（LazyColumn 首 item）。
- **默认页自定义**：`UserPreferences` 加 `novelDefaultTab: Flow<Int>`（0 推荐/1 关注，默认 0）+ `setNovelDefaultTab` + `KEY_NOVEL_DEFAULT_TAB`；`NovelFeedViewModel` 注入 `UserPreferences`（feature:novel build.gradle 补 `implementation(project(":core:datastore"))`）+ `novelDefaultTab: StateFlow<Int>`（stateIn 0）+ `suspend loadDefaultTab() = userPreferences.novelDefaultTab.first()`；`NovelRoute` 用 `LaunchedEffect(Unit)` + `rememberSaveable` 防重 guard → `pagerState.scrollToPage(viewModel.loadDefaultTab())` 首帧定位（旋转不跳页、进程重建回默认）。**坑**：不能用 stateIn 初始值做首帧定位（DataStore 异步读，初始 0 会先 emit 导致默认值永远生效）→ 用 `first()` 读真实落盘值。
- **我的页「浏览设置」分组**（用户选定新建分组，不并入外观/系统）：`MeRoute` 外观设置之后、系统设置之前新增 `SectionTitle(me_section_browse)` + Card「小说默认页」`PillSelectButton [推荐, 关注]`（weight 均分，样式与主题/语言胶囊一致）；`MeViewModel` 加 `novelDefaultTab: StateFlow<Int>` + `setNovelDefaultTab(value)`。
- **strings**：feature:user 加 `me_section_browse`/`me_novel_default_tab`/`me_novel_default_recommend`/`me_novel_default_follow`；feature:novel 加 `novel_cd_ranking`（排行榜图标描述）；`novel_cd_refresh` 已无引用但保留。
- 验证：`:app:compileDebugKotlin` 通过；`:feature:novel :feature:manga :feature:user :core:ui` 单测通过。**未提交**（第五十三+五十四轮全部改动）。

### 第五十五轮：路由 bug 修复——小说标签跳发现页后点回小说不跳转
- **现象**：小说页点标签 → 进发现页（bottom bar 也切到发现）→ 此时点底部「小说」不跳转，只有先点其它页再点小说才正常。
- **根因**：小说标签跳发现走 `navigate("discover_tab"){ launchSingleTop }`（**无 popUpTo**），把 discover **压栈**到 novel 之上形成非标准栈 `[home, novel, discover]`。此时点小说 Tab，tab 切换的 `navigate("novel_tab"){ popUpTo(home){saveState}; launchSingleTop; restoreState }` 在**同一次 navigate 内**先 `popUpTo` 把 novel 弹出并 saveState，紧接着 `restoreState` 恢复**同 route** 的 novel——「同一 navigate 中 save 后立即 restore 同一 destination」触发 Navigation 2.8 状态恢复异常，NavHost 未切到小说页。
- **为何其它路径正常**：点首页（startDestination）`launchSingleTop` 直接复用栈顶不走 restoreState；点从未进过的 tab 无 savedState 可恢复；先点其它页再点小说时 novel 的 savedState 是**跨 navigate** 保存的（正常路径）。
- **修复**：`MainShell` 新增 `fun navigateToTab(route)`（`popUpTo(findStartDestination){saveState}` + `launchSingleTop` + `restoreState`），**所有跳到某 Tab 的导航统一走它**：底部 tab `onSelect`、`onSearchTag`（小说标签→发现）、`HomeRoute.onOpenSearch`（首页搜索→发现）、`initialSearch` 通道（顶层 `main?search`）。栈始终保持标准 tab 栈（栈底 home、目标 tab 在顶），消除「discover 压栈 + 同 navigate save/restore 同 route」场景；小说页状态（Tab/滚动）经 saveState/restoreState 正常保留。
- **备选**（若仍复现说明 restoreState 无条件 bug）：tab 切换去掉 `restoreState`（代价：切 Tab 丢页面状态/滚动位置）。
- 验证：`:app:compileDebugKotlin` 通过；`:feature:novel :feature:home :core:ui` 单测通过。**未提交**（第五十三~五十五轮全部改动）。

### 第五十六轮：NovelCard 重构——上下两部分布局（HTML 设计稿驱动）
- **需求**：`NovelCard` 改上下两部分（上：左封面|右信息；下：标签），先出 HTML 设计稿 `novel-card-design.html`（项目根，可反复改）再实现。
- **HTML 迭代定稿要点**：① 封面角标（收藏数/字数）**底部居中**、**无背景**、白色 **加粗** 11sp + 轻文字阴影（用户先要居中→再要底部居中→无背景→加粗）；收藏数 icon 为**红色爱心** `Color(0xFFE53935)`（不随收藏状态）；② 系列名**仅换色为 APP 主题色 `primary`**（不要 Material 默认紫 #6750A4；App 主题色 = `theme/Color.kt` `Primary = 0xFF00639B` 蓝，字号/不加粗/无前缀不变）；③ **作者名 + 时间同一行**（头像 + 作者名撑满 + 时间靠右）并**抵到信息区底部**（`Spacer(weight 1f)` 弹性占位）；④ 标签区无分隔线。
- **实现（core:ui `NovelCard.kt`）**：`Card > Column(padding 14dp)`；上部分 `Row`（封面 104dp 3:4 + 底部居中角标 Column/红心12dp+收藏数/字数 `labelSmall.copy(weight=SemiBold, color=White, shadow=Shadow(黑0.45, 0,1,2))`；右信息 `Column(weight(1f) + fillMaxHeight)`——标题行（+收藏按钮）、系列（`primary`）、`Spacer(weight 1f)` 推底、作者+时间行（作者名 weight 1f、时间 `outline` 色））；下部分 `FlowRow` 标签（take3 + N，无分隔线）。`NovelCardData` 模型与全部调用方**不变**。
- **坑**：右信息 Column 必须 `fillMaxHeight()` 撑满封面高度，`Spacer(weight)` 才能把作者行抵底；移除 `Brush`/`sp` import（不再有渐变遮罩/硬编码字号）。
- **Bug 修复（补充）**：仅 `fillMaxHeight()` 在 LazyColumn 的 wrap 高度 Row 中不生效（Row 高度未确定），作者行不抵底 → Row 需加 `Modifier.height(IntrinsicSize.Min)`（等高 Row 标准做法，Row 高度取封面固有高度），右 Column fillMaxHeight 才有确定高度撑满、weight 推底生效。
- 验证：`:app:compileDebugKotlin` 通过；`:core:ui :feature:novel` 单测通过。**未提交**（第五十三~五十六轮全部改动）。

### 第五十七轮：NovelCard 收藏按钮放大 + 收藏通知 + 小说排行榜改用 NovelCard（含排名）
- **需求**：① 收藏按钮大一些 + 点击有通知；② 小说排行榜改用 `NovelCard` 但可显示排名。
- **`NovelCard` 扩展（通用）**：收藏按钮 `IconButton 36→40dp`、icon `20→24dp`；新增可选 `rank: Int? = null`——非 null 时封面**左上角**显示排名徽标（`Color.Black 0.45` 圆角底 + `titleMedium Bold Italic`，颜色 `rankColor(rank)`：1金/2橙/3灰，其余白色）。
- **收藏通知**：`NovelFeedViewModel`/`NovelRankingViewModel` 加 `_message = Channel<UiMessage>` + `message = receiveAsFlow()`，`toggleNovelFavorite` 成功发 `novel_msg_bookmarked/unbookmarked`、失败发 `novel_msg_action_failed`（复用 feature:novel 已有 string）。`NovelRoute` 主体（小说 Tab）与 `NovelRankingRoute` 的 Scaffold 加 `snackbarHost = NotificationHost` + `LaunchedEffect collect message`。
- **排行榜改用 NovelCard**：`NovelRankingRoute` 的 itemContent 从 `NovelRankingRow` 改为 `NovelCard`（映射 `NovelCardData` + `rank` 传入），新增参数 `onOpenReader/onOpenUser/onSearchTag`（`onToggleFavorite` 由 VM 处理）；卡片间用 `modifier = padding(bottom 10dp)` 分隔（RankingList 列表项无间距）。`PixivNavGraph` 注册处接线 reader/user；**onSearchTag 暂传空 lambda**（顶层路由无法直达 MainShell 内 Tab，跨 Tab 搜索后续再处理）。
- **删除**：`NovelRankingRow.kt`（改用 NovelCard 后无引用）+ core:ui `ranking_word_count` string（zh/en，仅该行用）；`RankingRow.kt` 的 `rankColor` 注释更新为「[NovelCard] 排名徽标复用」（仍在 core:ui 同包 internal 可见）。
- 验证：`:app:compileDebugKotlin` 通过；`:feature:novel :core:ui` 单测通过。**未提交**（第五十三~五十七轮全部改动）。

### 第五十八轮：平板限宽改造——小说/漫画/首页/发现页 + 两个排行榜
- **需求**：小说页、漫画页（尤其排行榜）在平板端内容全宽拉伸不符合平板设计；用户选定范围**含首页/发现页**，且 **TopAppBar 标题限宽居中**（与内容对齐）。
- **背景**：项目平板规范 = `AdaptiveContentBox`（`MAX_CONTENT_WIDTH_DP=760dp` 限宽居中），此前仅全屏页与 Me 页套用；Home/Manga/Novel/Discover/排行榜全宽。
- **core:ui 新增 `AdaptiveContentTitle(text, modifier, maxWidth)`**（AdaptiveScaffold.kt）：`Box(fillMaxWidth, CenterStart) > Box(widthIn(max)) > Text(SemiBold)`，TopAppBar 标题限宽居中（5 处 TopAppBar 复用）。
- **`RankingList`（通用）**：内部 `Column(modifier.fillMaxSize())` → 包 `AdaptiveContentBox(modifier)`，TabRow + 列表整体限宽居中（漫画/小说/未来插画排行榜自动适配）。**坑**：包一层后需在末尾多补一个闭合括号；HorizontalPager 内部内容缩进需整体 +4 整理。
- **各页面**：`HomeRoute`（title=AdaptiveContentTitle 动态推荐/关注 + content Column 包 AdaptiveContentBox）、`MangaRoute`（title + PullToRefreshBox 包 AdaptiveContentBox）、`NovelRoute`（title + Column(PrimaryTabRow+HorizontalPager) 包 AdaptiveContentBox）、`MangaRankingRoute`/`NovelRankingRoute`（title 限宽，列表由 RankingList 内部限宽）、`DiscoverScreen`（无 TopAppBar，外层 Column 包 AdaptiveContentBox，搜索栏+TabRow+结果限宽）。
- **坑**：① `AdaptiveScaffold.kt` 需补 `fillMaxWidth` import；② 删除 title 的 `FontWeight.SemiBold` 后清理 unused `FontWeight`/`Row`/`Alignment` imports（MangaRanking/NovelRanking/Home）；③ NovelRankingRoute 缺 `androidx.compose.ui.unit.dp` import（padding(bottom=10.dp) 用）需补。
- 验证：`:app:compileDebugKotlin` 通过；`:core:ui :feature:home :feature:manga :feature:novel :feature:discover` 单测通过。**未提交**（第五十三~五十八轮全部改动）。

### 第五十八轮补充（排行榜 TabRow 平板居中）
- **现象**：小说/漫画排行榜 TabRow 在平板限宽容器内 tabs 靠左，不居中。
- **根因**：`ScrollableTabRow` 内部强制 `fillMaxWidth` + tabs 内容宽度排列（靠左），限宽容器（760dp 居中）内左侧留白。
- **修复**（`RankingList`）：`BoxWithConstraints` 判断限宽是否生效（`maxWidth >= MAX_CONTENT_WIDTH_DP.dp`）——平板（限宽生效）改用 **`PrimaryTabRow`**（tab 自动均分占满 → 居中），手机保留 **`ScrollableTabRow`**（内容宽度可滑动）。用 `for (index in modes.indices)` 循环替代 `forEachIndexed`。
- **坑**：`Modifier.weight(1f)` 在 `ScrollableTabRow` content 的 `forEachIndexed` lambda 内无法解析 implicit RowScope receiver（lambda 无 receiver 丢失 dispatch receiver；`with(rowScope)`/`rowScope.weight(Modifier,1f)` 也失败）→ 最终放弃 weight 方案，改用 PrimaryTabRow 均分。
- 验证：`:app:compileDebugKotlin` 通过；`:core:ui :feature:novel :feature:manga` 单测通过。**未提交**（第五十三~五十八轮全部改动）。

### 第五十九轮：NovelCard 封面点击 → 全屏查看封面大图
- **需求**：NovelCard 封面点击由「直达阅读器」改为「全屏查看封面大图」（同插画 Viewer 体验）；阅读器入口移除，阅读走整卡→详情页；全局 7 处生效，快照无封面 URL 项点击不响应。
- **core:ui 新增 `FullscreenImageRoute(url, title, onBack)`**（FullscreenImageRoute.kt）：纯展示零依赖，黑底 `0xFF0A0A0A` + 复用 `ZoomableImage`（捏合缩放）+ `BackHandler` + 顶部黑渐变返回栏（仿 ViewerRoute）；core/ui strings 新增 `fullscreen_image_cd_back`（中/英）。
- **`NovelCard`**：删除 `onOpenReader` 参数 → 新增 `onOpenCover: () -> Unit = {}`；封面 `clickable(enabled = !coverUrl.isNullOrBlank(), onClick = onOpenCover)`。
- **app**：新增顶层路由 `ROUTE_IMAGE_PREVIEW = "image_preview?url={url}&title={title}"`（url/title 均可空，全屏路由隐藏底部导航）；`MainShell` 加 `onOpenCover: (String) -> Unit` 并删 `onOpenReader`（Discover/Novel 链已无阅读器直达）；UserRoute/BookmarkRoute/HistoryRoute/DownloadsRoute 顶层接线均 `navigate("image_preview?url=${Uri.encode(url)}")`（DownloadsRoute 保留 onOpenReader 供离线阅读）。
- **回调链改法**（feature 层）：各 NovelCard 链 `onOpenReader: (Long)` → `onOpenCover: (String)`，卡片处 `onOpenCover = { (novel.image_urls?.square_medium ?: novel.image_urls?.medium)?.let(onOpenCover) }`（Novel 模型无 `coverUrl` 属性，必须用 image_urls）；历史/下载快照用 `card.coverUrl?.let(onOpenCover)`（NovelCardData 有 coverUrl）。
- **坑**：① `Novel`（lib API 模型）无 `coverUrl`，编译期报 Unresolved reference（NovelRoute line 365）；② PixivNavGraph MainShell 调用 onOpenCover 传了两次（先加在 onOpenNovel 后、后加在 onOpenNovelRanking 后）→ "Argument already passed"。
- **保留**：`NovelDetailRoute` 的 `onOpenReader`（阅读按钮 onRead）不受影响；Reader 路由不变。
- 验证：`:app:compileDebugKotlin` 通过；`:core:ui :feature:novel :feature:discover :feature:user :feature:bookmark` 单测通过。**未提交**（第五十三~五十九轮全部改动）。

### 第六十轮：用户主页重设计（骨架 + 4 Tab 滑动 + 系列 Tab + 拉黑按钮 + 统计可点 + 3 新子页）
- **需求**（用户 8 条）：① 加载骨架图 ② 插画/漫画/小说滑动切换 ③ 小说系列（确认作为第 4 个滑动 Tab）④ 三个竖点替换为拉黑按钮 ⑤ 插画/小说/收藏/关注统计可点击 ⑥ Material 3 ⑦ 平板/手机两端 ⑧ 先出 HTML 原型。原型 `user-profile-design.html` 已产出（手机 390dp / 平板 880dp 双画框 + 6 状态视图：骨架/主页/系列Tab/收藏/关注/系列详情，可交互）。
- **UserRoute 重构**（feature:user/UserRoute.kt）：
  - 首屏 `LoadingBox` → 新增私有 `UserProfileSkeleton`（呼吸脉冲 `surfaceVariant.copy(alpha)`，头部头像/名称/按钮 + 统计条 + Tab 条 + 瀑布流占位）。
  - FilterChips → **`BoxWithConstraints` + `PrimaryTabRow`(平板均分)/`ScrollableTabRow`(手机) + `HorizontalPager`**（4 页），`LaunchedEffect(currentPage)` 同步 `viewModel.selectSection`；每段独立 `PagedState`（沿用 RankingList 模式）。
  - 头部：删 MoreVert+DropdownMenu → **FilledTonalButton(关注/已关注) + OutlinedButton(拉黑/取消拉黑)**（拉黑用 `ButtonDefaults.outlinedButtonColors(contentColor=error)`）。
  - 统计格 `StatItem(label, value, onClick)` 可点：插画/小说 → `pagerState.animateScrollToPage`；收藏/关注 → `onOpenUserBookmarks/onOpenUserFollowing`。
  - 新增 `SectionSeries`：系列卡（MenuBook 图标 + 标题/简介/N 篇/连载中或已完结徽章），点击 `onOpenSeries`。
- **UserViewModel**：`UserSection` 增 `SERIES`；`seriesPaged = PagedState<NovelSeriesItem>`（`getUserNovelSeries/getNextNovelSeries`）；`hasLoaded/loadSection/loadMore` 补 SERIES 分支。
- **3 新子页**：
  - `UserBookmarksRoute+VM`（ROUTE_USER_BOOKMARKS = `user_bookmarks/{userId}`）：`getUserBookmarkedIllusts(userId,"public")` + `getNextIllusts`，IllustWaterfallGrid。
  - `UserFollowingRoute+VM`（ROUTE_USER_FOLLOWING = `user_following/{userId}`）：`getFollowingUsers(userId,"public")` + `getNextUsers`，复用 `CreatorProfileCard` 用户行 + toggleFollowUser。
  - `NovelSeriesRoute+VM`（ROUTE_NOVEL_SERIES = `novel_series/{seriesId}`，feature:novel）：`getNovelSeries` 详情头（primaryContainer 信息卡：标题/简介/篇数/连载态/作者）+ `getNextNovelSeriesDetail` 分页 NovelCard。
- **坑**：① `OutlinedButtonDefaults` 在 material3 1.3.0 不可用 → 改 `ButtonDefaults.outlinedButtonColors`；② 跨模块 property（`NovelSeriesItem.caption`）不能 smart-cast → 先 `val caption = series.caption` 再判空；③ 标题想带用户名的方案（nav 传 name query）放弃，改用固定标题「该用户的收藏/关注」。
- 验证：`:app:compileDebugKotlin` 通过；`:feature:user :feature:novel :core:ui :core:network` 单测通过。**未提交**（第五十三~六十轮全部改动）。

### 第六十轮补充：系列 item + 系列详情页 UI 优化（书封视觉签名）
- **设计方向**（frontend-design + ui-ux-pro-max 技能）：系列无封面 URL，需要视觉签名 → 以「多卷层叠书本」意象作为系列在 App 内的统一视觉身份。
- **core:ui 新增 `SeriesBookCover`**（SeriesBookCover.kt）：三层错位圆角块（tertiaryContainer/secondaryContainer 错位书脊 + `primaryContainer→secondaryContainer` 渐变封面）+ 书名首字大号加粗 + 右侧书页边缘竖条 + 底部横线装饰（经典书封排印）；全部 MaterialTheme 取色，深/浅自适应；参数 `shape`/`initialTextStyle` 供列表小卡与详情 hero 复用。
- **用户主页 SeriesCard**（feature:user/UserRoute.kt）：图标盒 → `SeriesBookCover`（52×70dp）；新增 `watchlist_added`「已追更」徽章（surfaceContainerHigh 次要位）；右侧进入箭头 `KeyboardArrowRight`。
- **系列详情页 header 重设计**（feature:novel/NovelSeriesRoute.kt）：扁平 primaryContainer → 渐变 hero（`Brush.verticalGradient(primaryContainer→surface)`）：88×118dp 书封 + 眉题「系列作品」+ headlineSmall 标题 + 篇数/连载态徽章（完结=errorContainer）+ 总字数 `formatCountForNovel`；简介 3 行截断；**作者行可点击**（头像+名称+查看作者+箭头，`onOpenAuthor` 即 onOpenUser）；列表前新增「分册（N）」section 标签。
- **strings**（中/英）：user `user_series_watchlisted`；novel `novel_series_eyebrow/total_chars/view_author/volumes_section`。
- **坑**：`Modifier.padding(vertical=, end=)` 组合不合法 → 拆 `padding(top=,bottom=,end=)`。
- HTML 原型同步：系列卡 + 系列详情页更新为书封视觉与 hero 结构。
### 第六十轮补充 2：系列 UI 返工（纯 MD3 扁平，去渐变）
- **用户反馈**：渐变书封"还是丑，不符合 material-design 审美"。返工为纯 MD3 语言：**无渐变、无自创书封装饰**，改用标准扁平 token。
- **`SeriesBookCover` 重写**（core:ui）：删除三层错位 + 渐变 + 首字 + 书页边缘/横线装饰，改为 **MD3 Filled tonal icon container**——扁平 `secondaryContainer` 圆角容器 + `MenuBook` 图标（`onSecondaryContainer`），与「无头像显示首字母」兜底同思路；签名 `(modifier, iconSize=28.dp, shape)`。
- **用户主页 SeriesCard**：48dp 图标容器（去 52×70 书封、去右侧箭头）；简介 1 行截断；徽章改 **MD3 药丸**（`RoundedCornerShape(999.dp)`，间距 10×3，`labelMedium`），提炼私有 `SeriesStatusBadge(text, container, content)`；已追更=surfaceContainerHigh。
- **系列详情页 header 重写**（feature:novel）：删除渐变背景 + 眉题 + 88×118 书封；改为扁平 `Column`（surface 底）：64dp 图标容器 + headlineSmall 标题 + 篇数/连载态药丸（`SeriesMetaChip`）+ 总字数（bodySmall onSurfaceVariant）+ 简介（bodyMedium，3 行）+ 可点击作者行（头像 40dp + bodyLarge 名称 + labelMedium「查看作者」）。
- HTML 原型同步去渐变：系列卡 48dp 图标容器 + 详情页扁平头。
- 验证：`:app:compileDebugKotlin` 通过；`:core:ui :feature:user :feature:novel` 单测通过。**未提交**（第五十三~六十轮全部改动）。

### 第六十轮补充 3：系列详情页真实封面（方案 A，大封面适配）
- **背景**：用户问"为什么没有封面"——根因是 API 限制：`NovelSeriesItem`（/v1/user/novel-series 列表项）**无封面字段**（对比 `MangaSeriesItem` 有 cover_image_urls）；但系列详情 `NovelSeriesResp.novel_series_first_novel.image_urls`（第一册封面）可用。
- **方案 A**（用户确认）：系列详情页显示第一册真实封面；用户主页系列列表保持图标占位（API 无封面）。
- **`NovelSeriesViewModel`**：新增 `firstNovelCover: StateFlow<String?>`；`load()` 的 `also` 中取 `resp.novel_series_first_novel?.image_urls?.medium ?: ...?.square_medium`（与 NovelCard 取 URL 策略一致）。
- **`SeriesHeader`**（feature:novel）：新增 `coverUrl: String?` + `onOpenCover: () -> Unit` 参数；封面改为**大号 3:4（110×148dp）**：
  - 有图 → `PixivImage`（自动 Referer 防 403，圆角 12dp，**点击全屏看大图** `onOpenCover`）
  - 无图 → `SeriesBookCover` 图标兜底（同步放大至 110×148、iconSize 44）
  - 其他元素适配：头部 `Row` 改 `Alignment.Top` 顶对齐；药丸间距 10dp；总字数下移到右侧列内（封面并排区）；简介间距 14dp；作者头像 44dp。
- HTML 原型同步：系列详情头改为 110px 3:4 真实封面占位。
- 验证：`:app:compileDebugKotlin` 通过；`:feature:novel :core:ui` 单测通过。**未提交**（第五十三~六十轮全部改动）。

### 第六十轮补充 4：用户主页系列列表真实封面 + 总字数（SeriesCoverCache 内存缓存）
- **需求**：用户主页「系列」Tab 每个 item 显示真实封面（不是统一 icon）+ 总字数。
- **数据约束**：`NovelSeriesItem`（列表项）无封面字段；只能逐个调 `getNovelSeries` 详情取第一册 `image_urls`（统一 **medium**）。用户确认**进程级内存缓存**（不持久化到 Room）+ 统一 medium。
- **core:network 新增 `SeriesCoverCache`**（`session/SeriesCoverCache.kt`，`@Singleton`）：`ConcurrentHashMap<Long,String>` 驻留进程 + `inFlight: ConcurrentHashMap<Long, Deferred<String?>>` **in-flight 去重**（同 seriesId 并发只发一次网络，其余 await 同一结果）；`get(id)` 同步查 / `getOrFetch(id, fetcher)` 未命中才发请求并写缓存；自建 `CoroutineScope(SupervisorJob()+Dispatchers.IO)`；**未改 AppApi/PixivRepository 任何现有逻辑**（纯新增容器）。
- **`UserViewModel`**：注入 `SeriesCoverCache`；新增 `seriesCovers: StateFlow<Map<Long,String>>`；`loadSection(SERIES)`/`loadMore` 后 `loadSeriesCovers(ids)`——只对未缓存 id 取（`chunked(6)` 限并发），成功写 `_seriesCovers`。
- **`NovelSeriesViewModel`**：注入缓存，`firstNovelCover` 改走 `getOrFetch(seriesId)`——与用户主页列表**共享同一缓存**，从列表进详情零重复请求。
- **`SeriesCard`**（feature:user）：新增 `coverUrl: String?` + `totalCharacters`（取 `NovelSeriesItem.total_character_count`）；封面有 URL → `PixivImage`（3:4 52×70dp，自动 Referer）/ 无 → `SeriesBookCover` 兜底；元信息行加**总字数** `formatCountForNovel` + 「字」与「N 篇」并列。
- **strings**（中/英）：`user_series_chars`（`%1$s 字` / `%1$s characters`）。
- 验证：`:app:compileDebugKotlin` 通过；`:feature:user :feature:novel :core:network` 单测通过。**未提交**（第五十三~六十轮全部改动）。

### 第六十一轮：IllustCard 作者行可点击 → 用户主页（全插画网格接线）
- **需求**：插画卡片作者行（20dp 头像 + 名称）**整行可点击**进入作者用户主页（与 NovelCard onOpenAuthor 对齐）。
- **`IllustCard`**（core:ui）：新增 `onOpenAuthor: () -> Unit = {}`；作者行 `Row` 包 `clip(RoundedCornerShape(8.dp)) + clickable(onClick = onOpenAuthor) + padding(end=4.dp)`（user 为 null 时行不渲染，天然不可点）。
- **`IllustWaterfallGrid`**（core:ui）：新增 `onOpenUser: ((Long) -> Unit)? = null`；内部透传 `onOpenAuthor = onOpenUser?.let { cb -> { illust.user?.id?.let(cb); Unit } } ?: {}`。
- **10 处调用点接线**：
  - 已有 `onOpenUser` 直接传：`BookmarkRoute.BookmarkIllustList`、`HistoryRoute.IllustHistoryList`、`UserRoute.SectionIllust`（ILLUST/MANGA 两处）。
  - 补参数 + 透传：`DiscoverResults.IllustSearchResults`、`DiscoverScreen.HotIllustGrid`（经 SearchResultPager，DiscoverRoute 已有 onOpenUser）。
  - 路由新增 `onOpenUser`：`HomeRoute`（→RecommendContent/FollowContent）、`MangaRoute`、`UserBookmarksRoute`。
- **导航**：`MainShell` 的 HomeRoute/MangaRoute 调用补 `onOpenUser = onOpenUser`（MainShell 已有）；`PixivNavGraph` 的 UserBookmarksRoute 调用补 `onOpenUser = { id -> navigate("user/$id") }`。
- **不动**：`DownloadsRoute` 直调 `IllustCard`（快照 `toDownloadIllust` 无 user，作者行不渲染，无需接线）。
- **坑**：`onOpenUser?.let { cb -> { illust.user?.id?.let(cb) } }` 内层 lambda 推断为 `() -> Unit?` 类型不匹配 → 显式收尾 `; Unit` + 外补 `?: {}`。
- 验证：`:app:compileDebugKotlin` 通过；`:core:ui :feature:user :feature:bookmark :feature:discover` 单测通过。**未提交**（第五十三~六十一轮全部改动）。

### 第六十一轮补充：个人中心 TabBar 手机端不占满修复
- **Bug**：个人中心（UserRoute）4 个 Tab（插画/漫画/小说/系列）手机端靠左留白，未占满宽度。
- **根因**：手机端走了 `ScrollableTabRow`——其 tabs **按内容宽度靠左排列**，内容不足宽度时不拉伸；平板端 `PrimaryTabRow`（内部 Tab `weight(1f)` 均分）没问题。
- **全项目排查**（8 处 TabBar）：`TabRow`/`PrimaryTabRow` 均均分占满（Novel 推荐/关注、Discover 搜索、History、Downloads）；`ScrollableTabRow` 仅 2 处：个人中心（此 Bug）+ 排行榜 RankingList。排行榜 5~6 段、需可滑动（第五十八轮有意保留，段多均分会挤爆），**用户确认不改**，只修个人中心。
- **修复**：删除 `BoxWithConstraints`+`isWide` 分支，统一 `PrimaryTabRow`（4 个短标签手机放得下，均分占满，平板/手机一致）；清理未用 import（`BoxWithConstraints`/`ScrollableTabRow`/`MAX_CONTENT_WIDTH_DP`）。
- 验证：`:app:compileDebugKotlin` 通过；`:feature:user` 单测通过。**未提交**（第五十三~六十一轮全部改动）。

### 第六十一轮补充 2：个人中心插画/漫画卡片缺收藏按钮修复
- **Bug**：个人中心「插画」「漫画」Tab 的卡片右上角无收藏按钮（小说 Tab 有）。
- **根因**：`SectionIllust`（插画/漫画共用）签名与两处调用**都没接 `onToggleFavorite`**，`IllustWaterfallGrid` 未传 → null → `IllustCard.kt:138` `if (onToggleFavorite != null)` 不渲染收藏按钮。
- **修复**：`UserViewModel` 新增 `toggleIllustFavorite(illustId, nowFavorite)`（`bookmarkIllust/unbookmarkIllust`，与 toggleNovelFavorite/HomeViewModel 同款无通知）；`SectionIllust` 签名加 `onToggleFavorite: (Long, Boolean) -> Unit` + `IllustWaterfallGrid` 补传；两处调用（ILLUST/MANGA）传 `viewModel::toggleIllustFavorite`。
- 验证：`:app:compileDebugKotlin` 通过；`:feature:user` 单测通过。**未提交**（第五十三~六十一轮全部改动）。

### 第六十一轮补充 3：用户主页点击头像 → 全屏展示头像大图
- **需求**：用户主页头部点击头像全屏查看大图。
- **改动**（仅 feature:user/UserRoute.kt）：
  - `UserHeader` 新增 `onOpenAvatar: (String) -> Unit`；头部 `UserAvatar` 加 `onClick = { user.profile_image_urls?.best()?.let(onOpenAvatar) }`（头像 URL 空则不可点，天然兜底）。
  - `UserHeader` 调用处补 `onOpenAvatar = onOpenCover`（**复用现有 onOpenCover 回调**，与小说封面全屏同一路由）。
  - KDoc：onOpenCover 注释改「全屏大图（小说封面 / 头部头像共用）」。
- **不动**：`PixivNavGraph`（UserRoute 的 onOpenCover 已接 `image_preview?url=` 全屏路由，头像点击自动复用）；`FullscreenImageRoute`/`ROUTE_IMAGE_PREVIEW` 均为既有能力。
- 验证：`:app:compileDebugKotlin` 通过；`:feature:user` 单测通过。**未提交**（第五十三~六十一轮全部改动）。

### 第六十一轮补充 4：插画卡片封面收藏按钮溢出 + 爱心偏小修复
- **Bug**：插画/漫画卡片封面右上角收藏按钮超出边界、爱心图标偏小。
- **根因**：`IllustCard` 用了 Material3 `IconButton`——内部有强制最小交互尺寸（`minimumInteractiveComponentSize` ≈ 40dp 状态层 + 48dp 触控区），外层 `.size(24.dp)` 被覆盖，实际渲染约 40dp，在瀑布流窄卡片右上角溢出；内层 Icon 仅 14dp，在 40dp 容器内比例失衡。
- **修复**（core:ui/IllustCard.kt）：`IconButton` → **自绘 `Box` 浮层按钮**：`size(28.dp).clip(CircleShape).background(黑0.35).clickable` + `contentAlignment.Center`，内层 `Icon` 14→**18dp**（爱心放大）；删除未用 `IconButton` import。完全掌控尺寸，无强制最小尺寸。
- **不动**：`NovelCard` 标题行收藏按钮（非封面浮层，40dp 正常）；`FullscreenImageRoute`/`CommentInput` IconButton（工具栏/输入框场景）。
- 验证：`:app:compileDebugKotlin` 通过；`:core:ui` 单测通过。**未提交**（第五十三~六十一轮全部改动）。

### 第六十二轮：插画查看器竖向翻页 + 我的页切换方向
- **需求**：全屏插画查看器原本只能横向滑动，新增竖向查看方式；在「我的」页添加选项切换横向/竖向。
- **偏好**（core:datastore/UserPreferences）：`viewerOrientation` Flow<Int>，默认 **0 横向 / 1 竖向**，`KEY_VIEWER_ORIENTATION = "viewer_orientation"` + `setViewerOrientation(value)`；沿用 raw-int 风格（同 themeMode/novelDefaultTab），无数据库变更。
- **feature:viewer**：build.gradle.kts 新增 `api(project(":core:datastore"))`；`ViewerViewModel` 注入 `UserPreferences` 暴露 `viewerOrientation: StateFlow<Int>`（`stateIn`）+ `setViewerOrientation`。
- **ViewerRoute**：页内容抽为局部 `val pageContent: @Composable (Int) -> Unit`（含 ZoomableImage），`orientation == 1` 用 **`VerticalPager`**、否则 `HorizontalPager`；**共用同一 `pagerState`** → 初始定位 `scrollToPage`/页码指示/`userScrollEnabled = !anyZoomed`（缩放禁翻页）全部不变，切向零重复加载；动图（Ugoira）分支不受影响；`FullscreenImageRoute`（单图全屏无 pager）不涉及。
- **我的页**（feature:user）：`MeViewModel` + `viewerOrientation` StateFlow + `setViewerOrientation`；`MeRoute` 浏览设置节「小说默认页」卡片后新增「插画查看方向」卡片（横向滑动/竖向滑动两个 `PillSelectButton`，复用现有模式）；strings：`me_viewer_orientation`/`me_viewer_orientation_horizontal`/`me_viewer_orientation_vertical`（中英双语）。
- 验证：`:app:compileDebugKotlin` 通过；`:feature:user`/`:feature:viewer`/`:core:datastore`/`:core:ui` 单测通过。**未提交**。

### 第六十二轮补充：无缝竖向滑动模式
- **需求**：在横向/竖向翻页之外，新增「无缝竖向」webtoon 连续滚动模式；「我的」页插画查看方向改为三选项。
- **偏好语义**（core:datastore `viewerOrientation`）：**0 横向翻页 / 1 竖向翻页 / 2 无缝竖向**；key 不变、无数据库变更；三处注释同步更新（UserPreferences/MeViewModel/ViewerViewModel）。
- **每 P 真实宽高**（feature:viewer/ViewerViewModel）：新增 `loadRealSizes()`，镜像 `IllustViewModel.kt:131-149`——`pixivRepository.webApi.getIllustPages(illustId)`（`ajax/illust/{id}/pages`，单图也返回 1 项）合并 `width/height` 回 `_pages`；`load()` 始终调用，三模式统一受益，无缝模式按自然宽高比堆叠。
- **无缝 UI**（feature:viewer/ViewerRoute）：
  - `orientation == 2` → 新增私有 composable `SeamlessViewer`：`LazyColumn` + `rememberLazyListState`，`userScrollEnabled = !anyZoomed`（**foundation 1.7.0 已确认支持该参数**，与翻页模式缩放锁滚动一致）；每项 `Box(fillMaxWidth + aspectRatio(width/height))` + 复用 `pageContent`（ZoomableImage fillMaxSize 填满宽高比盒）；宽高缺失兜底 `aspectRatio(0.75f)`；无间距连续堆叠（真无缝）。
  - 页内容抽为 `pageContent: @Composable (Int) -> Unit` 三种方向共用；pager/无缝两套 list state 共存。
  - **currentIndex 统一**：`orientation == 2` 用 `listState.firstVisibleItemIndex`（snapshot state 驱动重组），否则 `pagerState.currentPage`；页码指示「当前页/N」、下载（菜单 + 底部条）、壁纸目标全部改用 `currentIndex`。
  - 初始定位：新增 `LaunchedEffect(pages.size){ if (initialPage>0) listState.scrollToItem(target) }`（与 pagerState 同逻辑）。
- **我的页**（feature:user）：插画查看方向卡片由 2 个胶囊改 3 个（横向滑动/竖向翻页/无缝竖向）；**竖向文案「竖向滑动」→「竖向翻页」**避免与无缝竖向混淆（key 不变）；新增 `me_viewer_orientation_seamless`（无缝竖向 / Seamless vertical），中英双语。
- 验证：`:app:compileDebugKotlin` 通过；`:feature:user`/`:feature:viewer`/`:core:datastore`/`:core:ui` 单测通过。**未提交**（第六十二轮全部改动）。

### 第六十三轮：Magic Number 抽象与统一（Token 化 + 模式枚举化）
- **需求**：分析全仓魔法数字并统一；用户决策：① 尺寸建 token + 替换通用组件/高频点（长尾保留字面量）② 阅读器色合并进 core:ui theme ③ 5 个裸 int 模式值全部枚举化。
- **新增 Token**（core/ui/theme）：
  - `Dimens.kt` `object Spacing`：xs=4 / sm=8 / md=12 / lg=16 / xl=24 / pagePadding=16
  - `Shapes.kt` `object AppShapes`：small=8 / card=12 / large=16 / pill=percent50 / circle；**统一「全圆」口径消灭 `999.dp` 与 `RoundedCornerShape(50)` 双写法**（原 3 处：NovelSeriesRoute:326 / UserRoute:621 / MeRoute:509）
  - `Durations.kt` `object Durations`：NOTIFICATION_TIMEOUT=2600 / PAGE_SWITCH_ANIM_MS=700 / READER_BAR_HIDE_MS=3000 / READER_UI_DELAY_MS=800
- **颜色**（core/ui/theme/Color.kt）：新增 `ViewerScrim(0xFF0A0A0A)` / `FavoriteRed(0xFFFF5252)` / `SuccessGreen(0xFF4CAF50)` / `PixivBlue(0xFF0096FA)` + 阅读器 4 主题完整调色板（`ReaderDay/Paper/Night/DeepBlack` × bg/text/secondary/divider/topBar，**取 ReaderTheme.kt 实际渲染色值**）；**删除 7 个无人引用的旧 `Reader*` 常量**（0xFFF6F1E7/0xFF2E3B32/0xFF121212 等，与真实渲染色不一致的死代码）。业务替换：ViewerRoute/FullscreenImageRoute 黑底、ViewerRoute/IllustCard 收藏红、Notification 成功绿、LoginScreen Pixiv 蓝、NovelCard 红心改 `MaterialTheme.colorScheme.error`。
- **阅读器色合并**：feature/reader `ReaderTheme.kt` 的 `readerThemeColors()` 改为引用 core:ui 调色板常量，参数改 `ReaderThemeMode`；`ReaderThemeColors` 数据类 + `READER_*_NAME_RES` 数组留 feature（依赖 R.string）。
- **数值常量**：`ZoomableImage` 顶加 `MAX_SCALE=6f`/`DOUBLE_TAP_SCALE=2.5f`；`SimulationPageContent:362` 手写 `3.14159265f` → `kotlin.math.PI`；`SeamlessViewer` 兜底 `0.75f` → `FALLBACK_ASPECT_RATIO`。
- **模式枚举化**（core/common `AppModes.kt`，仿 AppLanguage 风格）：`ThemeMode(0/1/2)` `ViewerOrientation(0/1/2)` `NovelDefaultTab(0/1)` `ReaderPageMode(0/1/2)` `ReaderThemeMode(0/1/2/3)`，各带 `value: Int` + `from(Int)` 兜底；**存储 key/value 均不变 → 零迁移**。
  - core:datastore `UserPreferences`：5 个 Flow 改 `Flow<枚举>`（`map { X.from(...) }`），setter 收枚举存 `.value`
  - app `MainActivity`：`themeMode` when → `ThemeMode.LIGHT/DARK/else`
  - feature:user `MeViewModel`/`MeRoute`：三处 `listOf(0/1/2 to R.string...)` → 枚举 entries 映射
  - feature:viewer `ViewerViewModel`/`ViewerRoute`：`orientation == 1/2` → `ViewerOrientation.VERTICAL/SEAMLESS`
  - feature:novel `NovelFeedViewModel`/`NovelRoute`：`loadDefaultTab(): NovelDefaultTab` + `.value.coerceIn`
  - feature:reader `ReaderViewModel`（StateFlow<枚举> + setter）、`ReaderRoute`（pageMode 判断、`effectiveTheme` 枚举化 `if(isDark) NIGHT else PAPER`）、`ReaderSettingsSheet`（`theme: ReaderThemeMode`/`pageMode: ReaderPageMode`，SegmentedButton `entries.getOrNull(index)` 与 `READER_*_NAME_RES` 数组 ordinal 对齐）
- **尺寸替换（Spacing/AppShapes）**：core:ui 通用组件全量语义替换——IllustCard（角标/收藏浮层/作者行）、NovelCard（封面/角标/标签 chip）、IllustWaterfallGrid（内容边距/间距）、RankingList（TabRow edgePadding/骨架/列表边距）、RankingRow（卡片角/间距）、Notification（卡片内边距/关闭钮）、SettingsCard、StatusViews（24→xl/16→lg）、SeriesBookCover（默认角=card）、CreatorProfileCard（16→large/12→md）；卡片角统一走 `AppShapes.small/card/large`，**6/10/14 等组件特异口径保留字面量**。
- **坑**：editor 微改 import 换行可能把两行 import 粘成一行（MeRoute/NovelSeriesRoute 曾报 "Expecting a top level declaration"），改 import 后需肉眼复查相邻行；`Shapes.kt` 用 `dp` 记得 import `androidx.compose.ui.unit.dp`。
- 验证：`:app:compileDebugKotlin` 通过；`:core:ui`/`:core:datastore`/`:feature:user`/`:feature:viewer`/`:feature:novel`/`:feature:reader`/`:feature:auth` 单测通过。**未提交**（第六十二~六十三轮全部改动）。

### 第六十三轮补充：无缝竖向单图居中修复
- **Bug**：无缝竖向模式只有 1 张图时图片置顶；期望单图垂直居中。
- **根因**：`SeamlessViewer` 的 `LazyColumn` 未指定 `verticalArrangement`，默认 `Arrangement.Top`，单图（图高 < 屏高）时内容从视口顶部排布。
- **修复**（feature:viewer/ViewerRoute.kt `SeamlessViewer`）：`verticalArrangement = if (pages.size == 1) Arrangement.Center else Arrangement.Top`——利用 LazyColumn 语义「内容总高小于视口时居中整组、超过时 arrangement 不生效仍从顶排布可滚动」；单图超一屏仍贴顶可滚动（合理），多图行为不变；缩放锁定/顶栏/页码指示不受影响。
- 验证：`:app:compileDebugKotlin` 通过；`:feature:viewer` 单测通过。**未提交**。

### 第六十四轮：小说详情页重构（依据 design/novel-detail-ui.html 原型）
- **背景**：用户先要求 HTML 原型（design/novel-detail-ui.html，手机+平板双画框、详情/评论双视图、SVG 图标、M3 色板）确认后进入实现。原型迭代要点：统计行撑满整行均分；平板目录放左侧且与信息等高（先等高→矮→再等高，最终确定等高+sticky 视觉）。
- **详情页（feature:novel/NovelRoute.kt）**：
  - 移除内嵌评论区（CommentsSection/CommentRow/ReplyRow/formatCommentDate 迁至 NovelCommentsRoute.kt，同包复用）；
  - NovelActions 加「评论」按钮成 4 钮行（收藏/追更/下载/评论，ModeComment 图标）→ `onOpenComments`；
  - 系列目录重构：手机端 `NovelTocScroll`（标题 + Column(heightIn(max=屏高×0.4f).verticalScroll) + SeriesMoreRow「查看完整系列」），平板端 `NovelTocPanel`（Row height(IntrinsicSize.Max) 双栏：左 264dp 卡片 fillMaxHeight 与右侧信息等高 + 内部滚动 + SeriesMoreRow）；`isTablet = screenWidthDp >= 600`；
  - ChapterRow 加序号徽标（28dp 圆角 9、当前章主色/其余 secondaryContainer、padStart(2,'0')）；
  - NovelHeader 加发布时间行（create_date.take(10) + DateRange 图标 + `novel_publish_date`）；统计行改 icon+值+标签（MenuBook/FavoriteBorder/Visibility）三块 weight(1f) 均分撑满整行；标签胶囊改 AppShapes.pill；
  - NovelDetailRoute 增 onOpenSeries/onOpenComments 参数；删除评论 collect/透传。
- **评论独立页**：新增 NovelCommentsRoute.kt + NovelCommentsViewModel.kt（@HiltViewModel、SavedStateHandle novelId）：PagedState<Comment> 分页（getNovelComments + getNextComments，镜像 IllustViewModel 模式）、触底加载更多（derivedStateOf lastIndex>=size-3）、replyTarget + postComment(parentCommentId)、回复条（primaryContainer 底 + Close 取消、输入框预填 @昵称）、CommentInput 底部固定、作者行点进 onOpenUser、ErrorBox/LoadingBox/EmptyBox 三态。
- **NovelViewModel**：移除评论职责（comments/commentsLoading/commentDraft/loadComments/onCommentDraftChange/postComment 及 load() 内调用）——详情页不再加载评论，职责分离。
- **导航**：PixivNavGraph 新增 `ROUTE_NOVEL_COMMENTS = "novel_comments/{novelId}"` + composable 注册；ROUTE_NOVEL 接线 onOpenSeries（→novel_series/{id}）与 onOpenComments（→novel_comments/{id}）。
- **strings**：新增 6 项双语（novel_publish_date/novel_toc_section/novel_series_view_all/novel_comment_button/novel_reply/novel_reply_cancel）；评论页标题复用 novel_comments_section。
- 验证：`:app:compileDebugKotlin` + `:feature:novel:testDebugUnitTest` 通过。**未提交**（第六十二~六十四轮全部改动待提交）。

### 第六十五轮：小说详情页完全重写 + 通用评论页（feature:comments）
- **背景**：用户反馈详情页"布局与 HTML 一致但体验完全不一致"，点名：按钮应为 col 竖排（实际 row）、字体太小/未加粗、部分按钮缺图标、原型无封面视差但实现了（须删）、简介缺首行缩进。选择：整页重写而非补丁；平板目录 sticky（重构）；深色跟随系统；阅读按钮换合理图标（AutoStories）；平板 banner 随滚且目录滚动不影响 banner；手机简介 6 行截断 + 展开全文；评论区做成通用页面（novel/illust 共用）。
- **小说详情整页重写（feature:novel/NovelDetailRoute.kt 新建，~700 行）**：
  - NovelRoute.kt 删详情页全部代码只留小说 Tab（428 行）；旧 NovelCommentsRoute/ViewModel 删除；
  - banner 无视差（去掉 graphicsLayer/NOVEL_BANNER_PARALLAX），底部 110dp 渐变，随滚；
  - 标题 21sp Bold；作者行 14sp SemiBold + KeyboardArrowRight ›；发布时间行 DateRange + 12sp；统计行 icon+15sp Bold+11sp 标签 weight(1f) 均分；标签 11sp pill；简介 13.5sp + TextIndent(firstLine=2.em) + 手机 maxLines=6 截断 + 展开全文（rememberSaveable，novel_intro_expand/collapse）；
  - 操作区：阅读主按钮 48dp + AutoStories + 15sp SemiBold；4 竖排卡片钮 VerticalActionButton（icon 上/label 下 11sp SemiBold、52dp、AppShapes.card 圆角 12、surfaceContainerLow+outline、激活 primaryContainer、disabled alpha 0.45）；
  - 系列目录：TocTitle（MenuBook + 15sp Bold + 数量）、ChapterRow（28dp 序号徽标 圆角9 当前主色、13sp SemiBold、当前胶囊 10sp）、SeriesMoreRow（12sp SemiBold + KeyboardArrowRight ›）；
  - 平板双栏（screenWidthDp≥600 且有系列）：Box{ LazyColumn{banner 360dp 全宽随滚; info_actions padding(start=280dp) 避让} ; Box(align TopStart){ padding(top=360dp) NovelTocPanel(264dp fillMaxHeight 内部滚动) } }——目录浮层与 LazyColumn 并列，滚动互不影响、banner 滚走目录仍固定；返回按钮平板 TopEnd/手机 TopStart；
  - 手机单列：banner 280dp + 目录 heightIn(屏高×0.4) 限高内部滚动；下载对话框原样迁移。
- **通用评论页（新建 feature:comments 模块）**：settings.gradle + app 依赖 + build.gradle（hilt/ksp/compose，api core:ui/core:network/core:model）；
  - 路由 `ROUTE_COMMENTS = "comments/{type}/{targetId}"`（app NavGraph 注册，type String + targetId Long）；
  - CommentListViewModel（@HiltViewModel）：PagedState<Comment> 按 type 分流 getNovelComments/getIllustComments + getNextComments；postComment 按 type 分流 postNovelComment/postIllustComment（parentCommentId 回复）；stamp 暂不启用；
  - CommentListRoute：TopAppBar「评论（N）」+ 分页列表 + 触底加载 + 回复条（@昵称 预填/Close 取消）+ CommentInput + 三态 + onOpenUser；
  - strings（comment_* 双语）；CommentRow/ReplyRow 从旧 novel 版迁移通用化。
- **接线与清理**：
  - novel 详情「评论」按钮 → comments/novel/{id}；删除 novel 评论 strings（novel_comments_*/novel_reply*/novel_msg_comment_*/novel_anonymous_user/novel_series_section）；
  - illust：IllustDetailRoute 移除内嵌评论（CommentSection/CommentList/CommentRow/IllustReplyRow + TwoPane 右栏），bottomBar 加 ModeComment 评论 IconButton → onOpenComments → comments/illust/{id}；IllustViewModel 移除评论职责（commentsPaged/commentDraft/loadComments/loadMoreComments/onCommentDraftChange/postComment + load() 内调用）；illust strings 清理 + 新增 illust_cd_comments。
- **坑**：import 清理脚本用 `\b符号\b` 统计使用次数会**误删委托类 import**（getValue/setValue 由 `by collectAsStateWithLifecycle()` 隐式使用、源码无字面标识符）——已回补 NovelRoute.kt/IllustDetailRoute.kt 的 getValue/setValue/derivedStateOf/remember/rememberSaveable/dp。教训：清理 unused import 必须排除 by 委托/运算符重载符号，或直接手动核对。
- 验证：`:app:compileDebugKotlin` + `:feature:novel`/`:feature:comments`/`:feature:illust` 单测通过。**未提交**。

### 第六十五轮补充：详情页 6 项微调 + 字体规范统一
- 用户反馈：①竖排按钮 icon/文字间隔过大 → 4dp→2dp（NovelIconLabelGap）；②图标无颜色 → 图标始终主色（仅 disabled 置灰，对齐 HTML `.abtn .ic{fill:var(--primary)}`）；③系列目录标题未加粗 → TocTitle 用 `novelTocTitleStyle()`（titleMedium.copy(Bold)）；④展开全文改为「展开」且居中按钮 → TextButton fillMaxWidth 居中，string 改 novel_intro_expand="展开"/Show more；⑤目录数字改为胶囊 → TocTitle 重构为「系列目录」标题 + 数量胶囊（primaryContainer 底 + pill + primary 字），新增 novel_toc_title、删除 novel_toc_section；⑥整体字号 +1sp。
- **字体规范**（遵循用户"查看 common 统一规范"）：core:ui/theme/Type.kt 已有统一 Typography（titleLarge 20sp/titleMedium 16sp/bodyLarge 16sp/bodyMedium 14sp/labelMedium 12sp）。详情页不再自定义 Novel*Font 常量，改为 **@Composable 派生样式函数**（novelTitleStyle/novelAuthorStyle/novelMetaStyle/novelStatValueStyle/novelSmallLabelStyle/novelIntroStyle/novelReadButtonStyle/novelTocTitleStyle/novelTocRowStyle/novelCurrentBadgeStyle/novelCountBadgeStyle/novelOptionTitleStyle），内部 `MaterialTheme.typography.xxx.copy(fontSize, fontWeight)`，字号集中定义、无散落 magic number。
- 验证：`:app:compileDebugKotlin` + `:feature:novel` 单测通过。**未提交**。

### 第六十五轮补充 2：通用评论页父子级评论（按需拉取 + 3 条截断展开）+ 子评论可回复
- **根因**：pixiv v3 评论列表只返回顶层 + `has_replies` 标志，子回复需 `getCommentReplies(type, comment_id)` 按需拉取（该端点此前全项目无人调用）；且 `ReplyRow` 无回复入口。
- **CommentListViewModel**：
  - `replies: StateFlow<Map<Long, List<Comment>>>`（顶层 id → 子回复）、`repliesLoading: StateFlow<Set<Long>>`、`expandedReplies: StateFlow<Set<Long>>`；
  - `loadReplies(parentId)`（防重 → getCommentReplies → 写缓存）；`toggleRepliesExpanded(parentId)`；
  - `setReplyTarget(comment, topLevelId)`：回复目标可为顶层或子评论，`_replyTargetTopId` 记录所属顶层 id（刷新用）；
  - `postComment()` 成功后刷新目标 = `replyTargetTopId ?: replyTarget.id` → 该顶层评论：自动加入 expandedReplies + 清缓存 + loadReplies（新回复即时可见）。
- **CommentListRoute**：
  - `CommentRow`：`onReply: (Comment) -> Unit`；`has_replies` 时自动 `LaunchedEffect` 加载子回复（懒组合 → 仅可见行请求），浅色块内小 loading；
  - 子回复**最多显示 3 条**（`MAX_VISIBLE_REPLIES = 3`），超出未展开时底部「查看全部 %d 条回复」入口（comment_reply_expand），点击展开全显**无收起**；
  - `ReplyRow` 新增「回复」入口（labelSmall primary，`onReply(reply)` 闭包携带外层顶层 id）→ 子评论可回复。
- strings：新增 `comment_reply_expand`（查看全部 %1$d 条回复 / View all %1$d replies）双语。
- 验证：`:app:compileDebugKotlin` + `:feature:comments:testDebugUnitTest` 通过。**未提交**。

### 第六十五轮补充 3：清理缓存残留修复（5.2M 清不掉）
- **根因**：`MeViewModel.clearCache()` 清理范围与 `refreshCacheSize()` 统计不一致——统计整个 `cacheDir`，但清理只清 Coil `diskCache`（image_cache）+ 误清 `filesDir/novel_debug`（死路径）。残留 = `cacheDir/ugoira`（动图帧缓存，UgoiraLoader 写） + `cacheDir/novel_debug`（阅读器调试 HTML，ReaderViewModel 写）。
- **修复**：`clearCache()` 改为：Coil `diskCache.clear()` + `memoryCache.clear()`（先正常关闭）→ **清空整个 `appContext.cacheDir`**（listFiles().forEach { deleteRecursively() }），一次覆盖 image_cache/ugoira/novel_debug，与统计一致；保留 filesDir/offline + downloadEntryDao.deleteByType("novel_offline")。`refreshCacheSize()` 移除死路径 filesDir/novel_debug。下载文件在 filesDir/Downloads、字体在 filesDir/fonts，均不在 cacheDir，不会误删。
- 验证：`:app:compileDebugKotlin` + `:feature:user:testDebugUnitTest` 通过。**未提交**。

### 第六十五轮补充 4：浏览历史页清空按钮改图标+文字
- 用户确认「清除缓存」保持只清缓存文件（浏览历史是 Room 数据库持久数据，不纳入清缓存）。
- HistoryRoute TopAppBar actions：清空按钮由纯文字 IconButton 改为 **TextButton（DeleteOutline 图标 18dp + 「清空」文字，error 色）**；新增 imports：Icons.Filled.DeleteOutline / TextButton / layout.size。
- 验证：`:app:compileDebugKotlin` + `:feature:user:testDebugUnitTest` 通过。**未提交**。

### 设计规范文档：design.md
- 新建 `F:\pixiv-mateiral3\design.md`：整理全项目设计规范唯一权威文档——色板（Color.kt 静态 M3 + 语义色 + 阅读器 4 主题）、字体（Type.kt Typography 档位 + 页面级派生样式约定）、间距 Spacing、形状 AppShapes（全圆仅 pill）、时长 Durations、主题机制（动态色/深浅）、模式枚举 AppModes、通用组件模式（收藏浮层 28/18、ReplyPill 胶囊、竖排按钮、子评论 3 条展开、沉浸 banner 无视差等）、i18n 约定、design/*.html 原型基准。后续新增 UI 一律引用该文档与 Token。
