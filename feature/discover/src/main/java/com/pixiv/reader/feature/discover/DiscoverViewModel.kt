package com.pixiv.reader.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.AutocompleteTag
import com.example.pixivapi.model.Illust
import com.example.pixivapi.model.Novel
import com.example.pixivapi.model.SearchGenreOption
import com.example.pixivapi.model.TrendingTag
import com.example.pixivapi.model.UserPreview
import com.pixiv.reader.core.database.dao.SearchHistoryDao
import com.pixiv.reader.core.database.entity.SearchHistoryEntity
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchType(val label: String) {
    ILLUST("插画"),
    NOVEL("小说"),
    USER("用户"),
}

/** 搜索模式：最新（正常搜索，sort=date_desc）/ 热门（仅显示热门作品，一次性）。 */
enum class SearchMode { LATEST, HOT }

/** 全量搜索筛选（sort 采用 pixiv 标准值 popular_desc）。 */
data class SearchFilters(
    val mode: SearchMode = SearchMode.LATEST,
    val sort: String = "date_desc",               // date_desc 最新 / date_asc 最旧 / popular_desc 收藏多
    val searchTarget: String = "partial_match_for_tags",
    val startDate: String? = null,
    val endDate: String? = null,
    val bookmarkNumMin: Int? = null,
    val aiType: Int = 0,                          // 0 全部 1 仅人绘 2 仅 AI
    // 插画专属
    val tool: String? = null,
    val ratioPattern: String? = null,             // square / wide / tall
    val contentType: String? = null,              // illust / manga / ugoira
    val widthMin: Int? = null,
    val widthMax: Int? = null,
    val heightMin: Int? = null,
    val heightMax: Int? = null,
    // 小说专属
    val genre: Int? = null,
    val isOriginalOnly: Boolean? = null,
    val isReplaceableOnly: Boolean? = null,
    val textLengthMin: Int? = null,
    val textLengthMax: Int? = null,
    val wordCountMin: Int? = null,
    val wordCountMax: Int? = null,
    val readingTimeMin: Int? = null,
    val readingTimeMax: Int? = null,
)

