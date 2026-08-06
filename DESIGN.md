# Pixiv Reader 设计规范（Design Spec）

> 本文件为全项目 UI 设计的唯一权威规范。源码 Token 定义在 `core/ui/theme/`，模式枚举在 `core/common/AppModes.kt`，HTML 设计预览在 `design/`。
> 新增 UI 一律引用本规范 Token，**禁止在业务代码散落裸色值 / 裸字号 / `999.dp` / `RoundedCornerShape(50)` 等 magic number**（历史迁移记录见 agent.md 第六十三轮）。

---

## 1. 设计语言

- **Material 3**（Compose Material3，BOM 2024.09.00）
- 静态配色由种子色 **#0096FA**（Pixiv 品牌蓝）派生；**API 31+ 默认启用动态取色**（`dynamicColor=true`，回退静态色板）
- 全圆角/胶囊统一：`AppShapes.pill`（percent=50），**禁止 `999.dp` / `RoundedCornerShape(50)` 两种写法**
- 图标：一律 `Icons.Filled.*` / `Icons.AutoMirrored.Filled.*`（material-icons-extended），不新增自绘 svg/vector 资源
- 布局断点：`screenWidthDp ≥ 600` 视为平板（详情页双栏、列表限宽等以 `MAX_CONTENT_WIDTH_DP = 760` 为内容上限）

---

## 2. 色板（`core/ui/theme/Color.kt`）

### 2.1 静态 M3 色板（种子 #0096FA；动态取色不可用时回退）

| Token | 值 | Token | 值 |
|---|---|---|---|
| `Primary` | `#00639B` | `OnPrimary` | `#FFFFFF` |
| `PrimaryContainer` | `#CDE5FF` | `OnPrimaryContainer` | `#001D33` |
| `Secondary` | `#52606F` | `SecondaryContainer` | `#D6E3F2` |
| `OnSecondaryContainer` | `#0E1D29` | `Tertiary` | `#66558B` |
| `TertiaryContainer` | `#EBDCFF` | `Error` | `#BA1A1A` |
| `ErrorContainer` | `#FFDAD6` | `Surface` / `Background` | `#FDFCFF` |
| `OnSurface` | `#1A1C1E` | `SurfaceVariant` | `#DFE2EB` |
| `OnSurfaceVariant` | `#42474D` | `Outline` | `#73777E` |
| `OutlineVariant` | `#C3C7CF` | `SurfaceContainerLow` | `#F7F9FB` |
| `SurfaceContainer` | `#F0F4F8` | `SurfaceContainerHigh` | `#E4EAF0` |

深色模式在 `Theme.kt` 中按 M3 惯例互换 primary/primaryContainer 等。

### 2.2 语义色（跨模块统一，业务代码用 Token 而非裸色）

| Token | 值 | 用途 |
|---|---|---|
| `ViewerScrim` | `#0A0A0A` | 全屏查看器/图片查看黑底 |
| `FavoriteRed` | `#FF5252` | 收藏/爱心选中红 |
| `SuccessGreen` | `#4CAF50` | 成功通知绿 |
| `PixivBlue` | `#0096FA` | Pixiv 品牌蓝（登录/launcher） |

### 2.3 小说阅读器 4 主题调色板（`Reader*`，源 feature/reader 实际渲染色值）

| 主题 | Background | Text | Secondary | Divider | TopBar |
|---|---|---|---|---|---|
| DAY | `#FFFFFF` | `#1A1A1A` | `#8A8A8A` | `#E5E5E5` | `#FAFAFA` |
| PAPER | `#F5EFE0` | `#3A3126` | `#8A7A60` | `#E2D9C4` | `#EDE4CF` |
| NIGHT | `#212121` | `#CFCFCF` | `#8A8A8A` | `#3A3A3A` | `#1C1C1C` |
| DEEP_BLACK | `#000000` | `#9E9E9E` | `#555555` | `#202020` | `#000000` |

---

## 3. 字体（`core/ui/theme/Type.kt`）

统一 `Typography` 档位（Material 3 语义），业务代码**以档位为基础派生，禁止直接写裸字号**：

| 档位 | 字号 | 字重 | 用途 |
|---|---|---|---|
| `displaySmall` | 30sp | Bold | 大标题/空态插画 |
| `headlineMedium` | 24sp | SemiBold | 页面主标题 |
| `titleLarge` | 20sp | SemiBold | 卡片标题/详情标题基座 |
| `titleMedium` | 16sp | Medium | 区块标题/按钮文字 |
| `bodyLarge` | 16sp | Normal | 强调正文 |
| `bodyMedium` | 14sp | Normal | 默认正文 |
| `labelMedium` | 12sp | Medium | 小标签/辅助信息 |

### 派生样式约定（页面级）

需要微调字号/字重时，**用 `@Composable` 函数基于 Typography 派生**（集中定义、无散落 magic number）。示例（小说详情页 `NovelDetailRoute.kt`）：

```kotlin
@Composable
private fun novelTitleStyle() = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold)
@Composable
private fun novelMetaStyle() = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
```

已在详情页落地的派生函数（`novel*Style()`）：`Title/Author/Meta/StatValue/SmallLabel/Intro/ReadButton/TocTitle/TocRow/CurrentBadge/CountBadge/OptionTitle`。新页面仿此命名。

---

## 4. 间距（`core/ui/theme/Dimens.kt` → `Spacing`）

| Token | 值 | 用途 |
|---|---|---|
| `Spacing.xs` | 4dp | 元素内微间距 |
| `Spacing.sm` | 8dp | 紧凑间距/图标与文字间隙 |
| `Spacing.md` | 12dp | 默认元素间距 |
| `Spacing.lg` | 16dp | 卡片内边距/页面级间距 |
| `Spacing.xl` | 24dp | 区块级大间距 |
| `Spacing.pagePadding` | 16dp(=lg) | 页面统一外边距 |

