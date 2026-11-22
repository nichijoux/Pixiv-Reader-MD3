package com.pixiv.reader.core.ui.component.layout

import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationItemColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.ui.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.common.ui.WindowSizeClass
import com.pixiv.reader.core.common.ui.classifyWindowWidth
import com.pixiv.reader.core.common.ui.useRail
import com.pixiv.reader.core.ui.component.feedback.PixivHaptics
import com.pixiv.reader.core.ui.component.feedback.rememberHapticCue
import com.pixiv.reader.core.ui.theme.LocalColorStyleEnabled
import com.pixiv.reader.core.ui.theme.resolveAccent
import kotlinx.coroutines.launch

/**
 * 底部/侧边导航项（自适应导航壳的数据单元）。
 *
 * @param route 路由标识（用于选中匹配）
 * @param label 显示文案
 * @param icon 未选中图标
 * @param selectedIcon 选中态图标（默认同 [icon]）
 * @param accent 选中态点缀色（图标 + 指示器 + 标签）；[Color.Unspecified] 回退主题 primary。
 *   彩色风格关闭时经 [resolveAccent] 统一回退为 primary。
 */
data class AdaptiveNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val accent: Color = Color.Unspecified,
)

/** 当前窗口尺寸类（基于屏幕宽度，无需额外依赖）。 */
@Composable
fun currentWindowSizeClass(): WindowSizeClass =
    classifyWindowWidth(LocalConfiguration.current.screenWidthDp)

/**
 * 自适应导航壳（App 主壳）：手机底部 NavigationBar，平板左侧 WideNavigationRail。
 *
 * ## UI 设计方式
 * 按窗口宽度分类（`classifyWindowWidth`）：
 * - Compact（<600dp）：`Scaffold` 底部 `NavigationBar`（贴边、由 bottomBar 槽承担布局高度，
 *   并消费底部系统导航栏 inset；Tab 内容不再需要自行避让底栏）
 * - Medium/Expanded（≥840dp）：左侧 84dp `WideNavigationRail` + 内容区 `weight(1f)`
 * 内容通过 [content] 传入 `PaddingValues`（壳内 Scaffold 已处理底栏高度与系统导航栏边距）。
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // 底栏显隐动画（进入发现页等全屏页时收起）：用 expand/shrink 平滑过渡尺寸，
            // Scaffold 的 bottom padding 逐帧变化、内容区不跳变；硬切会整帧消失显突兀
            AnimatedVisibility(
                visible = !useRail && showBottomBar,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
            ) {
                // 自绘导航栏：M3 NavigationBarItem 的指示器是每个 item 各自淡入淡出（无法跨 item 位移），
                // 这里改为「单一滑块 + 位移动画」，实现指示器从前一个选中项滑到新选中项
                SlidingIndicatorNavigationBar(
                    items = items,
                    selectedRoute = selectedRoute,
                    onSelect = onSelect,
                    haptic = haptic,
                )
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
                    val colorStyle = LocalColorStyleEnabled.current
                    val scheme = MaterialTheme.colorScheme
                    items.forEach { item ->
                        val selected = selectedRoute == item.route
                        val navColors = if (colorStyle) {
                            // 彩色风格开启：选中态取各 Tab 点缀色（经 resolveAccent 转浅/深合规色）
                            val railAccent = resolveAccent(
                                if (item.accent == Color.Unspecified) scheme.primary else item.accent,
                            )
                            NavigationItemColors(
                                selectedIconColor = railAccent,
                                selectedTextColorTopIconPosition = railAccent,
                                selectedTextColorStartIconPosition = railAccent,
                                selectedIndicatorColor = railAccent.copy(alpha = 0.16f),
                                unselectedIconColor = scheme.onSurfaceVariant,
                                unselectedTextColor = scheme.onSurfaceVariant,
                                disabledIconColor = scheme.onSurfaceVariant.copy(alpha = 0.38f),
                                disabledTextColor = scheme.onSurfaceVariant.copy(alpha = 0.38f),
                            )
                        } else {
                            // 纯 M3 单色：遵循 MD3 导航角色（secondaryContainer 指示器 + onSecondaryContainer 图标 + onSurface 标签）
                            NavigationItemColors(
                                selectedIconColor = scheme.onSecondaryContainer,
                                selectedTextColorTopIconPosition = scheme.onSurface,
                                selectedTextColorStartIconPosition = scheme.onSurface,
                                selectedIndicatorColor = scheme.secondaryContainer,
                                unselectedIconColor = scheme.onSurfaceVariant,
                                unselectedTextColor = scheme.onSurfaceVariant,
                                disabledIconColor = scheme.onSurfaceVariant.copy(alpha = 0.38f),
                                disabledTextColor = scheme.onSurfaceVariant.copy(alpha = 0.38f),
                            )
                        }
                        WideNavigationRailItem(
                            selected = selected,
                            onClick = {
                                // 仅切换页签时响 SELECT（与底栏同语义）
                                if (!selected) {
                                    haptic(PixivHaptics.HapticCue.SELECT)
                                }
                                onSelect(item.route)
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.icon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                            railExpanded = railExpanded,
                            colors = navColors,
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
 * 导航栏条目的图标过渡：选中/未选中图标交叉淡入淡出 + 选中态轻微弹性缩放。
 *
 * M3 `NavigationBarItem` 只对指示器胶囊做动画，图标本体是硬切（`if (selected) a else b`），
 * 切换到带独立 `selectedIcon` 的条目时观感突兀。这里用 [AnimatedContent] 以选中态为 key
 * 做交叉淡入淡出，动画走 [MaterialTheme.motionScheme] 的 effects 档
 * （遵守项目动效规范：不使用裸 tween 时长）。
 *
 * @param selected 当前是否选中
 * @param icon 未选中图标
 * @param selectedIcon 选中态图标
 * @param label 无障碍描述（取自导航项文案）
 * @param tint 图标着色（选中取该 Tab 点缀色、未选取 onSurfaceVariant）
 * @return 无返回值
 */
