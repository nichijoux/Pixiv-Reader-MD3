package com.pixiv.reader.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.PixivImage
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

private val PAGE_H_PADDING = 24.dp
private val PAGE_V_PADDING = 16.dp

/**
 * 仿真翻页：位置驱动的贝塞尔卷页效果（移植 legado-with-MD3 SimulationPageDelegate）。
 *
 * - 被卷起的角落由触摸点象限决定（点页面哪个区就掀哪个角）
 * - 拖拽点与角落之间用两条贝塞尔曲线构造卷页路径 mPath0（真实纸页卷曲）
 * - 当前页 = 整页减去卷页区域（ClipOp.Difference）
 * - 下一页在卷页区域内绘制 + 柔光阴影
 * - 纸背 = 纸色填充 + 阴影，**不绘制镜像文字**，彻底避免文字黑影重叠
 */
@Composable
fun SimulationPageContent(
    pages: List<ReaderPage>,
    baseStyle: TextStyle,
    imageHeight: Dp,
    backgroundColor: Color,
    restoreCharOffset: Int,
    onPageChange: (Int) -> Unit,
    onPageInfo: (Int, Int) -> Unit,
    onOpenSettings: () -> Unit,
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
    // 松手后的回弹/翻过动画
    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }
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

    if (pages.isEmpty()) {
        EmptyBox("没有正文内容", modifier = modifier)
        return
    }

    suspend fun finishTurn() {
        if (turningForward) {
            if (currentIndex.intValue < pages.size - 1) currentIndex.intValue += 1
        } else {
            if (currentIndex.intValue > 0) currentIndex.intValue -= 1
        }
        touch = null
        animating = false
    }

    /** 松手：判定翻过或回弹，并播放动画。 */
    suspend fun settle(cancel: Boolean = false) {
        val t = touch ?: return
        val cx = corner.x
        val cy = corner.y
        val w = pageW
        // 向页面中部拖得足够远则翻过，否则回弹
        val passed = hypot(t.x - cx, t.y - cy) > hypot(w, pageH) * 0.28f
        animating = true
        animX.snapTo(t.x)
        animY.snapTo(t.y)
        if (cancel || !passed) {
            animX.animateTo(cx, tween(250)) { touch = Offset(animX.value, animY.value) }
            animY.animateTo(cy, tween(250)) { touch = Offset(animX.value, animY.value) }
            touch = null
            animating = false
        } else {
            val tx = cx + (cx - t.x) * 6f
            val ty = cy + (cy - t.y) * 6f
            animX.animateTo(tx, tween(250)) { touch = Offset(animX.value, animY.value) }
            animY.animateTo(ty, tween(250)) { touch = Offset(animX.value, animY.value) }
            finishTurn()
        }
    }

    /** 点击翻页：整页翻动动画。 */
    suspend fun turnTo(forward: Boolean) {
        turningForward = forward
        val w = pageW
        val cx = if (forward) w else 0f
        val cy = pageH
        corner = Offset(cx, cy)
        hasCorner = true
        val tx = cx + if (forward) -w else w
        animating = true
        animX.snapTo(cx)
        animY.snapTo(cy)
        touch = Offset(cx, cy)
        animX.animateTo(tx, tween(300)) { touch = Offset(animX.value, animY.value) }
        animY.animateTo(cy, tween(300)) { touch = Offset(animX.value, animY.value) }
        finishTurn()
    }

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(pages.size) {
                detectTapGestures(onTap = { offset ->
                    val third = size.width / 3f
                    when {
                        offset.x < third -> scope.launch { turnTo(false) }
                        offset.x > size.width - third -> scope.launch { turnTo(true) }
                        else -> onOpenSettings()
                    }
                })
            }
            .pointerInput(pages.size) {
                detectDragGestures(
                    onDragStart = { pos ->
                        // 按触摸点象限选择被卷起的角落（点哪掀哪）
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        corner = Offset(
                            if (pos.x <= w / 2f) 0f else w,
                            if (pos.y <= h / 2f) 0f else h,
                        )
                        hasCorner = true
                        // 方向：起拖在页面右半=下一页，左半=上一页
                        turningForward = pos.x >= w / 2f
                        touch = pos
                        animating = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        touch = change.position
                    },
                    onDragEnd = { scope.launch { settle() } },
                    onDragCancel = { scope.launch { settle(true) } },
                )
            },
    ) {
        val density = LocalDensity.current
        pageW = with(density) { maxWidth.toPx() }
        pageH = with(density) { maxHeight.toPx() }

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
        val tx = (touch?.x ?: cx).coerceIn(0f, w)
        val ty = (touch?.y ?: cy).coerceIn(0f, h)
        val curl = if (running && hasCorner && w > 0f && hypot(tx - cx, ty - cy) >= 2f) {
            calcCurlPoints(tx, ty, cx, cy, w, h)
        } else {
            null
        }
        val path0 = curl?.let { curlPath0(it) }
        val nextTri = curl?.let { curlNextTri(it) }
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
                                // 卷角柔光阴影（下一页边缘）
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color.Black.copy(alpha = 0.15f), Color.Transparent),
                                        center = curl.touch,
                                        radius = curl.touchToCornerDis.coerceAtLeast(10f),
                                    )
                                )
                            }
                        }
                    },
            ) {
                renderPage(reveal, baseStyle, imageHeight)
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
            renderPage(current, baseStyle, imageHeight)
        }

        // 纸背：在纸背三角（mPath0 ∩ backPath）内铺纸底色 + 沿折痕镜像当前页内容（半透明灰化），
        // 模拟纸张背面隐约透字（对齐 legado drawCurrentBackArea）
        if (curl != null && path0 != null && backPath != null) {
            // legado 反射矩阵 R=[[1-2f9²,2f8f9],[2f8f9,1-2f8²]]：
            // 法线 n=(f9,-f8)，反射轴方向 = (f8,f9)，轴角 θ=atan2(f9,f8)
            val dis = hypot(cx - curl.control1.x, curl.control2.y - cy).coerceAtLeast(1e-4f)
            val f8 = (cx - curl.control1.x) / dis
            val f9 = (curl.control2.y - cy) / dis
            val axisAngle = atan2(f9, f8) * 180f / 3.14159265f
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
                                // 靠近卷曲边缘渐暗，形成纸张立体感
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
                                        start = curl.control1,
                                        end = curl.touch,
                                    )
                                )
                            }
                        }
                    },
            ) {
                renderPage(current, baseStyle, imageHeight)
            }
        }
    }
}

