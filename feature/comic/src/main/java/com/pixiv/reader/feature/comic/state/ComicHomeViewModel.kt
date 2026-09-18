package com.pixiv.reader.feature.comic.state

import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.ComicBanner
import com.pixiv.api.model.ComicWorkSummary
import com.pixiv.reader.core.common.loadFailureMessage
import com.pixiv.reader.core.network.comic.ComicRepository
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.feature.comic.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 排行榜模式（对应两条服务端榜单）。 */
enum class ComicRankingMode {
    /** 周榜（rankings/weekly/v3）。 */
    WEEKLY,

    /** 人气榜（rankings/popularity）。 */
    POPULARITY,
}

/** COMIC 首页「更新」页数据（top/v8：banner 位 + 最近更新）。 */
data class ComicTopState(
    val banners: List<ComicBanner> = emptyList(),
    val recentWorks: List<ComicWorkSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** COMIC 排行页数据：标签列表 + 当前模式 / 标签下的榜单（整榜下发，无翻页）。 */
data class ComicRankingState(
    val labels: List<String> = emptyList(),
    val selectedMode: ComicRankingMode = ComicRankingMode.WEEKLY,
    val selectedLabel: String? = null,
    val works: List<ComicWorkSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * COMIC 首页 ViewModel：「更新」页（top/v8 单次加载）与「排行」页（标签 + 榜单，
 * 模式 / 标签切换即重拉）双页签数据。
 */
@HiltViewModel
class ComicHomeViewModel @Inject constructor(
    private val comicRepository: ComicRepository,
) : MessageViewModel() {

    private val _top = MutableStateFlow(ComicTopState(isLoading = true))
    val top: StateFlow<ComicTopState> = _top.asStateFlow()

    private val _ranking = MutableStateFlow(ComicRankingState(isLoading = true))
    val ranking: StateFlow<ComicRankingState> = _ranking.asStateFlow()

    /** 排行标签的加载标记（标签列表失败时可整页重试）。 */
    private var labelsLoaded = false

    init {
        loadTop()
        loadLabels()
    }

    /** 拉取首页 top（banner + 最近更新）。 */
    fun loadTop() {
        if (_top.value.isLoading) return
        _top.value = _top.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val data = comicRepository.api.top().data
                _top.value = ComicTopState(
                    banners = data?.banners.orEmpty(),
                    recentWorks = data?.recentUpdatedOfficialWorks.orEmpty(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _top.value = ComicTopState(error = e.message)
                notifyFailure(e)
            }
        }
    }

    /** 拉取排行分类标签（成功后自动加载默认榜单：周榜 × 総合）。 */
    fun loadLabels() {
        if (_ranking.value.isLoading) return
        _ranking.value = _ranking.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                // 两个榜单目前共用一组标签，以周榜为准；服务端未来分化时切模式会重查
                val labels = comicRepository.api.weeklyRankingLabels().data?.labels.orEmpty()
                labelsLoaded = true
                _ranking.value = _ranking.value.copy(
                    labels = labels,
                    selectedLabel = labels.firstOrNull(),
                    isLoading = false,
                )
                labels.firstOrNull()?.let { loadRanking() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ranking.value = _ranking.value.copy(isLoading = false, error = e.message)
                notifyFailure(e)
            }
        }
    }

    /**
     * 切换排行模式（周榜 / 人气），切换即重拉当前标签的榜单。
     *
     * @param mode 目标模式
     */
    fun selectRankingMode(mode: ComicRankingMode) {
        if (_ranking.value.selectedMode == mode) return
        _ranking.value = _ranking.value.copy(selectedMode = mode)
        loadRanking()
    }

    /**
     * 切换排行分类标签，切换即重拉榜单。
     *
     * @param label 目标标签（日文原文）
     */
    fun selectLabel(label: String) {
        if (_ranking.value.selectedLabel == label) return
        _ranking.value = _ranking.value.copy(selectedLabel = label)
        loadRanking()
    }

    /** 整页重试（top 与排行并行）。 */
    fun retry() {
        loadTop()
        if (_ranking.value.error != null || _ranking.value.labels.isEmpty()) {
            _ranking.value = ComicRankingState()
            labelsLoaded = false
            loadLabels()
        }
    }

    /** 按当前模式 + 标签拉取榜单（整榜下发；标签未就绪或已在加载时忽略）。 */
    fun loadRanking() {
        val snapshot = _ranking.value
        val label = snapshot.selectedLabel ?: return
        if (snapshot.isLoading) return
        _ranking.value = snapshot.copy(works = emptyList(), isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val items = when (snapshot.selectedMode) {
                    ComicRankingMode.WEEKLY ->
                        comicRepository.api.weeklyRanking(label).data?.ranking.orEmpty()
                    ComicRankingMode.POPULARITY ->
                        comicRepository.api.popularityRanking(label).data?.ranking.orEmpty()
                }
                _ranking.value = _ranking.value.copy(works = items, isLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ranking.value = _ranking.value.copy(isLoading = false, error = e.message)
                notifyFailure(e)
            }
        }
    }

    /** 统一失败通知（reason + fallback 文案映射）。 */
    private fun notifyFailure(e: Exception) {
        trySendMessage(loadFailureMessage(e, R.string.comic_load_failed_reason, R.string.comic_load_failed))
    }
}
