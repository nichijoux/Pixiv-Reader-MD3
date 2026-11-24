package com.pixiv.reader.core.comment.data

/**
 * 评论草稿编解码：草稿文本（含 `(tag)` 文本表情与开头 `@昵称 ` 提及）↔ 段落序列。
 *
 * 输入框侧把 Emote/Mention 各渲染为 1 个 [EMOTE_SENTINEL] 占位字符 + Span，退格一次即整块删除；
 * 对外状态仍是纯文本草稿（发布 API 协议不变）。本文件为纯函数，本地 JVM 单测覆盖。
 */

/** 表情/提及在编辑缓冲区中的占位字符（不可见对象替换符）。 */
const val EMOTE_SENTINEL = '\uFFFC'

sealed interface EmoteSegment {
    data class Text(val value: String) : EmoteSegment

    data class Emote(val tag: String) : EmoteSegment

    /** 序列化为 `@name `（含尾随空格） */
    data class Mention(val name: String) : EmoteSegment
}

/** 与渲染侧标签正则保持一致 */
private val EMOTE_TAG_REGEX = Regex("""\(([a-z0-9_]+)\)""")

/**
 * 未命中 [knownTags] 的 `(word)` 保持原文本不转图；[mentionName] 必须精确匹配（不能用 `\S+` 截断，
 * pixiv 昵称可含空格）。
 */
fun encodeEmoteDraft(
    draft: String,
    knownTags: Set<String>,
    mentionName: String? = null,
): List<EmoteSegment> {
    val segments = mutableListOf<EmoteSegment>()
    var start = 0
    if (!mentionName.isNullOrEmpty() && draft.startsWith("@$mentionName ")) {
        segments += EmoteSegment.Mention(mentionName)
        start = mentionName.length + 2 // "@name "
    }
    for (match in EMOTE_TAG_REGEX.findAll(draft, start)) {
        val tag = match.groupValues[1]
        if (tag !in knownTags) continue
        if (start < match.range.first) segments += EmoteSegment.Text(draft.substring(start, match.range.first))
        segments += EmoteSegment.Emote(tag)
        start = match.range.last + 1
    }
    if (start < draft.length) segments += EmoteSegment.Text(draft.substring(start))
    return segments
}

fun List<EmoteSegment>.toDraftText(): String = joinToString(separator = "") { segment ->
    when (segment) {
        is EmoteSegment.Text -> segment.value
        is EmoteSegment.Emote -> "(${segment.tag})"
        is EmoteSegment.Mention -> "@${segment.name} "
    }
}
