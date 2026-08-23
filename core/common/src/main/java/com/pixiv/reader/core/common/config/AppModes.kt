package com.pixiv.reader.core.common.config

/**
 * 应用模式枚举集（统一裸 int 模式值）。
 *
 * 存储值即 [value]（int），与既有 DataStore 写入值完全一致，无迁移。
 * 反序列化一律走 [from]（非法值回退默认枚举）。
 */

/** 应用主题模式：跟随系统 / 浅色 / 深色（MainActivity + 我的页）。 */
enum class ThemeMode(val value: Int) {
    FOLLOW_SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun from(value: Int): ThemeMode = entries.firstOrNull { it.value == value } ?: FOLLOW_SYSTEM
    }
}

/** 插画查看器翻页方向：横向翻页 / 竖向翻页 / 无缝竖向。 */
enum class ViewerOrientation(val value: Int) {
    HORIZONTAL(0),
    VERTICAL(1),
    SEAMLESS(2);

    companion object {
        fun from(value: Int): ViewerOrientation =
            entries.firstOrNull { it.value == value } ?: HORIZONTAL
    }
}

/** 小说 Tab 默认页：推荐 / 关注。 */
enum class NovelDefaultTab(val value: Int) {
    RECOMMEND(0),
    FOLLOW(1);

    companion object {
        fun from(value: Int): NovelDefaultTab =
            entries.firstOrNull { it.value == value } ?: RECOMMEND
    }
}

/** 阅读器翻页方式：滑动 / 翻页 / 仿真。 */
enum class ReaderPageMode(val value: Int) {
    SCROLL(0),
    PAGINATE(1),
    SIMULATION(2);

    companion object {
        fun from(value: Int): ReaderPageMode = entries.firstOrNull { it.value == value } ?: SCROLL
    }
}

/** 阅读器主题：日间 / 纸张 / 夜间 / 深黑。 */
enum class ReaderThemeMode(val value: Int) {
    DAY(0),
    PAPER(1),
    NIGHT(2),
    DEEP_BLACK(3);

    companion object {
        fun from(value: Int): ReaderThemeMode = entries.firstOrNull { it.value == value } ?: PAPER
    }
}

/** 关注页左列用户排序：关注时间（官方序）/ 名称升序 / 名称降序 / 代表作最新发布。 */
enum class FollowSortMode(val value: Int) {
    FOLLOW_TIME(0),
    NAME_ASC(1),
    NAME_DESC(2),
    LATEST_WORK(3);

    companion object {
        fun from(value: Int): FollowSortMode =
            entries.firstOrNull { it.value == value } ?: FOLLOW_TIME
    }
}
