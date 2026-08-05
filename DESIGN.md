# 设计风格规范 — Pixiv Reader

> 本文件把项目代码里的视觉决策提炼成一份「设计语言文档」，供设计与开发对齐。
> 它是 **现状描述**（代码怎么写的），不是提案；改 UI 时请同步更新本文件。
> 配套阅读：`CODEFLOW.md`（流程）、`AGENTS.md`（规则）、`core/ui/.../theme/`（主题源码）。

---

## 1. 设计原则

1. **Material 3 为骨架，动态取色优先**：默认走系统壁纸动态取色（Android 12+），不可用时回退静态色板；夜间模式容器色互换。
2. **沉浸式优先**：详情/阅读器/查看器三类「内容消费页」让内容吃满屏幕，工具栏作浮层悬浮其上，不挤压正文。
3. **平板不撑满**：宽屏内容限宽居中（760dp）、双栏分屏（3:1）、左侧导航轨，避免阅读行宽过长。
4. **数据驱动卡片化**：列表项统一卡片化（插画瀑布流 / 小说横卡 / 设置卡），用 `surfaceContainer` 区分层级。
5. **图片中性浮层**：图片上的角标/按钮一律黑底（45% 透明）+ 白字，不抢图片本身色彩。
6. **单字体系，可导入**：UI 用系统默认字体；阅读器单独支持用户导入 ttf/otf 自定义字体。

---

## 2. 色彩体系

### 2.1 主色板（种子色 `#0096FA` 派生，`core/ui/.../theme/Color.kt`）

| 语义 | 浅色 | 用途 |
|---|---|---|
| Primary | `#00639B` | 主操作、选中态、链接、收藏图标 |
| PrimaryContainer | `#CDE5FF` | 主色容器背景 |
| Secondary | `#52606F` | 次级操作 |
| SecondaryContainer | `#D6E3F2` | 标签 chip 背景 |
| Tertiary | `#66558B` | 第三色（追更等） |
| Error | `#BA1A1A` | 错误文案 |
| Background/Surface | `#FDFCFF` | 页面底色 |
| SurfaceContainer | `#F0F4F8` | 卡片底色（插画卡/小说卡/设置卡） |
| SurfaceContainerHigh | `#E4EAF0` | 封面占位、次级卡片 |
| SurfaceContainerLow | `#F7F9FB` | 评论子回复块底色 |
| OutlineVariant | `#C3C7CF` | 分隔线 |

### 2.2 深色模式（`Theme.kt`）
`DarkColors` 把 Primary 与 PrimaryContainer、Secondary 与 SecondaryContainer 互换（容器色当主色、主色当容器），其余沿用浅色定义。动态取色开启时不使用这两套静态色。

### 2.3 主题切换逻辑
`PixivReaderTheme(darkTheme, dynamicColor)` 三分支：
1. `dynamicColor && SDK >= S` → `dynamicLight/DarkColorScheme(context)`（系统壁纸取色）
2. `darkTheme` → `DarkColors`
3. 否则 → `LightColors`

`darkTheme` 由 `MainActivity` 据 `UserPreferences.themeMode`（0 跟随系统 / 1 浅 / 2 深）决定。

### 2.4 图片浮层色（跨页统一，不随主题变）
| 用途 | 色值 |
|---|---|
| 角标底（AI/页码/收藏数） | `Color.Black.copy(alpha = 0.45f)` |
| 收藏按钮底（卡片右上） | `Color.Black.copy(alpha = 0.35f)` |
| 收藏激活红 | `Color(0xFFFF5252)` |
| 渐变遮罩（封面底/海报底） | ` Transparent → Black 70%` |
| 文字 | `Color.White` |

### 2.5 阅读器专用色（4 套，`Color.kt`）
阅读器不跟随全局主题，独立选色：
| 主题 | 背景 | 正文 |
|---|---|---|
| 日间（theme=0） | 跟随全局 surface | 跟随 onSurface |
| 纸张（theme=1，默认） | `#F6F1E7` | `#3B3328` |
| 夜间（theme=2） | `#2E3B32` | `#CFE3D2` |
| 深黑（theme=3） | `#121212` | `#AAAAAA` |
`followSystem` 开启时按系统深色自动在「纸张/夜间」间切换。

---

## 3. 字体体系（`core/ui/.../theme/Type.kt`）

全 UI 用 `FontFamily.Default`（系统字体，无自定义字族注入）。7 级 Typography：

