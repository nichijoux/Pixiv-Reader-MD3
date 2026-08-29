# AGENTS.md

Pixiv Reader — Android (Kotlin + Jetpack Compose + Hilt + Room) 客户端。Windows 环境、命令行构建、无 Android Studio。

## 模块架构（依赖方向硬约束）

```
app → feature/* → core/ui → core/network → core/database · core/datastore → core/common → lib:pixivapi
                              ↘ core/novel（解析/文档模型，被 core:network、feature:novel、feature:reader 依赖）
```

- **模块清单**（settings.gradle.kts）：`:app` + `:lib:pixivapi` + core（`common`/`network`/`database`/`datastore`/`ui`/`novel`）+ feature 15 个（`auth`/`home`/`discover`/`comments`/`illust`/`viewer`/`novel`/`reader`/`user`/`bookmark`/`watchlist`/`notification`/`manga`/`follow`/`onboarding`）。
- **feature 之间禁止互相依赖**；共享逻辑放 core 层。当前无 feature→feature、core→feature 项目边。
- 实际依赖边（与上图差异点）：app 另 `implementation` 直连 5 个 core（common/network/database/datastore/ui）；除 onboarding 外每个 feature `api` 依赖 `core:ui` + `core:network`，按需再直连 `core:database`/`core:datastore`/`core:novel`/`core:common`（discover/viewer/reader/user 等）；`core:network` 还依赖 `core:novel`；`core:datastore` 不被 core:network 依赖。
- **`core/model` 与 `feature/settings` 是空目录**（无 build.gradle.kts/src，未注册，勿引用）；`feature/download` 已删除（仅剩陈旧 `build/` 产物目录，可手动删除）；下载管理实现在 `feature:user`。
- **`lib:pixivapi` 是 vendor 副本**（namespace `com.pixiv.api`，11 个文件）：改 API 只能在 `lib/pixivapi/`。包结构：`com.pixiv.api`（PixivApi 入口/Constants）、`.auth`（PixivOAuth/SessionManager）、`.model`（Models/WebModels）、`.network`（Retrofit 接口 `AppApi`/`PixivWebApi`、`PixivClient` 创建实例、`Interceptors`/`TokenInterceptor`/`PixivLang`）。
- 新增 feature ViewModel 需在 build.gradle 加：`hilt`/`ksp` 插件 + `api(project(":core:ui"))` + `api(project(":core:network"))` + `api(libs.hilt.android)` + `ksp(libs.hilt.compiler)` + `implementation(libs.hilt.navigation.compose)`；用 DAO/DataStore/解析器再加对应 core 模块。
- **排行榜通用组件**：`core:ui RankingList<T>`（ScrollableTabRow + HorizontalPager 滑动切段 + 三态 + 触底加载，`itemContent(T, rank)` slot）+ `core:ui RankingIllustCard`（名次徽标大图卡）+ `core:common RankingModeInfo(@StringRes labelRes, value)`。mode 列表：漫画 5 段（day_manga/week/month/week_rookie/day_r18，`feature:manga/MangaRankingViewModel`）、小说 6 段（day/week/day_male/day_female/week_rookie/day_r18，`feature:novel/NovelRankingViewModel`）、插画 7 段（day/week/month/day_male/day_female/week_rookie/day_r18，`feature:manga/IllustRankingViewModel`）。**每段独立分页**：调用方传 `stateFor(mode) -> PagedState<T>`（VM 内 `pages.getOrPut` 缓存，数据驻留 VM）+ `onRetry(mode)`/`onLoadMore(mode)`；RankingList 每页只 collect 自己 mode 的 PagedState——已加载段滑动切回**不重复请求、无过渡动画**（AnimatedContent targetState 用该页自身内容三态）。`core:ui` 已依赖 `core:network`（PagedState）。

## 核心机制

