package com.pixiv.reader.core.ui.component.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixiv.reader.core.common.ui.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.common.ui.WindowSizeClass
import com.pixiv.reader.core.common.ui.classifyWindowWidth
import com.pixiv.reader.core.common.ui.useRail
import com.pixiv.reader.core.ui.component.feedback.PixivHaptics
import com.pixiv.reader.core.ui.component.feedback.rememberHapticCue
import com.pixiv.reader.core.ui.theme.AppShapes
import kotlinx.coroutines.launch

/** 浮动底栏高度（不含悬浮偏移与系统导航栏）。 */
internal val FloatingBarHeight = 64.dp

/** 浮动底栏距屏幕底部（或系统导航栏）的悬浮偏移。 */
internal val FloatingBarOffset = 12.dp

/**
 * 浮动胶囊底栏（Compact 布局）占用的底部避让高度。
 *
 * 消费约定：**只含「栏高 [FloatingBarHeight] + 悬浮偏移 [FloatingBarOffset]」，不含系统导航栏 inset**。
 * 内容页尾部 padding 与壳内浮层避让各自叠加 `navigationBarsPadding()`（壳层改造后不再消费
 * navigationBars，各页 `WindowInsets.navigationBars` 读数恢复有值），避免与系统栏重复叠加。
 * rail 模式（平板左栏）与底栏隐藏（发现页等全屏 Tab）时恒为 `0.dp`。
 */
val LocalFloatingNavBottomInset = compositionLocalOf { 0.dp }

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
 * 自适应导航壳（App 主壳）：手机底部浮动胶囊，平板左侧 NavigationRail。
 *
 * ## UI 设计方式
 * 按窗口宽度分类（`classifyWindowWidth`）：
 * - Compact（<600dp）：无 `bottomBar` 槽——底部浮动胶囊（[FloatingBottomBar]）作为内容区
 *   `Box` 内的 overlay 叠在页面之上（实色无毛玻璃，列表内容从栏后穿过）；壳层因此**不再消费**
 *   底部系统导航栏 inset，浮层与内容尾部各自叠加 `navigationBarsPadding()`。
 *   栏显隐经缩放 + 淡入淡出动画，同时通过 [LocalFloatingNavBottomInset] 下发避让高度。
 * - Medium/Expanded（≥840dp）：左侧 84dp `NavigationRail` + 内容区 `weight(1f)`
 * 内容通过 [content] 传入 `PaddingValues`：壳层 `contentWindowInsets` 置 0 且无 topBar/bottomBar，
 * 故该值各分量恒为 0（保留签名以兼容既有页面写法）；所有系统栏与底部避让均由页面自行处理。
 * 底部避让统一经 [LocalFloatingNavBottomInset] 下发。
 *
 * @param items 导航项列表（route 用于选中匹配）
 * @param selectedRoute 当前选中的路由（与 `currentBackStackEntry.route` 比对）
 * @param onSelect 点击导航项回调（通常 `navigate(route)` + popUpTo/restoreState）
 * @param showBottomBar 是否显示底栏/侧栏（全屏类内层页如发现页设为 false，隐藏导航栏）
 * @param content 内容区（接收壳的 `PaddingValues`）
 * @return 无返回值
 */
