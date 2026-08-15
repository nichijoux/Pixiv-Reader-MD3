package com.pixiv.reader.feature.reader.ui

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.feature.reader.R
import com.pixiv.reader.feature.reader.state.ReaderPage
import com.pixiv.reader.feature.reader.state.pageIndexForChar
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** 仿真翻页调试日志 TAG。 */
private const val TAG = "SimulationPage"

/**
 * 仿真翻页：位置驱动的贝塞尔卷页效果（移植 legado-with-MD3 SimulationPageDelegate）。
 *
 * - 被卷起的角落由触摸点象限决定（点页面哪个区就掀哪个角）
 * - 拖拽点与角落之间用两条贝塞尔曲线构造卷页路径 mPath0（真实纸页卷曲）
 * - 当前页 = 整页减去卷页区域（ClipOp.Difference）
 * - 下一页在卷页区域内绘制 + 柔光阴影
 * - 纸背 = 纸色填充 + 阴影，**不绘制镜像文字**，彻底避免文字黑影重叠
 *
 * 卷页几何计算见 [calcCurlPoints] / [curlPath0] / [curlBackPath] / [curlNextTri]（PageCurlGeometry.kt），
 * 单页内容渲染复用 [RenderReaderPage]。
 */
@Composable
fun SimulationPageContent(
    pages: List<ReaderPage>,
    pageHeight: Dp,
    backgroundColor: Color,
    restoreCharOffset: Int,
    jumpToChar: Int?,
    onPageChange: (Int) -> Unit,
    onPageInfo: (Int, Int) -> Unit,
    barsVisible: Boolean = false,
    onCloseBars: () -> Unit = {},
    onPrevChapterRequest: () -> Unit = {},
    onNextChapterRequest: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var restored by remember { mutableStateOf(false) }
    val currentIndex = remember { mutableIntStateOf(0) }
    // true=翻下一页，false=翻上一页
    var turningForward by remember { mutableStateOf(true) }
    // 被卷起的角落（由触摸象限决定）
    var corner by remember { mutableStateOf(Offset.Zero) }
    var hasCorner by remember { mutableStateOf(false) }
    // 拖拽触摸点（位置驱动卷页的核心）
    var touch by remember { mutableStateOf<Offset?>(null) }
    // 松手后的回弹/翻过动画（拖拽用，两个轴独立）
    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }
    // 点击翻页动画进度（单轴驱动，touch 沿直线路径插值，对齐 legado Scroller）
    val turnProgress = remember { Animatable(0f) }
    var animating by remember { mutableStateOf(false) }
    // 页面尺寸（px），由 BoxWithConstraints 填充
    var pageW by remember { mutableStateOf(0f) }
    var pageH by remember { mutableStateOf(0f) }

    // 首次定位到上次阅读位置
    LaunchedEffect(pages, restoreCharOffset) {
        if (restored || pages.isEmpty()) return@LaunchedEffect
        val index = pages.pageIndexForChar(restoreCharOffset)
        currentIndex.intValue = index
        restored = true
        onPageInfo(index, pages.size)
        onPageChange(index)
    }

    // 翻页完成后上报当前页
    LaunchedEffect(currentIndex.intValue) {
        if (restored) {
            onPageInfo(currentIndex.intValue, pages.size)
            onPageChange(currentIndex.intValue)
        }
    }

    // 目录/搜索跳转
    LaunchedEffect(jumpToChar) {
        val j = jumpToChar ?: return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        currentIndex.intValue = pages.pageIndexForChar(j)
        touch = null
        animating = false
    }

    if (pages.isEmpty()) {
        EmptyBox(stringResource(R.string.reader_empty_content), modifier = modifier)
        return
    }

    suspend fun finishTurn() {
        if (turningForward) {
            if (currentIndex.intValue < pages.size - 1) currentIndex.intValue += 1
        } else {
            if (currentIndex.intValue > 0) currentIndex.intValue -= 1
        }
        Log.d(TAG, "finishTurn forward=$turningForward index=${currentIndex.intValue}/${pages.size}")
        touch = null
        animating = false
    }

    /** 松手：判定翻过或回弹，并播放动画。 */
    suspend fun settle() {
        val t = touch ?: run {
            Log.w(TAG, "settle: touch is null, skip")
            return
        }
        val cx = corner.x
        val cy = corner.y
        val w = pageW
        // 向页面中部拖得足够远则翻过，否则回弹（手指滑出屏幕被系统取消时也按距离判定）
        val dist = hypot(t.x - cx, t.y - cy)
        val threshold = hypot(w, pageH) * 0.22f
        val passed = dist > threshold
        Log.d(TAG, "settle: touch=(${t.x},${t.y}) corner=($cx,$cy) w=$w h=$pageH " +
            "dist=${"%.1f".format(dist)} threshold=${"%.1f".format(threshold)} passed=$passed")
        animating = true
        animX.snapTo(t.x)
        animY.snapTo(t.y)
        if (!passed) {
            Log.d(TAG, "settle: => 回弹 corner")
            animX.animateTo(cx, tween(170)) { touch = Offset(animX.value, animY.value) }
            animY.animateTo(cy, tween(170)) { touch = Offset(animX.value, animY.value) }
            touch = null
            animating = false
        } else {
            // 翻过：touch 从当前位置沿「远离 corner」的方向飞出。
            // 旧实现 corner + (corner-touch)*3 的直线会经过 corner（卷页缩到 0，视觉像弹回）；
            // 改为 touch + (touch-corner)*scale，距离单调递增，卷页持续扩大并覆盖整页。
            val dx = t.x - cx
            val dy = t.y - cy
            val curDist = hypot(dx, dy).coerceAtLeast(1f)
            // 目标距离 ≥ 1.2 倍对角线，确保卷页覆盖整页；scale≥2 保证有足够动画行程
            val scale = (hypot(w, pageH) * 1.2f / curDist).coerceAtLeast(2f)
            val tx = t.x + dx * scale
            val ty = t.y + dy * scale
            Log.d(TAG, "settle: => 翻过 目标=($tx,$ty) scale=$scale")
            animX.animateTo(tx, tween(170)) { touch = Offset(animX.value, animY.value) }
            animY.animateTo(ty, tween(170)) { touch = Offset(animX.value, animY.value) }
            finishTurn()
        }
    }

    /**
     * 点击翻页：整页翻动动画（对齐 legado-with-MD3 nextPageByAnim/prevPageByAnim + onAnimStart）：
     * - 角落 = 点击点象限（上一页固定左下角 (0,h)）
     * - touch 起手：下一页 (0.9w, 0.9h|1)，上一页 (0,h)；目标：下一页飞出左缘 x=-w，上一页飞出右缘 x=w
     *   （y 落回角落所在水平线）→ 卷页水平扫过整页（从右向左翻 / 从左向右翻），无对角上翻
     * - 时长 = animationSpeed(200) × |dx| / w，LinearInterpolator 线性插值（单轴进度驱动直线路径）
     * - 动画结束瞬间切页（legado fillPage）
     */
    suspend fun turnTo(forward: Boolean, tapPoint: Offset) {
        turningForward = forward
        val w = pageW
        val h = pageH
        // 角落：下一页 = 点击点象限（右列；上半部顶角 / 下半部底角）；上一页固定左下角
        val cx = if (forward) w else 0f
        val cy = if (!forward) h else if (tapPoint.y > h / 2f) h else 0f
        corner = Offset(cx, cy)
        hasCorner = true
        // 起手 touch 点（legado setStartPoint）
        val startX = if (forward) w * 0.9f else 0f
        val startY = if (forward) (if (tapPoint.y > h / 2f) h * 0.9f else 1f) else h
        // 目标（legado onAnimStart）：沿 x 飞出对侧边缘，y 落回角落所在水平线
        val dx = if (forward) -(w + startX) else (w - startX)
        val dy = if (cy > 0f) (h - startY) else (1f - startY)
        val tx = startX + dx
        val ty = startY + dy
        // 时长 = animationSpeed × |dx| / w（legado startScroll，animationSpeed=200，比默认 300 更快）
        val duration = (200f * abs(dx) / w).toInt().coerceAtLeast(1)
        Log.d(TAG, "turnTo forward=$forward tap=(${tapPoint.x},${tapPoint.y}) corner=($cx,$cy) " +
            "start=($startX,$startY) 目标=($tx,$ty) duration=$duration")
        animating = true
        touch = Offset(startX, startY)
        turnProgress.snapTo(0f)
        turnProgress.animateTo(1f, tween(duration, easing = LinearEasing)) {
            touch = Offset(
                startX + (tx - startX) * turnProgress.value,
                startY + (ty - startY) * turnProgress.value,
            )
        }
        finishTurn()
    }

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(pages.size, barsVisible) {
                detectTapGestures(onTap = { offset ->
                    val third = size.width / 3f
                    when {
                        offset.x < third -> {
                            // 工具栏显示时：左右边缘点击关闭工具栏（不翻页）
                            if (barsVisible) onCloseBars()
                            else if (currentIndex.intValue > 0) scope.launch { turnTo(false, offset) }
                            // 当前章首页向前翻：非系列 / 系列第一章无操作（禁用无效动画），
                            // 系列且有上一章 → 由外层 onPrevChapterRequest 跳上一章尾页
                            else onPrevChapterRequest()
                        }
                        offset.x > size.width - third -> {
                            if (barsVisible) onCloseBars()
                            // 最后一页向后翻：系列且有下一章 → 由外层 onNextChapterRequest 跳下一章开头，
                            // 非系列 / 系列最后一章 → 无操作（禁用无效动画）
                            else if (currentIndex.intValue < pages.size - 1) scope.launch { turnTo(true, offset) }
                            else onNextChapterRequest()
                        }
                        else -> Unit // 中间点击切换工具栏由外层处理
                    }
                })
            }
            .pointerInput(pages.size) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val forward = pos.x >= w / 2f
                        val atFirstPage = currentIndex.intValue <= 0
                        val atLastPage = currentIndex.intValue >= pages.size - 1
                        when {
                            // 边界：第一页向前拖 → 系列且有上一章则跳上一章尾页；非系列无操作
                            !forward && atFirstPage -> onPrevChapterRequest()
                            // 最后一页向后拖：系列且有下一章 → 跳下一章开头；非系列 / 最后一章无操作
                            forward && atLastPage -> onNextChapterRequest()
                            else -> {
                                // 按触摸点象限选择被卷起的角落（点哪掀哪）
                                corner = Offset(
                                    if (pos.x <= w / 2f) 0f else w,
                                    if (pos.y <= h / 2f) 0f else h,
                                )
                                hasCorner = true
                                // 方向：起拖在页面右半=下一页，左半=上一页
                                turningForward = forward
                                touch = pos
                                animating = false
                                Log.d(TAG, "onDragStart pos=(${pos.x},${pos.y}) w=$w h=$h " +
                                    "corner=(${corner.x},${corner.y}) forward=$turningForward")
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        touch = change.position
                    },
                    onDragEnd = {
                        Log.d(TAG, "onDragEnd lastTouch=$touch")
                        scope.launch { settle() }
                    },
                    onDragCancel = {
                        Log.d(TAG, "onDragCancel lastTouch=$touch")
                        scope.launch { settle() }
                    },
                )
            },
    ) {
        // 页面尺寸（px）：直接用 scope 的 constraints（无需 Density 转换，IDE 可识别 scope 使用）
        pageW = constraints.maxWidth.toFloat()
        pageH = constraints.maxHeight.toFloat()

        val current = pages[currentIndex.intValue]
        val reveal = if (turningForward) {
            pages.getOrNull(currentIndex.intValue + 1)
        } else {
            pages.getOrNull(currentIndex.intValue - 1)
        }

        val running = touch != null || animating
        val w = pageW
        val h = pageH
        val cx = corner.x
        val cy = corner.y
        // 不夹取坐标：翻过动画中 touch 会出页（页外），卷页几何随之扩展覆盖整页，形成完整翻页动画。
        // 出页可能产生的 NaN/Infinity 由下方的 isFinite 防御兜底。
        val tx = touch?.x ?: cx
        val ty = touch?.y ?: cy
        val curlRaw = if (running && hasCorner && w > 0f && hypot(tx - cx, ty - cy) >= 2f) {
            calcCurlPoints(tx, ty, cx, cy, w, h)
        } else {
            null
        }
        // 防御：几何出现 NaN/Infinity（除零或直线交点退化）时不绘制卷页，避免 Brush 崩溃
        val curl = curlRaw?.takeIf {
            it.touchToCornerDis.isFinite() &&
                it.touch.isFinite() && it.corner.isFinite() &&
                it.control1.isFinite() && it.control2.isFinite() &&
                it.start1.isFinite() && it.start2.isFinite() &&
                it.end1.isFinite() && it.end2.isFinite() &&
                it.vertex1.isFinite() && it.vertex2.isFinite()
        }
        val path0 = curl?.let { curlPath0(it) }
        val nextTri = curl?.let { curlNextTri(it) }

        // 诊断：卷页几何状态翻转时打一次（翻过动画中 touch 出页时 curl 不应消失）
        var lastCurlNull by remember { mutableStateOf(true) }
        val curlNull = curl == null
        if (curlNull != lastCurlNull) {
            lastCurlNull = curlNull
            Log.d(TAG, "draw: curl ${if (curlNull) "LOST(null)" else "ok"} " +
                "tx=$tx ty=$ty animating=$animating touch=${touch != null}")
        }
        val backPath = curl?.let { curlBackPath(it) }

        // 下一页：只在"下一页露出三角"（mPath0 ∩ nextTri）内绘制，其余区域保持背景色被遮挡
        if (reveal != null && curl != null && path0 != null && nextTri != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipPath(path0) {
                            clipPath(nextTri) {
                                this@drawWithContent.drawContent()
                                // 卷角柔光阴影（下一页边缘）：防御 radius 非有限
                                val r = curl.touchToCornerDis.coerceAtLeast(10f)
                                if (r.isFinite() && curl.touch.isFinite()) {
                                    drawRect(
                                        brush = Brush.radialGradient(
                                            colors = listOf(Color.Black.copy(alpha = 0.15f), Color.Transparent),
                                            center = curl.touch,
                                            radius = r,
                                        )
                                    )
                                }
                            }
                        }
                    },
            ) {
                RenderReaderPage(
                    reveal,
                    pageHeight,
                    Modifier
                        .fillMaxSize()
                        .padding(PAGE_H_PADDING, PAGE_V_PADDING),
                )
            }
        }

        // 当前页正面：整页减去卷页区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (curl != null && path0 != null) {
                        val full = Path().apply { addRect(Rect(0f, 0f, w, h)) }
                        clipPath(full) {
                            clipPath(path0, ClipOp.Difference) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    } else {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            RenderReaderPage(
                current,
                pageHeight,
                Modifier
                    .fillMaxSize()
                    .padding(PAGE_H_PADDING, PAGE_V_PADDING),
            )
        }

        // 纸背：在纸背三角（mPath0 ∩ backPath）内铺纸底色 + 沿折痕镜像当前页内容（半透明灰化），
        // 模拟纸张背面隐约透字（对齐 legado drawCurrentBackArea）
        if (curl != null && path0 != null && backPath != null) {
            // legado 反射矩阵 R=[[1-2f9²,2f8f9],[2f8f9,1-2f8²]]：
            // 法线 n=(f9,-f8)，反射轴方向 = (f8,f9)，轴角 θ=atan2(f9,f8)
            val dis = hypot(cx - curl.control1.x, curl.control2.y - cy).coerceAtLeast(1e-4f)
            val f8 = (cx - curl.control1.x) / dis
            val f9 = (curl.control2.y - cy) / dis
            val axisAngle = atan2(f9, f8) * 180f / PI.toFloat()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipPath(path0) {
                            clipPath(backPath) {
                                // 先铺纸底色，保证纸背不透明
                                drawRect(backgroundColor)
                                // 沿折痕镜像当前页内容（半透明），模拟纸张背面隐约透字
                                withTransform({
                                    rotate(degrees = axisAngle, pivot = curl.control1)
                                    scale(scaleX = 1f, scaleY = -1f, pivot = curl.control1)
                                    rotate(degrees = -axisAngle, pivot = curl.control1)
                                }) {
                                    this@drawWithContent.drawContent()
                                }
                                // 灰化纸背文字，降低重叠感
                                drawRect(Color.Gray.copy(alpha = 0.45f))
                                // 靠近卷曲边缘渐暗，形成纸张立体感（防御 start/end 重合或非有限）
                                if (curl.control1.isFinite() && curl.touch.isFinite() &&
                                    hypot(curl.control1.x - curl.touch.x, curl.control1.y - curl.touch.y) > 1f
                                ) {
                                    drawRect(
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
                                            start = curl.control1,
                                            end = curl.touch,
                                        )
                                    )
                                }
                            }
                        }
                    },
            ) {
                RenderReaderPage(
                    current,
                    pageHeight,
                    Modifier
                        .fillMaxSize()
                        .padding(PAGE_H_PADDING, PAGE_V_PADDING),
                )
            }
        }
    }
}
