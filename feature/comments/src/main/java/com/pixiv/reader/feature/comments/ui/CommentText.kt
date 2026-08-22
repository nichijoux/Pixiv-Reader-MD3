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
import com.pixiv.reader.core.ui.component.emoji.PIXIV_EMOJI_IDS
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.feature.comments.R

// ── 评论文本表情（(xxx) → 行内图片）─────────────────────────────────────

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
