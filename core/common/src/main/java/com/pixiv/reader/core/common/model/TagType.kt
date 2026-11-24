package com.pixiv.reader.core.common.model

/** 标签跳转搜索的分类；[isNovel] 对应深链参数 `sn=1`。 */
enum class TagType(val isNovel: Boolean) {
    ILLUST(false),
    NOVEL(true),
}
