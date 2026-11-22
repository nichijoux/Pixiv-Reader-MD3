package com.pixiv.reader.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 主题色板与扩展语义色对比度回归测试：
 * 校验明暗静态色板、收藏红（常规表面与遮罩）、七色设置点缀在淡底上的对比度、
 * 通知反色容器组合以及阅读器次要文字灰阶。
 */
class ColorContrastTest {

    private fun linearize(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

    private fun relativeLuminance(color: Color): Float =
        0.2126f * linearize(color.red) +
            0.7152f * linearize(color.green) +
            0.0722f * linearize(color.blue)

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
    }

    private fun alphaBlend(foreground: Color, background: Color): Color {
        val a = foreground.alpha
        return Color(
            red = foreground.red * a + background.red * (1f - a),
            green = foreground.green * a + background.green * (1f - a),
            blue = foreground.blue * a + background.blue * (1f - a),
            alpha = 1f,
        )
    }

    private fun assertMinContrast(label: String, fg: Color, bg: Color, minRatio: Float) {
        val actual = contrastRatio(fg, bg)
        assertTrue(
            "$label contrast=${"%.2f".format(actual)}:1 expected >= ${"%.2f".format(minRatio)}:1",
            actual >= minRatio,
        )
    }

    @Test
    fun `扩展收藏色在浅深表面与主色容器上满足非文本对比度`() {
        assertMinContrast(
            "Light favorite on Surface",
            LightExtendedColors.favorite,
            LightColors.surface,
            3.5f,
        )
        assertMinContrast(
            "Light favorite on SurfaceContainer",
            LightExtendedColors.favorite,
            LightColors.surfaceContainer,
            3.5f,
        )
        assertMinContrast(
            "Light favorite on PrimaryContainer",
            LightExtendedColors.favorite,
            LightColors.primaryContainer,
            3.5f,
        )

        assertMinContrast(
            "Dark favorite on DarkSurface",
            DarkExtendedColors.favorite,
            DarkColors.surface,
            3.5f,
        )
        assertMinContrast(
            "Dark favorite on DarkSurfaceContainer",
            DarkExtendedColors.favorite,
            DarkColors.surfaceContainer,
            3.5f,
        )
        assertMinContrast(
            "Dark favorite on DarkPrimaryContainer",
            DarkExtendedColors.favorite,
            DarkColors.primaryContainer,
            3.5f,
        )
    }

    @Test
    fun `ImageScrim 在纯白最坏封面上保证收藏红与白字对比度`() {
        val worstCaseBg = alphaBlend(ImageScrim, Color.White)
        assertMinContrast(
            "favoriteOnScrim on ImageScrim/White",
            LightExtendedColors.favoriteOnScrim,
            worstCaseBg,
            3.5f,
        )
        assertMinContrast(
            "favoriteOnScrim (dark) on ImageScrim/White",
            DarkExtendedColors.favoriteOnScrim,
            worstCaseBg,
            3.5f,
        )
        assertMinContrast(
            "White on ImageScrim/White",
            Color.White,
            worstCaseBg,
            7.0f,
        )
    }

    @Test
    fun `七色设置点缀在浅色 12% 与深色 18% 淡底上均达标`() {
        val lightAccents = listOf(
            "Light Violet" to LightExtendedColors.accentViolet,
            "Light Blue" to LightExtendedColors.accentBlue,
            "Light Teal" to LightExtendedColors.accentTeal,
            "Light Green" to LightExtendedColors.accentGreen,
            "Light Amber" to LightExtendedColors.accentAmber,
            "Light Orange" to LightExtendedColors.accentOrange,
            "Light Pink" to LightExtendedColors.accentPink,
        )
        for ((name, color) in lightAccents) {
            val badgeBg = alphaBlend(color.copy(alpha = 0.12f), LightColors.surfaceContainer)
            assertMinContrast("$name on 12% light badge", color, badgeBg, 3.5f)
        }

        val darkAccents = listOf(
            "Dark Violet" to DarkExtendedColors.accentViolet,
            "Dark Blue" to DarkExtendedColors.accentBlue,
            "Dark Teal" to DarkExtendedColors.accentTeal,
            "Dark Green" to DarkExtendedColors.accentGreen,
            "Dark Amber" to DarkExtendedColors.accentAmber,
            "Dark Orange" to DarkExtendedColors.accentOrange,
            "Dark Pink" to DarkExtendedColors.accentPink,
        )
        for ((name, color) in darkAccents) {
            val badgeBg = alphaBlend(color.copy(alpha = 0.18f), DarkColors.surfaceContainer)
            assertMinContrast("$name on 18% dark badge", color, badgeBg, 3.5f)
        }
    }

