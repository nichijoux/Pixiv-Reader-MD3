# AGENTS.md

Pixiv Reader — Android (Kotlin + Jetpack Compose + Hilt + Room) 客户端。Windows 环境、命令行构建、无 Android Studio。

## 构建与测试（必须用此 JDK）

```powershell
$env:JAVA_HOME = "C:\Users\nichijoux\.jdks\jbr-21.0.11"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& .\gradlew.bat :app:compileDebugKotlin --console=plain   # 快速编译（推荐验证手段）
# 单测（改动涉及模块时跑，core:novel 24 用例 + feature:reader/network/novel/user/common）
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
- **`lib:pixivapi` 是 vendor 副本**：`pixiv-api-kotlin/` 只读勿改；改 API 只能在 `lib/pixivapi/`。所有 `com.example.pixivapi.*` import 解析到 lib 副本。
- `feature/download` 模块是空壳（未使用）；下载管理实现在 `feature:user`。

## 核心机制（易踩坑）

- **图片 URL 必须走 `PixivRepository.imageClient`**（自动 Referer，否则 403）。Coil 由 `PixivApp` 注入该 client，`PixivImage`/`AsyncImage` 自动带。
- **org.json 是 Android 内置类**：本地 JVM 单测（testDebugUnitTest）不可用，需 `testImplementation("org.json:json:20240303")`（core:novel 已配）。
- **Gson 经 lib:pixivapi 传递**，feature 层可直接 `Gson()`（如历史 payloadJson）。
- **数据库**：`core/database` `PixivDatabase` **version=3**，加实体/字段必须升 version + 写 Migration（见 `MIGRATION_1_2`/`MIGRATION_2_3`），`fallbackToDestructiveMigration()` 兜底。DAO 模式：`deleteByX 先删旧再 upsert` 去重置顶（BrowseHistory/SearchHistory）。
- **历史/下载快照完整性**：`BrowseHistoryEntity.payloadJson` 存完整卡片数据（历史插画宽高、小说作者等），否则通用组件信息不全/图片裁剪中间。
- **插画完整显示**：`IllustCard` 按 `illust.width/height` aspectRatio 显示；无宽高会固定高度 + Crop 裁剪中间——数据源需带宽高（历史/下载实体存 width/height）。
- **离线阅读**：`OfflineNovelRepository`（core:network）存解析后 `NovelDocument` JSON（`NovelDocumentCodec`，org.json）；阅读器离线优先（`ReaderViewModel.load` 先查 exists）。
- **本地 TXT/EPUB 阅读**：`TxtNovelParser`/`EpubNovelParser`（core:novel）→ `LocalReaderStore.set` → `local_reader/{novelId}` 路由（`ReaderRoute(localDocument=...)` → `ReaderViewModel.useLocalDocument`）。
- **主题**：`core/ui/theme/PixivReaderTheme(darkTheme, dynamicColor)`；`UserPreferences.themeMode`(0 跟随/1 浅色/2 深色) 由 `MainActivity` 收集生效。

## 通用组件（core:ui，优先复用）

`IllustCard`（瀑布流卡，含收藏按钮/AI/页码）、`IllustWaterfallGrid`、`NovelCard` + `NovelCardData`（小说通用卡）、`UserAvatar`（URL null 首字母圆）、`SettingsCard` + `SettingsCardItem`（数据驱动设置卡）、`ProfileHeader`、`CreatorProfileCard`、`CommentInput`、`AdaptiveContentBox`（平板限宽）、`AdaptiveNavScaffold`。

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

- **`agent.md`**：开发轮次完整记录（P0~P6、第 47 轮 + 各补充），改代码前先看它了解历史决策与既有机制，避免重复踩坑/重复实现。
