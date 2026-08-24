package com.pixiv.reader.core.ui.component.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ImageSpan
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import coil.imageLoader
import coil.request.ImageRequest
import com.pixiv.reader.core.common.parse.EMOTE_SENTINEL
import com.pixiv.reader.core.common.parse.EmoteSegment
import com.pixiv.reader.core.common.parse.encodeEmoteDraft
import com.pixiv.reader.core.common.parse.toDraftText
import com.pixiv.reader.core.ui.component.emoji.PIXIV_EMOJI_IDS
import com.pixiv.reader.core.ui.component.emoji.pixivEmojiUrl

/** 输入框内行内表情图边长（对齐渲染侧 pixiv 网页 24px）。 */
private const val EMOTE_SIZE_DP = 24f

/**
 * 评论富文本输入框控制器：持有内部 [EditText] 引用，供表情面板在光标位置插入表情。
 * 通过 [androidx.compose.runtime.remember] 创建后传入 [EmoteCommentField]。
 */
class EmoteFieldHandle internal constructor() {
    internal var view: EditText? = null

    /** 程序化改写缓冲区时置 true，抑制 TextWatcher 回环上报。 */
    internal var applying = false

    /** 最近一次接收/上报的草稿文本，用于双向同步去重。 */
    internal var latestDraft: String = ""

    /**
     * 在光标处插入一个文本表情（选区非空则替换选区）。
     * @return 插入后的完整草稿文本（调用方转交 onDraftChange 同步外部状态）；视图未就绪或标签非法返回 null
     */
    fun insertEmote(tag: String): String? {
        val et = view ?: return null
        if (pixivEmojiUrl(tag) == null) return null
        val piece = SpannableStringBuilder().append(EMOTE_SENTINEL)
        attachEmoteSpan(piece, 0, tag, et)
        val lo = minOf(et.selectionStart, et.selectionEnd).coerceIn(0, et.length())
        val hi = maxOf(et.selectionStart, et.selectionEnd).coerceIn(0, et.length())
        applying = true
        try {
            et.text.replace(lo, hi, piece)
            et.setSelection(lo + 1)
            latestDraft = et.text.toDraftText()
            return latestDraft
        } finally {
            applying = false
        }
    }
}

/**
 * 评论富文本输入框：AndroidView 包装原生 [EditText]，缓冲区内每个文本表情/回复提及为
 * 1 个 [EMOTE_SENTINEL] 占位字符 + Span——中文拼音组合输入、光标与选区均为原生行为，
 * 退格一次即整块删除。对外仍以含 `(tag)` / `@昵称 ` 的纯文本草稿经 [onDraftChange]
 * 双向同步（发布 API 协议不变）；表情图经 Coil 全局 loader（带 Referer）异步加载。
 *
 * 视觉由 Compose 侧注入：容器描边在外层完成，本组件只负责文字/hint 配色。
 *
 * @param draft 外部草稿状态（含 `(tag)` 与开头 `@昵称 `）
 * @param onDraftChange 草稿变化回调（含插入表情后的回传）
 * @param hint 占位提示文案
 * @param mentionName 当前回复目标精确昵称；草稿以其 `@昵称 ` 开头时渲染为胶囊
 * @param onFieldActivated 输入框激活（获得焦点或被点按）回调：调用方收起表情面板并确保键盘弹出
 */
@Composable
fun EmoteCommentField(
    draft: String,
    onDraftChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    handle: EmoteFieldHandle = remember { EmoteFieldHandle() },
    mentionName: String? = null,
    onFieldActivated: () -> Unit = {},
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    // 胶囊配色在组合期读取快照，经参数传入非 Composable 的 Span 工厂
    val chipFg = MaterialTheme.colorScheme.onSecondaryContainer.toArgb()
    val chipBg = MaterialTheme.colorScheme.secondaryContainer.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            EditText(ctx).apply {
                background = null // 去除主题默认下划线，视觉由 Compose 容器承担
                setSingleLine(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 0)
                handle.view = this
                doAfterTextChanged {
                    if (handle.applying) return@doAfterTextChanged
                    val newDraft = text.toDraftText()
                    if (newDraft != handle.latestDraft) {
                        handle.latestDraft = newDraft
                        onDraftChange(newDraft)
                    }
                }
                onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) onFieldActivated()
                }
                // 已持有焦点时再次点按同样回调（无 focus 事件，覆盖"收面板/唤起键盘"路径）
                setOnClickListener { onFieldActivated() }
            }
        },
        update = { et ->
            handle.latestDraft = draft
            // 外部草稿与缓冲区不一致（预填 @提及 / 发送后清空）才重建，避免输入回环
            if (et.text.toDraftText() != draft) {
                applyDraft(handle, et, draft, mentionName, chipFg, chipBg)
            }
            et.setTextColor(textColor.toArgb())
            et.setHintTextColor(hintColor.toArgb())
            if (et.hint?.toString() != hint) et.hint = hint
        },
    )
}

