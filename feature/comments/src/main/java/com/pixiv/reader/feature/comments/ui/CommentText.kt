package com.pixiv.reader.feature.comments.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixiv.reader.core.ui.component.PixivImage
import com.pixiv.reader.feature.comments.R

// ── 评论文本表情（(xxx) → 行内图片）─────────────────────────────────────

/**
 * pixiv 评论文本表情映射：`(name)` → emoji 数字 id。
 * URL 规则 `https://s.pximg.net/common/images/emoji/{id}.png`，数据对齐 pixiv 官方
 * （Pixiv-Shaft Emoji.java / pixiv-viewer-app stampList.json 一致）。
 */
private val EMOJI_IDS: Map<String, Int> = mapOf(
    "normal" to 101, "surprise" to 102, "serious" to 103, "heaven" to 104,
    "happy" to 105, "excited" to 106, "sing" to 107, "cry" to 108,
    "normal2" to 201, "shame2" to 202, "love2" to 203, "interesting2" to 204,
    "blush2" to 205, "fire2" to 206, "angry2" to 207, "shine2" to 208, "panic2" to 209,
    "normal3" to 301, "satisfaction3" to 302, "surprise3" to 303, "smile3" to 304,
    "shock3" to 305, "gaze3" to 306, "wink3" to 307, "happy3" to 308, "excited3" to 309, "love3" to 310,
    "normal4" to 401, "surprise4" to 402, "serious4" to 403, "love4" to 404,
    "shine4" to 405, "sweat4" to 406, "shame4" to 407, "sleep4" to 408,
    "heart" to 501, "teardrop" to 502, "star" to 503,
)

/** `(xxx)` 表情标签正则（名字为小写字母/数字/下划线）。 */
private val EMOJI_TAG_REGEX = Regex("\\(([a-z0-9_]+)\\)")

/** 评论表情行内图片尺寸（对齐 pixiv 网页 24px）。 */
private val EMOJI_SIZE_SP = 24.sp
private val EMOJI_SIZE_DP = 24.dp

/**
 * 评论正文渲染：解析 `(xxx)` 文本表情为行内小图（24dp，对齐 pixiv 网页）。
 * 未命中映射表的 `(word)` 保持原文本；表情图片走 [PixivImage]（s.pximg.net 自动带 Referer）。
 */
@Composable
internal fun CommentText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val (annotated, inlineContent) = remember(text) { buildCommentText(text) }
    Text(
        text = annotated,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/** 把评论文本解析为 AnnotatedString（含行内表情图片）+ inlineContent 映射。 */
internal fun buildCommentText(text: String): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val builder = AnnotatedString.Builder()
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    var last = 0
    for (match in EMOJI_TAG_REGEX.findAll(text)) {
        val id = EMOJI_IDS[match.groupValues[1]] ?: continue
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
                PixivImage(
                    url = "https://s.pximg.net/common/images/emoji/$id.png",
                    contentDescription = stringResource(R.string.comment_emoji_cd),
                    modifier = Modifier.size(EMOJI_SIZE_DP),
                    contentScale = ContentScale.Fit,
                )
            },
        )
        last = match.range.last + 1
    }
    builder.append(text.substring(last))
    return builder.toAnnotatedString() to inlineContent
}
