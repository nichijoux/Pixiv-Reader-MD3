package com.pixiv.reader.core.common.config

// 各枚举的 value 即 DataStore 持久化的裸 int，勿改既有编号；非法值经 from() 回退默认。

enum class ThemeMode(val value: Int) {
    FOLLOW_SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun from(value: Int): ThemeMode = entries.firstOrNull { it.value == value } ?: FOLLOW_SYSTEM
    }
}

enum class ViewerOrientation(val value: Int) {
    HORIZONTAL(0),
    VERTICAL(1),
    SEAMLESS(2);

    companion object {
        fun from(value: Int): ViewerOrientation =
            entries.firstOrNull { it.value == value } ?: HORIZONTAL
    }
}

enum class NovelDefaultTab(val value: Int) {
    RECOMMEND(0),
    FOLLOW(1);

    companion object {
        fun from(value: Int): NovelDefaultTab =
            entries.firstOrNull { it.value == value } ?: RECOMMEND
    }
}

enum class ReaderPageMode(val value: Int) {
    SCROLL(0),
    PAGINATE(1),
    SIMULATION(2);

    companion object {
        fun from(value: Int): ReaderPageMode = entries.firstOrNull { it.value == value } ?: SCROLL
    }
}

/** 仅翻页与仿真模式生效，滑动模式恒为单列；横屏 = 视口宽 > 高，平板 = smallestScreenWidthDp >= 600。 */
enum class ReaderDualPageMode(val value: Int) {
    OFF(0),
    ALWAYS(1),
    LANDSCAPE(2),
    LANDSCAPE_OR_TABLET(3);

    companion object {
        fun from(value: Int): ReaderDualPageMode =
            entries.firstOrNull { it.value == value } ?: LANDSCAPE_OR_TABLET
    }
}

enum class ReaderThemeMode(val value: Int) {
    DAY(0),
    PAPER(1),
    NIGHT(2),
    DEEP_BLACK(3);

    companion object {
        fun from(value: Int): ReaderThemeMode = entries.firstOrNull { it.value == value } ?: PAPER
    }
}

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

enum class BottomNavBarStyle(val value: Int) {
    DOCKED(0),
    FLOATING_CAPSULE(1);

    companion object {
        fun from(value: Int): BottomNavBarStyle =
            entries.firstOrNull { it.value == value } ?: DOCKED
    }
}
