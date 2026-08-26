package com.pixiv.reader.core.network.favorite

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.pixiv.reader.core.network.session.PixivRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 收藏 / 关注统一动作（core 共享）：封装 API 调用与 runCatching，返回成功/失败。
 *
 * 各 feature 的 ViewModel 注入本类后只保留消息文案与本地状态（防连点/翻转），
 * 消除此前在 10+ 个 VM 间逐文件复制的同体 runCatching 样板。
 */
@Singleton
class FavoriteActions @Inject constructor(
    private val pixivRepository: PixivRepository,
) {

    /** 收藏 / 取消收藏插画（nowFavorite 为目标状态）。 */
    suspend fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean): Result<Unit> = runCatching {
        if (nowFavorite) pixivRepository.api.bookmarkIllust(illustId, "public", emptyList())
        else pixivRepository.api.unbookmarkIllust(illustId)
    }

    /** 收藏 / 取消收藏小说（nowFavorite 为目标状态）。 */
    suspend fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean): Result<Unit> = runCatching {
        if (nowFavorite) pixivRepository.api.bookmarkNovel(novelId, "public", emptyList())
        else pixivRepository.api.unbookmarkNovel(novelId)
    }

    /** 关注 / 取关用户（nowFollowed 为目标状态）。 */
    suspend fun toggleFollowUser(userId: Long, nowFollowed: Boolean): Result<Unit> = runCatching {
        if (nowFollowed) pixivRepository.api.followUser(userId, "public")
        else pixivRepository.api.unfollowUser(userId)
    }

    // ── 静默变体（成功/失败均不提示，失败仅由调用方按需处理）：收敛各 VM 的
    //    `viewModelScope.launch { toggleX(...) }` 五行包装为方法体单行委托。 ──

    /** 收藏 / 取消收藏插画（静默）。 */
    fun toggleIllustFavoriteSilent(scope: CoroutineScope, illustId: Long, nowFavorite: Boolean) {
        scope.launch { toggleIllustFavorite(illustId, nowFavorite) }
    }

    /** 收藏 / 取消收藏小说（静默）。 */
    fun toggleNovelFavoriteSilent(scope: CoroutineScope, novelId: Long, nowFavorite: Boolean) {
        scope.launch { toggleNovelFavorite(novelId, nowFavorite) }
    }

    /** 关注 / 取关用户（静默）。 */
    fun toggleFollowUserSilent(scope: CoroutineScope, userId: Long, nowFollowed: Boolean) {
        scope.launch { toggleFollowUser(userId, nowFollowed) }
    }
}