    @Test
    fun `通知反色容器文字与类型徽标对比度达标`() {
        // 浅色主题：inverseSurface 为深灰
        assertMinContrast(
            "Light InverseOnSurface on InverseSurface",
            LightColors.inverseOnSurface,
            LightColors.inverseSurface,
            7.0f,
        )
        val lightNotifAccents = listOf(
            "Light Info" to LightColors.inversePrimary,
            "Light Success" to LightExtendedColors.successOnInverse,
            "Light Error" to LightExtendedColors.errorOnInverse,
        )
        for ((name, accent) in lightNotifAccents) {
            val bg = alphaBlend(accent.copy(alpha = 0.16f), LightColors.inverseSurface)
            assertMinContrast("$name on InverseSurface badge", accent, bg, 3.0f)
        }

        // 深色主题：inverseSurface 为浅灰
        assertMinContrast(
            "Dark InverseOnSurface on InverseSurface",
            DarkColors.inverseOnSurface,
            DarkColors.inverseSurface,
            7.0f,
        )
        val darkNotifAccents = listOf(
            "Dark Info" to DarkColors.inversePrimary,
            "Dark Success" to DarkExtendedColors.successOnInverse,
            "Dark Error" to DarkExtendedColors.errorOnInverse,
        )
        for ((name, accent) in darkNotifAccents) {
            val bg = alphaBlend(accent.copy(alpha = 0.14f), DarkColors.inverseSurface)
            assertMinContrast("$name on DarkInverseSurface badge", accent, bg, 3.0f)
        }
    }

    @Test
    fun `静态 LightColors 与 DarkColors 主次三错及容器成对对比度达 AA`() {
        val pairs = listOf(
            "Light Primary" to (LightColors.onPrimary to LightColors.primary),
            "Light PrimaryContainer" to (LightColors.onPrimaryContainer to LightColors.primaryContainer),
            "Light Secondary" to (LightColors.onSecondary to LightColors.secondary),
            "Light SecondaryContainer" to (LightColors.onSecondaryContainer to LightColors.secondaryContainer),
            "Light Tertiary" to (LightColors.onTertiary to LightColors.tertiary),
            "Light TertiaryContainer" to (LightColors.onTertiaryContainer to LightColors.tertiaryContainer),
            "Light Error" to (LightColors.onError to LightColors.error),
            "Light ErrorContainer" to (LightColors.onErrorContainer to LightColors.errorContainer),
            "Dark Primary" to (DarkColors.onPrimary to DarkColors.primary),
            "Dark PrimaryContainer" to (DarkColors.onPrimaryContainer to DarkColors.primaryContainer),
            "Dark Secondary" to (DarkColors.onSecondary to DarkColors.secondary),
            "Dark SecondaryContainer" to (DarkColors.onSecondaryContainer to DarkColors.secondaryContainer),
            "Dark Tertiary" to (DarkColors.onTertiary to DarkColors.tertiary),
            "Dark TertiaryContainer" to (DarkColors.onTertiaryContainer to DarkColors.tertiaryContainer),
            "Dark Error" to (DarkColors.onError to DarkColors.error),
            "Dark ErrorContainer" to (DarkColors.onErrorContainer to DarkColors.errorContainer),
        )
        for ((name, colors) in pairs) {
            assertMinContrast(name, colors.first, colors.second, 4.5f)
        }
    }

    @Test
    fun `阅读器四套主题次要文字灰阶满足 4_5 比 1`() {
        assertMinContrast("Reader Day Secondary", ReaderDaySecondary, ReaderDayBackground, 4.5f)
        assertMinContrast("Reader Paper Secondary", ReaderPaperSecondary, ReaderPaperBackground, 4.5f)
        assertMinContrast("Reader Night Secondary", ReaderNightSecondary, ReaderNightBackground, 4.5f)
        assertMinContrast("Reader DeepBlack Secondary", ReaderDeepBlackSecondary, ReaderDeepBlackBackground, 4.5f)
    }
}
