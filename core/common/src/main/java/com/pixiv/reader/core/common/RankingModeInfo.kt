package com.pixiv.reader.core.common

import androidx.annotation.StringRes

/**
 * 排行榜分段配置（通用组件数据源）。
 *
 * 每个内容类型（漫画/插画/小说）按自身可用的 pixiv ranking mode 提供一组 [RankingModeInfo]；
 * [labelRes] 为分段显示名资源（如"日榜"），[value] 为 `GET /v1/illust/ranking?mode=` 的 mode 值。
 * 由 `core:ui RankingList` 消费，未来小说/插画排行榜直接复用。
 */
data class RankingModeInfo(
    @StringRes val labelRes: Int,
    val value: String,
)