// ── 缓冲区构建 / 解析 ─────────────────────────────────────────

/**
 * 外部草稿 → 重建编辑缓冲区：`(tag)` 段落挂行内图 Span、`@提及` 段落挂胶囊 Span。
 * 仅在外部变更时调用（watcher 已被 [EmoteFieldHandle.applying] 抑制），光标置于末尾。
 */
private fun applyDraft(
    handle: EmoteFieldHandle,
    et: EditText,
    draft: String,
    mentionName: String?,
    chipFgColor: Int,
    chipBgColor: Int,
) {
    handle.applying = true
    try {
        val sb = SpannableStringBuilder()
        for (segment in encodeEmoteDraft(draft, PIXIV_EMOJI_IDS.keys, mentionName)) {
            when (segment) {
                is EmoteSegment.Text -> sb.append(segment.value)
                is EmoteSegment.Emote -> {
                    val start = sb.length
                    sb.append(EMOTE_SENTINEL)
                    attachEmoteSpan(sb, start, segment.tag, et)
                }
                is EmoteSegment.Mention -> {
                    val start = sb.length
                    sb.append(EMOTE_SENTINEL)
                    attachMentionSpan(sb, start, segment.name, et, chipFgColor, chipBgColor)
                }
            }
        }
        et.text = sb
        et.setSelection(sb.length)
    } finally {
        handle.applying = false
    }
}

/** 缓冲区 → 草稿段落序列：占位字符按 Span 还原，孤儿占位符丢弃。 */
internal fun Editable.toSegments(): List<EmoteSegment> {
    val segments = mutableListOf<EmoteSegment>()
    val textBuffer = StringBuilder()
    fun flush() {
        if (textBuffer.isNotEmpty()) {
            segments += EmoteSegment.Text(textBuffer.toString())
            textBuffer.clear()
        }
    }
    for (i in 0 until length) {
        if (this[i] == EMOTE_SENTINEL) {
            val emote = getSpans(i, i + 1, EmoteImageSpan::class.java).firstOrNull()
            val chip = getSpans(i, i + 1, ReplyChipSpan::class.java).firstOrNull()
            when {
                emote != null -> {
                    flush()
                    segments += EmoteSegment.Emote(emote.tag)
                }
                chip != null -> {
                    flush()
                    segments += EmoteSegment.Mention(chip.name)
                }
                else -> {} // 无 Span 的孤儿占位符直接丢弃，避免 \uFFFC 混入草稿文本
            }
        } else {
            textBuffer.append(this[i])
        }
    }
    flush()
    return segments
}

/** 缓冲区 → 草稿文本（复用 core:common 序列化）。 */
internal fun Editable.toDraftText(): String = toSegments().toDraftText()

// ── Span 定义 ────────────────────────────────────────────────

