package com.pixiv.reader.feature.comic.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.ComicPage
import com.pixiv.api.model.ComicReadEpisode
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.loadFailureMessage
import com.pixiv.reader.core.network.comic.ComicPageLoader
import com.pixiv.reader.core.network.comic.ComicRepository
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.feature.comic.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 阅读器单页状态：占位（数据已知宽高）→ 就绪（还原页文件）→ 失败（可单页重试）。 */
sealed interface ComicPageUi {

    /** 加载中（下载 + 去扰中），按元数据宽高占位避免滚动跳动。 */
    data object Loading : ComicPageUi

    /** 就绪：还原后的本地缓存文件，可直接交给 Coil 展示。 */
    data class Ready(val file: File, val width: Int, val height: Int) : ComicPageUi

    /** 本页失败（网络 / 解码），保留重试入口。 */
    data object Failed : ComicPageUi
}

/** 阅读器整页状态。 */
data class ComicReaderUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val episode: ComicReadEpisode? = null,
    val pages: List<ComicPageUi> = emptyList(),
)

/**
 * COMIC 阅读器 ViewModel：fetchReadEpisode 两步签名流取页数据后，逐页调
 * [ComicPageLoader.loadPage]（内部信号量限并发）下载去扰落盘；失败支持单页重试。
 */
@HiltViewModel
class ComicReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val comicRepository: ComicRepository,
    private val pageLoader: ComicPageLoader,
) : MessageViewModel() {

    /** 章节 id（路由参数；缺省 0 时加载必然失败，走错误态）。 */
    private val episodeId: Long = savedStateHandle["episodeId"] ?: 0L

    private val _state = MutableStateFlow(ComicReaderUiState())
    val state: StateFlow<ComicReaderUiState> = _state.asStateFlow()

    /** 加载代次：重进下一话 / 重试时作废旧代次的页结果，防止旧页覆盖新章节。 */
    private var loadGeneration = 0

    init {
        load()
    }

    /** 首次 / 整页重试：重取阅读数据并重新排产全部页加载（已还原页命中缓存零开销）。 */
    fun load() {
        val gen = ++loadGeneration
        _state.value = ComicReaderUiState(isLoading = true)
        viewModelScope.launch {
            try {
                // 两步流：viewer 页 salt → read_v4；失败走统一错误态
                val episode = comicRepository.fetchReadEpisode(episodeId)
                if (gen != loadGeneration) return@launch
                _state.value = ComicReaderUiState(
                    isLoading = false,
                    episode = episode,
                    pages = episode.pages.orEmpty().map { ComicPageUi.Loading },
                )
                // 页数据齐了才排产；逐页协程 + loader 内信号量限流
                episode.pages.orEmpty().forEachIndexed { index, page -> launchPageLoad(gen, index, page) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen != loadGeneration) return@launch
                _state.value = ComicReaderUiState(isLoading = false, error = e.message)
                trySendMessage(loadFailureMessage(e, R.string.comic_load_failed_reason, R.string.comic_load_failed))
            }
        }
    }

    /**
     * 单页重试（该页此前 Failed 时有效）。
     *
     * @param index 页序号（0 起）
     */
    fun retryPage(index: Int) {
        val page = _state.value.episode?.pages?.getOrNull(index) ?: return
        if (_state.value.pages.getOrNull(index) is ComicPageUi.Ready) return
        updatePage(index, ComicPageUi.Loading)
        launchPageLoad(loadGeneration, index, page)
    }

    /**
     * 排产单页加载（下载 → 去扰 → 落盘），结果按代次防过期写入页状态。
     *
     * @param gen 加载代次
     * @param index 页序号
     * @param page 页元数据（url / key / gridsize / 宽高）
     */
    private fun launchPageLoad(gen: Int, index: Int, page: ComicPage) {
        viewModelScope.launch {
            try {
                val file = pageLoader.loadPage(episodeId, index, page)
                if (gen != loadGeneration) return@launch
                updatePage(index, ComicPageUi.Ready(file, page.width, page.height))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen != loadGeneration) return@launch
                updatePage(index, ComicPageUi.Failed)
            }
        }
    }

    /** 不可变写入：拷贝页列表后按序号替换。 */
    private fun updatePage(index: Int, ui: ComicPageUi) {
        val pages = _state.value.pages.toMutableList()
        if (index >= pages.size) return
        pages[index] = ui
        _state.value = _state.value.copy(pages = pages)
    }
}
