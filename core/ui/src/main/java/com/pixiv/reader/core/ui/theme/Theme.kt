package com.pixiv.reader.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryContainer,
    onPrimary = OnPrimaryContainer,
    primaryContainer = Primary,
    onPrimaryContainer = OnPrimary,
    tertiary = TertiaryContainer,
    onTertiary = OnTertiaryContainer,
    tertiaryContainer = Tertiary,
    onTertiaryContainer = OnTertiary,
    error = ErrorContainer,
    onError = OnErrorContainer,
    errorContainer = Error,
    onErrorContainer = OnError,
    // 补全深色中性角色：此前缺失时静态深色会落到 M3 基线默认值
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

/**
 * 将色板的 secondary 系四角色镜像为 primary 系对应角色。
 *
 * 背景：动态取色（Android 12+ 壁纸取色）下系统会按壁纸生成多条色板，accent2 派生的
 * secondary 系常与主色异相（典型如 primary 蓝而 secondaryContainer 淡紫），而 M3 的
 * NavigationBarItem 指示器、SegmentedButton 选中段、FilterChip 等组件默认取
 * secondaryContainer，导致界面出现蓝紫混色。统一镜像为 primary 系可保证全局单一色相，
 * 且动态/静态色板行为一致。
 *
 * @param scheme 原始色板（动态或静态均可）
 * @return secondary 系已镜像为 primary 系的新色板，其余角色不变
 */
private fun unifySecondaryWithPrimary(scheme: ColorScheme): ColorScheme = scheme.copy(
    secondary = scheme.primary,
    onSecondary = scheme.onPrimary,
    secondaryContainer = scheme.primaryContainer,
    onSecondaryContainer = scheme.onPrimaryContainer,
)

/**
 * Material 3 Expressive 形状 token：M3 组件（Button/Card/Dialog/Sheet 等）默认形状统一
 * 收敛到更圆润的 Expressive 圆角口径，与 [AppShapes] 的自定义口径保持同一档位语言。
 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 全局主题入口：动态取色（Android 12+）或手写 Light/Dark 色板之上，
 * 启用 Material 3 Expressive——弹性 spring 动效方案、Expressive 形状 token
 * 与组件级 Expressive 默认行为（按压形变、加载指示样式等）。
 *
 * @param darkTheme 是否使用深色配色（默认跟随系统）
 * @param dynamicColor 是否启用 Android 12+ 动态取色
 * @param content 应用主题的内容
 * @return 无返回值；向 [content] 提供 Expressive 主题环境
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PixivReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    // secondary 系镜像 primary 系：消除动态取色 accent2 异相导致的蓝紫混色
    val colorScheme = unifySecondaryWithPrimary(
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColors
            else -> LightColors
        },
    )

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        // Expressive 动效方案：M3 组件默认动画切换为 spring 物理曲线（弹性进入/快速吸附），
        // 显式 tween 的自定义动画不受影响
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = Typography,
        content = content,
    )
}