- **i18n**：全项目用户可见文案必须走各模块 `res/values/strings.xml`（默认中文）+ `res/values-en/`（英文）+ `res/values-zh-rTW/`（繁体），Compose 用 `stringResource(R.string.x)`；跨模块共用 toast 文案已收敛到 core:common（`core_msg_*`，VM 侧 `import com.pixiv.reader.core.common.R as CoreR` 引用）。语言中性 token（AI/`xP`/`#tag`/`+N`）保留内联。应用内语言切换：`UserPreferences.appLanguage`（system/zh/zh-TW/en）+ `MainActivity.attachBaseContext` 同步读（`core:datastore.readAppLanguageSync`）→ `localeFor` → `Locale.setDefault` + `PixivLang.code = pixivLanguageCode(...)`（lib:pixivapi 网络语言头/`lang` 参数动态化）+ `createConfigurationContext` 覆盖；**我的页外观设置内嵌语言卡**（切换后 `activity.recreate()` 生效，无独立设置页）。**注意**：`@ApplicationContext.getString` 跟随系统语言而非应用内覆盖，VM 持续展示型文案偶发语言不一致——可接受。`formatCount/formatCountForNovel`（core:common）已 locale-aware（zh 万/亿，en K/M/B），纯函数默认 `Locale.getDefault()`。
- **图片 URL 必须走 `PixivRepository.imageClient`**（lib `PixivClient` 专用 OkHttp：maxRequestsPerHost=8、对 pximg.net 注入 `Referer=https://app-api.pixiv.net/` 与 APP_USER_AGENT，否则 403）。Coil 由 `PixivApp` 注入该 client，`PixivImage`/`AsyncImage` 自动带；需加载进度用 `PixivImage(showProgress=true)`（SubcomposeAsyncImage）。
- **消息管线**：ViewModel 通知继承 `MessageViewModel`（core:network，`Channel(BUFFERED)` 通道 + `sendMessage`/`trySendMessage`，满则丢），UI 侧一行 `UiMessageEffect(viewModel.message, notificationHostState)`（core:ui，封装 getString + `MessageType→NotificationType` 映射；**LaunchedEffect 内不可调 stringResource**）。`UiMessage(@StringRes res, args, type=INFO)` 定义在 core:common，附 `loadFailureMessage(failure, reasonRes, fallbackRes)` 按异常有无原因选文案。
- **剪贴板链接**：`PixivUrlParser.parse(text)`（core:common）从任意文本提取第一个 pixiv 小说/系列/插画/用户链接；评论草稿富文本经 `EmoteDraftCodec`（sentinel 编解码 `(tag)` 与 `@提及`）与 `EmoteCommentField` 配合。
- **Gson 经 lib:pixivapi 传递**，feature 层可直接 `Gson()`（如历史 payloadJson）。**Gson UnsafeAllocator 对缺失键字段给 null**——反序列化到 Kotlin 非空字段会 NPE，兜底必须 `.orEmpty()`（历史小说卡 NPE 崩溃的根因）。
- **数据库**：`core/database` `PixivDatabase` **version=3**，4 实体：`BrowseHistoryEntity`（id 自增 + targetType/targetId + title/coverUrl/payloadJson/viewedAt）、`DownloadEntryEntity`（**主键 targetType+targetId+format+scopeKey**，含 status/progress/width/height/seriesId/payloadJson 等快照）、`SearchHistoryEntity`、`ReadingProgressEntity`（novelId 主键 + seriesId/chapterOrder/charOffset/percentage）。现存迁移 `MIGRATION_1_2`（download_entry+payloadJson）、`MIGRATION_2_3`（主键扩为四列，旧 scopeKey=''）；历史 v1~v7 六条已清理，新装按当前 schema 建库。后续加实体/字段必须升 version 并接续写新 Migration（改主键需重建表搬迁数据，列定义须与实体逐列一致，Room 启动时校验 schema）。DAO 模式：**仅** Browse（`deleteByTarget`→upsert）与 Search（`deleteByKeyword`→upsert）是「先删旧再插」去重置顶；Download/Reading 不适用。小说下载条目按 scopeKey 区分单本（`""`）/整系列（`"series"`）/部分分册（`"partial"`），派生函数 `novelScopeKey(seriesId, chapterIds)` 在 `feature/novel/data/NovelExporter.kt`。
- **历史/下载快照完整性**：`BrowseHistoryEntity.payloadJson` 存完整卡片数据（历史插画宽高、小说作者等），否则通用组件信息不全/图片裁剪中间。
- **插画完整显示**：`IllustCard` 按 `illust.width/height` aspectRatio 显示；无宽高会固定高度 + Crop 裁剪中间——数据源需带宽高（历史/下载实体存 width/height）。
- **离线小说缓存已移除（确认）**：无 `OfflineNovelRepository`、无 `filesDir/offline` 读取，阅读器在线直连网络（`NovelContentLoader` 每次拉取+解析）；`PixivApp` 启动仅删除旧版 offline 残留目录。仍在的缓存：`feature/reader/data/ReaderChapterCache`（**进程级内存** LRU，章节上限 6 + series 目录，进程结束失效，非离线缓存）、`NovelExporter` 断点缓存（`filesDir/Downloads/novels/.export_*`，`NovelDocumentCodec` **运行时在用**——encode 写 payloadJson、decode 恢复断点，并非「仅测试使用」）。
- **本地 TXT/EPUB/MD 阅读**：`TxtNovelParser`/`EpubNovelParser`/`MarkdownNovelParser`（core:novel，Jsoup 依赖）→ `LocalReaderStore.set(document, title)`（**内存单槽**，`consume()` 取出即清空，**不按 novelId 存储**；`local_reader/{novelId}` 只是导航路由参数）→ `ReaderRoute(localDocument=...)` → `ReaderViewModel.useLocalDocument`。入口：下载管理页选本地文件（txt/md/epub 解析；pdf/docx 走 ACTION_VIEW）。
- **主题**：`core/ui/theme/PixivReaderTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = true)`（Android S+ 动态色，否则固定 Light/Dark token）；`UserPreferences.themeMode`(0 跟随/1 浅色/2 深色，`core:common AppModes.ThemeMode`) 与 `dynamicColor`、`appFontScale`（`StandardFontSize(LocalDensity, fontScale)` 包裹）由 `MainActivity` 收集生效。
- **会话/网络**：`PixivRepository`（core:network，`api`/`webApi`/`imageClient` 三个 accessor）；`SessionRepository`（`isLoggedIn: StateFlow<Boolean>`、PKCE OAuth 回调处理 `processLogin`/`logout`）；`NetworkMonitor`（`isOnline: StateFlow<Boolean>`）；`AppUpdateChecker`（GET GitHub `releases/latest`，404→null；自动检查在 `MainActivity` LaunchedEffect，手动入口在 MeRoute）。
- **下载完成通知**：`app/.../DownloadCompletionNotifier`（@Singleton）订阅 `DownloadEntryDao.observeAll()`，`scan` 快照对比仅对「非终态→done/failed」迁移发 `SharedFlow<UiMessage>`（首次订阅的既有终态不重复通知）；`MainActivity` 收集事件到 NotificationHost。
- **评论下沉复用**：`core/network/comment/CommentListViewModel`（`type`/`targetId` 取自 SavedStateHandle，`commentsPaged: PagedState<Comment>` + 回复树/贴纸/草稿 StateFlow）+ `core/ui/component/comment/CommentListContent`（不含 Scaffold 的可嵌入块）——`feature:comments` 评论页与排行右栏评论区共用。
- **排行分页**：`PagedState<T>`（core:network；`items/isLoading/isLoadingMore/hasMore/error` + next_url 游标 + generation 防过期请求；`loadInitial/loadMore/reset`）+ 基类 `RankingPagedViewModel<T>(modes)`（`pages.getOrPut` 缓存、`stateFor`、`onPageSelected` 仅首次加载、`retry`/`loadMore` 按 mode 分派，`loadInitialFor` 抽象由子类实现；父类构造不预载——子类字段尚未初始化）。