@Composable
fun AdaptiveNavScaffold(
    items: List<AdaptiveNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    showBottomBar: Boolean = true,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    val size = currentWindowSizeClass()
    val useRail = size.useRail()
    // 页签切换触觉：仅在真正切到另一页签时响（重复点当前页签不响）
    val haptic = rememberHapticCue()
    // 浮动栏可见性：仅 Compact 且需要显示时存在（rail 模式与全屏页均不渲染）
    val floatingBarVisible = !useRail && showBottomBar
    // 下发给内容页的底部避让：只含「栏高 + 悬浮偏移」，不含系统导航栏 inset（各页自行叠加）
    val barInset = if (floatingBarVisible) FloatingBarHeight + FloatingBarOffset else 0.dp

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        // 壳层不再消费导航栏 inset：Compact 分支已无 bottomBar，页面尾部须自行
        // navigationBarsPadding()；此处 consumeWindowInsets(padding) 仅吞掉壳内已应用的
        // 顶部/水平 padding，避免嵌套子 Scaffold 重复叠加。
        Row(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding),
        ) {
            // 侧栏显隐动画（平板进入发现页等全屏页时收起）：向左收起 + 淡出，内容区宽度平滑扩展
            AnimatedVisibility(
                visible = useRail && showBottomBar,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
            ) {
                // Expressive WideNavigationRail：collapsed 窄栏（图标），header 菜单键
                // 展开为带标签的宽栏（M3 Expressive 大屏导航组件，替代旧 NavigationRail）
                val railState = rememberWideNavigationRailState()
                val railScope = rememberCoroutineScope()
                WideNavigationRail(
                    state = railState,
                    modifier = Modifier.fillMaxHeight(),
                    header = {
                        IconButton(onClick = { railScope.launch { railState.toggle() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = null)
                        }
                    },
                ) {
                    val railExpanded = railState.targetValue == WideNavigationRailValue.Expanded
                    items.forEach { item ->
                        WideNavigationRailItem(
                            selected = selectedRoute == item.route,
                            onClick = {
                                // 仅切换页签时响 SELECT（与浮动底栏同语义）
                                if (selectedRoute != item.route) {
                                    haptic(PixivHaptics.HapticCue.SELECT)
                                }
                                onSelect(item.route)
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selectedRoute == item.route) item.selectedIcon else item.icon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                            railExpanded = railExpanded,
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                // 内容与浮动栏同处一层：内容穿栏而过，滚动尾部由内容页按 local inset 自行让位
                CompositionLocalProvider(LocalFloatingNavBottomInset provides barInset) {
                    content(padding)
                }
                // 浮动底栏：锚定内容区底部居中，从下方缩放 + 淡入（收起时反向）
                FloatingBarOverlay(
                    visible = floatingBarVisible,
                    items = items,
                    selectedRoute = selectedRoute,
                    onSelect = onSelect,
                    haptic = haptic,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * 浮动底栏显隐壳：缩放（自底向上 90% → 100%）+ 淡入 / 对称退出。
 *
 * 单独成 Composable 而非内联 [AnimatedVisibility]：调用点处于 `RowScope` 内，
 * 内联会命中 `RowScope.AnimatedVisibility` 扩展重载导致隐式接收者歧义。
 *
 * @param visible 是否显示浮动底栏
 * @param items 导航项列表
 * @param selectedRoute 当前选中路由
 * @param onSelect 点击导航项回调
 * @param haptic 触觉回调（SELECT 档）
 * @param modifier 外部传入的 Modifier（调用方用 align 锚定底部居中）
 * @return 无返回值
 */
@Composable
private fun FloatingBarOverlay(
    visible: Boolean,
    items: List<AdaptiveNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    haptic: (PixivHaptics.HapticCue) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.9f,
            transformOrigin = TransformOrigin(0.5f, 1f),
        ) + fadeIn(),
        exit = scaleOut(
            targetScale = 0.9f,
            transformOrigin = TransformOrigin(0.5f, 1f),
        ) + fadeOut(),
        modifier = modifier,
    ) {
        FloatingBottomBar(
            items = items,
            selectedRoute = selectedRoute,
            onSelect = onSelect,
            haptic = haptic,
        )
    }
}

/**
 * 浮动胶囊底部导航栏（Compact 布局专用）：实色 [MaterialTheme.colorScheme.surfaceContainerHigh]
 * 胶囊 + 6dp 投影，条目等分，选中项图标后挂 56×32dp `primaryContainer` 胶囊指示器。
 *
 * 无毛玻璃（不做背景模糊），靠实色与投影从页面上浮；内容从胶囊下方穿过、不为其预留布局高度。
 *
 * @param items 导航项列表（选中项用 [AdaptiveNavItem.selectedIcon]）
 * @param selectedRoute 当前选中路由（null 时全部为未选态）
 * @param onSelect 点击导航项回调（仅真正切页前触发触觉）
 * @param haptic 组合期绑定的触觉回调（SELECT 档）
 * @return 无返回值
 */
@Composable
internal fun FloatingBottomBar(
    items: List<AdaptiveNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    haptic: (PixivHaptics.HapticCue) -> Unit,
) {
    Surface(
        shape = AppShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = FloatingBarOffset),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FloatingBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = selectedRoute == item.route
                // 无涟漪：浮动栏以指示器胶囊 + 图标换色表达选中，涟漪会盖住胶囊层次
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) {
                            // 仅切换页签时响 SELECT（重复点当前页签只做回顶，不响）
                            if (!selected) {
                                haptic(PixivHaptics.HapticCue.SELECT)
                            }
                            onSelect(item.route)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // 图标槽：选中指示器（56×32 primaryContainer 胶囊）叠在图标之下
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 32.dp)
                                .clip(AppShapes.pill)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.icon,
                                contentDescription = item.label,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 11.sp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
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