/**
 * 发现页（搜索）ViewModel。
 *
 * ## 职责
 * - 搜索联想（300ms 防抖 + 去重，`searchAutocomplete`）
 * - 按类型（插画/小说/用户）+ 全量筛选（[SearchFilters]）+ 模式（最新/热门）执行搜索
 * - 热门搜索缓存（DataStore，24h TTL）、搜索历史（Room）、热门预览（popular-preview）
 * - 搜索结果分页：插画/小说/用户各自 `PagedState`（next_url 游标）
 *
 * ## 状态
 * `query`/`type`/`filters` 为可变 StateFlow（UI 直接改），其余只读 StateFlow。
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    companion object {
        /** 热门搜索缓存有效期：24 小时。 */
        private const val HOT_TAGS_TTL_MS = 24L * 60 * 60 * 1000
    }

    val query = MutableStateFlow("")
    val type = MutableStateFlow(SearchType.ILLUST)
    val filters = MutableStateFlow(SearchFilters())

    private val _hotTags = MutableStateFlow<List<TrendingTag>>(emptyList())
    val hotTags: StateFlow<List<TrendingTag>> = _hotTags.asStateFlow()

    private val _suggestions = MutableStateFlow<List<AutocompleteTag>>(emptyList())
    val suggestions: StateFlow<List<AutocompleteTag>> = _suggestions.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    /** 搜索历史（最近 20 条，倒序）。 */
    val searchHistory: StateFlow<List<SearchHistoryEntity>> =
        searchHistoryDao.observeRecent(20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 搜索选项：绘制工具 / 题材（来自 /v1/search/options）。 */
    private val _toolOptions = MutableStateFlow<List<String>>(emptyList())
    val toolOptions: StateFlow<List<String>> = _toolOptions.asStateFlow()

    private val _genreOptions = MutableStateFlow<List<SearchGenreOption>>(emptyList())
    val genreOptions: StateFlow<List<SearchGenreOption>> = _genreOptions.asStateFlow()

    /** 热门预览（popular-preview）。 */
    private val _popularIllusts = MutableStateFlow<List<Illust>>(emptyList())
    val popularIllusts: StateFlow<List<Illust>> = _popularIllusts.asStateFlow()

    private val _popularNovels = MutableStateFlow<List<Novel>>(emptyList())
    val popularNovels: StateFlow<List<Novel>> = _popularNovels.asStateFlow()

    val illustPaged = PagedState<Illust>()
    val novelPaged = PagedState<Novel>()
    val userPaged = PagedState<UserPreview>()

    init {
        loadHotTags()
        loadOptions()
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

    /** 更新搜索关键词（触发联想防抖）。 */
    fun onQueryChange(value: String) {
        query.value = value
    }

    /** 清除搜索：清空关键词并回到初始态（热门 + 搜索历史）。 */
    fun clearSearch() {
        query.value = ""
        _hasSearched.value = false
        _suggestions.value = emptyList()
        _popularIllusts.value = emptyList()
        _popularNovels.value = emptyList()
    }

    /** 切换搜索类型；已搜索过则按当前词重新搜索。 */
    fun setType(value: SearchType) {
        if (type.value == value) return
        type.value = value
        if (_hasSearched.value) {
            // 类型切换后按当前词重新搜索
            search()
        }
    }

    /** 应用筛选条件（筛选面板"应用"后调用）。 */
    fun applyFilters(value: SearchFilters) {
        filters.value = value
    }

    /** 拉取搜索选项（工具 / 题材下拉数据，来自 /v1/search/options）。 */
    private fun loadOptions() {
        viewModelScope.launch {
            runCatching { pixivRepository.api.searchOptions(word = "") }
                .onSuccess { resp ->
                    _toolOptions.value = resp.illust?.tool?.options.orEmpty()
                    _genreOptions.value = resp.novel?.genre?.options.orEmpty()
                }
        }
    }

    /** 搜索：按类型与模式（最新/热门）加载结果 + 写入搜索历史。 */
    fun search() {
        val word = query.value.trim()
        if (word.isBlank()) return
        _hasSearched.value = true
        val f = filters.value
        recordHistory(word)
        viewModelScope.launch {
            when (type.value) {
                SearchType.ILLUST -> if (f.mode == SearchMode.HOT) {
                    loadPopular(word, f)
                } else {
                    illustPaged.loadInitial(
                        fetch = {
                            pixivRepository.api.searchIllusts(
                                word = word,
                                sort = f.sort,
                                searchTarget = f.searchTarget,
                                startDate = f.startDate,
                                endDate = f.endDate,
                                bookmarkNumMin = f.bookmarkNumMin,
                                tool = f.tool,
                                searchAiType = f.aiType,
                                ratioPattern = f.ratioPattern,
                                contentType = f.contentType,
                                widthMin = f.widthMin,
                                widthMax = f.widthMax,
                                heightMin = f.heightMin,
                                heightMax = f.heightMax,
                            )
                        },
                        fetchNext = { pixivRepository.api.getNextIllusts(it) },
                    )
                }
                SearchType.NOVEL -> if (f.mode == SearchMode.HOT) {
                    loadPopular(word, f)
                } else {
                    novelPaged.loadInitial(
                        fetch = {
                            pixivRepository.api.searchNovels(
                                word = word,
                                sort = f.sort,
                                searchTarget = f.searchTarget,
                                startDate = f.startDate,
                                endDate = f.endDate,
                                bookmarkNumMin = f.bookmarkNumMin,
                                genre = f.genre,
                                searchAiType = f.aiType,
                                isOriginalOnly = f.isOriginalOnly,
                                isReplaceableOnly = f.isReplaceableOnly,
                                textLengthMin = f.textLengthMin,
                                textLengthMax = f.textLengthMax,
                                wordCountMin = f.wordCountMin,
                                wordCountMax = f.wordCountMax,
                                readingTimeMin = f.readingTimeMin,
                                readingTimeMax = f.readingTimeMax,
                            )
                        },
                        fetchNext = { pixivRepository.api.getNextNovels(it) },
                    )
                }
                SearchType.USER -> userPaged.loadInitial(
                    fetch = { pixivRepository.api.searchUsers(word) },
                    fetchNext = { pixivRepository.api.getNextUsers(it) },
                )
            }
        }
    }

    /** 热门预览（插画/小说顶部横滑区）。 */
    private suspend fun loadPopular(word: String, f: SearchFilters) {
        when (type.value) {
            SearchType.ILLUST -> runCatching {
                pixivRepository.api.popularPreview(
                    word = word,
                    sort = f.sort,
                    searchTarget = f.searchTarget,
                    startDate = f.startDate,
                    endDate = f.endDate,
                    bookmarkNumMin = f.bookmarkNumMin,
                    tool = f.tool,
                    searchAiType = f.aiType,
                    ratioPattern = f.ratioPattern,
                )
            }.onSuccess { _popularIllusts.value = it.illusts.take(10) }
                .onFailure { _popularIllusts.value = emptyList() }
            SearchType.NOVEL -> runCatching {
                pixivRepository.api.popularNovelPreview(
                    word = word,
                    sort = f.sort,
                    searchTarget = f.searchTarget,
                    startDate = f.startDate,
                    endDate = f.endDate,
                )
            }.onSuccess { _popularNovels.value = it.novels.take(10) }
                .onFailure { _popularNovels.value = emptyList() }
            SearchType.USER -> Unit
        }
    }

    /** 加载更多（当前类型的下一页，触底时由 UI 调用）。 */
    fun loadMore() {
        viewModelScope.launch {
            when (type.value) {
                SearchType.ILLUST -> illustPaged.loadMore()
                SearchType.NOVEL -> novelPaged.loadMore()
                SearchType.USER -> userPaged.loadMore()
            }
        }
    }

    /** 重试当前搜索。 */
    fun retry() = search()

    // ── 搜索历史 ──

    /** 记录搜索历史（先删同词旧记录再插入，去重置顶）。 */
    private fun recordHistory(keyword: String) {
        viewModelScope.launch {
            runCatching {
                searchHistoryDao.deleteByKeyword(keyword)
                searchHistoryDao.upsert(SearchHistoryEntity(keyword = keyword))
            }
        }
    }

    /** 删除单条搜索历史（历史胶囊长按删除）。 */
    fun removeHistory(entity: SearchHistoryEntity) {
        viewModelScope.launch { runCatching { searchHistoryDao.delete(entity) } }
    }

    /** 关注 / 取关用户（nowFollowed 为目标状态，由组件回调）。 */
    fun toggleFollowUser(userId: Long, nowFollowed: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (nowFollowed) pixivRepository.api.followUser(userId, "public")
                else pixivRepository.api.unfollowUser(userId)
            }
        }
    }

    /** 收藏 / 取消收藏小说（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (nowFavorite) pixivRepository.api.bookmarkNovel(novelId, "public", emptyList())
                else pixivRepository.api.unbookmarkNovel(novelId)
            }
        }
    }

    /** 收藏 / 取消收藏插画（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (nowFavorite) pixivRepository.api.bookmarkIllust(illustId, "public", emptyList())
                else pixivRepository.api.unbookmarkIllust(illustId)
            }
        }
    }

    /** 清空搜索历史。 */
    fun clearHistory() {
        viewModelScope.launch { runCatching { searchHistoryDao.clearAll() } }
    }

    /** 加载热门搜索（DataStore 缓存 24h TTL 优先，过期才网络刷新并回写缓存）。 */
    private fun loadHotTags() {
        viewModelScope.launch {
            // 缓存优先：24 小时内直接用本地缓存，避免每次打开都网络请求
            val cached = runCatching { userPreferences.hotTags.first() }.getOrDefault(emptyList())
            val cachedAt = runCatching { userPreferences.hotTagsUpdatedAt.first() }.getOrDefault(0L)
            if (cached.isNotEmpty() && System.currentTimeMillis() - cachedAt < HOT_TAGS_TTL_MS) {
                _hotTags.value = cached.map { TrendingTag(tag = it) }
                return@launch
            }
            runCatching { pixivRepository.api.getTrendingTags("illust") }
                .onSuccess { resp ->
                    val names = resp.trend_tags.take(10)
                        .map { it.translated_name ?: it.tag ?: "" }
                        .filter { it.isNotBlank() }
                    _hotTags.value = names.map { TrendingTag(tag = it) }
                    if (names.isNotEmpty()) {
                        viewModelScope.launch {
                            runCatching {
                                userPreferences.setHotTags(names)
                                userPreferences.setHotTagsUpdatedAt(System.currentTimeMillis())
                            }
                        }
                    }
                }
        }
    }
}
