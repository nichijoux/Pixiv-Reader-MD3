package com.pixiv.reader.core.common.parse

/**
 * 评论草稿编解码：草稿文本（含 `(tag)` 文本表情与开头 `@昵称 ` 回复提及）↔ 段落序列。
 *
 * 输入框（EditText）侧把 [EmoteSegment.Emote]/[EmoteSegment.Mention] 各渲染为 1 个
 * [EMOTE_SENTINEL] 占位字符 + Span，因此退格一次即整块删除；对外状态仍是纯文
 * 本草稿（发布 API 协议不变）。本文件为纯函数，本地 JVM 单测覆盖。
 */

/** 表情/提及在编辑缓冲区中的占位字符。 */
const val EMOTE_SENTINEL = '\uFFFC'

/** 草稿段落：普通文本 / 文本表情 / 回复提及。 */
sealed interface EmoteSegment {
    /** 普通文本片段。 */
    data class Text(val value: String) : EmoteSegment

    /** 文本表情 `(tag)`，缓冲区内渲染为行内小图。 */
    data class Emote(val tag: String) : EmoteSegment

    /** 回复提及，序列化为 `@name `（含尾随空格），缓冲区内渲染为胶囊。 */
    data class Mention(val name: String) : EmoteSegment
}

/** `(xxx)` 表情标签正则（名字为小写字母/数字/下划线），与渲染侧一致。 */
private val EMOTE_TAG_REGEX = Regex("""\(([a-z0-9_]+)\)""")

/**
 * 草稿文本 → 段落序列。
 *
 * @param knownTags 合法的表情标签集合；未命中的 `(word)` 保持原文本不转图
 * @param mentionName 当前回复目标的精确昵称；草稿以 `@昵称 ` 开头时解析为提及
 *   （精确匹配而非 `\S+` 截断——pixiv 昵称可含空格）
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

/** 段落序列 → 草稿文本（decode 反向）。 */
fun List<EmoteSegment>.toDraftText(): String = joinToString(separator = "") { segment ->
    when (segment) {
        is EmoteSegment.Text -> segment.value
        is EmoteSegment.Emote -> "(${segment.tag})"
        is EmoteSegment.Mention -> "@${segment.name} "
    }
}