| 角色 | 字号 | 行高 | 字重 | 用途 |
|---|---|---|---|---|
| displaySmall | 30 | 38 | Bold | 极少用 |
| headlineMedium | 24 | 32 | SemiBold | 极少用 |
| titleLarge | 20 | 28 | SemiBold | 页面标题、小说标题、TopAppBar 标题 |
| titleMedium | 16 | 24 | Medium | 区块标题、小说卡标题 |
| bodyLarge | 16 | 24 | Normal | 正文 |
| bodyMedium | 14 | 20 | Normal | 次级正文、评论、列表描述 |
| labelMedium | 12 | 16 | Medium | 角标、标签、统计标签、元信息 |
| labelSmall | 11 | 16 | Medium | 子回复、日期、极小角标 |

**阅读器正文**不使用 Typography，由 `ReaderViewModel` 的 `fontSize/lineHeight/fontFamily` 偏好动态构造 `TextStyle`，默认 17sp / 行高 2.05x / serif，用户可调字号 12–30sp、行距 1.0–3.0x、字族 serif/sans/mono 或导入 ttf/otf。

---

## 4. 圆角体系

| 元素 | 圆角 | 出处 |
|---|---|---|
| 插画卡（IllustCard） | 14dp | `RoundedCornerShape(14.dp)` |
| 小说卡（NovelCard） | 16dp（Card 整体） / 12dp（封面） | Material Card 默认 + 封面 |
| 封面/图片块 | 12dp | NovelCard 封面、NovelCenteredBox |
| 浮层角标 | 8dp | AI 标、页码、收藏数 |
| 标签 chip | 12dp（详情标签）/ 8dp（小说卡标签） | 两处略有差异 |
| 圆形按钮/头像 | CircleShape | 收藏按钮、返回按钮、头像 |
| 评论子回复块 | 12dp | surfaceContainerLow 块 |
| 设置卡、屏蔽卡 | Material Card 默认 | 16dp |

**约定**：卡片 14–16dp，封面 12dp，角标 8dp，chip 8–12dp。新增组件沿用。

---

## 5. 间距体系

| 场景 | 值 |
|---|---|
| 页面左右边距 | 16dp |
| 卡片内边距 | 10dp（插画卡）/ 14dp（小说卡） |
| 列表项间距 | 10–12dp（`Arrangement.spacedBy`） |
| 组件内小间距 | 6 / 8dp |
| 状态盒子留白 | 24dp |
| 标签内边距 | horizontal 8–10 / vertical 3–4 |
| 浮层角标距边 | 6–8dp |

**内容限宽**：`MAX_CONTENT_WIDTH_DP = 760`（约 46rem），平板上正文/列表在 `AdaptiveContentBox` 内限宽居中，两侧留白。

---

## 6. 卡片体系

### 6.1 IllustCard（瀑布流竖卡）
- 结构：`Column + clip(14dp) + surfaceContainer`（**非 Material Card**，更轻、无阴影）
- 封面区：按 `width/height` `aspectRatio` 完整显示（无宽高回退固定高度 + Crop 中间）
- 浮层：左上 AI/页码、右上收藏按钮（24dp 圆 + 黑 35% 底）、右下收藏数角标
- 信息区：标题 2 行省略 + 20dp 头像 + 作者名

### 6.2 NovelCard（横向卡）
- 结构：Material `Card + surfaceContainer + 16dp`
- 左封面：104dp 宽 + `aspectRatio(3/4)` 书籍比 + 12dp 圆角 + 底部黑渐变 + 收藏数/字数白字
- 右信息：`titleMedium` 标题 + 36dp 收藏 IconButton + 系列 + 28dp 头像作者行 + 标签 FlowRow（最多 3 + "+N"）

### 6.3 SettingsCard（数据驱动设置卡）
- Material Card + `SettingsCardItem(icon, title, description, trailingIcon, onClick)` 数据行

### 6.4 CreatorProfileCard（用户/创作者卡）
- 头像 + 名称 + 简介 + 操作，用于历史/屏蔽

### 6.5 屏蔽页卡（BlockedRoute）
- Material 卡片分组 + pill 标签（`FilledIconButton` ? 删除）

---

## 7. 按钮体系

| 类型 | 尺寸 | 用途 |
|---|---|---|
| `Button`（填充） | 高 44dp | 主操作：开始阅读、重试、全屏查看 |
| `OutlinedButton`（描边） | 高 40dp | 次操作：收藏/追更/下载（一行三个 `weight(1f)`） |
| `IconButton`（方形） | 默认 | 顶栏 MoreVert/Refresh、底栏目录/搜索/设置 |
| 圆形 `IconButton`（沉浸式） | 24dp（卡片收藏）/ 40dp（详情返回）/ 52dp（Viewer 底部操作条） | 黑底 35–45% + 白 icon |

**Viewer 底部圆形操作条**（项目标志设计）：4 个 52dp 圆形按钮，黑底 45% + 白 icon，收藏激活变红 `#FF5252`，原图选中高亮 28% 白底。与顶部对称的底部渐变。

