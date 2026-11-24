package com.pixiv.reader.feature.follow.data

import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.common.config.FollowSortMode
import java.text.Collator
import java.util.Locale

/**
 * 纯函数，JVM 单测覆盖。排序在已加载数据上做（零额外请求）；「关注时间」模式只能保持 API 顺序
 * （v1/user/following 官方序即关注时间倒序，模型无关注时间字段无法本地重排）。
 */
object FollowUserSorter {

    /**
     * zh 拼音 + 英文混合排序（Collator 拼音规则，JVM 标准库故纯 JVM 可测）。
     * 强度 PRIMARY 表忽略大小写与变音符。
     */
    private val nameCollator: Collator =
        Collator.getInstance(Locale.CHINA).apply { strength = Collator.PRIMARY }

    private val nameAsc = Comparator<UserPreview> { a, b ->
        val na = a.user?.name.orEmpty()
        val nb = b.user?.name.orEmpty()
        val blankA = na.isBlank()
        val blankB = nb.isBlank()
        when {
            blankA && blankB -> 0
            blankA -> 1
            blankB -> -1
            else -> nameCollator.compare(na, nb)
        }
    }

    /**
     * 分页加载新用户后调用方会重新执行，列表始终有序。
     * 名称类模式空白/缺失名恒排最后；代表作模式按 [UserPreview.illusts] 首项发布时间倒序，无代表作排最后。
     */
    fun sort(users: List<UserPreview>, mode: FollowSortMode) = when (mode) {
        FollowSortMode.FOLLOW_TIME -> users
        FollowSortMode.NAME_ASC -> users.sortedWith(nameAsc)
        FollowSortMode.NAME_DESC -> users.sortedWith(Comparator { a, b ->
            val na = a.user?.name.orEmpty()
            val nb = b.user?.name.orEmpty()
            val blankA = na.isBlank()
            val blankB = nb.isBlank()
            when {
                blankA && blankB -> 0
                // 降序时空白/缺失名同样恒排最后（不能直接 reversed，会破坏此约定）
                blankA -> 1
                blankB -> -1
                else -> nameCollator.compare(nb, na)
            }
        })

        FollowSortMode.LATEST_WORK -> users.sortedWith(
            compareByDescending { it.illusts.firstOrNull()?.create_date ?: "" },
        )
    }
}
