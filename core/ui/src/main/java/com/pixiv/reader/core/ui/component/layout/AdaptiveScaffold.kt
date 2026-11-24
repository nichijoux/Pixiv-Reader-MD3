package com.pixiv.reader.core.ui.component.layout

import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemColors
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.config.BottomNavBarStyle
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
 * 底部导航栏在手机端覆盖内容时，为可滚动列表预留的固定底部净空高度（不含系统导航栏 inset）。
 *
 * 保持该值在滚动显隐期间恒定（`64.dp` 或 `0.dp`），可避免动态修改列表 `contentPadding`
 * 触发瀑布流/列表视口重排与滚动抖动；平板端（侧栏）及隐藏底栏的内层页默认返回 `0.dp`。
 */
val LocalNavBarBottomPadding = compositionLocalOf { 0.dp }

/**
 * 手机端底部导航栏样式（CompositionLocal 下发）。
 *
 * [BottomNavBarStyle.DOCKED] 为 M3 Expressive 64dp 贴边短栏（默认），
 * [BottomNavBarStyle.FLOATING_CAPSULE] 为悬浮胶囊导航栏。
 * 由 `MainActivity` 依 `UserPreferences.bottomNavBarStyle` 注入，全局即时生效。
 */
val LocalBottomNavBarStyle = staticCompositionLocalOf { BottomNavBarStyle.DOCKED }

/**
 * 计算当前壳层为 Tab 滚动列表预留的恒定底部净空高度（不含系统导航栏 inset）。
 *
 * @param useRail 当前窗口是否使用平板左侧栏
 * @param showBottomBar 当前路由是否展示导航栏
 * @param style 当前手机端底部导航栏样式
 * @return 预留给列表底部的恒定 [Dp] 净空高度
 */
internal fun computeNavBarBottomPadding(
    useRail: Boolean,
    showBottomBar: Boolean,
    style: BottomNavBarStyle,
): Dp = when {
    useRail || !showBottomBar -> 0.dp
    style == BottomNavBarStyle.DOCKED -> NavBarHeight
    style == BottomNavBarStyle.FLOATING_CAPSULE -> FloatingNavBarHeight + FloatingNavBottomMargin
    else -> NavBarHeight
}

/**
 * 底部导航栏滚动自动显隐控制器（方向迟滞累积 + 边界回弹处理）。
 *
 * 通过 [NestedScrollConnection.onPostScroll] 仅观察子列表实际消费的纵向位移（`consumed.y`），
 * 避免不可滚动页面、水平翻页或列表触底过冲误触发隐藏；当列表隐藏态停在顶部继续向下拉
 * （`consumed.y == 0f` 且 `available.y > 0f`）时同样触发呼出。
 *
 * @param hideThresholdPx 连续向上浏览（内容上移，`consumed.y < 0`）触发隐藏的累计像素阈值
 * @param showThresholdPx 连续向下回滚（内容下移，`consumed.y > 0`）触发显示的累计像素阈值
 */
@Stable
internal class BottomBarAutoHideController(
    private val hideThresholdPx: Float,
    private val showThresholdPx: Float,
) {
    /** 当前底栏是否处于展开可见状态。 */
    var isVisible by mutableStateOf(true)
        private set

    /** 同方向纵向滚动累计偏移（负值表示上滑内容，正值表示下滑回看）。 */
    private var accumulatedDeltaY = 0f

    /**
     * 强制显示底栏并清零累计偏移（用于切换页签或从非主壳路由返回时复位）。
     *
     * @return 无返回值
     */
    fun show() {
        isVisible = true
        accumulatedDeltaY = 0f
    }

    /**
     * 处理一次纵向滚动增量并按方向迟滞阈值更新 [isVisible]。
     *
     * @param consumedY 子列表实际消费的纵向位移像素（<0 上滑内容，>0 下滑内容）
     * @param availableY 未消费剩余纵向位移像素（隐藏状态下到顶继续下拉用于呼出底栏）
     * @return 无返回值
     */
    fun onScrollDelta(consumedY: Float, availableY: Float) {
        val delta = when {
            consumedY != 0f -> consumedY
            !isVisible && availableY > 0f -> availableY
            else -> return
        }

        // 滚动方向反转时先清零累积量，防止反向微抖叠加旧方向残余值误判
        if ((delta < 0f && accumulatedDeltaY > 0f) || (delta > 0f && accumulatedDeltaY < 0f)) {
            accumulatedDeltaY = 0f
        }
        accumulatedDeltaY += delta

        if (isVisible && accumulatedDeltaY <= -hideThresholdPx) {
            isVisible = false
            accumulatedDeltaY = 0f
        } else if (!isVisible && accumulatedDeltaY >= showThresholdPx) {
            isVisible = true
            accumulatedDeltaY = 0f
        }
    }

    /** 挂载到手机端主内容容器的嵌套滚动连接器。 */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            onScrollDelta(consumedY = consumed.y, availableY = available.y)
            return Offset.Zero
        }
    }
}