---

## 8. 沉浸式设计模式（项目特色）

### 8.1 NovelDetail 视差 banner
- banner 高 280dp，图片实际高 `280 + 160dp`（余量）
- `graphicsLayer.translationY = scrollOffset * 0.45f`：上滑时封面移动慢于列表 → 视差
- 底部 110dp `verticalGradient(Transparent → surface)` 过渡到正文背景
- 返回按钮浮层：40dp 圆 + 黑 35% 底 + 白 ArrowBack，重叠在 banner 上

### 8.2 Reader 工具栏浮层
- 正文始终全屏，顶/底工具栏作 `Box` 浮层（`if (barsVisible)`）
- 顶栏：`Box + themeColors.topBar` 实色覆盖状态栏区，内层 `TopAppBar.statusBarsPadding()`
- 底栏：同上覆盖导航栏区，`ReaderToolBar` 三按钮（目录/搜索/设置）
- 工具栏显隐：中间 1/3 透明覆盖层点击切换（滑动模式用顶部 28dp 窄条）

### 8.3 IllustDetail 双栏（平板）
- `WindowSizeClass != Compact` → `TwoPaneContent`
- 左栏 `weight(3f)` 可滚动（图+作者+相关），右栏 `weight(1f).widthIn(max=420dp)` 评论 + 输入框固底
- `VerticalDivider(thickness=1dp, outlineVariant)` 分隔

---

## 9. 头像体系

| 场景 | 尺寸 |
|---|---|
| IllustCard 作者 | 20dp |
| NovelCard 作者 | 28dp |
| NovelDetail 评论（父） | 36dp |
| NovelDetail 子回复 | 28dp |
| IllustDetail 评论（父） | 28dp |
| IllustDetail 子回复 | 24dp |
| ProfileHeader（用户主页） | 大头像 |

URL 为 null 时用**首字母圆**兜底（`UserAvatar` 组件）。

---

## 10. 标签 chip 体系

| 位置 | 圆角 | 背景 | 文字色 | 内边距 |
|---|---|---|---|---|
| 详情标签（Novel/Illust） | 12dp | secondaryContainer | primary | 10×4 |
| NovelCard 标签 | 8dp | secondaryContainer | onSecondaryContainer | 8×3 |
| 屏蔽 pill | — | surfaceContainer 卡 + FilledIconButton | — | 卡片 12dp |
| 搜索历史胶囊 | 圆角 | surface | onSurface | 12×6 |

前缀：详情标签用 `#tag`，小说卡标签用 `#tag`。

---

## 11. 状态展示（`core/ui/.../component/StatusViews.kt`）

| 组件 | 视觉 |
|---|---|
| LoadingBox | 全屏居中 `CircularProgressIndicator` |
| ErrorBox | 居中 `error` 色文本 + `Button`（Refresh icon + "重试"，24dp padding） |
| EmptyBox | 居中 `onSurfaceVariant` 次级色文本（24dp padding） |
| 触底加载 | 24dp 小转圈 或 "上滑加载更多" 胶囊（labelMedium + 12dp 圆角） |

列表三态约定：`when { isLoading && items.isEmpty -> LoadingBox; error != null && items.isEmpty -> ErrorBox; items.isEmpty -> EmptyBox; else -> 列表 }`。

---

## 12. 导航与栏

### 12.1 自适应导航（`AdaptiveNavScaffold`）
- Compact（<600dp）：底部 `NavigationBar`
- Medium/Expanded（≥600dp 用 rail，≥840 正进入 Expanded）：左侧 84dp `NavigationRail`
- 5 Tab：首页 / 发现 / 排行 / 小说 / 我的（Material Icons）

### 12.2 TopAppBar
- 常规页：`surface` 容器色 + 标题 + 返回 ArrowBack + MoreVert 菜单（下载/收藏/举报）
- 沉浸式页：无 TopAppBar，用浮层 + 实色 Box 覆盖状态栏

---

## 13. 动效

| 场景 | 动画 |
|---|---|
| 翻页/卷页 | `tween(250–300ms)` |
| 多 P Pager 高度自适应 | `animateDpAsState tween(250)` |
| 视差 banner | `graphicsLayer.translationY` 实时（无动画） |
| 收藏按钮 | 状态切换瞬时（无动画），图标 Favorite↔FavoriteBorder |
| Tab 切换 | NavigationBar 默认 |

整体**克制动效**：只在翻页/尺寸变化用 tween，状态切换多为瞬时。

---

## 14. 评论树形结构

