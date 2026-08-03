package com.pixiv.reader.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.AutocompleteTag
import com.example.pixivapi.model.Illust
import com.example.pixivapi.model.Novel
import com.example.pixivapi.model.TrendingTag
import com.example.pixivapi.model.UserPreview
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class SearchType(val label: String) {
    ILLUST("插画"),
    NOVEL("小说"),
    USER("用户"),
}

data class SearchFilters(
    val sort: String = "date_desc",               // date_desc 最新 / bookmark 收藏多
    val searchTarget: String = "partial_match_for_tags",
    val startDate: String? = null,
    val endDate: String? = null,
    val bookmarkNumMin: Int? = null,
    val aiType: Int = 0,                          // 0 全部 1 仅人绘 2 仅 AI
)

@OptIn(FlowPreview::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    val query = MutableStateFlow("")
    val type = MutableStateFlow(SearchType.ILLUST)
    val filters = MutableStateFlow(SearchFilters())

    private val _hotTags = MutableStateFlow<List<TrendingTag>>(emptyList())
    val hotTags: StateFlow<List<TrendingTag>> = _hotTags.asStateFlow()

    private val _suggestions = MutableStateFlow<List<AutocompleteTag>>(emptyList())
    val suggestions: StateFlow<List<AutocompleteTag>> = _suggestions.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    val illustPaged = PagedState<Illust>()
    val novelPaged = PagedState<Novel>()
    val userPaged = PagedState<UserPreview>()

    init {
        loadHotTags()
        // 搜索联想（防抖）
        viewModelScope.launch {
            query.debounce(300).distinctUntilChanged().collect { q ->
                if (q.isBlank()) {
                    _suggestions.value = emptyList()
                    return@collect
                }
                runCatching { pixivRepository.api.searchAutocomplete(q) }
                    .onSuccess { _suggestions.value = it.tags.take(8) }
                    .onFailure { _suggestions.value = emptyList() }
            }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setType(value: SearchType) {
        type.value = value
    }

    fun applyFilters(value: SearchFilters) {
        filters.value = value
    }

    fun search() {
        val word = query.value.trim()
        if (word.isBlank()) return
        _hasSearched.value = true
        val f = filters.value
        viewModelScope.launch {
            when (type.value) {
                SearchType.ILLUST -> illustPaged.loadInitial(
                    fetch = {
                        pixivRepository.api.searchIllusts(
                            word = word,
                            sort = f.sort,
                            searchTarget = f.searchTarget,
                            startDate = f.startDate,
                            endDate = f.endDate,
                            bookmarkNumMin = f.bookmarkNumMin,
                            searchAiType = f.aiType,
                        )
                    },
                    fetchNext = { pixivRepository.api.getNextIllusts(it) },
                )
                SearchType.NOVEL -> novelPaged.loadInitial(
                    fetch = {
                        pixivRepository.api.searchNovels(
                            word = word,
                            sort = f.sort,
                            searchTarget = f.searchTarget,
                            startDate = f.startDate,
                            endDate = f.endDate,
                            bookmarkNumMin = f.bookmarkNumMin,
                            searchAiType = f.aiType,
                        )
                    },
                    fetchNext = { pixivRepository.api.getNextNovels(it) },
                )
                SearchType.USER -> userPaged.loadInitial(
                    fetch = { pixivRepository.api.searchUsers(word) },
                    fetchNext = { pixivRepository.api.getNextUsers(it) },
                )
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            when (type.value) {
                SearchType.ILLUST -> illustPaged.loadMore()
                SearchType.NOVEL -> novelPaged.loadMore()
                SearchType.USER -> userPaged.loadMore()
            }
        }
    }

    fun retry() = search()

    private fun loadHotTags() {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getTrendingTags("illust") }
                .onSuccess { _hotTags.value = it.trend_tags.take(10) }
        }
    }
}