@Composable
private fun NavBarIcon(
    selected: Boolean,
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    tint: Color,
) {
    // 组合期捕获动效规格：AnimatedContent 的 transitionSpec 不在组合上下文求值
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    // 选中态弹起：未选 0.82 → 选中 1.0（含轻微过冲由 spring 提供），
    // 与指示器胶囊同时进场形成「按下弹起」的层次
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.82f,
        animationSpec = spatialSpec,
        label = "navBarIconScale",
    )
    // 选中态额外叠一层不透明度，未选图标更「沉」，增强选中/未选的层次差
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.86f,
        animationSpec = effectsSpec,
        label = "navBarIconAlpha",
    )
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            // 交叉淡入淡出：旧图标快速淡出、新图标淡入（effects 档为快节奏，适配导航切换）
            fadeIn(animationSpec = effectsSpec) togetherWith fadeOut(animationSpec = effectsSpec)
        },
        label = "navBarIcon",
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = iconAlpha
        },
    ) { isSelected ->
        Icon(
            imageVector = if (isSelected) selectedIcon else icon,
            contentDescription = label,
            tint = tint,
        )
    }
}

/**
 * 自绘底部导航栏：单一指示器胶囊跨 item 位移。
 *
 * ## 为什么不用 M3 `NavigationBar`
 * M3 的 `NavigationBarItem` 指示器在每个 item 内部各自缩放淡入淡出，切换时是
 * 「旧指示器缩没 + 新指示器长大」，无法表达「指示器从前一项滑到新项」。
 * 这里改为在整栏上叠**唯一**一个指示器，用 `animateDpAsState` 对其 x 位移做动画。
 *
 * ## 结构
 * `Box` 铺满栏宽：底层指示器（按选中索引偏移 + 宽 56dp）+ 上层等分条目（图标+标签）。
 * 尺寸沿用 M3 规范：栏高 80dp、指示器 56×32dp、图标 24dp、标签 labelMedium。
 * 选中色取该 Tab 的 `accent`（经 [resolveAccent]，彩色风格关闭时回退 primary）。
 *
 * @param items 导航项列表
 * @param selectedRoute 当前选中路由（null 时无指示器）
 * @param onSelect 点击导航项回调
 * @param haptic 触觉回调（SELECT 档，仅真正切页时触发）
 * @return 无返回值
 */
