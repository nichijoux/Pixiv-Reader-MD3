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

enum class ComicRankingMode {
    WEEKLY,

    POPULARITY,
}

data class ComicTopState(
    val banners: List<ComicBanner> = emptyList(),
    val recentWorks: List<ComicWorkSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** 标签 + 当前模式/标签下的榜单（服务端整榜下发，无翻页游标）。 */
data class ComicRankingState(
    val labels: List<String> = emptyList(),
    val selectedMode: ComicRankingMode = ComicRankingMode.WEEKLY,
    val selectedLabel: String? = null,
    val works: List<ComicWorkSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

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

    fun loadTop() {
        // 占用加载标志：连点重试只保留一个请求。
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
                _top.value = _top.value.copy(isLoading = false)
            }
        }
    }

    private fun loadLabels() {
        // 标签请求与榜单请求共用排行加载标志。
        if (_ranking.value.isLoading) return
        _ranking.value = _ranking.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                // 两个榜单共用同一组标签，只拉周榜的。
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
                _ranking.value = _ranking.value.copy(isLoading = false)
            }
            loadRanking()
        }
    }

    fun selectRankingMode(mode: ComicRankingMode) {
        if (_ranking.value.selectedMode == mode) return
        // 在途时不并发拉榜，由 loadRanking 收尾补拉。
        _ranking.value = _ranking.value.copy(selectedMode = mode)
        loadRanking()
    }

    fun selectLabel(label: String) {
        if (_ranking.value.selectedLabel == label) return
        // 在途时不并发拉榜，由 loadRanking 收尾补拉。
        _ranking.value = _ranking.value.copy(selectedLabel = label)
        loadRanking()
    }

    fun retry() {
        if (_ranking.value.labels.isEmpty()) loadLabels() else loadRanking()
    }

    private fun loadRanking() {
        val snapshot = _ranking.value
        val label = snapshot.selectedLabel ?: return
        // 用快照占用加载标志，确保任一时刻只有一条榜单请求。
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
                _ranking.value = _ranking.value.copy(isLoading = false)
            }
            if (!isRankingSelectionCurrent(snapshot)) loadRanking()
        }
    }

    private fun isRankingSelectionCurrent(snapshot: ComicRankingState): Boolean {
        val current = _ranking.value
        return current.selectedMode == snapshot.selectedMode && current.selectedLabel == snapshot.selectedLabel
    }

    private fun notifyFailure(e: Exception) {
        trySendMessage(loadFailureMessage(e, R.string.comic_load_failed_reason, R.string.comic_load_failed))
    }
}
