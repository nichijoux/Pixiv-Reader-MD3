package com.pixiv.reader.feature.follow.data

import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.common.config.FollowSortMode
import java.text.Collator
import java.util.Locale

/**
 * 关注用户列表排序（纯函数，JVM 单测覆盖）。
 *
 * 排序在客户端已加载数据上进行（零额外网络请求）；「关注时间」模式保持 API
 * 返回顺序（v1/user/following 官方序 = 关注时间倒序，模型无关注时间字段无法本地重排）。
 */
object FollowUserSorter {

    /**
     * 名称比较器：zh 拼音 + 英文混合排序（Collator 拼音规则），空白/缺失名恒排最后。
     * [Collator] 为 JVM 标准库（纯 JVM 可测），强度 PRIMARY 忽略大小写/变音符。
     */
    private val nameCollator: Collator =
        Collator.getInstance(Locale.CHINA).apply { strength = Collator.PRIMARY }

    /** 按名称升序比较（空白排最后）。 */
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
     * 按模式排序。分页加载新用户后调用方会重新执行，列表始终有序。
     *
     * - [FollowSortMode.FOLLOW_TIME]：保持 API 顺序（不排序）
     * - [FollowSortMode.NAME_ASC] / [FollowSortMode.NAME_DESC]：按名称（zh 拼音规则），空白/缺失名排最后
     * - [FollowSortMode.LATEST_WORK]：按代表作（[UserPreview.illusts] 首项）发布时间倒序，无代表作排最后
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
