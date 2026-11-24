package com.pixiv.reader.core.network.favorite

import com.pixiv.api.PixivConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.pixiv.reader.core.network.session.PixivRepository
import javax.inject.Inject
import javax.inject.Singleton

/** 收藏 / 关注 / 追更统一动作，维护进程级乐观覆盖状态供跨页面同步；失败或取消时自动回滚。 */
@Singleton
class FavoriteActions @Inject constructor(
    private val pixivRepository: PixivRepository,
) {

    private val _illustBookmarks = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val illustBookmarks: StateFlow<Map<Long, Boolean>> = _illustBookmarks.asStateFlow()

    private val _novelBookmarks = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val novelBookmarks: StateFlow<Map<Long, Boolean>> = _novelBookmarks.asStateFlow()

    private val _followedUsers = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val followedUsers: StateFlow<Map<Long, Boolean>> = _followedUsers.asStateFlow()

    fun setIllustBookmarkState(illustId: Long, isBookmarked: Boolean) {
        _illustBookmarks.update { it + (illustId to isBookmarked) }
    }

    fun setNovelBookmarkState(novelId: Long, isBookmarked: Boolean) {
        _novelBookmarks.update { it + (novelId to isBookmarked) }
    }

    fun setUserFollowState(userId: Long, isFollowed: Boolean) {
        _followedUsers.update { it + (userId to isFollowed) }
    }

    fun clearSessionOverrides() {
        _illustBookmarks.value = emptyMap()
        _novelBookmarks.value = emptyMap()
        _followedUsers.value = emptyMap()
    }

    suspend fun toggleIllustFavorite(
        illustId: Long,
        nowFavorite: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
        tags: List<String> = emptyList(),
    ): Result<Unit> = runOptimisticToggle(_illustBookmarks, illustId, nowFavorite) {
        if (nowFavorite) pixivRepository.api.bookmarkIllust(illustId, restrict, tags)
        else pixivRepository.api.unbookmarkIllust(illustId)
    }

    suspend fun toggleNovelFavorite(
        novelId: Long,
        nowFavorite: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
        tags: List<String> = emptyList(),
    ): Result<Unit> = runOptimisticToggle(_novelBookmarks, novelId, nowFavorite) {
        if (nowFavorite) pixivRepository.api.bookmarkNovel(novelId, restrict, tags)
        else pixivRepository.api.unbookmarkNovel(novelId)
    }

    suspend fun toggleFollowUser(
        userId: Long,
        nowFollowed: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
    ): Result<Unit> = runOptimisticToggle(_followedUsers, userId, nowFollowed) {
        if (nowFollowed) pixivRepository.api.followUser(userId, restrict)
        else pixivRepository.api.unfollowUser(userId)
    }

    private inline fun runOptimisticToggle(
        state: MutableStateFlow<Map<Long, Boolean>>,
        id: Long,
        targetValue: Boolean,
        action: () -> Unit,
    ): Result<Unit> {
        var previous: Boolean? = null
        state.update { current ->
            previous = current[id]
            current + (id to targetValue)
        }
        val rollbackValue = previous ?: !targetValue
        val result = try {
            runDirect(action)
        } catch (e: CancellationException) {
            state.update { current ->
                if (current[id] == targetValue) current + (id to rollbackValue) else current
            }
            throw e
        }
        if (result.isFailure) {
            state.update { current ->
                if (current[id] == targetValue) current + (id to rollbackValue) else current
            }
        }
        return result
    }

    suspend fun toggleMangaWatchlist(
        seriesId: Long,
        nowWatchlisted: Boolean,
    ): Result<Unit> = runDirect {
        if (nowWatchlisted) pixivRepository.api.addWatchlistManga(seriesId)
        else pixivRepository.api.removeWatchlistManga(seriesId)
    }

    suspend fun toggleNovelWatchlist(
        seriesId: Long,
        nowWatchlisted: Boolean,
    ): Result<Unit> = runDirect {
        if (nowWatchlisted) pixivRepository.api.addWatchlistNovel(seriesId)
        else pixivRepository.api.removeWatchlistNovel(seriesId)
    }

    // fire-and-forget 静默变体：不回传失败，仅用于卡片快捷操作
    fun toggleIllustFavoriteSilent(
        scope: CoroutineScope,
        illustId: Long,
        nowFavorite: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
        tags: List<String> = emptyList(),
    ) {
        scope.launch { toggleIllustFavorite(illustId, nowFavorite, restrict, tags) }
    }

    fun toggleNovelFavoriteSilent(
        scope: CoroutineScope,
        novelId: Long,
        nowFavorite: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
        tags: List<String> = emptyList(),
    ) {
        scope.launch { toggleNovelFavorite(novelId, nowFavorite, restrict, tags) }
    }

    fun toggleFollowUserSilent(
        scope: CoroutineScope,
        userId: Long,
        nowFollowed: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
    ) {
        scope.launch { toggleFollowUser(userId, nowFollowed, restrict) }
    }
}

private inline fun <T> runDirect(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
