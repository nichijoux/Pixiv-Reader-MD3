package com.pixiv.reader.core.ui.theme

import androidx.compose.ui.graphics.Color

// ── Material 3 静态配色（种子色 #0096FA 派生；动态取色不可用时回退） ──

val Primary = Color(0xFF00639B)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFCDE5FF)
val OnPrimaryContainer = Color(0xFF001D33)
val Secondary = Color(0xFF52606F)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFD6E3F2)
val OnSecondaryContainer = Color(0xFF0E1D29)
val Tertiary = Color(0xFF66558B)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFEBDCFF)
val OnTertiaryContainer = Color(0xFF211245)
val Error = Color(0xFFBA1A1A)
val ErrorContainer = Color(0xFFFFDAD6)
val OnError = Color(0xFFFFFFFF)
val OnErrorContainer = Color(0xFF410002)
val Background = Color(0xFFFDFCFF)
val OnBackground = Color(0xFF1A1C1E)
val Surface = Color(0xFFFDFCFF)
val OnSurface = Color(0xFF1A1C1E)
val SurfaceVariant = Color(0xFFDFE2EB)
val OnSurfaceVariant = Color(0xFF42474D)
val Outline = Color(0xFF73777E)
val OutlineVariant = Color(0xFFC3C7CF)
val SurfaceDim = Color(0xFFD9E2EA)
val SurfaceContainerLow = Color(0xFFF7F9FB)
val SurfaceContainer = Color(0xFFF0F4F8)
val SurfaceContainerHigh = Color(0xFFE4EAF0)

// ── 语义色（跨模块统一，禁止在业务代码写裸色值） ──

/** 全屏查看器 / 图片查看黑底 */
val ViewerScrim = Color(0xFF0A0A0A)

/** 收藏/爱心选中红 */
val FavoriteRed = Color(0xFFFF5252)

/** 成功通知绿 */
val SuccessGreen = Color(0xFF4CAF50)

/** Pixiv 品牌蓝（登录等） */
val PixivBlue = Color(0xFF0096FA)

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
