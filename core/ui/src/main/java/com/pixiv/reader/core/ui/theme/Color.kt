package com.pixiv.reader.core.ui.theme

import androidx.compose.ui.graphics.Color

// ── Material 3 静态配色（种子色 #0096FA 派生；动态取色不可用时回退） ──

val Primary = Color(0xFF00639B)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFCDE5FF)
val OnPrimaryContainer = Color(0xFF001D33)
val Secondary = Color(0xFF51606F)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFD5E4F7)
val OnSecondaryContainer = Color(0xFF0E1D2A)
val Tertiary = Color(0xFF66558B)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFEBDCFF)
val OnTertiaryContainer = Color(0xFF211245)
val Error = Color(0xFFBA1A1A)
val ErrorContainer = Color(0xFFFFDAD6)
val OnError = Color(0xFFFFFFFF)
val OnErrorContainer = Color(0xFF410002)

// ── 深色主色阶（MD3 标准 tone-80 / tone-20 / tone-30 / tone-90） ──
val DarkPrimary = Color(0xFF98CBFF)
val DarkOnPrimary = Color(0xFF003353)
val DarkPrimaryContainer = Color(0xFF004A76)
val DarkOnPrimaryContainer = Color(0xFFCDE5FF)
val DarkSecondary = Color(0xFFB9C8DA)
val DarkOnSecondary = Color(0xFF233240)
val DarkSecondaryContainer = Color(0xFF3A4857)
val DarkOnSecondaryContainer = Color(0xFFD5E4F7)
val DarkTertiary = Color(0xFFD1BCFA)
val DarkOnTertiary = Color(0xFF37275A)
val DarkTertiaryContainer = Color(0xFF4E3E72)
val DarkOnTertiaryContainer = Color(0xFFEBDCFF)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

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
// 保持与 primary 蓝同相的冷调中性。

val DarkSurfaceDim = Color(0xFF191C1F)
val DarkSurfaceBright = Color(0xFF34383D)
val DarkSurfaceContainerLowest = Color(0xFF131619)
val DarkSurfaceContainerLow = Color(0xFF1D2125)
val DarkSurfaceContainer = Color(0xFF21252A)
val DarkSurfaceContainerHigh = Color(0xFF2B3035)
val DarkSurfaceContainerHighest = Color(0xFF363B41)
/** surfaceTint：深色下与 DarkPrimary 角色一致（tone-80 #98CBFF） */
val DarkSurfaceTint = Color(0xFF98CBFF)
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

/**
 * 图片上的文字/图标遮罩底（纯黑 70% alpha）。
 *
 * 用于角标文字、底部信息条等「在其上叠白字」的场景。取 0.7 而非 0.45 是为了 WCAG 对比度：
 * 最坏情况（纯白封面）下底色为 #4C4C4C，白字 8.6:1、金色名次 6.4:1、橙色 5.4:1；
 * 0.45 alpha 时底色 #8C8C8C，白字仅 3.3:1（小号文字 4.5:1 不达标）。
 */
val ImageScrim = Color(0xFF000000).copy(alpha = 0.70f)

/** 收藏/爱心选中红（浅色表面 tone-40，在 SurfaceContainer 上 ≥4.5:1） */
val FavoriteLight = Color(0xFFD32F2F)

/** 收藏/爱心选中红（深色表面与 ImageScrim 遮罩用 tone-80，在 70% 黑遮罩最坏底上 3.7:1） */
val FavoriteDark = Color(0xFFFF8A80)

/** 成功通知绿（浅色表面用 tone-40） */
val SuccessLight = Color(0xFF2E7D32)

/** 成功通知绿（深色表面与浅色反色通知条用 tone-80） */
val SuccessDark = Color(0xFF81C784)

/** Pixiv 品牌蓝（登录等） */
val PixivBlue = Color(0xFF0096FA)

/**
 * 设置行/导航点缀色组（浅色 tone-40 档）：容器底 = 12% alpha，图标 tint = 色值本身；
 * 与 [DarkSettingAccent*] 配合由 [resolveAccent] 随明暗主题自动映射，在 12%/18% 淡底上均 ≥4.0:1。
 */
val SettingAccentViolet = Color(0xFF5E3FDB)
val SettingAccentBlue = Color(0xFF00639B)
val SettingAccentTeal = Color(0xFF006A6A)
val SettingAccentGreen = Color(0xFF2E7D32)
val SettingAccentAmber = Color(0xFF8C5A00)
val SettingAccentOrange = Color(0xFFB54A00)
val SettingAccentPink = Color(0xFFB8255F)

/** 设置行/导航点缀色组（深色 tone-80 档，在深色 18% 淡底上 ≥5.2:1）。 */
val DarkSettingAccentViolet = Color(0xFFC4B5FF)
val DarkSettingAccentBlue = Color(0xFF7CC7FF)
val DarkSettingAccentTeal = Color(0xFF6AD9D9)
val DarkSettingAccentGreen = Color(0xFF81C784)
val DarkSettingAccentAmber = Color(0xFFFFD266)
val DarkSettingAccentOrange = Color(0xFFFFB07A)
val DarkSettingAccentPink = Color(0xFFFF9AB8)

// ── 小说阅读器 4 主题调色板（源：feature/reader ReaderTheme.kt 实际渲染色值） ──

val ReaderDayBackground = Color(0xFFFFFFFF)
val ReaderDayText = Color(0xFF1A1A1A)
val ReaderDaySecondary = Color(0xFF6E6E6E)
val ReaderDayDivider = Color(0xFFE5E5E5)
val ReaderDayTopBar = Color(0xFFFAFAFA)

val ReaderPaperBackground = Color(0xFFF5EFE0)
val ReaderPaperText = Color(0xFF3A3126)
val ReaderPaperSecondary = Color(0xFF73634B)
val ReaderPaperDivider = Color(0xFFE2D9C4)
val ReaderPaperTopBar = Color(0xFFEDE4CF)

val ReaderNightBackground = Color(0xFF212121)
val ReaderNightText = Color(0xFFCFCFCF)
val ReaderNightSecondary = Color(0xFF8A8A8A)
val ReaderNightDivider = Color(0xFF3A3A3A)
val ReaderNightTopBar = Color(0xFF1C1C1C)

val ReaderDeepBlackBackground = Color(0xFF000000)
val ReaderDeepBlackText = Color(0xFF9E9E9E)
val ReaderDeepBlackSecondary = Color(0xFF757575)
val ReaderDeepBlackDivider = Color(0xFF202020)
val ReaderDeepBlackTopBar = Color(0xFF000000)
