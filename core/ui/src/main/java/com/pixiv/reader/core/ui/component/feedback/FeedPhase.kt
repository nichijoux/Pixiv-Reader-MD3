package com.pixiv.reader.core.ui.component.feedback

/**
 * 分页列表互斥四态：`(isLoading, hasItems, hasError)` 等布尔组合的集中判定结果。
 *
 * 各列表页曾用布尔组合手写 `when` 分支（写错顺序即出 bug，如 error 先于 loading 判定
 * 会吞掉首载骨架），本判定收敛为单一权威：[feedPhase]。
 */
enum class FeedPhase {
    /** 首载中（无内容 + 加载中）：显示骨架占位。 */
    LOADING,

    /** 加载失败且无内容：显示错误 + 重试。 */
    ERROR,

    /** 无内容且无错误：显示空态文案。 */
    EMPTY,

    /** 有内容：渲染列表（触底加载中不影响本态）。 */
    CONTENT,
}

/**
 * 判定列表当前所处互斥态。分支顺序与项目既有约定一致：
 * 首载骨架 → 失败 → 空态 → 内容（有内容恒优先渲染）。
 *
 * @param loading 首载 / 下拉刷新进行中
 * @param hasItems 是否已有内容
 * @param hasError 是否存在错误（error 文案非空等）
 * @return 互斥四态之一
 */
fun feedPhase(loading: Boolean, hasItems: Boolean, hasError: Boolean): FeedPhase = when {
    loading && !hasItems -> FeedPhase.LOADING
    hasError && !hasItems -> FeedPhase.ERROR
    !hasItems -> FeedPhase.EMPTY
    else -> FeedPhase.CONTENT
}
