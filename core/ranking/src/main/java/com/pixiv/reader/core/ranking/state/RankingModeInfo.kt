package com.pixiv.reader.core.ranking.state

import androidx.annotation.StringRes

/**
 * 各内容类型（漫画/插画/小说）按自身可用的 pixiv ranking mode 提供一组；[value] 直接作为
 * `GET /v1/illust/ranking?mode=` 的 mode 值。
 */
data class RankingModeInfo(
    @param:StringRes val labelRes: Int,
    val value: String,
)