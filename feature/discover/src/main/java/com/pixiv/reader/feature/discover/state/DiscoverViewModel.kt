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
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.pixiv.reader.feature.discover.R
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
import java.time.LocalDate

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
 *
 * 类型与筛选模型见 [SearchType] / [SearchFilters]（DiscoverModels.kt）。
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

        /** 插画可用的匹配方式（对齐 Pixiv-Shaft：插画不认 text/keyword）。 */
        private val ILLUST_TARGETS = setOf(
            "partial_match_for_tags", "exact_match_for_tags", "title_and_caption",
        )

        /** 小说可用的匹配方式（对齐 Pixiv-Shaft：小说不认 title_and_caption）。 */
        private val NOVEL_TARGETS = setOf(
            "partial_match_for_tags", "exact_match_for_tags", "text", "keyword",
        )
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

    private val _langOptions = MutableStateFlow<List<SearchLangOption>>(emptyList())
    val langOptions: StateFlow<List<SearchLangOption>> = _langOptions.asStateFlow()

    /** 是否 Premium（决定排序档里的男/女性向人气两档是否可选，对齐 Pixiv-Shaft）。 */
    val isPremium: StateFlow<Boolean> = MutableStateFlow(pixivRepository.pixivApi.session.isPremium)

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
        loadSavedFilters()
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
        // 匹配方式按类型归一：小说/插画各自不认对方的档位（text/keyword ↔ title_and_caption），
        // 跨类型残留值会 400 —— 切类型时回退默认档
        val allowed = if (value == SearchType.NOVEL) NOVEL_TARGETS else ILLUST_TARGETS
        if (filters.value.searchTarget !in allowed) {
            filters.value = filters.value.copy(searchTarget = "partial_match_for_tags")
        }
        if (_hasSearched.value) {
            // 类型切换后按当前词重新搜索
            search()
        }
    }

    /** 应用筛选条件（筛选面板"应用"后调用）。 */
    fun applyFilters(value: SearchFilters) {
        filters.value = value
        // 持久化常用条件（对齐 Pixiv-Shaft 全局默认语义：下次打开发现页沿用上次筛选）
        viewModelScope.launch {
            runCatching {
                userPreferences.setSearchFilterSort(value.sort)
                userPreferences.setSearchFilterTarget(value.searchTarget)
                userPreferences.setSearchFilterBookmarkMin(value.bookmarkNumMin ?: 0)
                userPreferences.setSearchFilterKeywordUsers(value.keywordUsersBucket ?: 0)
                userPreferences.setSearchFilterAiType(value.aiType)
            }
        }
    }

    /** 读取上次应用的筛选条件（DataStore）作为本会话默认。 */
    private fun loadSavedFilters() {
        viewModelScope.launch {
            val sort = runCatching { userPreferences.searchFilterSort.first() }.getOrDefault("popular_desc")
            val target = runCatching { userPreferences.searchFilterTarget.first() }
                .getOrDefault("partial_match_for_tags")
            val bookmarkMin = runCatching { userPreferences.searchFilterBookmarkMin.first() }.getOrDefault(0)
            val keywordUsers = runCatching { userPreferences.searchFilterKeywordUsers.first() }.getOrDefault(0)
            val aiType = runCatching { userPreferences.searchFilterAiType.first() }.getOrDefault(0)
            // 恢复的匹配方式按当前类型归一（持久化可能来自另一类型，跨类型值会 400）
            val allowed = if (type.value == SearchType.NOVEL) NOVEL_TARGETS else ILLUST_TARGETS
            filters.value = SearchFilters(
                sort = sort,
                searchTarget = target.takeIf { it in allowed } ?: "partial_match_for_tags",
                bookmarkNumMin = bookmarkMin.takeIf { it > 0 },
                keywordUsersBucket = keywordUsers.takeIf { it > 0 },
                aiType = aiType,
            )
        }
    }

    /** 拉取搜索选项（工具 / 题材下拉数据，来自 /v1/search/options）。 */
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

    /** 搜索：按类型与排序（热门预览/分页搜索）加载结果 + 写入搜索历史。 */
    fun search() {
        val rawWord = query.value.trim()
        if (rawWord.isBlank()) return
        _hasSearched.value = true
        val f = filters.value
        // 「Xusers入り」关键字后缀拼进请求（对齐 Pixiv-Shaft：非会员也可用的收藏量过滤）；
        // 搜索历史只记原始词
        val keywordSuffix = f.keywordUsersBucket?.let { " ${it}users入り" } ?: ""
        val word = rawWord + keywordSuffix
        // pixiv API：title_and_caption（标题简介）与 date_* 时间排序组合返回 400 —— 请求侧统一降级为按热度
        val sort = if (f.searchTarget == "title_and_caption" &&
            (f.sort == "date_desc" || f.sort == "date_asc")
        ) {
            "popular_desc"
        } else {
            f.sort
        }
        // 非会员 + popular_* 人气排序走 /v1/search/illust 会 400（对齐 Pixiv-Shaft：非付费用户的人气
        // 排序只能走 popular-preview，男/女性向两档同为 Premium 专属）。无借号系统 → 降级为热门预览，
        // 并写回 filters 让结果页渲染分支一致。
        val premiumSorts = setOf("popular_desc", "popular_male_desc", "popular_female_desc")
        val effectiveSort = if (sort in premiumSorts && !pixivRepository.pixivApi.session.isPremium) {
            filters.value = filters.value.copy(sort = "popular_preview")
            "popular_preview"
        } else {
            sort
        }
        // 匹配方式按类型合法化（跨类型残留值会 400：插画不认 text/keyword、小说不认 title_and_caption）；
        // 默认档「标签部分一致」不传 search_target（对齐 Pixiv-Shaft #906：服务端合并 tag+标题命中）
        val searchTarget = f.searchTarget
            .takeIf { it in if (type.value == SearchType.NOVEL) NOVEL_TARGETS else ILLUST_TARGETS }
            ?.takeUnless { it == "partial_match_for_tags" }
        // 官方 search_ai_type 只有 0/1（对齐 Pixiv-Shaft）：「仅人绘」发 1；「全部」「仅看 AI」都发 0——
        // 「仅看 AI」由客户端按真实 ai_type==2 过滤
        val searchAiType = if (f.aiType == 1) 1 else 0
        // 投稿期间相对档当场算 today−N（每次搜索重算，跨午夜自动跟随）；bucket 为空回落自定义起止
        val computed = durationRange(f.durationBucket)
        val startDate = computed?.first ?: f.startDate
        val endDate = computed?.second ?: f.endDate
        recordHistory(rawWord)
        viewModelScope.launch {
            when (type.value) {
                SearchType.ILLUST -> if (effectiveSort == "popular_preview") {
                    loadPopular(word, f, searchTarget, searchAiType, startDate, endDate)
                } else {
                    val body = bodyRange(f)
                    val res = resolutionRange(f.resolutionBucket)
                    // reset 作废旧代次：上次搜索仍在途时重搜不被幂等忽略（PagedState 在途时 loadInitial 直接 return）
                    illustPaged.reset()
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
                            // 客户端过滤：仅看 AI（illust_ai_type==2）+ R18 档（x_restrict）
                            resp.copy(illusts = resp.illusts.filter {
                                (f.aiType != 2 || it.illust_ai_type == 2) &&
                                    r18Accept(it.x_restrict, f.r18Mode)
                            })
                        },
                        fetchNext = { url ->
                            val resp = pixivRepository.api.getNextIllusts(url)
                            resp.copy(illusts = resp.illusts.filter {
                                (f.aiType != 2 || it.illust_ai_type == 2) &&
                                    r18Accept(it.x_restrict, f.r18Mode)
                            })
                        },
                    )
                }
                SearchType.NOVEL -> if (effectiveSort == "popular_preview") {
                    loadPopular(word, f, searchTarget, searchAiType, startDate, endDate)
                } else {
                    val body = bodyRange(f)
                    novelPaged.reset()
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
                            // 客户端过滤：仅看 AI（novel_ai_type==2）+ R18 档（x_restrict）
                            resp.copy(novels = resp.novels.filter {
                                (f.aiType != 2 || it.novel_ai_type == 2) &&
                                    r18Accept(it.x_restrict, f.r18Mode)
                            })
                        },
                        fetchNext = { url ->
                            val resp = pixivRepository.api.getNextNovels(url)
                            resp.copy(novels = resp.novels.filter {
                                (f.aiType != 2 || it.novel_ai_type == 2) &&
                                    r18Accept(it.x_restrict, f.r18Mode)
                            })
                        },
                    )
                }
                SearchType.USER -> {
                    userPaged.reset()
                    userPaged.loadInitial(
                        fetch = { pixivRepository.api.searchUsers(word) },
                        fetchNext = { pixivRepository.api.getNextUsers(it) },
                    )
                }
            }
        }
    }

    /** 热门预览（popular-preview endpoint，一次性列表；对齐 Pixiv-Shaft 不传 sort）。 */
    private suspend fun loadPopular(
        word: String,
        f: SearchFilters,
        searchTarget: String?,
        searchAiType: Int,
        startDate: String?,
        endDate: String?,
    ) {
        when (type.value) {
            SearchType.ILLUST -> runCatching {
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
            }.onSuccess {
                _popularIllusts.value = it.illusts.take(10).filter { i ->
                    (f.aiType != 2 || i.illust_ai_type == 2) && r18Accept(i.x_restrict, f.r18Mode)
                }
            }.onFailure { _popularIllusts.value = emptyList() }
            SearchType.NOVEL -> runCatching {
                pixivRepository.api.popularNovelPreview(
                    word = word,
                    searchTarget = searchTarget,
                    startDate = startDate,
                    endDate = endDate,
                )
            }.onSuccess {
                _popularNovels.value = it.novels.take(10).filter { n ->
                    (f.aiType != 2 || n.novel_ai_type == 2) && r18Accept(n.x_restrict, f.r18Mode)
                }
            }.onFailure { _popularNovels.value = emptyList() }
            SearchType.USER -> Unit
        }
    }

    // ── 请求参数映射（对齐 Pixiv-Shaft / iOS 8.6.6 抓包）──

    /** 投稿期间相对档 → (start_date, end_date)；today−N 当场计算，跨午夜自动跟随；无效 bucket 返回 null。 */
    private fun durationRange(bucket: String?, today: LocalDate = LocalDate.now()): Pair<String?, String?>? =
        when (bucket) {
            "Last24Hours" -> today.minusDays(1).toString() to today.toString()
            "LastWeek" -> today.minusWeeks(1).toString() to today.toString()
            "LastMonth" -> today.minusMonths(1).toString() to today.toString()
            "LastHalfYear" -> today.minusMonths(6).toString() to today.toString()
            "LastYear" -> today.minusYears(1).toString() to today.toString()
            else -> null
        }

    /** 正文长度维度 → 三组 API 参数（text_length / word_count / reading_time，单位分钟）。 */
    private fun bodyRange(f: SearchFilters): BodyRange = when (f.bodyLengthUnit) {
        0 -> BodyRange(f.bodyLengthMin, f.bodyLengthMax, null, null, null, null)
        1 -> BodyRange(null, null, f.bodyLengthMin, f.bodyLengthMax, null, null)
        2 -> BodyRange(null, null, null, null, f.bodyLengthMin, f.bodyLengthMax)
        else -> BodyRange(null, null, null, null, null, null)
    }

    /** 分辨率档位 → width/height min/max 四参数（对齐 Shaft：≥3000 / 1000~2999 / ≤999）。 */
    private fun resolutionRange(bucket: String?): ResRange? = when (bucket) {
        "Above3000" -> ResRange(3000, null, 3000, null)
        "Between1000And2999" -> ResRange(1000, 2999, 1000, 2999)
        "Below1000" -> ResRange(null, 999, null, 999)
        else -> null
    }

    /** 作品类别 5 档：默认档「插画、漫画、动图」等价不传（对齐 Shaft / iOS）。 */
    private fun contentTypeQuery(value: String?): String? = when (value) {
        null, "illust_and_manga_and_ugoira" -> null
        else -> value
    }

    /** R18 三档客户端过滤（对齐 Shaft：按 x_restrict，缺失当全年龄 0）。 */
    private fun r18Accept(xRestrict: Int?, mode: Int): Boolean = when (mode) {
        1 -> (xRestrict ?: 0) <= 0
        2 -> (xRestrict ?: 0) > 0
        else -> true
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

/** 正文长度三组 API 参数（text_length / word_count / reading_time）。 */
private data class BodyRange(
    val textMin: Int?, val textMax: Int?,
    val wordMin: Int?, val wordMax: Int?,
    val readMin: Int?, val readMax: Int?,
)

/** 分辨率档位展开的 width/height 区间参数。 */
private data class ResRange(
    val widthMin: Int?, val widthMax: Int?,
    val heightMin: Int?, val heightMax: Int?,
)