## 通用组件（core:ui，优先复用）

- **card**：`IllustCard`（瀑布流卡：原比例封面 + 收藏/AI/页码/动图/下载进度）、`IllustWaterfallGrid` 配合 `NovelCard` + `NovelCardData`（core:common 下沉，ui 侧 typealias；字段 id/title/coverUrl/authorId/authorName/authorAvatarUrl/publishDate/seriesTitle/seriesId/favoriteCount/wordCount/tags/isFavorite）、`SeriesCard` + `SeriesCardData`、`SeriesBookCover`（无封面图书图标）、`RankingIllustCard`（名次徽标大图卡）、`UserAvatar`（URL null 首字母圆）、`ProfileHeader` + `ProfileHeaderData`（工厂 `from(user)`）、`CreatorProfileCard` + `CreatorProfile`、`UserPreviewToProfile`、`NovelToCardData`（ui re-export）、`rankColor`（internal，1/2/3 金橙灰）。
- **list**：`RankingList<T>`（mode 数据驱动容器，stateFor 取 PagedState，自动重试/触底）、`RankingBanner`（排行入口卡）、`RankingIllustSkeleton`、`LoadMoreItem`（尾部可见即分页，加载中转圈）。
- **comment**：`CommentListContent`（可嵌入评论块：三态/回复树/贴纸/底部输入栏，参数全回调）。
- **detail**：`IllustDetailContent` + `IllustDetailStrings`（文案参数化，详情路由与排行右栏共用）、`IllustPagePager`/`IllustInfoSection`/`IllustRelatedSection`。
- **feedback**：`UiMessageEffect`、`StatusViews`（`LoadingBox`/`ErrorBox`/`EmptyBox` 三态）、`Notification`（`NotificationHostState` + `rememberNotificationHostState` + `NotificationHost`，自定义 Material3 风格通知替代 Snackbar：inverseSurface 深底胶囊 + Info/Success/Error 徽标 + 关闭按钮，底部滑入淡入动画、2.6s 自动消失、新消息顶替；用法 `val s = rememberNotificationHostState()` → `Scaffold(snackbarHost = { NotificationHost(s) })` → `s.show(text, type = NotificationType.Success)`）、`Skeleton`（`skeletonPulseColor`/`SkeletonBlock`/`RankingBannerSkeleton`）。
- **image**：`PixivImage`（全项目图片入口，走 imageClient）、`ZoomableImage`（telephoto 捏合/双击缩放）、`UgoiraPlayer`/`UgoiraCardPlayer`（动图双缓冲逐帧播放）。
- **input**：`CommentInput`（详情评论行 + 表情/贴纸面板）、`EmoteCommentField` + `EmoteFieldHandle`（EditText 富文本表情/回复提及）、`SettingsCard` + `SettingsCardItem`（数据驱动设置卡）、`VerticalActionButton`（竖排操作钮）、`ConfirmDialog` + `ConfirmDialogVariant`（危险/警告确认框）。
- **emoji**：`PixivEmojiTag`（`PIXIV_EMOJI_IDS`/`PIXIV_EMOJI_TAGS`/`pixivEmojiUrl`/`PixivEmojiTagImage`/`PixivEmojiTagChip`/`buildEmojiAnnotatedString`——评论 `(tag)` 行内渲染）。
- **layout**：`AdaptiveNavScaffold` + `AdaptiveNavItem`（Compact 底部 NavigationBar / Medium+ 左 NavigationRail）、`AdaptiveContentBox`（平板居中限宽 MAX_CONTENT_WIDTH_DP=760）、`AdaptiveContentTitle`、`currentWindowSizeClass`（core:common `WindowSizeClass` 分类：<600 Compact / <840 Medium / 否则 Expanded）、`FullscreenImageRoute`（黑底单图全屏）。
- **grid**：`IllustWaterfallGrid`（自适应列数瀑布流 + header + 触底分页）、`IllustWaterfallSkeleton`。
- **theme**：`PixivReaderTheme`；`Color.kt`（MD3 token + 语义色 ViewerScrim/FavoriteRed/SuccessGreen/PixivBlue + 阅读器四主题色）、`Type.kt`（Typography）、`Shapes.kt`（`AppShapes`）、`Dimens.kt`（`Spacing`）、`Durations.kt`（通知 2600ms/翻页动画 700ms）。

