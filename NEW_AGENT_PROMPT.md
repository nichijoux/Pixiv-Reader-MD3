# 新会话 Prompt —— Pixiv Reader 项目速览

> 用法：新开对话后，把下方整段（或引用本文件路径 `NEW_AGENT_PROMPT.md`）作为第一条消息发给 agent。

---

你是一个 Android (Kotlin + Jetpack Compose + Hilt + Room) 项目的开发 agent，工作目录 F:\pixiv-mateiral3（Windows，命令行构建，无 Android Studio）。

## 开工前必读（按顺序，务必先读完再动手）
1. `AGENTS.md` —— 构建/测试命令、模块架构与依赖方向硬约束、核心机制（i18n/图片 URL/数据库/主题/导航）、文件修改规则、Git 规则
2. `agent.md` —— 开发轮次完整记录（P0~P7、至第六十五轮 + 各补充，文末「文档更正记录」对齐代码现状），含历史决策与踩坑，改代码前先看避免重复踩坑
3. `design.md` —— 设计规范唯一权威：色板/字体 Typography/间距 Spacing/形状 AppShapes/时长 Durations/模式枚举 AppModes/通用组件约定；**新增 UI 一律引用 Token，禁止散落 magic number**
4. `design/*.html` —— HTML 设计原型（`design/novel-detail-ui.html` 为小说详情页基准；新页面先出原型再实现）

## 关键约束（速记）
- 架构：`app → feature/* → core/ui → core/network → core/database·datastore·model → core/common`，**feature 之间禁止互相依赖**，共享逻辑放 core 层
- 新增 feature ViewModel 需在 build.gradle 配 hilt/ksp + `api(project(":core:network"))` + `api(libs.hilt.android)` + `ksp(libs.hilt.compiler)` + `implementation(libs.hilt.navigation.compose)`
- 图片 URL 必须走 `PixivRepository.imageClient`（自动 Referer）；Coil 由 PixivApp 注入
- 用户可见文案必须走各模块 `res/values/strings.xml`（中文）+ `res/values-en/`（英文）；ViewModel 通知走 `UiMessage(@StringRes, args)`
- `lib:pixivapi` 是 vendor 副本（`pixiv-api-kotlin/` 只读勿改），改 API 只在 lib 下
- 数据库 `PixivDatabase` version=1（最终结构，历史迁移已清理），加实体/字段必须升版本 + 写 Migration
- 模式值用 `core/common/AppModes.kt` 枚举（`value`+`from()`，存储值不变零迁移）
- 收藏按钮用自绘 Box 浮层（28dp 容器 + 18dp 爱心），禁用 IconButton

## 构建与测试（每次改代码后先编译）
```powershell
$env:JAVA_HOME = "C:\Users\nichijoux\.jdks\jbr-21.0.11"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& .\gradlew.bat :app:compileDebugKotlin --console=plain   # 快速编译
# 单测：:core:novel :feature:reader :core:network :feature:novel :feature:user :core:common（涉及模块跑）
```

## 当前状态（截至 2026-08，第六十五轮 + 文档更正）
- 最近已提交：`d6d7e14`（小说导出引擎升级：导出前统一格式化 + OpenCC 繁转简 + DOCX/EPUB 样式 + 默认输出系统 Download/PixivReader）、`d3753dd`（底栏空白带/通知避让/作者关注胶囊）、`bc1256a`（下载体验优化）
- **工作区未提交**（以 `git status` 为准）：`.omp/rules/workspace-scope.md`（TTSR 工作区文件范围规则，未跟踪）
- 最近完成的机制（可复用）：通用评论页 `feature:comments`（路由 `comments/{type}/{targetId}`，PagedState 分页 + 子回复按需 `getCommentReplies` + 最多 3 条展开 + 子评论可回复）；小说详情 `NovelDetailRoute.kt`（对齐 HTML 原型：竖排按钮/无视差 banner/简介首行缩进+展开/平板左目录固定）；小说导出 `NovelExportWorker`（TXT/EPUB/PDF/MD/DOCX，单本/系列/部分分册，断点续传）
- 文档已与代码对齐（数据库 v7、离线小说缓存已移除、排行榜 = 漫画 5 段 + 小说 6 段），详见 agent.md「文档更正记录」

## 工作方式
- 改文件前先 read；不整文件重写（结构性迁移除外）
- 大改动先给出计划、经用户确认再实施
- 提交前自查 `:app:compileDebugKotlin` + 相关单测；`git add` 与 `git commit` 必须分开执行；提交信息 `feat:` 中文覆盖本轮改动
