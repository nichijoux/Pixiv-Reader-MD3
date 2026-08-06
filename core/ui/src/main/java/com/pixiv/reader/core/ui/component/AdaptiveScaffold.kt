package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.common.WindowSizeClass
import com.pixiv.reader.core.common.classifyWindowWidth
import com.pixiv.reader.core.common.useRail

/** 底部/侧边导航项（自适应导航壳的数据单元）。 */
data class AdaptiveNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

/** 当前窗口尺寸类（基于屏幕宽度，无需额外依赖）。 */
@Composable
fun currentWindowSizeClass(): WindowSizeClass =
    classifyWindowWidth(LocalConfiguration.current.screenWidthDp)

/**
 * 自适应导航壳（App 主壳）：手机底部 NavigationBar，平板左侧 NavigationRail。
 *
 * ## UI 设计方式
 * 按窗口宽度分类（`classifyWindowWidth`）：
 * - Compact（<600dp）：`Scaffold` 底部 `NavigationBar`
 * - Medium/Expanded（≥840dp）：左侧 84dp `NavigationRail` + 内容区 `weight(1f)`
 * 内容通过 [content] 传入 `PaddingValues`（壳内 Scaffold 已处理系统栏边距）。
 *
 * @param items 导航项列表（route 用于选中匹配）
 * @param selectedRoute 当前选中的路由（与 `currentBackStackEntry.route` 比对）
 * @param onSelect 点击导航项回调（通常 `navigate(route)` + popUpTo/restoreState）
 * @param content 内容区（接收壳的 `PaddingValues`）
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
        // 壳层已通过 bottomBar（NavigationBar）处理底部系统导航栏 inset：
        // 在此消费 innerPadding，避免嵌套的子 Scaffold（各 Tab 页面）再用默认
        // contentWindowInsets（systemBars）重复加一次底部 inset padding，
        // 否则页面内容会被整体抬高，底栏上方留出背景色空白带。
        Row(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding),
        ) {
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
 * 平板内容宽度约束：内容居中且不超过 [maxWidth]（默认 `MAX_CONTENT_WIDTH_DP`=760dp）。
 *
 * ## UI 设计方式
 * 双层 `Box`：外层 `fillMaxSize` + `TopCenter` 对齐，内层 `fillMaxHeight` + `widthIn(max)`，
 * 使宽屏（平板）上内容限宽居中、两侧留白；手机宽度不足时自然占满。
 *
 * @param modifier 外部传入的 Modifier（通常带 padding）
 * @param maxWidth 内容最大宽度
 * @param content 限宽内容
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

/**
 * TopAppBar 标题（平板限宽居中）：标题内容限宽 [maxWidth]（默认 `MAX_CONTENT_WIDTH_DP`=760dp）
 * 并水平居中，与下方 [AdaptiveContentBox] 的内容对齐；手机（宽度 < maxWidth）自然占满。
 * 用于小说/漫画/首页等带 TopAppBar 的页面，解决平板端标题最左、内容居中的错位。
 *
 * @param text 标题文本
 * @param modifier 外部传入的 Modifier（TopAppBar 的 title slot 内无需传）
 */
@Composable
fun AdaptiveContentTitle(
    text: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = MAX_CONTENT_WIDTH_DP.dp,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.widthIn(max = maxWidth)) {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}