## 导航约定

- 顶层路由常量集中在 `app/.../navigation/PixivNavGraph.kt`（`ROUTE_*`）：`auth`、`onboarding`、`main`（底部 5 Tab 壳：首页/漫画/小说/关注/我的，`MainShell.rememberTabs` 定义，`navigateToTab` 统一 saveState+singleTop+restoreState）、`illust/{illustId}`、`viewer/{illustId}?page={page}`、`novel/{novelId}`、`comments/{type}/{targetId}`、`reader/{novelId}?toEnd={toEnd}`、`local_reader/{novelId}`、`user/{userId}`、`user_bookmarks/{userId}`、`user_following/{userId}`、`novel_series/{seriesId}`、`history`、`bookmarks?type={type}&tag={tag}`、`watchlist`、`notifications`、`notification_group/{groupId}?title={title}`、`blocked`、`downloads`、`manga_ranking`、`illust_ranking`、`novel_ranking`、`image_preview?url={url}&title={title}`。**除 `main` 外全部为全屏路由（隐藏底部导航）；MainShell 内 discover_tab 也隐藏**。
- **启动链路**：未完成引导 → `onboarding`（完成后写 DataStore）→ 未登录 → `auth`（OAuth PKCE）→ 已登录 → `main`。`PixivNavGraph(isLoggedIn, onboardingComplete, onCompleteOnboarding, onLogout)`。
- **深链 scheme `pixiv://`**：`illust/{id}`、`novel/{id}`、`reader/{id}`、`user/{id}`；OAuth 回调 `pixiv://account/login`（MainActivity + Manifest）。
- **内层 Tab 无法直达顶层路由**——用回调链 `PixivNavGraph → MainShell → feature`（`onOpenUser`/`onOpenReader`/`onOpenIllust`/`onOpenNovel`/`onOpenSeries`/`onOpenComments` 等），全在顶层统一接线。
- 跨 Tab 传搜索词：`main?search={search}` + `MainShell.pendingSearch`（State 单次消费）+ `DiscoverRoute.initialQuery`；阅读器章节切换用 `popUpTo(ROUTE_READER){inclusive=true}` 替换栈（`?toEnd=true` 定位末尾）。
- **返回安全**：栈底时 `safeBack` 静默忽略（防快速操作连点把 startDestination 弹出，导致空栈 + 过渡竞态）。
- 新增全屏页 = 顶层路由常量 + `PixivNavGraph` 接线（含回调链上抛）。

