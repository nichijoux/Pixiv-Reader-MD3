package com.pixiv.reader.core.ui.component.feedback

/** 分页列表互斥四态，统一由 [feedPhase] 判定（避免手写分支顺序写错吞掉首载骨架）。 */
enum class FeedPhase {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT,
}

fun feedPhase(loading: Boolean, hasItems: Boolean, hasError: Boolean): FeedPhase = when {
    loading && !hasItems -> FeedPhase.LOADING
    hasError && !hasItems -> FeedPhase.ERROR
    !hasItems -> FeedPhase.EMPTY
    else -> FeedPhase.CONTENT
}
