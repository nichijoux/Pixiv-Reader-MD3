package com.pixiv.reader.feature.novel.state

import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.ranking.state.RankingModeInfo
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.ranking.state.RankingPagedViewModel
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.feature.novel.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 6 段榜单；分段/惰性加载/重试/触底由 [RankingPagedViewModel] 承担，mode 与插画榜通用。 */
@HiltViewModel
class NovelRankingViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
) : RankingPagedViewModel<Novel>(
    modes = listOf(
        RankingModeInfo(R.string.novel_ranking_day, "day"),
        RankingModeInfo(R.string.novel_ranking_week, "week"),
        RankingModeInfo(R.string.novel_ranking_male, "day_male"),
        RankingModeInfo(R.string.novel_ranking_female, "day_female"),
        RankingModeInfo(R.string.novel_ranking_rookie, "week_rookie"),
        RankingModeInfo(R.string.novel_ranking_r18, "day_r18"),
    ),
    keySelector = { it.id },
) {


    /** 内存态不持久化，与漫画榜类型切换一致。 */
    private val _languageFilter = MutableStateFlow(NovelLanguageFilter.ALL)
    val languageFilter: StateFlow<NovelLanguageFilter> = _languageFilter.asStateFlow()

    fun setLanguageFilter(filter: NovelLanguageFilter) {
        _languageFilter.value = filter
    }

    override suspend fun loadInitialFor(paged: PagedState<Novel>, mode: String, date: String?) {
        paged.loadInitial(
            fetch = { pixivRepository.api.getRankingNovels(mode, date) },
            fetchNext = { pixivRepository.api.getNextNovels(it) },
        )
    }

    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) =
        toggleFavoriteNotified(
            nowFavorite,
            CoreR.string.core_msg_bookmarked,
            CoreR.string.core_msg_unbookmarked,
            CoreR.string.core_msg_action_failed,
        ) { favoriteActions.toggleNovelFavorite(novelId, it) }
}

enum class NovelLanguageFilter { ALL, CHINESE, JAPANESE }

@androidx.annotation.StringRes
fun NovelLanguageFilter.labelRes(): Int = when (this) {
    NovelLanguageFilter.ALL -> R.string.novel_ranking_filter_all
    NovelLanguageFilter.CHINESE -> R.string.novel_ranking_filter_chinese
    NovelLanguageFilter.JAPANESE -> R.string.novel_ranking_filter_japanese
}

/**
 * 优先接口 `language` 字段（app-api 可能缺省），缺失时用「是否含假名」启发式：
 * 日文正文几乎必含假名而中文不含，无 CJK 字符的其它语种作品不落入中/日筛选。
 */
internal fun Novel.matchesLanguageFilter(filter: NovelLanguageFilter): Boolean {
    language?.lowercase()?.let { lang ->
        return when (filter) {
            NovelLanguageFilter.ALL -> true
            NovelLanguageFilter.CHINESE -> lang.startsWith("zh")
            NovelLanguageFilter.JAPANESE -> lang.startsWith("ja")
        }
    }
    val text = title.orEmpty() + caption.orEmpty()
    val hasKana = text.any { ch ->
        val code = ch.code
        code in 0x3040..0x309F || code in 0x30A0..0x30FF ||
                code in 0x31F0..0x31FF || code in 0xFF66..0xFF9D
    }
    val hasHanzi = text.any { it.code in 0x3400..0x4DBF || it.code in 0x4E00..0x9FFF }
    return when (filter) {
        NovelLanguageFilter.ALL -> true
        NovelLanguageFilter.JAPANESE -> hasKana
        NovelLanguageFilter.CHINESE -> hasHanzi && !hasKana
    }
}