@Composable
private fun SlidingIndicatorNavigationBar(
    items: List<AdaptiveNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    haptic: (PixivHaptics.HapticCue) -> Unit,
) {
    if (items.isEmpty()) return
    val colorScheme = MaterialTheme.colorScheme
    val colorStyle = LocalColorStyleEnabled.current
    // 选中索引：用于指示器位移目标；无选中时 -1（不渲染指示器）
    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    val colorsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            // 底栏背景延伸进系统导航栏区域（M3 默认行为：容器色铺满 + 内容避让 inset）
            .background(colorScheme.surfaceContainer)
            .windowInsetsPadding(NavigationBarDefaults.windowInsets),
    ) {
        // 每个条目的可用宽度（栏宽均分）；指示器在其中水平居中
        val itemWidth = maxWidth / items.size
        val indicatorWidth = NavIndicatorWidth
        // 指示器目标水平偏移：第 i 项中心 - 指示器半宽
        val targetOffset = if (selectedIndex >= 0) {
            itemWidth * selectedIndex + (itemWidth - indicatorWidth) / 2
        } else {
            0.dp
        }
        val offset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spatialSpec,
            label = "navIndicatorOffset",
        )
        // 指示器颜色：彩色风格开启时跟随选中项 accent；关闭时遵循 MD3 secondaryContainer
        val indicatorColor by animateColorAsState(
            targetValue = when {
                selectedIndex < 0 -> Color.Transparent
                colorStyle -> {
                    val selectedItem = items[selectedIndex]
                    resolveAccent(
                        if (selectedItem.accent == Color.Unspecified) colorScheme.primary else selectedItem.accent,
                    ).copy(alpha = 0.16f)
                }
                else -> colorScheme.secondaryContainer
            },
            animationSpec = colorsSpec,
            label = "navIndicatorColor",
        )

        // 指示器层：唯一滑块，随选中索引横向滑动
        if (selectedIndex >= 0) {
            Box(
                modifier = Modifier
                    .offset(x = offset)
                    // 指示器水平居中于条目区、垂直与图标对齐（距顶 12dp，与 M3 一致）
                    .padding(top = NavIndicatorTopPadding)
                    .size(width = indicatorWidth, height = NavIndicatorHeight)
                    .clip(AppShapes.pill)
                    .background(indicatorColor),
            )
        }

        // 条目前景层：等分铺满，每项可点
        Row(modifier = Modifier.fillMaxWidth()) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                val selectedIconTint: Color
                val selectedLabelTint: Color
                if (colorStyle) {
                    val accent = resolveAccent(
                        if (item.accent == Color.Unspecified) colorScheme.primary else item.accent,
                    )
                    selectedIconTint = accent
                    selectedLabelTint = accent
                } else {
                    selectedIconTint = colorScheme.onSecondaryContainer
                    selectedLabelTint = colorScheme.onSurface
                }
                // 无涟漪：由指示器滑块与图标缩放表达选中态，涟漪会盖住滑块层次
                val interactionSource = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(NavBarHeight)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = interactionSource,
                            indication = null,
                        ) {
                            // 仅切换页签时响 SELECT（重复点当前页签只做回顶，不响）
                            if (!selected) {
                                haptic(PixivHaptics.HapticCue.SELECT)
                            }
                            onSelect(item.route)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 图标区固定 32dp 高，与指示器垂直对齐
                    Box(
                        modifier = Modifier.height(NavIndicatorHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        NavBarIcon(
                            selected = selected,
                            icon = item.icon,
                            selectedIcon = item.selectedIcon,
                            label = item.label,
                            tint = if (selected) selectedIconTint else colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) selectedLabelTint else colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
            }
        }
    }
}

/** 导航栏内容高度（不含系统导航栏 inset），M3 TallContainerHeight。 */
private val NavBarHeight = 80.dp

/** 选中指示器尺寸（M3 ActiveIndicatorWidth × ActiveIndicatorHeight）。 */
private val NavIndicatorWidth = 56.dp
private val NavIndicatorHeight = 32.dp

/** 指示器距栏顶偏移：与图标垂直居中位置对齐。 */
private val NavIndicatorTopPadding = 12.dp

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
