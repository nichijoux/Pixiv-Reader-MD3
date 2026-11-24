package com.pixiv.reader.feature.comic.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.ComicEpisodeEntry
import com.pixiv.api.model.ComicWork
import com.pixiv.reader.core.common.LoadState
import com.pixiv.reader.core.common.loadFailureMessage
import com.pixiv.reader.core.network.comic.ComicRepository
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.feature.comic.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

typealias ComicWorkState = LoadState<ComicWork>

data class ComicEpisodesState(
    val ascending: Boolean = true,
    val loadState: LoadState<List<ComicEpisodeEntry>> = LoadState.Loading,
)

@HiltViewModel
class ComicWorkDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val comicRepository: ComicRepository,
) : MessageViewModel() {

    private val workId: Long = savedStateHandle["workId"] ?: 0L

    private val _work = MutableStateFlow<ComicWorkState>(LoadState.Loading)
    val work: StateFlow<ComicWorkState> = _work.asStateFlow()

    private val _episodes = MutableStateFlow(ComicEpisodesState())
    val episodes: StateFlow<ComicEpisodesState> = _episodes.asStateFlow()

    private var workJob: Job? = null
    private var episodesJob: Job? = null

    init {
        loadWork()
        loadEpisodes()
    }

    fun loadWork() {
        if (workJob?.isActive == true) return
        _work.value = LoadState.Loading
        workJob = viewModelScope.launch {
            try {
                val data = comicRepository.api.work(workId).data?.officialWork
                // 服务端缺作品时进可重试错误态，文案由界面本地化兜底。
                _work.value = if (data != null) LoadState.Content(data) else LoadState.Error(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _work.value = LoadState.Error(e.message)
                trySendMessage(loadFailureMessage(e, R.string.comic_load_failed_reason, R.string.comic_load_failed))
            }
        }
    }

    fun loadEpisodes() {
        // 锁定请求使用的排序方向，加载结束前忽略重复操作。
        if (episodesJob?.isActive == true) return
        val ascending = _episodes.value.ascending
        _episodes.value = _episodes.value.copy(loadState = LoadState.Loading)
        episodesJob = viewModelScope.launch {
            try {
                val entries = comicRepository.api
                    .episodes(workId, if (ascending) "asc" else "desc")
                    .data?.episodes.orEmpty()
                _episodes.value = _episodes.value.copy(loadState = LoadState.Content(entries))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _episodes.value = _episodes.value.copy(loadState = LoadState.Error(e.message))
                trySendMessage(loadFailureMessage(e, R.string.comic_load_failed_reason, R.string.comic_load_failed))
            }
        }
    }

    fun toggleOrder() {
        if (episodesJob?.isActive == true) return
        _episodes.value = _episodes.value.copy(ascending = !_episodes.value.ascending)
        loadEpisodes()
    }

    fun retry() {
        if (_work.value is LoadState.Error) loadWork()
        if (_episodes.value.loadState is LoadState.Error) loadEpisodes()
    }
}
