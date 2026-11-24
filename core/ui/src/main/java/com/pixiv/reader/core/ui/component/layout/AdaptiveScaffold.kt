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

/** 自适应导航项；[accent] 为 [Color.Unspecified] 或关闭彩色风格时经 [resolveAccent] 回退 primary。 */
data class AdaptiveNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val accent: Color = Color.Unspecified,
)

@Composable
fun currentWindowSizeClass(): WindowSizeClass =
    classifyWindowWidth(LocalConfiguration.current.screenWidthDp)

/**
 * 手机端覆盖底栏为可滚动列表预留的恒定底部净空（不含系统导航栏 inset）。
 * 显隐期间保持恒定，避免动态改 `contentPadding` 触发瀑布流/列表重排抖动；侧栏及隐藏底栏页为 `0.dp`。
 */
val LocalNavBarBottomPadding = compositionLocalOf { 0.dp }

/** 手机端底栏样式，由 MainActivity 依 `UserPreferences.bottomNavBarStyle` 注入。 */
val LocalBottomNavBarStyle = staticCompositionLocalOf { BottomNavBarStyle.DOCKED }

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
 * 仅观察 [NestedScrollConnection.onPostScroll] 的 `consumed.y`，避免不可滚动页或触底过冲误隐藏；
 * 隐藏态在顶部继续下拉（`consumed.y == 0f && available.y > 0f`）时用 `available.y` 呼出。
 */
@Stable
internal class BottomBarAutoHideController(
    private val hideThresholdPx: Float,
    private val showThresholdPx: Float,
) {
    var isVisible by mutableStateOf(true)
        private set

    private var accumulatedDeltaY = 0f

    fun show() {
        isVisible = true
        accumulatedDeltaY = 0f
    }

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
 * 主壳自适应导航：Compact 走覆盖式底栏（滚动自动显隐，通过 [LocalNavBarBottomPadding] 预留恒定净空），
 * Medium+ 走左侧 [WideNavigationRail]；[content] 固定接收 `PaddingValues(0.dp)`。
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
    val haptic = rememberHapticCue()
    val density = LocalDensity.current
    val autoHideController = remember(density) {
        BottomBarAutoHideController(
            hideThresholdPx = with(density) { 40.dp.toPx() },
            showThresholdPx = with(density) { 24.dp.toPx() },
        )
    }

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
            // 刻意不调用 consumeWindowInsets，避免手机端覆盖模式下子页面拿不到真实 navigationBars inset 而被手势条遮挡
            Row(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = useRail && showBottomBar,
                    enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                ) {
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
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.82f,
        animationSpec = spatialSpec,
        label = "navBarIconScale",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.86f,
        animationSpec = effectsSpec,
        label = "navBarIconAlpha",
    )
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
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

/** 自绘贴边底栏：M3 NavigationBarItem 指示器在各自 item 内缩放，改用整栏唯一滑块实现跨项位移。 */
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
    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
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
                    .windowInsetsPadding(NavigationBarDefaults.windowInsets),
            ) {
                val itemWidth = maxWidth / items.size
                val indicatorWidth = NavIndicatorWidth
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

                if (selectedIndex >= 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = offset)
                            .padding(top = NavIndicatorTopPadding)
                            .size(width = indicatorWidth, height = NavIndicatorHeight)
                            .clip(AppShapes.pill)
                            .background(indicatorColor),
                    )
                }

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
                        // 无涟漪：由滑块与图标缩放表达选中态，避免涟漪盖住滑块层次
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
                                    // 重复点当前页签只做回顶，不响触觉
                                    if (!selected) {
                                        haptic(PixivHaptics.HapticCue.SELECT)
                                    }
                                    onSelect(item.route)
                                }
                                .padding(top = NavIndicatorTopPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top,
                        ) {
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

private val NavBarHeight = 64.dp
private val NavIndicatorWidth = 56.dp
private val NavIndicatorHeight = 32.dp
private val NavIndicatorTopPadding = 6.dp

/**
 * 悬浮胶囊底栏：外层透明定位盒不挂点击让两侧空白透传底层列表；
 * 64dp 外胶囊配 4dp 内边距使滑块圆角（28dp）与外缘（32dp）严格同心。
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
                            // 悬浮胶囊中标签位于 secondaryContainer 滑块内，单色模式用 onSecondaryContainer 保对比度
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

private val FloatingNavBarHeight = 64.dp
private val FloatingNavHorizontalMargin = 16.dp
private val FloatingNavBottomMargin = 12.dp
private val FloatingNavMaxWidth = 420.dp
private val FloatingNavInnerPadding = 4.dp

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

/** 限宽 [maxWidth] 约束 TopAppBar 标题区，避免宽屏下与下方 [AdaptiveContentBox] 错位。 */
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
