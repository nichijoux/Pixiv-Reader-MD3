# AGENTS.md

Pixiv Reader — Android (Kotlin + Jetpack Compose + Hilt + Room) 客户端。Windows 环境、命令行构建、无 Android Studio。

## 构建与测试（必须用此 JDK）

```powershell
$env:JAVA_HOME = "C:\Users\nichijoux\.jdks\jbr-21.0.11"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& .\gradlew.bat :app:compileDebugKotlin --console=plain   # 快速编译（推荐验证手段）
# 单测（改动涉及模块时跑，core:novel 28 用例 + feature:reader/network/novel/user/common）
& .\gradlew.bat :core:novel:testDebugUnitTest :feature:reader:testDebugUnitTest :core:network:testDebugUnitTest :feature:novel:testDebugUnitTest :feature:user:testDebugUnitTest :core:common:testDebugUnitTest --console=plain
```

- 每次改代码后先 `:app:compileDebugKotlin`，涉及数据层再加对应模块单测。
- Windows 无 Android Studio；编译错误看 `^e: ` 行。

## 模块架构（依赖方向硬约束）

```
app → feature/* → core/ui → core/network → core/database · core/datastore · core/model → core/common
                                          ↘ lib:pixivapi
```

- **feature 之间禁止互相依赖**（如 reader 不能用 novel 的东西）。共享逻辑放 core 层。
- 新增 feature ViewModel 需在 build.gradle 加：`hilt`/`ksp` 插件 + `api(project(":core:network"))` + `api(libs.hilt.android)` + `ksp(libs.hilt.compiler)` + `implementation(libs.hilt.navigation.compose)`；用 DAO/DataStore 再加对应 core 模块。
- **`lib:pixivapi` 是 vendor 副本**（pixiv API 上游源码封装）：改 API 只能在 `lib/pixivapi/`。所有 `com.pixiv.api.*` import 解析到 lib 副本（`namespace = com.pixiv.api`，Retrofit 接口在 `com.pixiv.api.network`）。
- `feature/download` 模块是空壳（仅 build.gradle + manifest，无 Kotlin 代码；build.gradle 残留 core:ui/model/navigation 依赖未清理）；下载管理实现在 `feature:user`。
- **排行榜通用组件**：`core:ui RankingList<T>`（ScrollableTabRow + HorizontalPager 滑动切段 + 三态 + 触底加载，`itemContent(T, rank)` slot）+ `core:ui RankingRow`（插画/漫画默认行）+ `core:common RankingModeInfo(@StringRes labelRes, value)`。各 feature 提供自己的 mode 列表即可复用（漫画 5 段在 `feature:manga/MangaRankingViewModel`；小说 6 段在 `feature:novel/NovelRankingViewModel`，均已落地；未来插画榜直接复用）。**每段独立分页**：调用方传 `stateFor(mode) -> PagedState<T>`（VM 内 `pages.getOrPut` 缓存，数据驻留 VM）+ `onRetry(mode)`/`onLoadMore(mode)`；RankingList 每页只 collect 自己 mode 的 PagedState——已加载段滑动切回**不重复请求、无过渡动画**（AnimatedContent targetState 用该页自身内容三态）。`core:ui` 已依赖 `core:network`（PagedState）。

## 核心机制（易踩坑）

