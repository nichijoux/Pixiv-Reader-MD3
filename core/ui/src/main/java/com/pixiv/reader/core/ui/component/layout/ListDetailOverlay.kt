package com.pixiv.reader.core.ui.component.layout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 主列表限宽上限（与 `MAX_CONTENT_WIDTH_DP` 一致；pane 模式下列表会适度让位）。 */
private const val LIST_MAX_WIDTH_DP = 760

/** 主列表与详情 pane 之间的固定间隙。 */
private const val PANE_GAP_DP = 24

/** 详情 pane 最小 / 最大宽度（过窄详情不可用，过宽浪费空间）。 */
private const val PANE_MIN_DP = 280
private const val PANE_MAX_DP = 460

/** pane 模式主列表保底宽度（低于此值列表过窄不可用，不启用 pane）。 */
private const val LIST_MIN_DP = 400

/** pane 宽度占内容区的比例（pane 优先，列表吃剩余）。 */
private const val PANE_WIDTH_FRACTION = 0.34f

/**
 * 计算内容区宽度 [contentWidth]（dp，平板已减 NavigationRail）下的详情 pane 宽度；
 * 列表剩余空间不足 [LIST_MIN_DP] 时返回 null（不启用 pane 模式）。
 * [ListDetailOverlay] 与 [isDetailPaneEnabled] 共用同一公式，保证判定一致。
 *
 * @param contentWidth 内容区宽度（dp）
 * @return pane 宽度（dp）；空间不足返回 null
 */
internal fun detailPaneWidth(contentWidth: Float): Float? {
    val pane = (contentWidth * PANE_WIDTH_FRACTION).coerceIn(PANE_MIN_DP.toFloat(), PANE_MAX_DP.toFloat())
    val list = contentWidth - pane - PANE_GAP_DP
    return if (list >= LIST_MIN_DP) pane else null
}

/**
 * 平板 Master-Detail 双栏壳：主列表左移让位 + 详情 pane 从右侧滑入。
 *
 * ## 布局原理
 * - 未选中（[selected] == null）：主列表按 [listContent] 自行限宽居中（传入限宽 = 760，
 *   保持现在样式全宽浏览）；pane 在屏幕右侧外不可见
 * - 选中后：**pane 优先**——pane 宽 = 内容区 34%（夹在 280~460dp），主列表限宽动态收缩
 *   `lerp(760 → 内容区−pane−间隙)` 让位（瀑布流列数自适应微调），同时整块左移
 *   `(内容区−列表宽)/2` 从居中平移到贴左；动画 `tween(250)`
 * - 返回键 / [onClose] 反向恢复
 *
 * ## 启用条件
 * 列表让位后剩余宽度 ≥ [LIST_MIN_DP]（≈ 内容区 ≥ 704dp，覆盖竖屏 800dp 平板 / 手机横屏）
 * 才启用 pane 模式；手机竖屏不启用——组件退化为主列表原样全宽显示。
 * 调用方点击分流用 [isDetailPaneEnabled] 保持同一判定。
 *
 * @param selected 当前选中项（null = 未选中，列表全宽显示）
 * @param onClose 关闭 pane 回调（返回键与 pane 关闭按钮共用）
 * @param modifier 外部传入的 Modifier（通常带 padding；必须位于内容区**全宽**位置）
 * @param listContent 主列表内容；接收当前限宽（未选中 760 / 选中让位后的宽度），
 *   内部用该值限宽（如 [AdaptiveContentBox]（maxWidth = 传入值））
 * @param detailPane 右侧详情内容（常驻组合，未选中时在屏幕外；内部三态自管）
 */
@Composable
fun ListDetailOverlay(
    selected: Any?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    listContent: @Composable (listMaxWidth: Dp) -> Unit,
    detailPane: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val contentWidth = maxWidth
        val paneWidth = detailPaneWidth(contentWidth.value)
        val enabled = paneWidth != null
        // 选中时列表让位后的限宽（未选中保持 760 全宽浏览）
        val listWidth = if (enabled) contentWidth - paneWidth!!.dp - PANE_GAP_DP.dp else LIST_MAX_WIDTH_DP.dp
        val progress by animateFloatAsState(
            targetValue = if (enabled && selected != null) 1f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "listDetailProgress",
        )
        // 列表限宽瞬切（选中/未选中各一次重排）——不做逐帧动画：
        // 限宽动画会让瀑布流每帧重排列数/布局导致掉帧，平移才用 GPU 动画
        val listMax = if (enabled && selected != null) listWidth else LIST_MAX_WIDTH_DP.dp
        // 左移量：未选中 0（内容居中）→ 选中 (内容区−列表宽)/2（内容贴左，右侧全给 pane）
        val density = LocalDensity.current
        val shiftPx = with(density) { ((contentWidth - listWidth) / 2 * progress).toPx() }
        val panePx = with(density) { (paneWidth ?: 300f).dp.toPx() }

        // 返回键关闭 pane（仅 pane 可见时拦截，不干扰列表自身返回行为）
        BackHandler(enabled = enabled && selected != null) { onClose() }

        Box(modifier = Modifier.fillMaxSize()) {
            // 主列表：整体平移（内部按 listMax 限宽重排，列数自适应微调）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = -shiftPx },
            ) {
                listContent(listMax)
            }
            // 详情 pane：从屏幕右侧滑入，停靠在列表让出的空间
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(paneWidth!!.dp)
                    .graphicsLayer { translationX = panePx * (1f - progress) },
            ) {
                detailPane()
            }
        }
    }
}

/**
 * 当前窗口是否启用 Master-Detail pane 模式（列表让位后仍有 ≥ [LIST_MIN_DP] 剩余）。
 * 与 [ListDetailOverlay] 内部判定一致：平板减 NavigationRail 后计算内容区宽。
 * 调用方在「点击列表项」分流时使用：启用 → 设置选中项进 pane；否则 → 全屏路由跳转。
 */
@Composable
fun isDetailPaneEnabled(): Boolean {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    // 平板（≥600dp）内容区需减左侧 NavigationRail 宽（84dp）
    val contentWidth = if (screenWidth >= 600) screenWidth - 84 else screenWidth
    return detailPaneWidth(contentWidth.toFloat()) != null
}
