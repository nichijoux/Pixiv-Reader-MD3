package com.pixiv.reader.feature.reader.ui

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.feature.reader.R
import com.pixiv.reader.feature.reader.state.ReaderPage
import com.pixiv.reader.feature.reader.state.ReaderSpread
import com.pixiv.reader.feature.reader.state.spreadIndexForChar
import kotlinx.coroutines.launch

/** 仿真翻页调试日志 TAG。 */
private const val TAG = "SimulationPage"

/** 回弹/翻过动画基准时长（ms，按剩余行程比例缩短）。 */
private const val SETTLE_BASE_MS = 300

/** 回弹/翻过动画最短时长（ms）。 */
private const val SETTLE_MIN_MS = 120

/** 松手完成阈值：翻页进度 ≥ 0.5 判定翻过（否则回弹）。 */
private const val SETTLE_PROGRESS_THRESHOLD = 0.5f

/** 甩动翻页速度阈值（书页坐标 px/s，沿翻页方向）。 */
private const val SETTLE_FLING_VELOCITY = 1200f

/** 速度采样窗口（ms）：松手判定取最近该时段的位移。 */
private const val VELOCITY_WINDOW_MS = 120L

/** 折痕阴影长度（单页宽比例）。 */
private const val FOLD_SHADOW_FRACTION = 0.35f

/** 翻页阶段（规则十：PRESS 并入 DRAGGING 起手，SETTLING 后仅 COMPLETED/CANCELED 两出边）。 */
private enum class Phase { IDLE, DRAGGING, SETTLING }

/**
 * 正在翻转的纸张（TurningSheet，规则九/十五）：
 * 任意时刻至多一个；正反面内容绑定同一方向与同一实时折痕，拖动全程不改 currentIndex。
 *
 * @property direction 翻页方向
 * @property topCorner true = 掀上角（角点 y=0），false = 掀下角（角点 y=H）
 * @property touch 拖动点 A（书脊坐标，已经 [clampDragPoint] 合法化）
 * @property press 按下手指数点（屏幕坐标）：A 从角点出发按相对位移驱动，按下时零折叠
 */
private data class TurnState(
    val direction: TurnDirection,
    val topCorner: Boolean,
    val touch: Offset,
    val press: Offset,
)

/**
 * 仿真翻页：基于「物理纸张双面翻转 + 动态折痕 + 半平面裁剪」的折页效果。
 *
 * 页面模型（规则一）：跨页 spread N = 左页 P(2N-1) | 右页 P(2N)；相邻两页是同一张纸的正反面。
 *
 * 向前翻（卷右页）：TurningSheet 正面 = 当前右页，背面 = 下一跨页左页（P2→P3），
 * 静态底层 = 当前左页 | 下一跨页右页（P1 | P4）；完成后跨页 +1（P3 | P4）。
 * 向后翻（卷左页）严格镜像：正面 = 当前左页，背面 = 上一跨页右页，
 * 静态底层 = 上一跨页左页 | 当前右页；完成后跨页 -1。
 *
 * 几何（规则四~八）：折痕 = 拖动点 A 与角点 B 的垂直平分线；正面 = 页矩形 ∩ A 侧半平面；
 * 背面 = 页矩形 ∩ B 侧半平面沿折痕反射。正反面共享同一张纸：
 * 背面内容画在「翻完后的阅读位」，经 reflect(折痕)∘reflect(书脊) 复合变换映射到当前翻起位置，
 * 全翻时折痕 = 书脊、复合变换 = 恒等、背面恰好可读平铺到目标页槽（无缝切换到新静态跨页）。
 *
 * 单页（columns = 1）：屏幕页 = 书的右页（书脊在屏幕左缘），两个方向动作相反：
 * 向后翻（去下一页）= 当前页从右缘往左翻走，折回区露出下一页文字，底层为下一页；
 * 向前翻（回上一页，对齐 e0864dd 卷角）= 角点固定书脊侧 (0, cy)（屏内），当前页
 * 从左缘向右卷走（纸背呈当前页镜像透字），底层为上一页，卷过大半屏或向右甩动
 * 即落定上一页，可掀一角跟手拖拽。
 * 双页（columns = 2）：对开叶模型，向前卷右叶、向后卷左叶（几何按方向镜像）。
 *
 * 手势（规则十/十一）：PRESS→DRAGGING→SETTLING→COMPLETED/CANCELED；
 * 松手按进度 ≥ 0.5 或沿翻向甩动速度判定完成/回弹，settle 动画驱动同一拖动点走同一几何管线。
 *
 * @param spreads 跨页列表（翻页单元，字符锚点取跨页 startChar）
 * @param columns 列数：1 单页整宽 / 2 双页对开
 * @param pageHeight 页面渲染高度（底部贴底微调用）
 * @param backgroundColor 纸底色（纸背与静态底层铺色）
 * @param restoreCharOffset 恢复进度的字符偏移（首次定位）
 * @param jumpToChar 目录/搜索跳转目标字符偏移
 * @param onPageChange 翻页完成后回调当前跨页下标（上层换算进度）
 * @param onPageInfo 回调当前跨页下标与跨页总数（页码指示）
 * @param contentTopInset 内容顶部额外避让（沉浸式纸面覆盖状态栏时 = 状态栏高度）
 * @param barsVisible 工具栏可见性（点击分区在工具栏显示时仅关闭工具栏）
 * @param onCloseBars 关闭工具栏回调
 * @param onToggleBars 切换工具栏回调
 * @param onPrevChapterRequest 首跨页向前翻：系列跳上一章尾页
 * @param onNextChapterRequest 末跨页向后翻：系列跳下一章开头
 */
