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

data class ComicWorkState(
    val work: ComicWork? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class ComicEpisodesState(
    val entries: List<ComicEpisodeEntry> = emptyList(),
    val ascending: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ComicWorkDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val comicRepository: ComicRepository,
) : MessageViewModel() {

    private val workId: Long = savedStateHandle["workId"] ?: 0L

    private val _work = MutableStateFlow(ComicWorkState())
    val work: StateFlow<ComicWorkState> = _work.asStateFlow()

    private val _episodes = MutableStateFlow(ComicEpisodesState())
    val episodes: StateFlow<ComicEpisodesState> = _episodes.asStateFlow()

    init {
        loadWork()
        loadEpisodes()
    }

    fun loadWork() {
        // 占用加载标志，避免重复拉取。
        if (_work.value.isLoading) return
        _work.value = _work.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val data = comicRepository.api.work(workId).data?.officialWork
                // 服务端缺作品时置空错误串进可重试错误态，文案由界面本地化兜底。
                _work.value = _work.value.copy(work = data, error = if (data == null) "" else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _work.value = _work.value.copy(error = e.message.orEmpty())
                trySendMessage(loadFailureMessage(e, R.string.comic_load_failed_reason, R.string.comic_load_failed))
            } finally {
                _work.value = _work.value.copy(isLoading = false)
            }
        }
    }

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
                _episodes.value = _episodes.value.copy(isLoading = false)
            }
        }
    }

    fun toggleOrder() {
        if (_episodes.value.isLoading) return
        _episodes.value = _episodes.value.copy(ascending = !_episodes.value.ascending)
        loadEpisodes()
    }

    fun retry() {
        if (_work.value.error != null) loadWork()
        if (_episodes.value.error != null) loadEpisodes()
    }
}
