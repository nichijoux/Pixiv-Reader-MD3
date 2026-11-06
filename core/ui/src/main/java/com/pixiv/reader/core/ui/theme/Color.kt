package com.pixiv.reader.core.ui.theme

import androidx.compose.ui.graphics.Color

// ── Material 3 静态配色（种子色 #0096FA 派生；动态取色不可用时回退） ──
// secondary 系已由 PixivReaderTheme 镜像 primary 系，静态色板不再单独定义 secondary 系 token。

val Primary = Color(0xFF00639B)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFCDE5FF)
val OnPrimaryContainer = Color(0xFF001D33)
val Tertiary = Color(0xFF66558B)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFEBDCFF)
val OnTertiaryContainer = Color(0xFF211245)
val Error = Color(0xFFBA1A1A)
val ErrorContainer = Color(0xFFFFDAD6)
val OnError = Color(0xFFFFFFFF)
val OnErrorContainer = Color(0xFF410002)

// ── 深色中性板（#0096FA 种子派生，DarkColors 专用；原缺失导致静态深色落到 M3 基线默认值）──
val DarkBackground = Color(0xFF191C1F)
val DarkOnBackground = Color(0xFFE1E2E8)
val DarkSurface = Color(0xFF191C1F)
val DarkOnSurface = Color(0xFFE1E2E8)
val DarkSurfaceVariant = Color(0xFF42474D)
val DarkOnSurfaceVariant = Color(0xFFC3C7CF)
val DarkOutline = Color(0xFF8D9199)
val DarkOutlineVariant = Color(0xFF42474D)

// ── M3 容器/反向角色（深色，由 DarkSurface #191C1F 种子派生）──
// 与浅色同理：显式定义深色容器角色，替换 M3 基线紫灰（深色 #211F26），
// 保持与 primary 蓝同相的冷调中性。注意本文件深色色板「容器与内容互换」的既有约定：
// DarkColors 里 primary = PrimaryContainer（#CDE5FF），故 surfaceTint 取该值。

val DarkSurfaceDim = Color(0xFF191C1F)
val DarkSurfaceBright = Color(0xFF34383D)
val DarkSurfaceContainerLowest = Color(0xFF131619)
val DarkSurfaceContainerLow = Color(0xFF1D2125)
val DarkSurfaceContainer = Color(0xFF21252A)
val DarkSurfaceContainerHigh = Color(0xFF2B3035)
val DarkSurfaceContainerHighest = Color(0xFF363B41)
/** surfaceTint：深色下与 primary 角色一致（= PrimaryContainer #CDE5FF） */
val DarkSurfaceTint = Color(0xFFCDE5FF)
val DarkInverseSurface = Color(0xFFE1E2E8)
val DarkInverseOnSurface = Color(0xFF2F3033)
val DarkInversePrimary = Color(0xFF00639B)

val Background = Color(0xFFFDFCFF)
val OnBackground = Color(0xFF1A1C1E)
val Surface = Color(0xFFFDFCFF)
val OnSurface = Color(0xFF1A1C1E)
val SurfaceVariant = Color(0xFFDFE2EB)
val OnSurfaceVariant = Color(0xFF42474D)
val Outline = Color(0xFF73777E)
val OutlineVariant = Color(0xFFC3C7CF)

// ── M3 容器/反向角色（浅色，由 Surface #FDFCFF 种子派生）──
// M3 容器角色此前未显式定义，全项目 72 处 surfaceContainer* 消费点实际拿到 M3 基线
// 紫灰色（浅色 #F3EDF7），与本色板的蓝调中性冲突。以下 token 以同色相低饱和蓝调微调，
// 保证卡片底/导航指示器/对话框等容器角色与 primary 蓝同相，不出现紫灰。

val SurfaceDim = Color(0xFFDCDADF)
val SurfaceBright = Color(0xFFFDFCFF)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF7F9FC)
val SurfaceContainer = Color(0xFFF1F4F9)
val SurfaceContainerHigh = Color(0xFFEBEFF5)
val SurfaceContainerHighest = Color(0xFFE5EAF1)
/** surfaceTint = Primary：M3 高亮蒙层用主色，保持与 primary 同一色相 */
val SurfaceTint = Color(0xFF00639B)
val InverseSurface = Color(0xFF2F3033)
val InverseOnSurface = Color(0xFFF1F0F4)
val InversePrimary = Color(0xFF98CBFF)

// ── 语义色（跨模块统一，禁止在业务代码写裸色值） ──

/** 全屏查看器 / 图片查看黑底 */
val ViewerScrim = Color(0xFF0A0A0A)

/** 收藏/爱心选中红 */
val FavoriteRed = Color(0xFFFF5252)

/** 成功通知绿 */
val SuccessGreen = Color(0xFF4CAF50)

/** Pixiv 品牌蓝（登录等） */
val PixivBlue = Color(0xFF0096FA)

/**
 * 设置行图标点缀色组（装饰层，不参与主题角色）：容器底 = 色值 12% alpha，图标 tint = 色值本身；
 * 色值取高亮度档保证深色卡片上图标可见（蓝/绿与 PixivBlue/SuccessGreen 同值），浅深色通用（参照 Notification accent 范式）。
 */
val SettingAccentViolet = Color(0xFF7C57FF)
val SettingAccentBlue = Color(0xFF0096FA)
val SettingAccentTeal = Color(0xFF00A5A5)
val SettingAccentGreen = Color(0xFF4CAF50)
val SettingAccentAmber = Color(0xFFFFB300)
val SettingAccentOrange = Color(0xFFFF8A3D)
val SettingAccentPink = Color(0xFFE85D8A)

// ── 小说阅读器 4 主题调色板（源：feature/reader ReaderTheme.kt 实际渲染色值） ──

val ReaderDayBackground = Color(0xFFFFFFFF)
val ReaderDayText = Color(0xFF1A1A1A)
val ReaderDaySecondary = Color(0xFF8A8A8A)
val ReaderDayDivider = Color(0xFFE5E5E5)
val ReaderDayTopBar = Color(0xFFFAFAFA)

val ReaderPaperBackground = Color(0xFFF5EFE0)
val ReaderPaperText = Color(0xFF3A3126)
val ReaderPaperSecondary = Color(0xFF8A7A60)
val ReaderPaperDivider = Color(0xFFE2D9C4)
val ReaderPaperTopBar = Color(0xFFEDE4CF)

val ReaderNightBackground = Color(0xFF212121)
val ReaderNightText = Color(0xFFCFCFCF)
val ReaderNightSecondary = Color(0xFF8A8A8A)
val ReaderNightDivider = Color(0xFF3A3A3A)
val ReaderNightTopBar = Color(0xFF1C1C1C)

val ReaderDeepBlackBackground = Color(0xFF000000)
val ReaderDeepBlackText = Color(0xFF9E9E9E)
val ReaderDeepBlackSecondary = Color(0xFF555555)
val ReaderDeepBlackDivider = Color(0xFF202020)
val ReaderDeepBlackTopBar = Color(0xFF000000)
