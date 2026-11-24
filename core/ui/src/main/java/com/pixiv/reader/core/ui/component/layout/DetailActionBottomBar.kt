package com.pixiv.reader.core.ui.component.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.config.BottomNavBarStyle
import com.pixiv.reader.core.common.ui.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.LocalColorStyleEnabled
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.resolveAccent

data class DetailBottomAction(
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val accent: Color = Color.Unspecified,
    val activeIconTint: Color? = null,
    val iconModifier: Modifier = Modifier,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

/** 覆盖式详情底栏为下方滚动内容预留的恒定净空（不含系统导航栏 inset），与主壳底栏几何一致。 */
fun computeDetailActionBarBottomPadding(style: BottomNavBarStyle): Dp = when (style) {
    BottomNavBarStyle.DOCKED -> NavBarHeight
    BottomNavBarStyle.FLOATING_CAPSULE -> FloatingNavBarHeight + FloatingNavBottomMargin
}

/**
 * 详情页统一底部操作栏（插画详情 / 小说详情 / 详情 pane 共用）：
 * 跟随 [LocalBottomNavBarStyle] 在贴边栏与悬浮胶囊间切换，并支持配合 [BottomBarAutoHideController] 滚动自动显隐。
 */
@Composable
fun DetailActionBottomBar(
    actions: List<DetailBottomAction>,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    style: BottomNavBarStyle = LocalBottomNavBarStyle.current,
    navigationBarInset: Boolean = true,
) {
    if (actions.isEmpty()) return
    var barHeightPx by remember { mutableIntStateOf(0) }
    val hiddenFraction by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "detailActionBarAutoHide",
    )
    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val scaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    AnimatedContent(
        targetState = style,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(animationSpec = fadeSpec) +
                scaleIn(initialScale = 0.96f, animationSpec = scaleSpec)) togetherWith
                fadeOut(animationSpec = fadeSpec)
        },
        label = "detailActionBarStyleSwitch",
    ) { currentStyle ->
        val animatedModifier = Modifier
            .onSizeChanged { if (currentStyle == style) barHeightPx = it.height }
            .graphicsLayer {
                val rawTranslationY = barHeightPx * hiddenFraction
                // 贴边栏钳制下限 0，避免弹簧回弹时底缘悬空露缝；悬浮胶囊保留自然弹性
                translationY = if (currentStyle == BottomNavBarStyle.DOCKED) {
                    rawTranslationY.coerceAtLeast(0f)
                } else {
                    rawTranslationY
                }
                alpha = (1f - hiddenFraction).coerceIn(0f, 1f)
            }

        when (currentStyle) {
            BottomNavBarStyle.DOCKED -> DockedDetailActionBar(
                actions = actions,
                navigationBarInset = navigationBarInset,
                modifier = animatedModifier,
            )
            BottomNavBarStyle.FLOATING_CAPSULE -> FloatingDetailActionBar(
                actions = actions,
                navigationBarInset = navigationBarInset,
                modifier = animatedModifier,
            )
        }
    }
}

@Composable
private fun DockedDetailActionBar(
    actions: List<DetailBottomAction>,
    navigationBarInset: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (navigationBarInset) {
                            Modifier.windowInsetsPadding(NavigationBarDefaults.windowInsets)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = MAX_CONTENT_WIDTH_DP.dp)
                        .fillMaxWidth()
                        .height(NavBarHeight)
                        .padding(horizontal = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { action ->
                        DetailBottomActionItem(
                            action = action,
                            floating = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingDetailActionBar(
    actions: List<DetailBottomAction>,
    navigationBarInset: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (navigationBarInset) {
                    Modifier.windowInsetsPadding(NavigationBarDefaults.windowInsets)
                } else {
                    Modifier
                },
            )
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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(FloatingNavInnerPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    DetailBottomActionItem(
                        action = action,
                        floating = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailBottomActionItem(
    action: DetailBottomAction,
    floating: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val colorStyle = LocalColorStyleEnabled.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "detailActionPressScale",
    )
    val accentTint = resolveAccent(
        if (action.accent == Color.Unspecified) colorScheme.primary else action.accent,
    )
    val activeBase = action.activeIconTint ?: accentTint
    val activeBgTarget = when {
        !action.active -> Color.Transparent
        colorStyle || action.activeIconTint != null -> activeBase.copy(alpha = 0.16f)
        else -> colorScheme.secondaryContainer
    }
    val activeBg by animateColorAsState(
        targetValue = activeBgTarget,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "detailActionBg",
    )
    val iconTint = when {
        !action.enabled -> colorScheme.onSurfaceVariant
        action.active -> action.activeIconTint ?: if (colorStyle) accentTint else colorScheme.onSecondaryContainer
        colorStyle -> accentTint
        else -> colorScheme.onSurfaceVariant
    }
    val labelTint = when {
        !action.enabled -> colorScheme.onSurfaceVariant
        action.active -> when {
            colorStyle || action.activeIconTint != null -> activeBase
            floating -> colorScheme.onSecondaryContainer
            else -> colorScheme.onSurface
        }
        else -> colorScheme.onSurfaceVariant
    }

    val clickModifier = if (action.onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = action.enabled,
            onClick = action.onClick,
            onLongClick = action.onLongClick,
        )
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = action.enabled,
            onClick = action.onClick,
        )
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(AppShapes.pill)
            .then(if (floating) Modifier.background(activeBg) else Modifier)
            .then(clickModifier)
            .alpha(if (action.enabled) 1f else 0.42f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (floating) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = action.iconModifier.size(DetailActionIconSize),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = DockedPillWidth, height = DockedPillHeight)
                    .clip(AppShapes.pill)
                    .background(activeBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = action.iconModifier.size(DetailActionIconSize),
                )
            }
        }
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (action.active) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = labelTint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
}

private val DetailActionIconSize = 21.dp
private val DockedPillWidth = 52.dp
private val DockedPillHeight = 30.dp
