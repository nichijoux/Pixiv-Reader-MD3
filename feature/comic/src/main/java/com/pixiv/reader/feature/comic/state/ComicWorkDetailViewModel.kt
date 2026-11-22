package com.pixiv.reader.feature.comic.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.ComicEpisodeEntry
import com.pixiv.api.model.ComicWork
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

/** 作品详情状态（works/v5 单次加载）。 */
data class ComicWorkState(
    val work: ComicWork? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** 章节列表状态（含排序方向；排序切换即重拉）。 */
data class ComicEpisodesState(
    val entries: List<ComicEpisodeEntry> = emptyList(),
    val ascending: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * COMIC 作品详情 ViewModel：作品信息 + 章节列表两路独立加载，排序切换重拉章节。
 */
@HiltViewModel
class ComicWorkDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val comicRepository: ComicRepository,
) : MessageViewModel() {

    /** 作品 id（路由参数；缺省 0 时加载必然失败，走错误态）。 */
    private val workId: Long = savedStateHandle["workId"] ?: 0L

    private val _work = MutableStateFlow(ComicWorkState())
    val work: StateFlow<ComicWorkState> = _work.asStateFlow()

    private val _episodes = MutableStateFlow(ComicEpisodesState())
    val episodes: StateFlow<ComicEpisodesState> = _episodes.asStateFlow()

    init {
        loadWork()
        loadEpisodes()
    }

    /**
     * 拉取作品信息。
     *
     * @return 无返回值
     */
    fun loadWork() {
        // 请求发起时占用加载标志，避免重复拉取作品。
        if (_work.value.isLoading) return
        _work.value = _work.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val data = comicRepository.api.work(workId).data?.officialWork
                // 缺少作品时进入可重试的错误态，由界面使用本地化兜底文案。
                _work.value = _work.value.copy(work = data, error = if (data == null) "" else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _work.value = _work.value.copy(error = e.message.orEmpty())
                trySendMessage(loadFailureMessage(e, R.string.comic_load_failed_reason, R.string.comic_load_failed))
            } finally {
                // 请求退出时释放加载标志，允许后续重试。
                _work.value = _work.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 按当前排序方向拉取章节列表。
     *
     * @return 无返回值
     */
    fun loadEpisodes() {
        // 锁定请求使用的排序方向，加载结束前忽略重复操作。
        if (_episodes.value.isLoading) return
        val ascending = _episodes.value.ascending
        _episodes.value = _episodes.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val entries = comicRepository.api
                    .episodes(workId, if (ascending) "asc" else "desc")
                    .data?.episodes.orEmpty()
                _episodes.value = _episodes.value.copy(entries = entries)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _episodes.value = _episodes.value.copy(error = e.message.orEmpty())
                trySendMessage(loadFailureMessage(e, R.string.comic_load_failed_reason, R.string.comic_load_failed))
            } finally {
                // 请求退出时恢复排序与重试入口。
                _episodes.value = _episodes.value.copy(isLoading = false)
            }
        }
    }

    /** 切换章节排序方向（asc ↔ desc），切换即重拉。 */
    fun toggleOrder() {
        if (_episodes.value.isLoading) return
        _episodes.value = _episodes.value.copy(ascending = !_episodes.value.ascending)
        loadEpisodes()
    }

    /** 整页重试（作品信息与章节并行）。 */
    fun retry() {
        if (_work.value.error != null) loadWork()
        if (_episodes.value.error != null) loadEpisodes()
    }
}
