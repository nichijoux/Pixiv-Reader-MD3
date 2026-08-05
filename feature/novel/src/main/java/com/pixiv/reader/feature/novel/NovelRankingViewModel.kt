package com.pixiv.reader.feature.novel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.RankingModeInfo
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 小说排行榜 ViewModel：6 段榜单（日榜/周榜/男性向/女性向/新人/R18）滑动切换，
 * `GET /v1/novel/ranking?mode=` 游标分页（mode 与插画榜通用，无小说专属 mode）。
 *
 * 每段**独立** [PagedState]（[stateFor] 惰性创建并缓存，数据驻留 VM——滑动切回/配置变更不丢）；
 * 段首次进入时才加载（[onPageSelected] 幂等），失败可 [retry]，触底 [loadMore]。
 */
@HiltViewModel
class NovelRankingViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    val modes = listOf(
        RankingModeInfo(R.string.novel_ranking_day, "day"),
        RankingModeInfo(R.string.novel_ranking_week, "week"),
        RankingModeInfo(R.string.novel_ranking_male, "day_male"),
        RankingModeInfo(R.string.novel_ranking_female, "day_female"),
        RankingModeInfo(R.string.novel_ranking_rookie, "week_rookie"),
        RankingModeInfo(R.string.novel_ranking_r18, "day_r18"),
    )

    /** 各段独立分页状态：mode → PagedState，首次访问时创建并驻留。 */
    private val pages = mutableMapOf<String, PagedState<Novel>>()

    /** 已触发过首次加载的段（防止滑动切回重复请求）。 */
    private val initialized = mutableSetOf<String>()

    /** 操作通知（收藏等）：UI 侧 collect 显示 NotificationHost。 */
    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    init {
        ensureLoaded(modes.first().value)
    }

    /** 返回某段的分页状态（惰性创建，每次调用返回同一实例）。 */
    fun stateFor(value: String): PagedState<Novel> = pages.getOrPut(value) { PagedState() }

    /** 滑动/点 Tab 切到某段：仅首次进入才加载（数据已驻留则不重复请求）。 */
    fun onPageSelected(value: String) = ensureLoaded(value)

    /** 某段加载失败重试（始终重拉该段第一页）。 */
    fun retry(value: String) {
        initialized += value
        viewModelScope.launch { loadInitialFor(stateFor(value), value) }
    }

    /** 某段触底加载下一页。 */
    fun loadMore(value: String) {
        viewModelScope.launch { stateFor(value).loadMore() }
    }

    private fun ensureLoaded(value: String) {
        if (!initialized.add(value)) return
        viewModelScope.launch { loadInitialFor(stateFor(value), value) }
    }

    private suspend fun loadInitialFor(paged: PagedState<Novel>, mode: String) {
        paged.loadInitial(
            fetch = { pixivRepository.api.getRankingNovels(mode) },
            fetchNext = { pixivRepository.api.getNextNovels(it) },
        )
    }

    /** 收藏 / 取消收藏小说（nowFavorite 为目标状态，由组件回调），成功/失败发通知。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (nowFavorite) pixivRepository.api.bookmarkNovel(novelId, "public", emptyList())
                else pixivRepository.api.unbookmarkNovel(novelId)
            }.onSuccess {
                _message.send(UiMessage(if (nowFavorite) R.string.novel_msg_bookmarked else R.string.novel_msg_unbookmarked))
            }.onFailure {
                _message.send(UiMessage(R.string.novel_msg_action_failed, listOf(it.message ?: "")))
            }
        }
    }
}