/**
 * 自适应导航壳（App 主壳）：手机底部可切换 M3 Expressive 贴边短栏（64dp）或悬浮胶囊导航栏
 * （均支持滚动自动显隐），平板左侧 `WideNavigationRail`。
 *
 * ## UI 设计方式
 * 按窗口宽度分类（`classifyWindowWidth`）：
 * - Compact（<600dp）：底部导航栏覆盖在内容上方，随列表上滑自动滑出、下滑快速回弹；
 *   通过 [LocalNavBarBottomPadding] 为各 Tab 列表提供恒定尾部净空，避免滚动期间重排抖动，
 *   并保留系统 `navigationBars` inset 供内容贴底边滚动。
 * - Medium/Expanded（≥600dp）：左侧 `WideNavigationRail` + 内容区 `weight(1f)`，不启用底栏自动隐藏。
 *
 * @param items 导航项列表（route 用于选中匹配）
 * @param selectedRoute 当前选中的路由（与 `currentBackStackEntry.route` 比对）
 * @param onSelect 点击导航项回调（通常 `navigate(route)` + popUpTo/restoreState）
 * @param showBottomBar 是否显示底栏/侧栏（全屏类内层页如发现页设为 false，隐藏导航栏）
 * @param navBarStyle 手机端底部导航栏样式（默认取 [LocalBottomNavBarStyle]）
 * @param content 内容区（壳层叠加导航栏后固定传入 `PaddingValues(0.dp)`）
 * @return 无返回值
 */
