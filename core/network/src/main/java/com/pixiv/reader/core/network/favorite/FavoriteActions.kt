package com.pixiv.reader.core.network.favorite

import com.pixiv.api.PixivConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.pixiv.reader.core.network.session.PixivRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 收藏 / 关注 / 追更统一动作（core 共享）：直连 API，返回成功/失败
 * （断网等网络失败对调用方表现为失败——UI 弹错误提示并回滚乐观状态）。
 *
 * 各 feature 的 ViewModel 注入本类后只保留消息文案与本地状态（防连点/翻转），
 * 消除此前在 10+ 个 VM 间逐文件复制的同体 runCatching 样板。
 */
@Singleton
class FavoriteActions @Inject constructor(
    private val pixivRepository: PixivRepository,
) {

    /**
     * 收藏 / 取消收藏插画（nowFavorite 为目标状态）。
     *
     * @param illustId 插画 id
     * @param nowFavorite 目标状态：true=收藏，false=取消收藏
     * @param restrict 收藏可见性：public（默认公开）/ private（私密收藏）
     * @param tags 收藏标签名列表（随收藏一并提交，可空）
     * @return 成功 / 失败（含断网等网络失败与服务端明确拒绝）
     */
    suspend fun toggleIllustFavorite(
        illustId: Long,
        nowFavorite: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
        tags: List<String> = emptyList(),
    ): Result<Unit> = runDirect {
        if (nowFavorite) pixivRepository.api.bookmarkIllust(illustId, restrict, tags)
        else pixivRepository.api.unbookmarkIllust(illustId)
    }

    /**
     * 收藏 / 取消收藏小说（nowFavorite 为目标状态）。
     *
     * @param novelId 小说 id
     * @param nowFavorite 目标状态：true=收藏，false=取消收藏
     * @param restrict 收藏可见性：public（默认公开）/ private（私密收藏）
     * @param tags 收藏标签名列表（随收藏一并提交，可空）
     * @return 成功 / 失败（含断网等网络失败与服务端明确拒绝）
     */
    suspend fun toggleNovelFavorite(
        novelId: Long,
        nowFavorite: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
        tags: List<String> = emptyList(),
    ): Result<Unit> = runDirect {
        if (nowFavorite) pixivRepository.api.bookmarkNovel(novelId, restrict, tags)
        else pixivRepository.api.unbookmarkNovel(novelId)
    }

    /**
     * 关注 / 取关用户（nowFollowed 为目标状态）。
     *
     * @param userId 用户 id
     * @param nowFollowed 目标状态：true=关注，false=取关
     * @param restrict 关注可见性：public（默认公开）/ private（私密关注，仅自己可见）
     * @return 成功 / 失败（含断网等网络失败与服务端明确拒绝）
     */
    suspend fun toggleFollowUser(
        userId: Long,
        nowFollowed: Boolean,
        restrict: String = PixivConstants.RESTRICT_PUBLIC,
    ): Result<Unit> = runDirect {
        if (nowFollowed) pixivRepository.api.followUser(userId, restrict)
        else pixivRepository.api.unfollowUser(userId)
    }

    /**
     * 追更 / 取消追更漫画系列（nowWatchlisted 为目标状态）。
     *
     * @param seriesId 漫画系列 id
     * @param nowWatchlisted 目标状态：true=追更，false=取消追更
     * @return 成功 / 失败（含断网等网络失败与服务端明确拒绝）
     */
    suspend fun toggleMangaWatchlist(
        seriesId: Long,
        nowWatchlisted: Boolean,
    ): Result<Unit> = runDirect {
        if (nowWatchlisted) pixivRepository.api.addWatchlistManga(seriesId)
        else pixivRepository.api.removeWatchlistManga(seriesId)
    }

    /**
     * 追更 / 取消追更小说系列（nowWatchlisted 为目标状态）。
     *
     * @param seriesId 小说系列 id
     * @param nowWatchlisted 目标状态：true=追更，false=取消追更
     * @return 成功 / 失败（含断网等网络失败与服务端明确拒绝）
     */
    suspend fun toggleNovelWatchlist(
        seriesId: Long,
        nowWatchlisted: Boolean,
    ): Result<Unit> = runDirect {
        if (nowWatchlisted) pixivRepository.api.addWatchlistNovel(seriesId)
        else pixivRepository.api.removeWatchlistNovel(seriesId)
    }

    // ── 静默变体（fire-and-forget：结果不回传，失败时调用方不可感知——
    //    仅适合卡片快速收藏等低风险场景；需感知失败请直接调用挂起变体）。
    //    收敛各 VM 的 `viewModelScope.launch { toggleX(...) }` 五行包装为方法体单行委托。 ──

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

/** 直连包装：成功返回 [Result.success]；取消异常原样上抛（作用域销毁不误报失败），其余转失败。 */
private inline fun <T> runDirect(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
