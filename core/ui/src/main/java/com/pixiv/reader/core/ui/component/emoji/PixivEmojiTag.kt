package com.pixiv.reader.core.ui.component.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * pixiv 评论文本表情映射：`(name)` → emoji 数字 id。
 * URL 规则 `https://s.pximg.net/common/images/emoji/{id}.png`，数据对齐 pixiv 官方
 * （Pixiv-Shaft Emoji.java / pixiv-viewer-app stampList.json 一致）。
 *
 * 供评论**渲染侧**（把 `(xxx)` 文本转行内图）、**输入框 VisualTransformation**（显示表情图像）
 * 与**发布面板**（表情选择器显示图像）共用。
 */
val PIXIV_EMOJI_IDS: Map<String, Int> = mapOf(
    "normal" to 101,
    "surprise" to 102,
    "serious" to 103,
    "heaven" to 104,
    "happy" to 105,
    "excited" to 106,
    "sing" to 107,
    "cry" to 108,
    "normal2" to 201,
    "shame2" to 202,
    "love2" to 203,
    "interesting2" to 204,
    "blush2" to 205,
    "fire2" to 206,
    "angry2" to 207,
    "shine2" to 208,
    "panic2" to 209,
    "normal3" to 301,
    "satisfaction3" to 302,
    "surprise3" to 303,
    "smile3" to 304,
    "shock3" to 305,
    "gaze3" to 306,
    "wink3" to 307,
    "happy3" to 308,
    "excited3" to 309,
    "love3" to 310,
    "normal4" to 401,
    "surprise4" to 402,
    "serious4" to 403,
    "love4" to 404,
    "shine4" to 405,
    "sweat4" to 406,
    "shame4" to 407,
    "sleep4" to 408,
    "heart" to 501,
    "teardrop" to 502,
    "star" to 503,
)

/** 评论表情标签（与 [PIXIV_EMOJI_IDS] 的 key 一致），发布面板选项顺序。 */
val PIXIV_EMOJI_TAGS: List<String> = listOf(
    "normal",
    "surprise",
    "serious",
    "heaven",
    "happy",
    "excited",
    "sing",
    "cry",
    "normal2",
    "shame2",
    "love2",
    "interesting2",
    "blush2",
    "fire2",
    "angry2",
    "shine2",
    "panic2",
    "normal3",
    "satisfaction3",
    "surprise3",
    "smile3",
    "shock3",
    "gaze3",
    "wink3",
    "happy3",
    "excited3",
    "love3",
    "normal4",
    "surprise4",
    "serious4",
    "love4",
    "shine4",
    "sweat4",
    "shame4",
    "sleep4",
    "heart",
    "teardrop",
    "star",
)

/**
 * 表情图片 URL（`s.pximg.net/common/images/emoji/{id}.png`）。
 * 输入框行内图 / 渲染侧 / 发布面板选择器共用；tag 未命中映射返回 null。
 */
fun pixivEmojiUrl(tag: String): String? =
    PIXIV_EMOJI_IDS[tag]?.let { id -> "https://s.pximg.net/common/images/emoji/$id.png" }

/**
 * pixiv 评论表情图片（按 [tag] 从 [PIXIV_EMOJI_IDS] 取 id，渲染 `s.pximg.net/common/images/emoji/{id}.png`）。
 * 用于发布面板表情选择器显示图像；渲染侧（评论文本 `(xxx)` 转图）在 feature/comments 复用同映射。
 *
 * @param tag 表情标签（如 `normal`/`happy`，不含括号）
 * @param contentDescription 无障碍描述
 * @param modifier 外部传入的 Modifier（如尺寸/圆角/点击）
 * @param size 图片边长（默认 24dp，对齐 pixiv 网页行内表情）
 */
@Composable
fun PixivEmojiTagImage(
    tag: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val url = pixivEmojiUrl(tag) ?: return
    PixivImage(
        url = url,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

/**
 * 评论表情选择项（发布面板用）：圆角底 + emoji 图像，点击回调。
 * 显示**图像**而非文本标签（用户可视化选择）。
 *
 * @param tag 表情标签（不含括号）
 * @param onClick 点击回调（外部插 `($tag)` 到草稿）
 */
@Composable
fun PixivEmojiTagChip(
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .size(Sizes.s36),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        PixivEmojiTagImage(tag = tag, contentDescription = tag, size = 24.dp)
    }
}

/** `(xxx)` 表情标签正则（名字为小写字母/数字/下划线）；编解码与渲染侧共用。 */
internal val EMOJI_TAG_REGEX = Regex("""\(([a-z0-9_]+)\)""")

/** 评论表情行内图片尺寸（对齐 pixiv 网页 24px）。 */
private val EMOJI_SIZE_SP = 24.sp
private val EMOJI_SIZE_DP = 24.dp

/**
 * 把评论文本解析为含行内表情图片的 [AnnotatedString] + inlineContent 映射。
 * 未命中映射的 `(word)` 保持原文本；每个命中表情用 `appendInlineContent` 占位（文本长度不变）。
 * 渲染侧（评论文本转图）与发布面板预览共用。
 *
 * @param contentDescription 表情图的无障碍描述；null 不描述
 */
fun buildEmojiAnnotatedString(
    text: String,
    contentDescription: String? = null,
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val builder = AnnotatedString.Builder()
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    var last = 0
    for (match in EMOJI_TAG_REGEX.findAll(text)) {
        val id = PIXIV_EMOJI_IDS[match.groupValues[1]] ?: continue
        builder.append(text.substring(last, match.range.first))
        val tag = "emoji_$id"
        builder.appendInlineContent(tag, "◼")
        inlineContent[tag] = InlineTextContent(
            placeholder = Placeholder(
                width = EMOJI_SIZE_SP,
                height = EMOJI_SIZE_SP,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
            children = { _ ->
                PixivEmojiTagImage(
                    tag = match.groupValues[1],
                    contentDescription = contentDescription,
                    size = EMOJI_SIZE_DP
                )
            },
        )
        last = match.range.last + 1
    }
    builder.append(text.substring(last))
    return builder.toAnnotatedString() to inlineContent
}
