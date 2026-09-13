package com.pixiv.reader.core.common.model

/**
 * 标签内容类型：卡片标签点击跳转搜索时，决定发现页预选的搜索分类。
 *
 * @property isNovel 是否为小说类标签（深链 `main?search=&sn=1` 线上格式的映射依据）
 */
enum class TagType(val isNovel: Boolean) {
    /** 作品（插画 / 漫画 / 动图）标签 → 搜作品。 */
    ILLUST(false),

    /** 小说标签（小说卡 / 系列 / 追更等）→ 搜小说。 */
    NOVEL(true),
}