- 父评论：头像（36/28dp）+ 名称 + 日期（`labelSmall` 次级色）+ 正文
- 子回复：`surfaceContainerLow` 浅色块 + `RoundedCornerShape(12dp)` + 缩进，小头像（28/24dp），无分隔线，正文前缀"回复 @父用户名："
- 最多展示 20 条子回复

---

## 15. 组件目录（`core/ui/.../component/`）

| 组件 | 文件 | 用途 |
|---|---|---|
| PixivImage | PixivImage.kt | Coil AsyncImage 封装，url=null 渲染色块，默认 Crop |
| IllustCard | IllustCard.kt | 插画瀑布流竖卡 |
| IllustWaterfallGrid | IllustWaterfallGrid.kt | 自适应列数瀑布流 |
| NovelCard + NovelCardData | NovelCard.kt | 小说横卡 |
| UserAvatar | UserAvatar.kt | 首字母圆头像 |
| ProfileHeader | ProfileHeader.kt | 用户主页头部 |
| CreatorProfileCard | CreatorProfileCard.kt | 创作者卡 |
| SettingsCard + SettingsCardItem | SettingsCard.kt | 数据驱动设置卡 |
| CommentInput | CommentInput.kt | 评论输入框 |
| AdaptiveContentBox | AdaptiveScaffold.kt | 平板限宽 760dp 居中 |
| AdaptiveNavScaffold | AdaptiveScaffold.kt | 自适应导航壳 |
| LoadingBox/ErrorBox/EmptyBox | StatusViews.kt | 三态占位 |
| ZoomableImage | ZoomableImage.kt | 可缩放图片（查看器用） |
| RankingList | RankingList.kt | 通用排行榜（ScrollableTabRow + HorizontalPager 滑动切段 + 三态 + 触底加载，`itemContent(T, rank)`） |
| RankingRow | RankingRow.kt | 排名行（徽标 1金/2橙/3灰 + 封面 + 标题/作者/收藏） |

### 15.1 排行榜设计（漫画 Tab 已用，未来小说/插画复用）
- **入口**：漫画 Tab 顶部 banner（`tertiaryContainer` 纯色卡 + 奖杯图标块 + 标题/副文案 + 箭头），整卡点击进全屏页；TopBar 另有奖杯图标快捷入口。
- **分段**：`ScrollableTabRow`（label 走 `RankingModeInfo.labelRes`）+ `HorizontalPager` 左右滑动切换；点 Tab 平滑滑动；滑段后回调 `onModeSelect` 重载数据。
- **排名行**：徽标 28dp 斜体加粗（1 `#E8A33D` / 2 `#B45309` / 3 `#6B7280`，其余 `onSurfaceVariant`）+ 64dp 圆角封面 + 标题 2 行 + 作者 + 收藏（`ranking_bookmarks` 资源）。
- **配色**：全部 `MaterialTheme.colorScheme` 纯色，无渐变。

---

## 16. 窗口尺寸分类（`core/common/Adaptive.kt`）

| 类 | 宽度 | 行为 |
|---|---|---|
| Compact | <600dp | 手机，底部导航，单列 |
| Medium | 600–839dp | 小平板，左侧导航轨，单列限宽 |
| Expanded | ≥840dp | 平板，左侧导航轨，可双栏 |

`MAX_CONTENT_WIDTH_DP = 760` 限制内容最大宽度。

---

## 17. 图标库

全项目用 **Material Icons**（`androidx.compose.material.icons.filled.*`），无自定义 SVG：
- 收藏：`Favorite` / `FavoriteBorder`
- 追更：`Notifications` / `NotificationsNone`
- 下载：`Download`
- 返回：`ArrowBack`（AutoMirrored）
- 更多：`MoreVert`
- 刷新：`Refresh`
- 目录：`List`（AutoMirrored）
- 搜索：`Search`
- 设置：`Settings`
- 全屏：`Fullscreen`
- 壁纸：`Wallpaper`
- 原图：`HighQuality`

---

## 18. 改 UI 时的自查清单

- [ ] 颜色取自 `MaterialTheme.colorScheme`，不硬编码（图片浮层例外，用 Black/White 中性）
- [ ] 圆角按 §4 体系选值
- [ ] 列表三态（Loading/Error/Empty/列表）齐全
- [ ] 宽屏：内容是否需要 `AdaptiveContentBox` 限宽或双栏
- [ ] 沉浸式页：工具栏是否作浮层、是否处理状态栏/导航栏 padding
- [ ] 卡片：用 `surfaceContainer` 区分层级，不用阴影（项目偏平）
- [ ] 图片走 `PixivImage`（自动 Referer），null 用色块兜底
- [ ] 新增色值先加到 `Color.kt`，别散落在 feature
- [ ] tag/角标字号用 labelMedium/labelSmall，别自定义