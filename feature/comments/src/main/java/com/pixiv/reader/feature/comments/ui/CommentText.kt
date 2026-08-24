package com.pixiv.reader.feature.comments.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pixiv.reader.core.ui.component.emoji.buildEmojiAnnotatedString
import com.pixiv.reader.feature.comments.R

/**
 * 评论正文渲染：`(xxx)` 文本表情 → 行内小图（24dp，对齐 pixiv 网页），
 * 未命中映射表的 `(word)` 保持原文本。解析逻辑复用 core:ui 的 [buildEmojiAnnotatedString]。
 */
@Composable
internal fun CommentText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val contentDescription = stringResource(R.string.comment_emoji_cd)
    val (annotated, inlineContent) = remember(text, contentDescription) {
        buildEmojiAnnotatedString(text, contentDescription)
    }
    Text(
        text = annotated,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}