@Composable
fun AdaptiveNavScaffold(
    items: List<AdaptiveNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    showBottomBar: Boolean = true,
    navBarStyle: BottomNavBarStyle = LocalBottomNavBarStyle.current,
    content: @Composable (PaddingValues) -> Unit,
) {
    val size = currentWindowSizeClass()
    val useRail = size.useRail()
    // 页签切换触觉：仅在真正切到另一页签时响（重复点当前页签不响）
    val haptic = rememberHapticCue()
    val density = LocalDensity.current
    val autoHideController = remember(density) {
        BottomBarAutoHideController(
            hideThresholdPx = with(density) { 40.dp.toPx() },
            showThresholdPx = with(density) { 24.dp.toPx() },
        )
    }

    // 切换页签、进出全屏内层页（如 discover_tab）或切换底栏样式时复位底栏显隐状态
    LaunchedEffect(selectedRoute, showBottomBar, navBarStyle) {
        autoHideController.show()
    }

    var barHeightPx by remember { mutableIntStateOf(0) }
    val hiddenFraction by animateFloatAsState(
        targetValue = if (autoHideController.isVisible) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "navBarAutoHide",
    )
    val navBottomPadding = computeNavBarBottomPadding(
        useRail = useRail,
        showBottomBar = showBottomBar,
        style = navBarStyle,
    )

    CompositionLocalProvider(LocalNavBarBottomPadding provides navBottomPadding) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 注意：此处刻意不调用 consumeWindowInsets，避免手机端覆盖模式下子页面无法读取
            // 真实 WindowInsets.navigationBars 导致底栏隐藏后列表底部被系统手势条遮挡
            Row(modifier = Modifier.fillMaxSize()) {
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(
                            if (!useRail && showBottomBar) {
                                Modifier.nestedScroll(autoHideController.connection)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    content(PaddingValues(0.dp))
                }
            }

            val navBarEnterSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
            val navBarFadeInSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            val navBarScaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
            AnimatedVisibility(
                visible = !useRail && showBottomBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(animationSpec = navBarEnterSpec) { it } +
                    fadeIn(animationSpec = navBarFadeInSpec),
                exit = slideOutVertically(animationSpec = navBarEnterSpec) { it } +
                    fadeOut(animationSpec = navBarFadeInSpec),
            ) {
                AnimatedContent(
                    targetState = navBarStyle,
                    transitionSpec = {
                        (fadeIn(animationSpec = navBarFadeInSpec) +
                            scaleIn(initialScale = 0.96f, animationSpec = navBarScaleSpec)) togetherWith
                            fadeOut(animationSpec = navBarFadeInSpec)
                    },
                    label = "navBarStyleSwitch",
                ) { style ->
                    val barModifier = Modifier
                        .onSizeChanged { if (style == navBarStyle) barHeightPx = it.height }
                        .graphicsLayer {
                            val rawTranslationY = barHeightPx * hiddenFraction
                            // 贴边栏限制下限 0 避免 fastSpatialSpec 回弹时底缘短暂悬空露缝；悬浮胶囊保留自然弹性
                            translationY = if (style == BottomNavBarStyle.DOCKED) {
                                rawTranslationY.coerceAtLeast(0f)
                            } else {
                                rawTranslationY
                            }
                            alpha = (1f - hiddenFraction).coerceIn(0f, 1f)
                        }
                    when (style) {
                        BottomNavBarStyle.DOCKED -> SlidingIndicatorNavigationBar(
                            items = items,
                            selectedRoute = selectedRoute,
                            onSelect = onSelect,
                            haptic = haptic,
                            modifier = barModifier,
                        )
                        BottomNavBarStyle.FLOATING_CAPSULE -> FloatingCapsuleNavigationBar(
                            items = items,
                            selectedRoute = selectedRoute,
                            onSelect = onSelect,
                            haptic = haptic,
                            modifier = barModifier,
                        )
                    }
                }
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
 * 计算当前底部导航栏选中项滑块指示器的动画颜色（彩色风格取对应 Tab 点缀色淡底，单色模式取 `secondaryContainer`）。
 *
 * @param items 导航项列表
 * @param selectedIndex 当前选中索引（`< 0` 时返回透明）
 * @param colorStyle 是否开启彩色风格
 * @return 当前帧平滑插值后的指示器背景色
 */
@Composable
private fun animateNavIndicatorColor(
    items: List<AdaptiveNavItem>,
    selectedIndex: Int,
    colorStyle: Boolean,
): Color {
    val colorScheme = MaterialTheme.colorScheme
    val colorsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
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
    return indicatorColor
}

/**
 * 自绘贴边底部导航栏（M3 Expressive Short Navigation Bar）：单一指示器胶囊跨 item 位移。
 *
 * ## 为什么不用 M3 `NavigationBar`
 * M3 的 `NavigationBarItem` 指示器在每个 item 内部各自缩放淡入淡出，切换时无法表达
 * 「指示器从前一项滑到新项」。这里改为在整栏上叠唯一滑块，用 `animateDpAsState` 做横向位移。
 *
 * ## 结构
 * 全宽 64dp 短栏铺底并延伸进系统导航栏 inset，顶部附 `0.5dp` 细线；指示器 `56×32dp` 居中对齐图标。
 * 选中色取该 Tab 的 `accent`（经 [resolveAccent]，彩色风格关闭时回退主题角色）。
 *
 * @param items 导航项列表
 * @param selectedRoute 当前选中路由（null 时无指示器）
 * @param onSelect 点击导航项回调
 * @param haptic 触觉回调（SELECT 档，仅真正切页时触发）
 * @param modifier 外部修饰符（用于滚动隐显位移与测量）
 * @return 无返回值
 */
@Composable
private fun SlidingIndicatorNavigationBar(
    items: List<AdaptiveNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    haptic: (PixivHaptics.HapticCue) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val colorScheme = MaterialTheme.colorScheme
    val colorStyle = LocalColorStyleEnabled.current
    // 选中索引：用于指示器位移目标；无选中时 -1（不渲染指示器）
    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 顶部 0.5dp 细分割线：让内容滚动至导航栏下方时仍保持清晰层次边界
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colorScheme.outlineVariant.copy(alpha = 0.35f))
                    .align(Alignment.TopCenter),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    // 底栏背景延伸进系统导航栏区域（M3 默认行为：容器色铺满 + 内容避让 inset）
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
                val indicatorColor = animateNavIndicatorColor(items, selectedIndex, colorStyle)

                // 指示器层：唯一滑块，随选中索引横向滑动
                if (selectedIndex >= 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = offset)
                            // 指示器水平居中于条目区、垂直与图标对齐（M3 Expressive Short Bar 64dp：距顶 6dp）
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
                                }
                                .padding(top = NavIndicatorTopPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top,
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
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = Spacing.xxs),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** M3 Expressive Short Navigation Bar 内容高度（不含系统导航栏 inset）。 */
private val NavBarHeight = 64.dp

/** 选中指示器尺寸（M3 ActiveIndicatorWidth × ActiveIndicatorHeight）。 */
private val NavIndicatorWidth = 56.dp
private val NavIndicatorHeight = 32.dp

/** 指示器距栏顶偏移：在 64dp 短栏内与 32dp 图标框垂直位置严格对齐。 */
private val NavIndicatorTopPadding = 6.dp

/**
 * 悬浮胶囊导航栏（方案 A）：水平内缩、全圆 `AppShapes.pill` 悬浮容器 + 等高同心内嵌滑块。
 *
 * ## 几何与对比度设计
 * - 外层定位盒透明且不挂载点击事件，确保胶囊两侧与底缘空白区域的触摸手势直接穿透到底层列表。
 * - 外层胶囊高 `64.dp`（圆角 `32.dp`）搭配 `4.dp` 内边距，内部滑块高 `56.dp`（圆角 `28.dp`），
 *   严格满足同心圆角关系，首尾项指示器完全贴合胶囊外缘且不裁切。
 * - 单色模式下文字位于 `secondaryContainer` 滑块内部，统一使用 `onSecondaryContainer` 以保障对比度。
 *
 * @param items 导航项列表
 * @param selectedRoute 当前选中路由（null 时无指示器）
 * @param onSelect 点击导航项回调
 * @param haptic 触觉回调（SELECT 档，仅真正切页时触发）
 * @param modifier 外部修饰符（用于滚动隐显位移与测量）
 * @return 无返回值
 */
@Composable
private fun FloatingCapsuleNavigationBar(
    items: List<AdaptiveNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    haptic: (PixivHaptics.HapticCue) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val colorScheme = MaterialTheme.colorScheme
    val colorStyle = LocalColorStyleEnabled.current
    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()

    // 透明定位盒：只负责避让系统导航栏并留出悬浮边距，不吞触摸事件
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(horizontal = FloatingNavHorizontalMargin)
            .padding(bottom = FloatingNavBottomMargin),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = FloatingNavMaxWidth)
                .fillMaxWidth()
                .height(FloatingNavBarHeight),
            shape = AppShapes.pill,
            color = colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            border = BorderStroke(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(FloatingNavInnerPadding),
            ) {
                val itemWidth = maxWidth / items.size
                val targetOffset = if (selectedIndex >= 0) itemWidth * selectedIndex else 0.dp
                val offset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = spatialSpec,
                    label = "floatingNavIndicatorOffset",
                )
                val indicatorColor = animateNavIndicatorColor(items, selectedIndex, colorStyle)

                if (selectedIndex >= 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = offset)
                            .size(width = itemWidth, height = maxHeight)
                            .clip(AppShapes.pill)
                            .background(indicatorColor),
                    )
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    items.forEach { item ->
                        val selected = item.route == selectedRoute
                        val selectedTint = if (colorStyle) {
                            resolveAccent(
                                if (item.accent == Color.Unspecified) colorScheme.primary else item.accent,
                            )
                        } else {
                            // 悬浮胶囊中标签位于 secondaryContainer 滑块内部，单色模式统一用 onSecondaryContainer 保对比度
                            colorScheme.onSecondaryContainer
                        }
                        val interactionSource = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
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
                            NavBarIcon(
                                selected = selected,
                                icon = item.icon,
                                selectedIcon = item.selectedIcon,
                                label = item.label,
                                tint = if (selected) selectedTint else colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) selectedTint else colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = Spacing.xxs),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 悬浮胶囊导航栏高度与外/内边距常数（外圆角 32dp 与内滑块圆角 28dp 同心）。 */
private val FloatingNavBarHeight = 64.dp
private val FloatingNavHorizontalMargin = 16.dp
private val FloatingNavBottomMargin = 12.dp
private val FloatingNavMaxWidth = 420.dp
private val FloatingNavInnerPadding = 4.dp

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