/** 在 [sb] 的 [start] 处挂载文本表情行内图 Span，并经 Coil 异步加载图片。 */
private fun attachEmoteSpan(sb: SpannableStringBuilder, start: Int, tag: String, et: EditText) {
    val sizePx = (EMOTE_SIZE_DP * et.resources.displayMetrics.density).toInt()
    val swap = SwapDrawable(FixedSizeTransparentDrawable(sizePx))
    val span = EmoteImageSpan(swap, tag)
    sb.setSpan(span, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    val context = et.context
    val url = pixivEmojiUrl(tag) ?: return
    // allowHardware(false)：ImageSpan 绘制需软件位图，硬件位图在部分系统版本异常
    context.imageLoader.enqueue(
        ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(sizePx)
            .target(onSuccess = { loaded ->
                loaded.bounds = Rect(0, 0, sizePx, sizePx)
                swap.delegate = loaded
                // 仅 invalidate 不会重算 span 宽度（布局缓存不感知 drawable 变化）；
                // 移除再重挂同一 span 触发 SpanWatcher → TextView 重建文本布局
                val text = et.text
                val s = text.getSpanStart(span)
                val e = text.getSpanEnd(span)
                if (s >= 0 && e >= 0) {
                    text.removeSpan(span)
                    text.setSpan(span, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                et.invalidate()
            })
            .build(),
    )
}

/** 在 [sb] 的 [start] 处挂载回复提及胶囊 Span（颜色随当前主题快照固定）。 */
private fun attachMentionSpan(
    sb: SpannableStringBuilder,
    start: Int,
    name: String,
    et: EditText,
    fgColor: Int,
    bgColor: Int,
) {
    val span = ReplyChipSpan(
        name = name,
        fgColor = fgColor,
        bgColor = bgColor,
        editorTextSizePx = et.textSize,
        density = et.resources.displayMetrics.density,
    )
    sb.setSpan(span, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

/** 固定尺寸透明占位图：intrinsic 为正，加载前即保持行内宽度稳定。 */
private class FixedSizeTransparentDrawable(private val sizePx: Int) : Drawable() {
    override fun draw(canvas: Canvas) {}

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSPARENT

    override fun getIntrinsicWidth(): Int = sizePx

    override fun getIntrinsicHeight(): Int = sizePx
}

/** 文本表情行内图 Span：携带标签用于反向序列化。 */
private class EmoteImageSpan(drawable: Drawable, val tag: String) : ImageSpan(drawable)

/** 回复胶囊显示名最大字符数（超出截断加省略号；序列化仍用全名，payload 不受影响）。 */
private const val CHIP_MAX_NAME_LENGTH = 10

/**
 * 回复提及胶囊（`@昵称`）：[ReplacementSpan] 实时绘制圆角底 + 昵称文本（字号略小于正文、
 * 超长显示名截断），单占位字符、一次退格整体删除；通过撑高 FontMetrics 容纳胶囊背景。
 */
private class ReplyChipSpan(
    val name: String,
    fgColor: Int,
    bgColor: Int,
    editorTextSizePx: Float,
    private val density: Float,
) : ReplacementSpan() {
    // 仅用于绘制/测量的显示标签；name 保持全名供反向序列化
    private val label = if (name.length > CHIP_MAX_NAME_LENGTH) name.take(CHIP_MAX_NAME_LENGTH) + "…" else name

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
        color = fgColor
        textSize = editorTextSizePx * CHIP_TEXT_SCALE
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
    private val hPad = 6f * density
    private val vPad = 3f * density
    private val ascentExtra = (textPaint.textSize * 1.15f + vPad).toInt()
    private val descentExtra = (textPaint.textSize * 0.35f + vPad).toInt()

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            fm.ascent = minOf(fm.ascent, -ascentExtra)
            fm.descent = maxOf(fm.descent, descentExtra)
            fm.top = minOf(fm.top, fm.ascent)
            fm.bottom = maxOf(fm.bottom, fm.descent)
        }
        return width().toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        baseline: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val chipHeight = (ascentExtra + descentExtra).toFloat()
        canvas.drawRoundRect(
            x,
            baseline - ascentExtra.toFloat(),
            x + width(),
            baseline + descentExtra.toFloat(),
            chipHeight / 2,
            chipHeight / 2,
            bgPaint,
        )
        canvas.drawText("@$label", x + hPad, baseline.toFloat(), textPaint)
    }

    private fun width(): Float = textPaint.measureText("@$label") + hPad * 2

    private companion object {
        /** 胶囊字号相对编辑器字号的缩放。 */
        const val CHIP_TEXT_SCALE = 0.82f
    }
}

/** 可热替换的转发 Drawable：表情位图异步加载完成后替换内部实现并刷新视图。 */
private class SwapDrawable(initial: Drawable) : Drawable() {
    var delegate: Drawable = initial
        set(value) {
            field = value
            bounds = value.bounds
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) = delegate.draw(canvas)

    override fun setAlpha(alpha: Int) {
        delegate.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        delegate.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = delegate.opacity

    override fun getIntrinsicWidth(): Int = delegate.intrinsicWidth

    override fun getIntrinsicHeight(): Int = delegate.intrinsicHeight
}