// ── 卷页几何（翻译自 legado calcPoints） ──────────────────────────────────────

internal data class CurlPoints(
    val start1: Offset,
    val control1: Offset,
    val vertex1: Offset,
    val end1: Offset,
    val start2: Offset,
    val control2: Offset,
    val vertex2: Offset,
    val end2: Offset,
    val touch: Offset,
    val corner: Offset,
    val touchToCornerDis: Float,
)

internal fun calcCurlPoints(
    touchX: Float,
    touchY: Float,
    cornerX: Float,
    cornerY: Float,
    viewWidth: Float,
    viewHeight: Float,
): CurlPoints {
    var tx = touchX
    var ty = touchY
    var mx = (tx + cornerX) / 2f
    var my = (ty + cornerY) / 2f
    var c1x = mx - (cornerY - my) * (cornerY - my) / (cornerX - mx)
    var c1y = cornerY
    var c2x = cornerX
    var c2y = calcC2Y(mx, my, cornerX, cornerY)
    var s1x = c1x - (cornerX - c1x) / 2f
    var s1y = cornerY

    // 固定左边上下两个点（边界修正）
    if (tx > 0f && tx < viewWidth) {
        if (s1x < 0f || s1x > viewWidth) {
            if (s1x < 0f) s1x = viewWidth - s1x
            val f1 = abs(cornerX - tx)
            val f2 = viewWidth * f1 / s1x
            tx = abs(cornerX - f2)
            val f3 = abs(cornerX - tx) * abs(cornerY - ty) / f1
            ty = abs(cornerY - f3)
            mx = (tx + cornerX) / 2f
            my = (ty + cornerY) / 2f
            c1x = mx - (cornerY - my) * (cornerY - my) / (cornerX - mx)
            c1y = cornerY
            c2x = cornerX
            c2y = calcC2Y(mx, my, cornerX, cornerY)
            s1x = c1x - (cornerX - c1x) / 2f
        }
    }
    val s2x = cornerX
    val s2y = c2y - (cornerY - c2y) / 2f

    val touchToCornerDis = hypot(tx - cornerX, ty - cornerY)

    val e1 = getCross(Offset(tx, ty), Offset(c1x, c1y), Offset(s1x, s1y), Offset(s2x, s2y))
    val e2 = getCross(Offset(tx, ty), Offset(c2x, c2y), Offset(s1x, s1y), Offset(s2x, s2y))

    return CurlPoints(
        start1 = Offset(s1x, s1y),
        control1 = Offset(c1x, c1y),
        vertex1 = Offset((s1x + 2 * c1x + e1.x) / 4f, (2 * c1y + s1y + e1.y) / 4f),
        end1 = e1,
        start2 = Offset(s2x, s2y),
        control2 = Offset(c2x, c2y),
        vertex2 = Offset((s2x + 2 * c2x + e2.x) / 4f, (2 * c2y + s2y + e2.y) / 4f),
        end2 = e2,
        touch = Offset(tx, ty),
        corner = Offset(cornerX, cornerY),
        touchToCornerDis = touchToCornerDis,
    )
}

