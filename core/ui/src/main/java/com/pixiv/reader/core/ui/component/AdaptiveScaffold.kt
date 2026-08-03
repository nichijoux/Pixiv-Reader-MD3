package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.common.WindowSizeClass
import com.pixiv.reader.core.common.classifyWindowWidth
import com.pixiv.reader.core.common.useRail

/** 底部/侧边导航项 */
data class AdaptiveNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

/** 当前窗口尺寸类（基于屏幕宽度，无需额外依赖） */
@Composable
fun currentWindowSizeClass(): WindowSizeClass =
    classifyWindowWidth(LocalConfiguration.current.screenWidthDp)

/**
 * 自适应导航壳：
 * - Compact（手机）：底部 NavigationBar
 * - Medium / Expanded（平板）：左侧 NavigationRail
 */
@Composable
fun AdaptiveNavScaffold(
    items: List<AdaptiveNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    val size = currentWindowSizeClass()
    val useRail = size.useRail()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!useRail) {
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = selectedRoute == item.route,
                            onClick = { onSelect(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedRoute == item.route) item.selectedIcon else item.icon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    modifier = Modifier.width(84.dp).fillMaxHeight().statusBarsPadding(),
                ) {
                    items.forEach { item ->
                        NavigationRailItem(
                            selected = selectedRoute == item.route,
                            onClick = { onSelect(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedRoute == item.route) item.selectedIcon else item.icon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                content(padding)
            }
        }
    }
}

/**
 * 平板内容宽度约束：内容居中且不超过 [maxWidth]。
 * 详情页 / 阅读器使用，避免在宽屏上拉伸过长。
 */
@Composable
fun AdaptiveContentBox(
    modifier: Modifier = Modifier,
    maxWidth: Dp = MAX_CONTENT_WIDTH_DP.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = maxWidth),
        ) {
            content()
        }
    }
}
