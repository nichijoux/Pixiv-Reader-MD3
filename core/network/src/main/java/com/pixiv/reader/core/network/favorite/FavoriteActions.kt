package com.pixiv.reader.core.network.favorite

import com.pixiv.api.PixivConstants
import com.pixiv.reader.core.database.entity.PendingActionEntity
import com.pixiv.reader.core.network.action.OfflineActionQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.pixiv.reader.core.network.session.PixivRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 收藏 / 关注 / 追更统一动作（core 共享）：经 [OfflineActionQueue] 在线直连、离线入队补发，
 * 返回成功/失败（网络类失败对调用方表现为成功——UI 乐观翻转，实际同步由队列兜底）。
 *
 * 各 feature 的 ViewModel 注入本类后只保留消息文案与本地状态（防连点/翻转），
 * 消除此前在 10+ 个 VM 间逐文件复制的同体 runCatching 样板。
 */
@Singleton
class FavoriteActions @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val offlineQueue: OfflineActionQueue,
) {

    /**
     * 收藏 / 取消收藏插画（nowFavorite 为目标状态；断网自动入队待同步）。
     *
     * @param illustId 插画 id
     * @param nowFavorite 目标状态：true=收藏，false=取消收藏
     * @param restrict 收藏可见性：public（默认公开）/ private（私密收藏）
     * @param tags 收藏标签名列表（随收藏一并提交，可空）
     * @return 成功（含离线入队）/ 失败（服务端明确拒绝）
     */
    suspend fun toggleIllustFavorite(
        illustId: Long,
        nowFavorite: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
        tags: List<String> = emptyList(),
    ): Result<Unit> = offlineQueue.runOrQueue(
        family = PendingActionEntity.FAMILY_ILLUST_BOOKMARK,
        targetId = illustId,
        targetState = nowFavorite,
        params = OfflineActionQueue.bookmarkParams(restrict, tags),
    ) {
        if (nowFavorite) pixivRepository.api.bookmarkIllust(illustId, restrict, tags)
        else pixivRepository.api.unbookmarkIllust(illustId)
    }

    /**
     * 收藏 / 取消收藏小说（nowFavorite 为目标状态；断网自动入队待同步）。
     *
     * @param novelId 小说 id
     * @param nowFavorite 目标状态：true=收藏，false=取消收藏
     * @param restrict 收藏可见性：public（默认公开）/ private（私密收藏）
     * @param tags 收藏标签名列表（随收藏一并提交，可空）
     * @return 成功（含离线入队）/ 失败（服务端明确拒绝）
     */
    suspend fun toggleNovelFavorite(
        novelId: Long,
        nowFavorite: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
        tags: List<String> = emptyList(),
    ): Result<Unit> = offlineQueue.runOrQueue(
        family = PendingActionEntity.FAMILY_NOVEL_BOOKMARK,
        targetId = novelId,
        targetState = nowFavorite,
        params = OfflineActionQueue.bookmarkParams(restrict, tags),
    ) {
        if (nowFavorite) pixivRepository.api.bookmarkNovel(novelId, restrict, tags)
        else pixivRepository.api.unbookmarkNovel(novelId)
    }

    /**
     * 关注 / 取关用户（nowFollowed 为目标状态；断网自动入队待同步）。
     *
     * @param userId 用户 id
     * @param nowFollowed 目标状态：true=关注，false=取关
     * @param restrict 关注可见性：public（默认公开）/ private（私密关注，仅自己可见）
     * @return 成功（含离线入队）/ 失败（服务端明确拒绝）
     */
    suspend fun toggleFollowUser(
        userId: Long,
        nowFollowed: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
    ): Result<Unit> = offlineQueue.runOrQueue(
        family = PendingActionEntity.FAMILY_FOLLOW_USER,
        targetId = userId,
        targetState = nowFollowed,
    ) {
        if (nowFollowed) pixivRepository.api.followUser(userId, restrict)
        else pixivRepository.api.unfollowUser(userId)
    }

    /**
     * 追更 / 取消追更漫画系列（nowWatchlisted 为目标状态；断网自动入队待同步）。
     *
     * @param seriesId 漫画系列 id
     * @param nowWatchlisted 目标状态：true=追更，false=取消追更
     * @return 成功（含离线入队）/ 失败（服务端明确拒绝）
     */
    suspend fun toggleMangaWatchlist(
        seriesId: Long,
        nowWatchlisted: Boolean,
    ): Result<Unit> = offlineQueue.runOrQueue(
        family = PendingActionEntity.FAMILY_MANGA_WATCHLIST,
        targetId = seriesId,
        targetState = nowWatchlisted,
    ) {
        if (nowWatchlisted) pixivRepository.api.addWatchlistManga(seriesId)
        else pixivRepository.api.removeWatchlistManga(seriesId)
    }

    /**
     * 追更 / 取消追更小说系列（nowWatchlisted 为目标状态；断网自动入队待同步）。
     *
     * @param seriesId 小说系列 id
     * @param nowWatchlisted 目标状态：true=追更，false=取消追更
     * @return 成功（含离线入队）/ 失败（服务端明确拒绝）
     */
    suspend fun toggleNovelWatchlist(
        seriesId: Long,
        nowWatchlisted: Boolean,
    ): Result<Unit> = offlineQueue.runOrQueue(
        family = PendingActionEntity.FAMILY_NOVEL_WATCHLIST,
        targetId = seriesId,
        targetState = nowWatchlisted,
    ) {
        if (nowWatchlisted) pixivRepository.api.addWatchlistNovel(seriesId)
        else pixivRepository.api.removeWatchlistNovel(seriesId)
    }

    // ── 静默变体（成功/失败均不提示，失败仅由调用方按需处理）：收敛各 VM 的
    //    `viewModelScope.launch { toggleX(...) }` 五行包装为方法体单行委托。 ──

    /** 收藏 / 取消收藏插画（静默；[restrict]/[tags] 语义同 [toggleIllustFavorite]）。 */
    fun toggleIllustFavoriteSilent(
        scope: CoroutineScope,
        illustId: Long,
        nowFavorite: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
        tags: List<String> = emptyList(),
    ) {
        scope.launch { toggleIllustFavorite(illustId, nowFavorite, restrict, tags) }
    }

    /** 收藏 / 取消收藏小说（静默；[restrict]/[tags] 语义同 [toggleNovelFavorite]）。 */
    fun toggleNovelFavoriteSilent(
        scope: CoroutineScope,
        novelId: Long,
        nowFavorite: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
        tags: List<String> = emptyList(),
    ) {
        scope.launch { toggleNovelFavorite(novelId, nowFavorite, restrict, tags) }
    }

    /** 关注 / 取关用户（静默；[restrict] 语义同 [toggleFollowUser]）。 */
    fun toggleFollowUserSilent(
        scope: CoroutineScope,
        userId: Long,
        nowFollowed: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
    ) {
        scope.launch { toggleFollowUser(userId, nowFollowed, restrict) }
    }
}