@Composable
fun SimulationPageContent(
    spreads: List<ReaderSpread>,
    columns: Int,
    pageHeight: Dp,
    contentTopInset: Dp = 0.dp,
    backgroundColor: Color,
    restoreCharOffset: Int,
    jumpToChar: Int?,
    onPageChange: (Int) -> Unit,
    onPageInfo: (Int, Int) -> Unit,
    barsVisible: Boolean = false,
    onCloseBars: () -> Unit = {},
    onToggleBars: () -> Unit = {},
    onPrevChapterRequest: () -> Unit = {},
    onNextChapterRequest: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var restored by remember { mutableStateOf(false) }
    val currentIndex = remember { mutableIntStateOf(0) }
    // 翻页阶段与正在翻转的纸张（规则十五：唯一 TurningSheet；完成时才改 currentIndex）
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var turn by remember { mutableStateOf<TurnState?>(null) }
    // 页面尺寸（px），由 BoxWithConstraints 填充
    var pageW by remember { mutableStateOf(0f) }
    var pageH by remember { mutableStateOf(0f) }
    // 松手速度判定样本（事件时间 ms → 拖动点 A.x），仅 onDragEnd 读取，无需触发重组
    val velocitySamples = remember { mutableListOf<Pair<Long, Float>>() }

    // 首次定位到上次阅读位置
    LaunchedEffect(spreads, restoreCharOffset) {
        if (restored || spreads.isEmpty()) return@LaunchedEffect
        val index = spreads.spreadIndexForChar(restoreCharOffset)
        currentIndex.intValue = index
        restored = true
        onPageInfo(index, spreads.size)
        onPageChange(index)
    }

    // 翻页完成后上报当前页
    LaunchedEffect(currentIndex.intValue) {
        if (restored) {
            onPageInfo(currentIndex.intValue, spreads.size)
            onPageChange(currentIndex.intValue)
        }
    }

    // 目录/搜索跳转：随时可打断翻页动画
    LaunchedEffect(jumpToChar) {
        val j = jumpToChar ?: return@LaunchedEffect
        if (spreads.isEmpty()) return@LaunchedEffect
        currentIndex.intValue = spreads.spreadIndexForChar(j)
        turn = null
        phase = Phase.IDLE
    }

    if (spreads.isEmpty()) {
        EmptyBox(stringResource(R.string.reader_empty_content), modifier = modifier)
        return
    }

    fun canTurnForward(): Boolean = currentIndex.intValue < spreads.size - 1
    fun canTurnBackward(): Boolean = currentIndex.intValue > 0

    /** 书页几何快照（页宽 = 双页半屏 / 单页整屏，页高 = 排版页高）。 */
    fun currentGeom(): BookGeometry = BookGeometry(
        pageWidth = if (columns >= 2) pageW / 2f else pageW,
        pageHeight = pageH,
    )

    /**
     * 被翻页角点 B：双页按方向取对侧叶缘；**单页向前（回看）取书脊侧角 (0, cy)**——
     * 对齐 e0864dd 卷角实现（上一页固定左下/左上角）：角点在屏内书脊上，
     * 卷角从按下第一刻就在屏内可见（可掀起一角、跟手扩展），A 飞出右缘切页；
     * 单页向后取右缘 (W, cy)（现几何）。
     */
    fun turnCornerFor(t: TurnState, geom: BookGeometry): Offset =
        if (columns <= 1 && t.direction == TurnDirection.BACKWARD) {
            Offset(0f, if (t.topCorner) 0f else geom.pageHeight)
        } else {
            turnCorner(geom, t.direction, t.topCorner)
        }

    /**
     * 松手判定（规则十一）：进度 ≥ 阈值，或近窗速度沿翻向超过甩动阈值 → 完成，否则回弹。
     * 单页向前角点在书脊上，A 行程为 [0, W]（progress 上限 0.5），阈值取 0.25（卷过半屏）。
     */
    fun judgeSettle(): Boolean {
        val t = turn ?: return false
        val geom = currentGeom()
        val corner = turnCornerFor(t, geom)
        val progress = computeFold(geom, corner, t.touch)?.progress ?: 0f
        val threshold = if (columns <= 1 && t.direction == TurnDirection.BACKWARD) {
            SETTLE_PROGRESS_THRESHOLD / 2f
        } else {
            SETTLE_PROGRESS_THRESHOLD
        }
        if (progress >= threshold) return true
        if (velocitySamples.size < 2) return false
        val (t0, x0) = velocitySamples.first()
        val (t1, x1) = velocitySamples.last()
        val dt = (t1 - t0).coerceAtLeast(1L).toFloat()
        val vx = (x1 - x0) / dt * 1000f
        return when (t.direction) {
            TurnDirection.FORWARD -> vx < -SETTLE_FLING_VELOCITY
            TurnDirection.BACKWARD -> vx > SETTLE_FLING_VELOCITY
        }
    }

    /**
     * 收尾动画（规则十二）：驱动拖动点 A 连续运动到目标位——
     * 完成 → reflect(书脊)(B)（翻过；末帧背面可读平铺目标槽，与新静态渲染逐像素一致），
     * 回弹 → B。全程走同一折页几何管线；结束后才提交跨页变更。
     *
     * @param complete true 翻过（COMPLETED），false 回弹（CANCELED）
     */
    suspend fun settleTurn(complete: Boolean) {
        val t = turn ?: return
        val geom = currentGeom()
        phase = Phase.SETTLING
        val corner = turnCornerFor(t, geom)
        // 完成目标：回弹 = 角点；翻过 = 飞出对侧边缘。
        // 单页向前角点在书脊上，须飞到 2W（折痕扫过右缘）：A=W 时折痕仅到半屏，
        // 页面卷到一半就切页会显得"翻一半消失"；A=2W 时折痕 = W，当前页完全卷出
        // 右缘（折回区整体滑出屏外），末帧 = 纯底层上一页，与静态渲染无缝衔接
        val target = when {
            !complete -> corner
            columns <= 1 && t.direction == TurnDirection.BACKWARD ->
                Offset(2f * geom.pageWidth, corner.y)
            else -> Offset(-corner.x, corner.y)
        }
        val from = t.touch
        val full = 2f * geom.pageWidth
        // 时长按剩余行程比例收缩（从角点起手的整程用基准时长）
        val remain = (target - from).getDistance()
        val duration = (SETTLE_BASE_MS * (remain / full.coerceAtLeast(1f)))
            .toInt().coerceIn(SETTLE_MIN_MS, SETTLE_BASE_MS)
        Log.d(TAG, "settle complete=$complete from=${from} target=$target duration=$duration")
        val anim = Animatable(0f)
        anim.animateTo(1f, tween(duration, easing = EaseOutCubic)) {
            // 跳转等外部操作已复位（phase 离开 SETTLING）则放弃本次收尾
            if (phase == Phase.SETTLING) {
                turn = t.copy(
                    touch = Offset(
                        from.x + (target.x - from.x) * anim.value,
                        from.y + (target.y - from.y) * anim.value,
                    ),
                )
            }
        }
        if (phase != Phase.SETTLING) return
        if (complete) {
            // 仅此刻修改正式跨页（规则十五）
            currentIndex.intValue += if (t.direction == TurnDirection.FORWARD) 1 else -1
        }
        turn = null
        phase = Phase.IDLE
    }

    /** 点击翻页（九宫格左右区）：从静止位起手直接播完整翻过动画。 */
    fun startTapTurn(direction: TurnDirection, tapPoint: Offset, w: Float, h: Float) {
        val geom = BookGeometry(if (columns >= 2) w / 2f else w, h)
        val topCorner = tapPoint.y < h / 2f
        val t = TurnState(direction, topCorner, touch = Offset.Zero, press = Offset.Zero)
        // A = B 起手：首帧无折叠（静态），动画随拖动点远离角点连续展开
        turn = t.copy(touch = turnCornerFor(t, geom))
        scope.launch { settleTurn(complete = true) }
    }

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(spreads.size, columns, barsVisible) {
                detectTapGestures(onTap = { offset ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val x = offset.x
                    val y = offset.y
                    // 井字九宫格：中间格切换工具栏；左右半区翻页（工具栏显示时仅关闭，防误翻）
                    val centerCell =
                        x >= w / 3f && x <= 2f * w / 3f && y >= h / 3f && y <= 2f * h / 3f
                    when {
                        centerCell -> onToggleBars()
                        x < w / 2f -> {
                            if (barsVisible) {
                                onCloseBars()
                            } else if (phase == Phase.IDLE) {
                                // 首跨页向前区：转交章节切换（系列上一章尾页）
                                if (canTurnBackward()) startTapTurn(TurnDirection.BACKWARD, offset, w, h)
                                else onPrevChapterRequest()
                            }
                        }
                        else -> {
                            if (barsVisible) {
                                onCloseBars()
                            } else if (phase == Phase.IDLE) {
                                // 末跨页向后区：转交章节切换（系列下一章开头）
                                if (canTurnForward()) startTapTurn(TurnDirection.FORWARD, offset, w, h)
                                else onNextChapterRequest()
                            }
                        }
                    }
                })
            }
            .pointerInput(spreads.size, columns) {
                detectDragGestures(
                    onDragStart = { pos ->
                        if (phase != Phase.IDLE) return@detectDragGestures
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        // 方向：双页按落点半屏；单页左 1/3 为向后（上一张纸自屏外翻入），其余向前
                        val direction =
                            if (pos.x >= (if (columns >= 2) w / 2f else w / 3f)) {
                                TurnDirection.FORWARD
                            } else {
                                TurnDirection.BACKWARD
                            }
                        // 边界（规则十三）：无对应页时不伪造页面，转交章节切换
                        when (direction) {
                            TurnDirection.FORWARD ->
                                if (!canTurnForward()) {
                                    onNextChapterRequest()
                                    return@detectDragGestures
                                }
                            TurnDirection.BACKWARD ->
                                if (!canTurnBackward()) {
                                    onPrevChapterRequest()
                                    return@detectDragGestures
                                }
                        }
                        val geom = BookGeometry(if (columns >= 2) w / 2f else w, h)
                        val topCorner = pos.y < h / 2f
                        val corner = turnCornerFor(
                            TurnState(direction, topCorner, touch = Offset.Zero, press = pos),
                            geom,
                        )
                        // 起手 = 角点（零折叠）：按下瞬间不产生任何翻页量、不露出下层内容，
                        // 折叠量由后续手指相对按下点的位移驱动（规则十 PRESS → DRAGGING）。
                        // 单页向前角点在书脊上（屏内），一拖折痕/卷角立即屏内可见
                        velocitySamples.clear()
                        turn = TurnState(
                            direction = direction,
                            topCorner = topCorner,
                            touch = corner,
                            press = pos,
                        )
                        phase = Phase.DRAGGING
                    },
                    onDrag = { change, _ ->
                        if (phase != Phase.DRAGGING) return@detectDragGestures
                        change.consume()
                        val t = turn ?: return@detectDragGestures
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val geom = BookGeometry(if (columns >= 2) w / 2f else w, h)
                        val corner = turnCornerFor(t, geom)
                        // 相对位移驱动：A = 角点 + （手指 - 按下点）。
                        // 向后（去下一页）角点在右缘：往左拖展开；
                        // 向前（回看）角点在书脊左缘：往右拖展开
                        val delta = Offset(
                            change.position.x - t.press.x,
                            change.position.y - t.press.y,
                        )
                        // 拖动点合法化后重算折痕（规则五：T 必须先 clamp 再进几何）
                        val a = clampDragPoint(geom, corner, corner + delta)
                        // 近窗速度采样（松手甩动判定用）
                        velocitySamples += (change.uptimeMillis to a.x)
                        val cutoff = change.uptimeMillis - VELOCITY_WINDOW_MS
                        while (velocitySamples.size > 2 && velocitySamples.first().first < cutoff) {
                            velocitySamples.removeAt(0)
                        }
                        turn = t.copy(touch = a)
                    },
                    onDragEnd = {
                        val complete = judgeSettle()
                        Log.d(TAG, "dragEnd complete=$complete")
                        velocitySamples.clear()
                        scope.launch { settleTurn(complete) }
                    },
                    onDragCancel = {
                        // 手势被系统打断：无可靠速度，仅按当前进度判定
                        val complete = judgeSettle()
                        velocitySamples.clear()
                        scope.launch { settleTurn(complete) }
                    },
                )
            },
    ) {
        // 页面尺寸（px）：直接用 scope 的 constraints（无需 Density 转换，IDE 可识别 scope 使用）
        pageW = constraints.maxWidth.toFloat()
        pageH = constraints.maxHeight.toFloat()

        val density = LocalDensity.current
        val bookW = if (columns >= 2) pageW / 2f else pageW
        val spineScreenX = if (columns >= 2) bookW else 0f
        // 页面槽位宽（双页半屏 / 单页整屏）
        val slotWidthDp = with(density) { bookW.toDp() }
        val i = currentIndex.intValue
        val turnV = turn
        val forward = turnV?.direction != TurnDirection.BACKWARD

        // ── 规则八：静态底层（双页向前 = currentLeft | nextRight；双页向后 = prevLeft | currentRight；
        //    单页向前 = 下一页（随掀页逐渐露出）；单页向后 = 当前页（保持不动直至完成）；
        //    静止 = 当前跨页）。单页模式静态页恒走左槽整宽渲染。──
        val staticPair: Pair<ReaderPage?, ReaderPage?> = when {
            turnV == null -> {
                val s = spreads.getOrNull(i)
                s?.left to (if (columns >= 2) s?.right else null)
            }
            forward -> when {
                columns >= 2 -> {
                    val s = spreads.getOrNull(i)
                    val n = spreads.getOrNull(i + 1)
                    s?.left to n?.right
                }
                else -> spreads.getOrNull(i + 1)?.left to null
            }
            else -> when {
                columns >= 2 -> {
                    val p = spreads.getOrNull(i - 1)
                    val s = spreads.getOrNull(i)
                    p?.left to s?.right
                }
                // 单页向前（回看）：底层 = 上一页（当前页向右卷走后露出并落定为它）
                else -> spreads.getOrNull(i - 1)?.left to null
            }
        }
        RenderSpreadColumns(
            left = staticPair.first,
            right = staticPair.second,
            columns = columns,
            containerHeight = pageHeight,
            contentTopInset = contentTopInset,
            modifier = Modifier.fillMaxSize(),
        )

        if (turnV != null) {
            val geomNow = currentGeom()
            val corner = turnCornerFor(turnV, geomNow)
            val foldResult = computeFold(geomNow, corner, turnV.touch)
            // 正面两方向都平铺屏内槽位：零折叠帧（foldResult = null）也必须渲染，
            // 完整盖住静态底层（否则起手瞬间闪现下层页）
            if (true) {
                // 书脊坐标 → 屏幕坐标
                val toScreen: (Offset) -> Offset = { Offset(it.x + spineScreenX, it.y) }
                // 零折叠帧（A=B）无折痕多边形：正面按完整页面绘制（无裁剪、无阴影）
                val flatPath = foldResult?.let { polygonToPath(it.flatPolygon.map(toScreen)) }
                val shadowLen = bookW * FOLD_SHADOW_FRACTION

                // 正面（正在翻的纸的平展部分）与背面（纸翻到位后朝上的那面）内容归属：
                // 双页向前：正面 = 当前右页（右槽）、背面 = 下一跨页左页（终位左槽）；
                // 双页向后：正面 = 当前左页（左槽）、背面 = 上一跨页右页（终位右槽）；
                // 单页向后（去下一页）：当前页从右缘往左翻走，背面 = 下一页（终位左槽屏外）；
                // 单页向前（回上一页，对齐 e0864dd 卷角）：当前页从书脊左缘向右卷走，
                //   正面 = 当前页（整宽），背面 = 当前页自身（经折痕反射后呈镜像透字，
                //   灰化后即纸背隐约透字观感），上一页由底层静态层随卷过区域渐显
                val frontPage: ReaderPage?
                val backPage: ReaderPage?
                var frontAlign = Alignment.CenterStart
                var backAlign = Alignment.CenterEnd
                // 背面终位槽位偏移（单页两方向 = 书脊左侧屏外）
                var backSlotOffsetXDp = 0.dp
                when {
                    columns <= 1 -> if (forward) {
                        frontPage = spreads.getOrNull(i)?.left
                        backPage = spreads.getOrNull(i + 1)?.left
                        frontAlign = Alignment.CenterStart
                        backAlign = Alignment.CenterStart
                        backSlotOffsetXDp = -slotWidthDp
                    } else {
                        frontPage = spreads.getOrNull(i)?.left
                        backPage = spreads.getOrNull(i)?.left
                        frontAlign = Alignment.CenterStart
                        backAlign = Alignment.CenterStart
                        backSlotOffsetXDp = -slotWidthDp
                    }
                    forward -> {
                        frontPage = spreads.getOrNull(i)?.right
                        backPage = spreads.getOrNull(i + 1)?.left
                        frontAlign = Alignment.CenterEnd
                        backAlign = Alignment.CenterStart
                    }
                    else -> {
                        frontPage = spreads.getOrNull(i)?.left
                        backPage = spreads.getOrNull(i - 1)?.right
                        frontAlign = Alignment.CenterStart
                        backAlign = Alignment.CenterEnd
                    }
                }

                // ── 层 1：正面静止部分（页矩形 ∩ A 侧半平面）+ 折痕投影阴影 ──
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val clip = flatPath
                            val fold = foldResult
                            if (clip == null || fold == null) {
                                // 零折叠起始帧：正面槽位先铺纸再画内容（纸只铺被翻页所在
                                // 槽位，对侧槽位的静态页保持可见），完全遮住静态下一页
                                val slotX = if (frontAlign == Alignment.CenterEnd) spineScreenX else 0f
                                drawRect(
                                    color = backgroundColor,
                                    topLeft = Offset(slotX, 0f),
                                    size = Size(bookW, size.height),
                                )
                                this@drawWithContent.drawContent()
                            } else {
                                val mid = toScreen(fold.fold.midPoint)
                                clipPath(clip) {
                                    // 纸底必须先铺：正面文字是透明绘制，
                                    // 不铺纸会把静态底层的下一页文字透出来（两层文字重叠）
                                    drawRect(backgroundColor)
                                    this@drawWithContent.drawContent()
                                    // 折痕阴影：由折痕向 A 侧渐隐
                                    drawRect(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.18f),
                                                Color.Transparent,
                                            ),
                                            start = mid,
                                            end = mid - fold.fold.normal * shadowLen,
                                        ),
                                    )
                                }
                            }
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .align(frontAlign)
                            .fillMaxHeight()
                            .width(slotWidthDp),
                    ) {
                        if (frontPage != null) {
                            RenderReaderPage(
                                frontPage,
                                pageHeight,
                                Modifier
                                    .fillMaxSize()
                                    .padding(
                                        start = PAGE_H_PADDING,
                                        end = PAGE_H_PADDING,
                                        top = PAGE_V_PADDING + contentTopInset,
                                        bottom = PAGE_V_PADDING,
                                    ),
                            )
                        }
                    }
                }

                // ── 层 2：背面翻起部分（页矩形 ∩ B 侧半平面沿折痕反射）──
                // 双面共享几何（规则九）：背面内容画在「翻完后的阅读位」，
                // 经 reflect(折痕) ∘ reflect(书脊) 复合变换映射到当前翻起位置；
                // 全翻时折痕=书脊、复合变换=恒等，背面恰好可读平铺目标页槽。
                // 零折叠帧无翻起部分，不渲染。
                if (foldResult != null) {
                    val flapPath = polygonToPath(foldResult.flapReflected.map(toScreen))
                    val midScreen = toScreen(foldResult.fold.midPoint)
                    val dragScreen = toScreen(turnV.touch)
                    val foldLine = foldResult.fold
                    val slotOffsetPx = with(density) { backSlotOffsetXDp.toPx() }
                    Log.d(
                        TAG,
                        "draw dir=${turnV.direction} cols=$columns i=$i prog=${foldResult.progress} " +
                            "flat=${foldResult.flatPolygon.size} flap=${foldResult.flapReflected.size} " +
                            "back=${backPage != null} par=${foldLine.parallelToSpine} off=$backSlotOffsetXDp",
                    )
                    if (columns <= 1) {
                        // ── 单页（手机端）：独立渲染路径，与双页（平板）互不影响 ──
                        SingleFoldBackLayer(
                            foldLine = foldLine,
                            flapPath = flapPath,
                            backPage = backPage,
                            pageHeight = pageHeight,
                            slotWidthDp = slotWidthDp,
                            slotTranslatePx = slotOffsetPx,
                            contentTopInset = contentTopInset,
                            backgroundColor = backgroundColor,
                            shadowBrush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.22f),
                                    Color.Transparent,
                                ),
                                start = midScreen,
                                end = dragScreen,
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // ── 双页（平板端）：保持现状，不在手机端修复中改动 ──
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                clipPath(flapPath) {
                                    // 先铺纸底，保证背面不透明
                                    drawRect(backgroundColor)
                                    // 背面文字随折叠逐渐露出（规则十六）：折回区反射到哪里，
                                    // 就显示背面内容对应的那一条——掀角初期只有自由边一角，
                                    // 翻过书脊后逐渐铺满对页槽位
                                    if (backPage != null) {
                                        // reflect(折痕)∘reflect(书脊) 的复合 = 绕「折痕×书脊交点」
                                        // 旋转 2(θ折痕 − 90°)；折痕平行书脊（纯横向折叠）时退化为平移。
                                        // 槽位平移（内容槽 0..W → 终位阅读位）先于复合变换应用
                                        withTransform({
                                            if (foldResult.fold.parallelToSpine) {
                                                translate(
                                                    left = 2f * (foldResult.fold.midPoint.x - spineScreenX),
                                                    top = 0f,
                                                )
                                            } else {
                                                rotate(
                                                    degrees = 2f * (foldResult.fold.angleDeg - 90f),
                                                    pivot = Offset(spineScreenX, foldResult.fold.spineHitY ?: 0f),
                                                )
                                            }
                                        }) {
                                            withTransform({
                                                translate(left = slotOffsetPx, top = 0f)
                                            }) {
                                                this@drawWithContent.drawContent()
                                            }
                                        }
                                    }
                                    // 折痕阴影：由折痕向翻起边缘（反射后的角点 = A）渐隐
                                    drawRect(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.22f),
                                                Color.Transparent,
                                            ),
                                            start = midScreen,
                                            end = dragScreen,
                                        ),
                                    )
                                }
                            },
                    ) {
                        if (backPage != null) {
                            Box(
                                modifier = Modifier
                                    .align(backAlign)
                                    .fillMaxHeight()
                                    .width(slotWidthDp),
                            ) {
                                RenderReaderPage(
                                    backPage,
                                    pageHeight,
                                    Modifier
                                        .fillMaxSize()
                                        .padding(
                                            start = PAGE_H_PADDING,
                                            end = PAGE_H_PADDING,
                                            top = PAGE_V_PADDING + contentTopInset,
                                            bottom = PAGE_V_PADDING,
                                        ),
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

/**
 * 单页（手机端）仿真翻页的背面层：独立于双页（平板）实现，互不影响。
 *
 * - 向后翻（去下一页）：背面 = 下一页，内容槽在屏内 [0, W] 布局，绘制时先平移
 *   [slotTranslatePx]（= −页宽）到终位（书脊左侧屏外槽 [−W, 0]），再经
 *   reflect(书脊 x=0) 与 reflect(折痕) 两次**精确**反射映射到当前折回区
 * - 向前翻（回上一页）：背面 = 上一页，终位即右位原位（[slotTranslatePx] = 0），
 *   完成时折痕 = 书脊、复合变换 = 恒等，上一页可读平铺右位落定
 * - [backPage] 为 null（边界无对应页）→ 只画纸背 + 阴影
 *
 * @param foldLine 实时折痕（中点/法向/方向角）
 * @param flapPath 折回区裁剪路径（屏幕坐标）
 * @param backPage 背面内容页（向后 = 下一页，向前 = 上一页；null → 纸背）
 * @param pageHeight 页面渲染高度
 * @param slotWidthDp 内容槽宽（= 页宽）
 * @param slotTranslatePx 槽位平移量（px，把屏内布局的内容槽移到终位阅读位）
 * @param contentTopInset 内容顶部额外避让（沉浸式纸面覆盖状态栏时 = 状态栏高度）
 * @param backgroundColor 纸底色
 * @param shadowBrush 折痕阴影画刷
 */
@Composable
private fun SingleFoldBackLayer(
    foldLine: FoldLine,
    flapPath: Path,
    backPage: ReaderPage?,
    pageHeight: Dp,
    slotWidthDp: Dp,
    slotTranslatePx: Float,
    contentTopInset: Dp,
    backgroundColor: Color,
    shadowBrush: Brush,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                clipPath(flapPath) {
                    // 先铺纸底，保证背面不透明
                    drawRect(backgroundColor)
                    if (backPage != null) {
                        // 双面共享几何（规则九）：两次精确反射的复合
                        // reflect(折痕) ∘ reflect(书脊 x=0)——
                        // 点序：槽位平移 → reflect(书脊) → reflect(折痕)
                        withTransform({
                            rotate(degrees = foldLine.angleDeg, pivot = foldLine.midPoint)
                            scale(scaleX = 1f, scaleY = -1f, pivot = foldLine.midPoint)
                            rotate(degrees = -foldLine.angleDeg, pivot = foldLine.midPoint)
                        }) {
                            withTransform({
                                rotate(degrees = 90f, pivot = Offset(0f, 0f))
                                scale(scaleX = 1f, scaleY = -1f, pivot = Offset(0f, 0f))
                                rotate(degrees = -90f, pivot = Offset(0f, 0f))
                            }) {
                                withTransform({ translate(left = slotTranslatePx, top = 0f) }) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                        }
                    }
                    // 折痕阴影：由折痕向翻起边缘（反射后的角点 = A）渐隐
                    drawRect(brush = shadowBrush)
                }
            },
    ) {
        if (backPage != null) {
            Box(modifier = Modifier.fillMaxHeight().width(slotWidthDp)) {
                RenderReaderPage(
                    backPage,
                    pageHeight,
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = PAGE_H_PADDING,
                            end = PAGE_H_PADDING,
                            top = PAGE_V_PADDING + contentTopInset,
                            bottom = PAGE_V_PADDING,
                        ),
                )
            }
        }
    }
}