## 注释规范

- 新增或实质修改的函数 / 方法 / Composable，必须在声明正上方写多行 KDoc（`/** ... */`），简要说明职责。
- KDoc 必须为每个参数提供 `@param`，并给出 `@return`；方法可能主动抛错时补充 `@throws`。
- 无返回值（Unit）或返回 Flow / State 的方法也写明 `@return`，如「无返回值」或「操作完成后结束的协程」。
- 方法内部应在准备、校验、状态切换、资源释放、回滚、清理等关键步骤前添加单行注释。
- 单行注释应解释步骤目的、约束或失败边界，不要逐行复述代码行为。
- 修改实现时同步更新相关注释，禁止保留与当前行为不一致的旧注释。
- 项目惯例：注释 / KDoc 一律中文（风格参照 `PixivNavGraph.kt`、`AppLanguage.kt` 现有 KDoc）。

## 文件修改规则（重要）

- **txt/md/json/yaml/xml/csv 等文本文件禁止 shell 改写**，用 edit/write 工具。
- 代码注释用中文；命名按 `数据层 / 状态 / UI` 分包。
- 改文件前先 read；不整文件重写。

## Git

- 提交前自查：`:app:compileDebugKotlin` + 相关单测。
- **`git add` 与 `git commit` 必须分开执行**（同一命令链会争 `index.lock`）；提交信息用 `feat:` 中文，覆盖本轮改动。