---

## 5. 形状（`core/ui/theme/Shapes.kt` → `AppShapes`）

| Token | 值 | 用途 |
|---|---|---|
| `AppShapes.small` | 8dp 圆角 | 小控件/小卡片角 |
| `AppShapes.card` | 12dp 圆角 | **默认卡片角** |
| `AppShapes.large` | 16dp 圆角 | 大卡片/弹层角 |
| `AppShapes.pill` | 50% 全圆 | 药丸/胶囊按钮（**全圆唯一写法**） |
| `AppShapes.circle` | 正圆 | 头像/圆形按钮 |

---

## 6. 时长（`core/ui/theme/Durations.kt` → `Durations`）

| Token | 值 | 用途 |
|---|---|---|
| `NOTIFICATION_TIMEOUT` | 2600ms | 通知自动消失 |
| `PAGE_SWITCH_ANIM_MS` | 700ms | 页面切换动画 |
| `READER_BAR_HIDE_MS` | 3000ms | 阅读器 UI 淡出延迟 |
| `READER_UI_DELAY_MS` | 800ms | 阅读器进入延迟 |

---

## 7. 主题机制（`core/ui/theme/Theme.kt`）

- `PixivReaderTheme(darkTheme, dynamicColor=true)`：API 31+ 走 `dynamicLight/DarkColorScheme`，否则静态 `LightColors/DarkColors`
- 应用内主题：`UserPreferences.themeMode`（`ThemeMode.FOLLOW_SYSTEM/LIGHT/DARK`，`core/common/AppModes.kt`），由 `MainActivity` 收集生效
- 应用内语言：`UserPreferences.appLanguage`（system/zh/en）+ `MainActivity.attachBaseContext` 同步读
- **HTML 原型为浅色基准**；真机深色/动态色模式下配色跟随系统（设计如此，非 bug）

---

## 8. 模式枚举（`core/common/AppModes.kt`）

统一裸 int 模式值；存储值即 `value`，反序列化走 `from()`（非法回退默认）：

| 枚举 | 值 | 语义 |
|---|---|---|
| `ThemeMode` | 0/1/2 | 跟随系统/浅色/深色 |
| `ViewerOrientation` | 0/1/2 | 横向翻页/竖向翻页/无缝竖向 |
| `NovelDefaultTab` | 0/1 | 推荐/关注 |
| `ReaderPageMode` | 0/1/2 | 滑动/翻页/仿真 |
| `ReaderThemeMode` | 0/1/2/3 | 日间/纸张/夜间/深黑 |

---

## 9. 通用组件与 UI 模式约定

| 模式 | 规范 |
|---|---|
| **收藏按钮（IllustCard 浮层）** | 自绘 `Box`：`align(TopEnd).padding(8).size(28.dp).clip(CircleShape).background(Black 0.35f)`，`contentAlignment=Center`，Icon 18dp；选中 `FavoriteRed` / 未选 `White`；**禁用 `IconButton`**（40dp 交互区会溢出） |
| **评论回复胶囊（feature:comments `ReplyPill`）** | `clip(AppShapes.pill)` + `background(primaryContainer)` + `labelSmall SemiBold` + `primary` 字 + `padding(12×4)` |
| **竖排操作按钮（详情页 `VerticalActionButton`）** | icon 上 / label 下，52dp 高，`AppShapes.card` 圆角，`surfaceContainerLow`+outline 边框；激活态 `primaryContainer`+primary 边框；disabled `alpha 0.45` |
| **标签胶囊** | `clip(AppShapes.pill)` + `secondaryContainer` 底 + `primary` 字，`labelSmall` |
| **子评论** | 父评论下浅色块（`surfaceContainerLow` + 圆角 12 + 缩进）树形；默认显示 ≤3 条，超出「查看全部 %d 条回复」展开（无收起） |
| **序号徽标（目录行）** | 28dp、圆角 9、当前项 `primary` 底/其余 `secondaryContainer` 底、两位数字 |
| **沉浸式封面 banner** | 全宽背景图 + 底部 110dp 渐变过渡到 `surface`；**无视差**；返回钮为 40dp 黑 35% 半透明圆底 |
| **统计行** | icon(16dp primary) + 值 + 标签，三块 `weight(1f)` 撑满整行 |
| **通知** | `NotificationHost`（inverseSurface 胶囊 + 类型徽标 + 2.6s 自动消失），替代 Snackbar |

---

## 10. 文案与 i18n

- 用户可见文案走各模块 `res/values/strings.xml`（默认中文）+ `res/values-en/`（英文）
- Compose 用 `stringResource(R.string.x)`；ViewModel 通知/error 发 `UiMessage(@StringRes, args)`（core:common），UI 侧 `LaunchedEffect` collect 后 `context.getString` 解析
- 语言中性 token（AI/`xP`/`#tag`/`+N`）保留内联
- `formatCount/formatCountForNovel` locale-aware（zh 万/亿，en K/M/B）

---

## 11. 设计原型（`design/`）

- `design/*.html`：自包含 HTML 设计预览（内联 CSS + 内联 SVG 图标，M3 色板与 Color.kt 对齐）
- `design/novel-detail-ui.html`：小说详情页原型（手机/平板双画框、详情/评论双视图）——详情页实现的基准
- `design/user-profile-design.html`（根目录）：用户主页原型
- 新页面优先产出 HTML 原型评审，再落地 Compose；原型色板以 Color.kt 为准（勿照抄其它 html 的派生色）
