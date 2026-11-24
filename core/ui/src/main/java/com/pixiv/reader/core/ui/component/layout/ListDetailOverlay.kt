package com.pixiv.reader.core.ui.component.layout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.pixiv.reader.core.ui.component.detail.LocalNavAnimatedVisibilityScope

private const val LIST_MAX_WIDTH_DP = 760
private const val PANE_GAP_DP = 24
private const val PANE_MIN_DP = 280
private const val PANE_MAX_DP = 460
private const val LIST_MIN_DP = 400
private const val PANE_WIDTH_FRACTION = 0.34f

/** 按内容区宽（dp）算详情 pane 宽度，剩余不足 [minListWidth] 返回 null；与 [ListDetailOverlay]/[isDetailPaneEnabled] 共用。 */
fun detailPaneWidth(contentWidth: Float, minListWidth: Float = LIST_MIN_DP.toFloat()): Float? {
    val pane = (contentWidth * PANE_WIDTH_FRACTION).coerceIn(PANE_MIN_DP.toFloat(), PANE_MAX_DP.toFloat())
    val list = contentWidth - pane - PANE_GAP_DP
    return if (list >= minListWidth) pane else null
}

/**
 * 平板 Master-Detail 双栏壳：列表限宽按窗口尺寸固定（避免开合动画期瀑布流列数突变重排），
 * 纯 GPU 平移让位并下发 [LocalNavAnimatedVisibilityScope] 供 pane 内 hero 跨栏过渡。
 * pane 关闭后随组合销毁重建（不保留内部 remember 状态）；[onBack] 为 null 时默认 [onClose]。
 */
@Composable
fun ListDetailOverlay(
    selected: Any?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    minListWidth: Dp = LIST_MIN_DP.dp,
    onBack: (() -> Unit)? = null,
    listContent: @Composable (listMaxWidth: Dp) -> Unit,
    detailPane: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val contentWidth = maxWidth
        val paneWidth = detailPaneWidth(contentWidth.value, minListWidth.value)
        val enabled = paneWidth != null
        val listWidth = if (enabled) contentWidth - paneWidth!!.dp - PANE_GAP_DP.dp
        else minOf(LIST_MAX_WIDTH_DP.dp, contentWidth)
        val progress by animateFloatAsState(
            targetValue = if (enabled && selected != null) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "listDetailProgress",
        )
        // enter/exit 在组合期构造，与列表让位共用同一 spatial 规格
        val enterSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
        val exitSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()

        val density = LocalDensity.current
        val listShiftBasePx = with(density) { ((contentWidth - listWidth) / 2).toPx() }

        // pane 内更深层 BackHandler 注册更晚、优先级更高，不受此处拦截影响
        BackHandler(enabled = enabled && selected != null) { (onBack ?: onClose)() }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = -listShiftBasePx * progress },
            ) {
                listContent(listWidth)
            }
            // 窄屏 enabled=false 时整棵子树直接卸载，同时避免 paneWidth!! 空指针
            if (enabled) {
                AnimatedVisibility(
                    visible = selected != null,
                    enter = slideInHorizontally(enterSpec) { it },
                    exit = slideOutHorizontally(exitSpec) { it },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(paneWidth!!.dp),
                ) {
                    CompositionLocalProvider(
                        LocalNavAnimatedVisibilityScope provides this@AnimatedVisibility,
                    ) {
                        detailPane()
                    }
                }
            }
        }
    }
}

/**
 * 按窗口宽估算是否启用 Master-Detail pane（供列表点击分流）。
 * 主壳内 Tab 传 `subtractRail = true`（扣除 84dp 侧栏）；无侧栏的全屏路由页须传 false，否则阈值偏严。
 */
@Composable
fun isDetailPaneEnabled(subtractRail: Boolean = true): Boolean {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val contentWidth = if (subtractRail && screenWidth >= 600) screenWidth - 84 else screenWidth
    return detailPaneWidth(contentWidth.toFloat()) != null
}
