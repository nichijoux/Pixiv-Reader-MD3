package com.pixiv.reader.feature.discover.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.AutocompleteTag
import com.pixiv.api.model.Illust
import com.pixiv.api.model.Novel
import com.pixiv.api.model.SearchGenreOption
import com.pixiv.api.model.SearchLangOption
import com.pixiv.api.model.TrendingTag
import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.database.dao.SearchHistoryDao
import com.pixiv.reader.core.database.entity.SearchHistoryEntity
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val userPreferences: UserPreferences,
    private val favoriteActions: FavoriteActions,
) : ViewModel() {

    companion object {
        private const val HOT_TAGS_TTL_MS = 24L * 60 * 60 * 1000

        // 插画接口不接受 text/keyword，小说接口不接受 title_and_caption，跨类型传参会 400
        private val ILLUST_TARGETS = setOf(
            "partial_match_for_tags", "exact_match_for_tags", "title_and_caption",
        )

        private val NOVEL_TARGETS = setOf(
            "partial_match_for_tags", "exact_match_for_tags", "text", "keyword",
        )
    }

    val query = MutableStateFlow("")
    val type = MutableStateFlow(SearchType.ILLUST)
    val filters = MutableStateFlow(SearchFilters())

    // search() 在此置 true 前挂起，防止异步恢复值覆盖非会员降级写回的 filters.sort，导致深链首屏空白
    private val filtersRestored = MutableStateFlow(false)

    private val _hotTags = MutableStateFlow<List<TrendingTag>>(emptyList())
    val hotTags: StateFlow<List<TrendingTag>> = _hotTags.asStateFlow()

    private val _suggestions = MutableStateFlow<List<AutocompleteTag>>(emptyList())
    val suggestions: StateFlow<List<AutocompleteTag>> = _suggestions.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    val searchHistory: StateFlow<List<SearchHistoryEntity>> =
        searchHistoryDao.observeRecent(20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _toolOptions = MutableStateFlow<List<String>>(emptyList())
    val toolOptions: StateFlow<List<String>> = _toolOptions.asStateFlow()

    private val _genreOptions = MutableStateFlow<List<SearchGenreOption>>(emptyList())
    val genreOptions: StateFlow<List<SearchGenreOption>> = _genreOptions.asStateFlow()

    private val _langOptions = MutableStateFlow<List<SearchLangOption>>(emptyList())
    val langOptions: StateFlow<List<SearchLangOption>> = _langOptions.asStateFlow()

    val isPremium: StateFlow<Boolean> = MutableStateFlow(pixivRepository.pixivApi.session.isPremium)

    private val _popularIllusts = MutableStateFlow<List<Illust>>(emptyList())
    val popularIllusts: StateFlow<List<Illust>> = _popularIllusts.asStateFlow()

    private val _popularNovels = MutableStateFlow<List<Novel>>(emptyList())
    val popularNovels: StateFlow<List<Novel>> = _popularNovels.asStateFlow()

    private val _popularIllustsLoading = MutableStateFlow(false)
    val popularIllustsLoading: StateFlow<Boolean> = _popularIllustsLoading.asStateFlow()

    private val _popularNovelsLoading = MutableStateFlow(false)
    val popularNovelsLoading: StateFlow<Boolean> = _popularNovelsLoading.asStateFlow()

    // 跨页重复 ID 会导致列表 key 冲突；用户预览缺失 user 时返回 null 避免误去重
    val illustPaged = PagedState<Illust>(keySelector = { it.id })
    val novelPaged = PagedState<Novel>(keySelector = { it.id })
    val userPaged = PagedState<UserPreview>(keySelector = { it.user?.id })
    private var searchJob: Job? = null

    init {
        loadHotTags()
        loadOptions()
        loadSavedFilters()
        viewModelScope.launch {
            query.collectLatest { q ->
                _suggestions.value = emptyList()
                if (q.isBlank()) return@collectLatest

                delay(300)
                val result = runCatching { pixivRepository.api.searchAutocomplete(q) }
                // runCatching 会吞掉 CancellationException，需显式检查协程存活并校验词未过期
                currentCoroutineContext().ensureActive()
                if (query.value == q) {
                    _suggestions.value = result.getOrNull()?.tags.orEmpty().take(8)
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun clearSearch() {
        query.value = ""
        _hasSearched.value = false
        _suggestions.value = emptyList()
        resetSearchResults()
    }

    private fun resetSearchResults() {
        searchJob?.cancel()
        searchJob = null
        illustPaged.reset()
        novelPaged.reset()
        userPaged.reset()
        _popularIllusts.value = emptyList()
        _popularNovels.value = emptyList()
        _popularIllustsLoading.value = false
        _popularNovelsLoading.value = false
    }

    fun setType(value: SearchType) {
        if (type.value == value) return
        type.value = value
        // 跨类型残留的 searchTarget 会触发 400，切类型时回退默认档
        val allowed = if (value == SearchType.NOVEL) NOVEL_TARGETS else ILLUST_TARGETS
        if (filters.value.searchTarget !in allowed) {
            filters.value = filters.value.copy(searchTarget = "partial_match_for_tags")
        }
        if (_hasSearched.value) {
            search()
        }
    }

    fun applyFilters(value: SearchFilters) {
        filters.value = value
        viewModelScope.launch {
            runCatching {
                userPreferences.setSearchFilterSort(value.sort)
                userPreferences.setSearchFilterTarget(value.searchTarget)
                userPreferences.setSearchFilterBookmarkMin(value.bookmarkNumMin ?: 0)
                userPreferences.setSearchFilterKeywordUsers(value.keywordUsersBucket ?: 0)
                userPreferences.setSearchFilterAiType(value.aiType.wire)
            }
        }
    }

    private fun loadSavedFilters() {
        viewModelScope.launch {
            val sort = runCatching { userPreferences.searchFilterSort.first() }.getOrDefault("popular_desc")
            val target = runCatching { userPreferences.searchFilterTarget.first() }
                .getOrDefault("partial_match_for_tags")
            val bookmarkMin = runCatching { userPreferences.searchFilterBookmarkMin.first() }.getOrDefault(0)
            val keywordUsers = runCatching { userPreferences.searchFilterKeywordUsers.first() }.getOrDefault(0)
            val aiType = AiFilter.fromWire(runCatching { userPreferences.searchFilterAiType.first() }.getOrDefault(0))
            val allowed = if (type.value == SearchType.NOVEL) NOVEL_TARGETS else ILLUST_TARGETS
            filters.value = SearchFilters(
                sort = sort,
                searchTarget = target.takeIf { it in allowed } ?: "partial_match_for_tags",
                bookmarkNumMin = bookmarkMin.takeIf { it > 0 },
                keywordUsersBucket = keywordUsers.takeIf { it > 0 },
                aiType = aiType,
            )
            filtersRestored.value = true
        }
    }

    private fun loadOptions() {
        viewModelScope.launch {
            runCatching { pixivRepository.api.searchOptions(word = "") }
                .onSuccess { resp ->
                    _toolOptions.value = resp.illust?.tool?.options.orEmpty()
                    _genreOptions.value = resp.novel?.genre?.options.orEmpty()
                    _langOptions.value = resp.novel?.lang?.options.orEmpty()
                }
        }
    }

    fun search() {
        val rawWord = query.value.trim()
        if (rawWord.isBlank()) return
        val searchType = type.value
        resetSearchResults()
        _hasSearched.value = true
        searchJob = viewModelScope.launch {
            filtersRestored.first { it }
            ensureActive()
            executeSearch(rawWord, searchType)
        }
    }

    private suspend fun executeSearch(rawWord: String, searchType: SearchType) {
        recordHistory(rawWord)
        if (searchType == SearchType.USER) {
            userPaged.loadInitial(
                fetch = { pixivRepository.api.searchUsers(rawWord) },
                fetchNext = { pixivRepository.api.getNextUsers(it) },
            )
            return
        }

        val f = filters.value
        // 「Xusers入り」拼入请求以支持非会员收藏量过滤，历史仍只记原始词
        val keywordSuffix = f.keywordUsersBucket?.let { " ${it}users入り" } ?: ""
        val word = rawWord + keywordSuffix
        // pixiv API：title_and_caption 与 date_* 组合会返回 400，统一降级为 popular_desc
        val sort = if (f.searchTarget == "title_and_caption" &&
            (f.sort == "date_desc" || f.sort == "date_asc")
        ) {
            "popular_desc"
        } else {
            f.sort
        }
        // 非会员用 popular_* 调普通搜索会 400，需降级为 popular_preview 并写回 filters 使结果页渲染分支一致
        val premiumSorts = setOf("popular_desc", "popular_male_desc", "popular_female_desc")
        val usePopularPreview = sort == "popular_preview" ||
            (sort in premiumSorts && !pixivRepository.pixivApi.session.isPremium)
        val hasNovelLengthFilter = searchType == SearchType.NOVEL && f.bodyLengthUnit != null &&
            (f.bodyLengthMin != null || f.bodyLengthMax != null)
        val effectiveSort = when {
            // 小说热门预览无正文长度参数，改走分页搜索
            usePopularPreview && hasNovelLengthFilter -> "date_desc"
            usePopularPreview -> "popular_preview"
            else -> sort
        }
        if (usePopularPreview && effectiveSort != f.sort) {
            filters.value = f.copy(sort = effectiveSort)
        }
        // 默认档 partial_match_for_tags 不传 search_target，让服务端合并 tag 与标题命中
        val searchTarget = f.searchTarget
            .takeIf { it in if (searchType == SearchType.NOVEL) NOVEL_TARGETS else ILLUST_TARGETS }
            ?.takeUnless { it == "partial_match_for_tags" }
        // 服务端 search_ai_type 仅支持 0/1，「仅看 AI」需发 0 后在客户端按 ai_type==2 过滤
        val searchAiType = if (f.aiType == AiFilter.HUMAN_ONLY) 1 else 0
        val computed = durationRange(f.durationBucket)
        val startDate = computed?.first ?: f.startDate
        val endDate = computed?.second ?: f.endDate
        when (searchType) {
            SearchType.ILLUST -> if (effectiveSort == "popular_preview") {
                loadPopular(searchType, word, f, searchTarget, searchAiType, startDate, endDate)
            } else {
                val res = resolutionRange(f.resolutionBucket)
                illustPaged.loadInitial(
                    fetch = {
                        val resp = pixivRepository.api.searchIllusts(
                            word = word,
                            sort = effectiveSort,
                            searchTarget = searchTarget,
                            startDate = startDate,
                            endDate = endDate,
                            bookmarkNumMin = f.bookmarkNumMin,
                            tool = f.tool,
                            lang = f.lang,
                            searchAiType = searchAiType,
                            ratioPattern = f.ratioPattern,
                            contentType = contentTypeQuery(f.contentType),
                            widthMin = res?.widthMin,
                            widthMax = res?.widthMax,
                            heightMin = res?.heightMin,
                            heightMax = res?.heightMax,
                        )
                        resp.copy(illusts = resp.illusts.filter {
                            (f.aiType != AiFilter.AI_ONLY || it.illust_ai_type == 2) &&
                                r18Accept(it.x_restrict, f.r18Mode)
                        })
                    },
                    fetchNext = { url ->
                        val resp = pixivRepository.api.getNextIllusts(url)
                        resp.copy(illusts = resp.illusts.filter {
                            (f.aiType != AiFilter.AI_ONLY || it.illust_ai_type == 2) &&
                                r18Accept(it.x_restrict, f.r18Mode)
                        })
                    },
                )
            }
            SearchType.NOVEL -> if (effectiveSort == "popular_preview") {
                loadPopular(searchType, word, f, searchTarget, searchAiType, startDate, endDate)
            } else {
                val body = bodyRange(f)
                novelPaged.loadInitial(
                    fetch = {
                        val resp = pixivRepository.api.searchNovels(
                            word = word,
                            sort = effectiveSort,
                            searchTarget = searchTarget,
                            startDate = startDate,
                            endDate = endDate,
                            bookmarkNumMin = f.bookmarkNumMin,
                            genre = f.genre,
                            lang = f.lang,
                            searchAiType = searchAiType,
                            isOriginalOnly = f.isOriginalOnly,
                            isReplaceableOnly = f.isReplaceableOnly,
                            textLengthMin = body.textMin,
                            textLengthMax = body.textMax,
                            wordCountMin = body.wordMin,
                            wordCountMax = body.wordMax,
                            readingTimeMin = body.readMin,
                            readingTimeMax = body.readMax,
                        )
                        resp.copy(novels = resp.novels.filter {
                            (f.aiType != AiFilter.AI_ONLY || it.novel_ai_type == 2) &&
                                r18Accept(it.x_restrict, f.r18Mode)
                        })
                    },
                    fetchNext = { url ->
                        val resp = pixivRepository.api.getNextNovels(url)
                        resp.copy(novels = resp.novels.filter {
                            (f.aiType != AiFilter.AI_ONLY || it.novel_ai_type == 2) &&
                                r18Accept(it.x_restrict, f.r18Mode)
                        })
                    },
                )
            }
            SearchType.USER -> Unit
        }
    }

    private suspend fun loadPopular(
        searchType: SearchType,
        word: String,
        f: SearchFilters,
        searchTarget: String?,
        searchAiType: Int,
        startDate: String?,
        endDate: String?,
    ) {
        when (searchType) {
            SearchType.ILLUST -> {
                _popularIllustsLoading.value = true
                val result = runCatching {
                    pixivRepository.api.popularPreview(
                        word = word,
                        searchTarget = searchTarget,
                        startDate = startDate,
                        endDate = endDate,
                        bookmarkNumMin = f.bookmarkNumMin,
                        tool = f.tool,
                        lang = f.lang,
                        searchAiType = searchAiType,
                        ratioPattern = f.ratioPattern,
                    )
                }
                // runCatching 会吞掉 CancellationException，需防止已取消请求回写结果或误清加载态
                currentCoroutineContext().ensureActive()
                _popularIllusts.value = result.getOrNull()?.illusts.orEmpty().take(10)
                    .filter { i ->
                        (f.aiType != AiFilter.AI_ONLY || i.illust_ai_type == 2) && r18Accept(i.x_restrict, f.r18Mode)
                    }
                    .distinctBy { it.id }
                _popularIllustsLoading.value = false
            }
            SearchType.NOVEL -> {
                _popularNovelsLoading.value = true
                val result = runCatching {
                    pixivRepository.api.popularNovelPreview(
                        word = word,
                        searchTarget = searchTarget,
                        startDate = startDate,
                        endDate = endDate,
                    )
                }
                currentCoroutineContext().ensureActive()
                _popularNovels.value = result.getOrNull()?.novels.orEmpty().take(10)
                    .filter { n ->
                        (f.aiType != AiFilter.AI_ONLY || n.novel_ai_type == 2) && r18Accept(n.x_restrict, f.r18Mode)
                    }
                    .distinctBy { it.id }
                _popularNovelsLoading.value = false
            }
            SearchType.USER -> Unit
        }
    }

    private fun durationRange(bucket: String?, today: LocalDate = LocalDate.now()): Pair<String?, String?>? =
        when (bucket) {
            "Last24Hours" -> today.minusDays(1).toString() to today.toString()
            "LastWeek" -> today.minusWeeks(1).toString() to today.toString()
            "LastMonth" -> today.minusMonths(1).toString() to today.toString()
            "LastHalfYear" -> today.minusMonths(6).toString() to today.toString()
            "LastYear" -> today.minusYears(1).toString() to today.toString()
            else -> null
        }

    private fun bodyRange(f: SearchFilters): BodyRange = when (f.bodyLengthUnit) {
        0 -> BodyRange(f.bodyLengthMin, f.bodyLengthMax, null, null, null, null)
        1 -> BodyRange(null, null, f.bodyLengthMin, f.bodyLengthMax, null, null)
        2 -> BodyRange(null, null, null, null, f.bodyLengthMin, f.bodyLengthMax)
        else -> BodyRange(null, null, null, null, null, null)
    }

    private fun resolutionRange(bucket: String?): ResRange? = when (bucket) {
        "Above3000" -> ResRange(3000, null, 3000, null)
        "Between1000And2999" -> ResRange(1000, 2999, 1000, 2999)
        "Below1000" -> ResRange(null, 999, null, 999)
        else -> null
    }

    private fun contentTypeQuery(value: String?): String? = when (value) {
        null, "illust_and_manga_and_ugoira" -> null
        else -> value
    }

    private fun r18Accept(xRestrict: Int?, mode: R18Filter): Boolean = when (mode) {
        R18Filter.SAFE_ONLY -> (xRestrict ?: 0) <= 0
        R18Filter.R18_ONLY -> (xRestrict ?: 0) > 0
        else -> true
    }

    fun loadMore(resultType: SearchType) {
        viewModelScope.launch {
            // Pager 会预组合相邻页，仅当前类型允许触发翻页
            if (!_hasSearched.value || type.value != resultType) return@launch
            when (resultType) {
                SearchType.ILLUST -> illustPaged.loadMore()
                SearchType.NOVEL -> novelPaged.loadMore()
                SearchType.USER -> userPaged.loadMore()
            }
        }
    }

    fun retry() = search()

    private fun recordHistory(keyword: String) {
        viewModelScope.launch {
            runCatching {
                searchHistoryDao.replaceTop(SearchHistoryEntity(keyword = keyword))
            }
        }
    }

    fun removeHistory(entity: SearchHistoryEntity) {
        viewModelScope.launch { runCatching { searchHistoryDao.delete(entity) } }
    }

    fun toggleFollowUser(userId: Long, nowFollowed: Boolean) =
        favoriteActions.toggleFollowUserSilent(viewModelScope, userId, nowFollowed)

    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleNovelFavoriteSilent(viewModelScope, novelId, nowFavorite)

    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleIllustFavoriteSilent(viewModelScope, illustId, nowFavorite)

    fun clearHistory() {
        viewModelScope.launch { runCatching { searchHistoryDao.clearAll() } }
    }

    private fun loadHotTags() {
        viewModelScope.launch {
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

private data class BodyRange(
    val textMin: Int?, val textMax: Int?,
    val wordMin: Int?, val wordMax: Int?,
    val readMin: Int?, val readMax: Int?,
)

private data class ResRange(
    val widthMin: Int?, val widthMax: Int?,
    val heightMin: Int?, val heightMax: Int?,
)