- **i18n**：全项目用户可见文案必须走各模块 `res/values/strings.xml`（默认中文）+ `res/values-en/`（英文），Compose 用 `stringResource(R.string.x)`；ViewModel 的通知/error 发 `UiMessage(@StringRes, args)`（core:common），UI 侧 `LaunchedEffect{ message.collect{ msg -> context.getString(msg.res, *msg.args.toTypedArray()) } }` 解析后交给 `NotificationHostState.show(...)`（**LaunchedEffect 内不可调 stringResource**）。语言中性 token（AI/`xP`/`#tag`/`+N`）保留内联。应用内语言切换：`UserPreferences.appLanguage`(system/zh/en) + `MainActivity.attachBaseContext` 同步读（`core:datastore.readAppLanguageSync`）→ `createConfigurationContext` 覆盖 + `Locale.setDefault` + `PixivLang.code`（lib:pixivapi 网络语言头/`lang` 参数动态化）；**我的页外观设置内嵌语言卡**（切换后 `activity.recreate()` 生效，MeRoute 内直接 `(LocalContext.current as? Activity)?.recreate()`，无独立设置页）。**注意**：`@ApplicationContext.getString` 跟随系统语言而非应用内覆盖（应用上下文未重建），VM 持续展示型文案（如下载进度）用 context.getString 时仅在应用内切换语言后偶发语言不一致——可接受。`formatCount/formatCountForNovel` 已 locale-aware（zh 万/亿，en K/M/B），纯函数默认 `Locale.getDefault()`。
- **图片 URL 必须走 `PixivRepository.imageClient`**（自动 Referer，否则 403）。Coil 由 `PixivApp` 注入该 client，`PixivImage`/`AsyncImage` 自动带。
- **org.json 是 Android 内置类**：本地 JVM 单测（testDebugUnitTest）不可用，需 `testImplementation("org.json:json:20240303")`（core:novel 已配）。
- **Gson 经 lib:pixivapi 传递**，feature 层可直接 `Gson()`（如历史 payloadJson）。
- **数据库**：`core/database` `PixivDatabase` **version=3**——历史迁移（原 v1~v7 六条）已全部清理，新装直接按当前 schema 建库；现存迁移 `MIGRATION_1_2`（download_entry+payloadJson）、`MIGRATION_2_3`（主键扩为 targetType+targetId+format+scopeKey）。后续加实体/字段必须升 version 并接续写新 Migration（改主键需重建表搬迁数据，列定义须与实体逐列一致，Room 启动时校验 schema）。DAO 模式：`deleteByX 先删旧再 upsert` 去重置顶（BrowseHistory/SearchHistory）；小说下载条目按 scopeKey 区分单本（""）/整系列（"series"）/部分分册（"partial"），派生函数 `novelScopeKey`。
- **历史/下载快照完整性**：`BrowseHistoryEntity.payloadJson` 存完整卡片数据（历史插画宽高、小说作者等），否则通用组件信息不全/图片裁剪中间。
- **插画完整显示**：`IllustCard` 按 `illust.width/height` aspectRatio 显示；无宽高会固定高度 + Crop 裁剪中间——数据源需带宽高（历史/下载实体存 width/height）。
- **离线小说缓存已移除**：原 `OfflineNovelRepository`（core:network，`filesDir/offline` + `NovelDocumentCodec`）已删除，阅读器**无离线优先逻辑**（在线小说直连网络，每次重新拉取+解析）；`PixivApp` 启动时会清理旧版 offline 缓存目录。`NovelDocumentCodec` 现存于 core:novel，仅测试使用。
- **本地 TXT/EPUB/MD 阅读**：`TxtNovelParser`/`EpubNovelParser`/`MarkdownNovelParser`（core:novel）→ `LocalReaderStore.set` → `local_reader/{novelId}` 路由（`ReaderRoute(localDocument=...)` → `ReaderViewModel.useLocalDocument`）。
- **主题**：`core/ui/theme/PixivReaderTheme(darkTheme, dynamicColor)`；`UserPreferences.themeMode`(0 跟随/1 浅色/2 深色) 由 `MainActivity` 收集生效。

## 通用组件（core:ui，优先复用）

`IllustCard`（瀑布流卡，含收藏按钮/AI/页码）、`IllustWaterfallGrid`、`NovelCard` + `NovelCardData`（小说通用卡）、`UserAvatar`（URL null 首字母圆）、`SettingsCard` + `SettingsCardItem`（数据驱动设置卡）、`ProfileHeader`、`CreatorProfileCard`、`CommentInput`、`AdaptiveContentBox`（平板限宽）、`AdaptiveNavScaffold`、`NotificationHost` + `rememberNotificationHostState`（**自定义 Material3 风格通知，替代 Snackbar**：`inverseSurface` 深底胶囊 + 类型图标徽标 Info/Success/Error + 关闭按钮，底部滑入淡入动画、2.6s 自动消失、新消息顶替；用法 `val s = rememberNotificationHostState()` → `Scaffold(snackbarHost = { NotificationHost(s) })` → `s.show(text, type = NotificationType.Success)`）。

## 导航约定

- 顶层路由在 `app/.../PixivNavGraph.kt`（常量 `ROUTE_*`）；底部 Tab 在内层 `MainShell`。
- **内层 Tab 无法直达顶层路由**——用回调链 `PixivNavGraph → MainShell → feature`（如 `onOpenUser`/`onOpenReader`）。
- 跨 Tab 传搜索词：`main?search={search}` + `MainShell.initialSearch/pendingSearch` + `DiscoverRoute.initialQuery`。
- 新增全屏页 = 顶层路由 + 常量 + `PixivNavGraph` 接线。

## 文件修改规则（重要）

- **txt/md/json/yaml/xml/csv 等文本文件禁止 shell 改写**，用 edit/write 工具。
- 代码注释用中文；命名按 `数据层 / 状态 / UI` 分包。
- 改文件前先 read；不整文件重写。

## Git

- 提交前自查：`:app:compileDebugKotlin` + 相关单测。
- **`git add` 与 `git commit` 必须分开执行**（同一命令链会争 `index.lock`）；提交信息用 `feat:` 中文，覆盖本轮改动。

## 参考

- **`agent.md`**：开发轮次完整记录（P0~P7、至第六十五轮 + 各补充），改代码前先看它了解历史决策与既有机制，避免重复踩坑/重复实现。
