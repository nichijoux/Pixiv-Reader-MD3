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

/** 主列表限宽上限（与 `MAX_CONTENT_WIDTH_DP` 一致；pane 不可用时列表按此值限宽）。 */
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
 * 列表剩余空间不足 [minListWidth] 时返回 null（不启用 pane 模式）。
 * [ListDetailOverlay] 与 [isDetailPaneEnabled] 共用同一公式，保证判定一致；
 * 公开供调用方按自身实际可用宽度实算 pane 可用性（如关注页：内容区需先扣除固定用户列宽）。
 *
 * @param contentWidth 内容区宽度（dp）
 * @param minListWidth 列表保底宽度（dp），默认 [LIST_MIN_DP]；关注页等有固定前导列的页面
 *   传更小的值（与其列表单列宽度下限一致）
 * @return pane 宽度（dp）；空间不足返回 null
 */
fun detailPaneWidth(contentWidth: Float, minListWidth: Float = LIST_MIN_DP.toFloat()): Float? {
    val pane = (contentWidth * PANE_WIDTH_FRACTION).coerceIn(PANE_MIN_DP.toFloat(), PANE_MAX_DP.toFloat())
    val list = contentWidth - pane - PANE_GAP_DP
    return if (list >= minListWidth) pane else null
}

/**
 * 平板 Master-Detail 双栏壳：主列表左移让位 + 详情 pane 从右侧滑入。
 *
 * ## 布局原理
 * - **列表宽度按窗口尺寸固定**（与 pane 开合状态无关）：pane 可用时 = `内容区 − pane − 间隙`，
 *   不可用时 = min(760, 内容区)。窗口尺寸不变期间宽度恒定，瀑布流列数在开合动画中
 *   **零重排、零重组**——彻底消除「列数随开合突变」与「重排帧与动画帧争抢主线程掉帧」
 *   （瀑布流列数是宽度的阶梯函数，只能取整数，任何宽度过渡都无法让列数连续变化，
 *   唯一无突变的做法是让开合动画不改变宽度）。代价：空闲态列表即以让位宽度居中显示
 *   （两侧留白），不再 3 列满宽
 * - 选中后：整块列表左移 `(内容区−列表宽)/2` 从居中平移到贴左，pane 从右侧滑入
 *   `内容区×34%`（夹在 280~460dp）；平移动画 `tween(250)` 纯 GPU（graphicsLayer 内读
 *   progress，动画帧不重组）
 * - 返回键 / [onClose] 反向恢复；快速连点开-关时动画中途反向，纯位移自然衔接
 *
 * ## 启用条件
 * 列表让位后剩余宽度 ≥ [LIST_MIN_DP]（≈ 内容区 ≥ 704dp，覆盖竖屏 800dp 平板 / 手机横屏）
 * 才启用 pane 模式；手机竖屏不启用——组件退化为主列表原样全宽显示。
 * 调用方点击分流用 [isDetailPaneEnabled] 保持同一判定。
 *
 * @param selected 当前选中项（null = 未选中，列表居中显示）
 * @param onClose 关闭 pane 回调（返回键与 pane 关闭按钮共用）
 * @param modifier 外部传入的 Modifier（通常带 padding；必须位于内容区**全宽**位置）
 * @param minListWidth 列表保底宽度（dp）：pane 启用判定的列表下限。默认 400dp（通用页面）；
 *   有固定前导列或列表可接受单列的页面（如关注页，瀑布流单列下限 240dp）传更小值，
 *   让较窄内容区（竖屏平板）仍可启用 pane。判定与 [detailPaneWidth] 同公式
 * @param listContent 主列表内容；接收当前限宽（按窗口尺寸固定的值，pane 可用时为
 *   内容区−pane−间隙，否则 min(760, 内容区)），内部用该值限宽居中
 *   （如 [AdaptiveContentBox]（maxWidth = 传入值））
 * @param detailPane 右侧详情内容（常驻组合，未选中时在屏幕外；内部三态自管）
 */
@Composable
fun ListDetailOverlay(
    selected: Any?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    minListWidth: Dp = LIST_MIN_DP.dp,
    listContent: @Composable (listMaxWidth: Dp) -> Unit,
    detailPane: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val contentWidth = maxWidth
        val paneWidth = detailPaneWidth(contentWidth.value, minListWidth.value)
        val enabled = paneWidth != null
        // 列表限宽按窗口尺寸固定，与开合状态无关：窗口尺寸不变期间瀑布流永不重排
        val listWidth = if (enabled) contentWidth - paneWidth!!.dp - PANE_GAP_DP.dp
        else minOf(LIST_MAX_WIDTH_DP.dp, contentWidth)
        val progress by animateFloatAsState(
            targetValue = if (enabled && selected != null) 1f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "listDetailProgress",
        )

        // 左移量在绘制阶段逐帧计算（graphicsLayer 内读 progress State，动画帧不触发重组）：
        // 未选中 0（内容居中）→ 选中 (内容区−列表宽)/2（内容贴左，右侧全给 pane）。
        // 列表宽度恒定，平移终点恰好是「内容居中边距」，动画全程内容不会越过区域左缘
        val density = LocalDensity.current
        val listShiftBasePx = with(density) { ((contentWidth - listWidth) / 2).toPx() }
        val panePx = with(density) { (paneWidth ?: 300f).dp.toPx() }

        // 返回键关闭 pane（仅 pane 可见时拦截，不干扰列表自身返回行为）
        BackHandler(enabled = enabled && selected != null) { onClose() }

        Box(modifier = Modifier.fillMaxSize()) {
            // 主列表：整体平移（GPU 层属性动画；宽度恒定，动画中零重排）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = -listShiftBasePx * progress },
            ) {
                listContent(listWidth)
            }
            // 详情 pane：从屏幕右侧滑入，停靠在列表让出的空间。
            // 仅内容区 ≥ [LIST_MIN_DP]（[paneWidth] 非 null）时组合——空间不足（手机竖屏）
            // 组件退化为主列表全宽，此时组合 pane 会因 `paneWidth!!` 空指针崩溃
            if (enabled) {
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
}

/**
 * 当前窗口是否启用 Master-Detail pane 模式（列表让位后仍有 ≥ [LIST_MIN_DP] 剩余）。
 * 与 [ListDetailOverlay] 内部判定一致（[ListDetailOverlay] 用真实可用宽计算，此处按窗口宽估算）。
 * 调用方在「点击列表项」分流时使用：启用 → 设置选中项进 pane；否则 → 全屏路由跳转。
 *
 * @param subtractRail 是否减去左侧 NavigationRail 宽（84dp）。壳内 Tab（MainShell）传默认
 *   true；全屏路由页（排行榜等，无 rail，TopAppBar 全宽）传 false，否则阈值偏严漏启 pane。
 */
@Composable
fun isDetailPaneEnabled(subtractRail: Boolean = true): Boolean {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    // 平板（≥600dp）内容区需减左侧 NavigationRail 宽（84dp）
    val contentWidth = if (subtractRail && screenWidth >= 600) screenWidth - 84 else screenWidth
    return detailPaneWidth(contentWidth.toFloat()) != null
}
