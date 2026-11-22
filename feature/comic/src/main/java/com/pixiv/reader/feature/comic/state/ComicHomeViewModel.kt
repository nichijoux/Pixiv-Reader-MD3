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

    private val _top = MutableStateFlow(ComicTopState())
    val top: StateFlow<ComicTopState> = _top.asStateFlow()

    private val _ranking = MutableStateFlow(ComicRankingState())
    val ranking: StateFlow<ComicRankingState> = _ranking.asStateFlow()

    init {
        loadTop()
        loadLabels()
    }

    /**
     * 拉取首页更新数据（banner + 最近更新）。
     *
     * @return 无返回值
     */
    fun loadTop() {
        // 请求发起时占用加载标志，连续点击重试只保留一个请求。
        if (_top.value.isLoading) return
        _top.value = _top.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val data = comicRepository.api.top().data
                _top.value = _top.value.copy(
                    banners = data?.banners.orEmpty(),
                    recentWorks = data?.recentUpdatedOfficialWorks.orEmpty(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _top.value = _top.value.copy(error = e.message.orEmpty())
                notifyFailure(e)
            } finally {
                // 成功、失败或取消均释放首页的加载标志。
                _top.value = _top.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 拉取排行分类标签，并加载首个标签对应的榜单。
     *
     * @return 无返回值
     */
    private fun loadLabels() {
        // 标签请求与榜单请求共用排行加载标志。
        if (_ranking.value.isLoading) return
        _ranking.value = _ranking.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                // 两个榜单共用周榜的标签列表。
                val labels = comicRepository.api.weeklyRankingLabels().data?.labels.orEmpty()
                _ranking.value = _ranking.value.copy(
                    labels = labels,
                    selectedLabel = labels.firstOrNull(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ranking.value = _ranking.value.copy(error = e.message.orEmpty())
                notifyFailure(e)
                return@launch
            } finally {
                // 释放排行加载标志后，榜单请求才可接续。
                _ranking.value = _ranking.value.copy(isLoading = false)
            }
            loadRanking()
        }
    }

    /**
     * 切换排行模式（周榜 / 人气），切换即重拉当前标签的榜单。
     *
     * @param mode 目标模式
     * @return 无返回值
     */
    fun selectRankingMode(mode: ComicRankingMode) {
        if (_ranking.value.selectedMode == mode) return
        // 在途请求结束后读取最新选择，避免并发拉榜。
        _ranking.value = _ranking.value.copy(selectedMode = mode)
        loadRanking()
    }

    /**
     * 切换排行分类标签，切换即重拉榜单。
     *
     * @param label 目标标签（日文原文）
     * @return 无返回值
     */
    fun selectLabel(label: String) {
        if (_ranking.value.selectedLabel == label) return
        // 在途请求结束后读取最新选择，避免并发拉榜。
        _ranking.value = _ranking.value.copy(selectedLabel = label)
        loadRanking()
    }

    /**
     * 重试排行页失败的请求，保留用户当前选择。
     *
     * @return 无返回值
     */
    fun retry() {
        // 已取得标签时可直接重试当前榜单。
        if (_ranking.value.labels.isEmpty()) loadLabels() else loadRanking()
    }

    /**
     * 按当前模式和标签拉榜。在途时只记录后续选择，完成后再加载其对应榜单。
     *
     * @return 无返回值
     */
    private fun loadRanking() {
        val snapshot = _ranking.value
        val label = snapshot.selectedLabel ?: return
        // 读取选中项并占用加载标志，确保任一时刻只有一条榜单请求。
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
                // 等待响应期间切换了选项时，旧榜单不可写入当前选择。
                if (isRankingSelectionCurrent(snapshot)) {
                    _ranking.value = _ranking.value.copy(works = items)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isRankingSelectionCurrent(snapshot)) {
                    _ranking.value = _ranking.value.copy(error = e.message.orEmpty())
                    notifyFailure(e)
                }
            } finally {
                // 释放加载标志，让最新选项或重试可以发起下一次请求。
                _ranking.value = _ranking.value.copy(isLoading = false)
            }
            if (!isRankingSelectionCurrent(snapshot)) loadRanking()
        }
    }

    /**
     * 判断榜单请求对应的模式和标签是否仍处于选中状态。
     *
     * @param snapshot 请求发起时的排行状态
     * @return 两个选项仍相同时为 true
     */
    private fun isRankingSelectionCurrent(snapshot: ComicRankingState): Boolean {
        val current = _ranking.value
        return current.selectedMode == snapshot.selectedMode && current.selectedLabel == snapshot.selectedLabel
    }

    /** 统一失败通知（reason + fallback 文案映射）。 */
    private fun notifyFailure(e: Exception) {
        trySendMessage(loadFailureMessage(e, R.string.comic_load_failed_reason, R.string.comic_load_failed))
    }
}