private fun calcC2Y(mx: Float, my: Float, cornerX: Float, cornerY: Float): Float {
    val f4 = cornerY - my
    return if (f4 == 0f) {
        my - (cornerX - mx) * (cornerX - mx) / 0.1f
    } else {
        my - (cornerX - mx) * (cornerX - mx) / (cornerY - my)
    }
}

/** 直线 P1P2 与 P3P4 的交点。 */
private fun getCross(p1: Offset, p2: Offset, p3: Offset, p4: Offset): Offset {
    val denom = (p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x)
    if (abs(denom) < 1e-6f) return p3
    val a = p1.x * p2.y - p1.y * p2.x
    val b = p3.x * p4.y - p3.y * p4.x
    val x = (a * (p3.x - p4.x) - (p1.x - p2.x) * b) / denom
    val y = (a * (p3.y - p4.y) - (p1.y - p2.y) * b) / denom
    return Offset(x, y)
}

// ── 路径构建（翻译自 legado mPath0 / 纸背三角） ─────────────────────────────

/** mPath0：被卷起的区域（贝塞尔曲线围成）。 */
private fun curlPath0(p: CurlPoints): Path = Path().apply {
    moveTo(p.start1.x, p.start1.y)
    quadraticTo(p.control1.x, p.control1.y, p.end1.x, p.end1.y)
    lineTo(p.touch.x, p.touch.y)
    lineTo(p.end2.x, p.end2.y)
    quadraticTo(p.control2.x, p.control2.y, p.start2.x, p.start2.y)
    lineTo(p.corner.x, p.corner.y)
    close()
}

/** 纸背三角：vertex2 → vertex1 → end1 → touch → end2。 */
private fun curlBackPath(p: CurlPoints): Path = Path().apply {
    moveTo(p.vertex2.x, p.vertex2.y)
    lineTo(p.vertex1.x, p.vertex1.y)
    lineTo(p.end1.x, p.end1.y)
    lineTo(p.touch.x, p.touch.y)
    lineTo(p.end2.x, p.end2.y)
    close()
}

/** 下一页露出三角：start1 → vertex1 → vertex2 → start2 → corner。 */
private fun curlNextTri(p: CurlPoints): Path = Path().apply {
    moveTo(p.start1.x, p.start1.y)
    lineTo(p.vertex1.x, p.vertex1.y)
    lineTo(p.vertex2.x, p.vertex2.y)
    lineTo(p.start2.x, p.start2.y)
    lineTo(p.corner.x, p.corner.y)
    close()
}

/** 渲染单页内容（文本或图片）。 */
@Composable
private fun renderPage(
    page: ReaderPage,
    baseStyle: TextStyle,
    imageHeight: Dp,
) {
    when (page) {
        is ReaderPage.Text -> Text(
            text = page.annotated,
            style = baseStyle,
            modifier = Modifier
                .fillMaxSize()
                .padding(PAGE_H_PADDING, PAGE_V_PADDING),
        )

        is ReaderPage.Image -> Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(PAGE_H_PADDING, PAGE_V_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            PixivImage(
                url = page.url,
                contentDescription = page.caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
