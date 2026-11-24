package com.pixiv.reader.core.network.favorite

import com.pixiv.api.network.AppApi
import com.pixiv.api.PixivConstants
import com.pixiv.api.model.BookmarkTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 收藏编辑状态机（随宿主 VM 生命周期销毁）：管理公开/私密与标签选择。
 * pixiv 的 public/private 标签目录相互独立，切换可见性会重拉目录；重复 add 即更新收藏设置。
 */
class BookmarkEditor(
    private val scope: CoroutineScope,
    private val api: AppApi,
    private val targetType: String,
    private val targetId: () -> Long,
    private val uid: () -> Long,
) {

    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    /** 已提交生效的可见性，仅在详情加载或保存成功后更新。 */
    private val _committedRestrict = MutableStateFlow(PixivConstants.RESTRICT_PUBLIC)
    val committedRestrict: StateFlow<String> = _committedRestrict.asStateFlow()

    private val _restrict = MutableStateFlow(PixivConstants.RESTRICT_PUBLIC)
    val restrict: StateFlow<String> = _restrict.asStateFlow()

    private val _savedTags = MutableStateFlow<List<String>>(emptyList())
    val savedTags: StateFlow<List<String>> = _savedTags.asStateFlow()

    private val _allTags = MutableStateFlow<List<BookmarkTag>>(emptyList())
    val allTags: StateFlow<List<BookmarkTag>> = _allTags.asStateFlow()

    private val _tagsLoading = MutableStateFlow(false)
    val tagsLoading: StateFlow<Boolean> = _tagsLoading.asStateFlow()

    private var targetLoadJob: Job? = null
    private var targetLoadGeneration = 0L
    private var tagsJob: Job? = null
    private var tagsGeneration = 0L

    fun open() {
        _isOpen.value = true
        if (_allTags.value.isEmpty()) loadTags()
    }

    fun close() {
        _isOpen.value = false
    }

    fun setRestrict(value: String) {
        if (value == _restrict.value) return
        _restrict.value = value
        _allTags.value = emptyList()
        loadTags()
    }

    fun toggleTag(name: String) {
        _savedTags.value = if (name in _savedTags.value) _savedTags.value - name else _savedTags.value + name
    }

    fun createTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed in _savedTags.value) return
        _savedTags.value += trimmed
        if (_allTags.value.none { it.name == trimmed }) {
            _allTags.value += BookmarkTag(name = trimmed, count = 0, is_registered = true)
        }
    }

    /** 详情加载或切换作品后重置并回填已收藏状态；用代次与目标 id 双重校验防旧响应覆盖。 */
    fun onTargetLoaded(isBookmarked: Boolean) {
        targetLoadJob?.cancel()
        tagsJob?.cancel()
        val gen = ++targetLoadGeneration
        ++tagsGeneration
        _tagsLoading.value = false
        _savedTags.value = emptyList()
        _committedRestrict.value = PixivConstants.RESTRICT_PUBLIC
        _restrict.value = PixivConstants.RESTRICT_PUBLIC
        _allTags.value = emptyList()
        if (!isBookmarked) return
        val requestedId = targetId()
        targetLoadJob = scope.launch {
            try {
                val resp = if (targetType == "illust") {
                    api.getIllustBookmarkDetail(requestedId)
                } else {
                    api.getNovelBookmarkDetail(requestedId)
                }
                if (gen != targetLoadGeneration || targetId() != requestedId) return@launch
                val detail = resp.bookmark_detail ?: return@launch
                val restrictValue = detail.restrict ?: PixivConstants.RESTRICT_PUBLIC
                _committedRestrict.value = restrictValue
                _restrict.value = restrictValue
                _savedTags.value = detail.tags.mapNotNull { it.name }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 回显失败静默，编辑器仍可用于保存新设置
            }
        }
    }

    /** 提交收藏设置，成功后更新 [committedRestrict]；协程取消原样上抛。 */
    suspend fun save(): Result<Unit> {
        val requestedId = targetId()
        val draftRestrict = _restrict.value
        val draftTags = _savedTags.value
        val result = runCatching<Unit> {
            if (targetType == "illust") {
                api.bookmarkIllust(requestedId, draftRestrict, draftTags)
            } else {
                api.bookmarkNovel(requestedId, draftRestrict, draftTags)
            }
        }
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        if (result.isSuccess && targetId() == requestedId) {
            _committedRestrict.value = draftRestrict
        }
        return result
    }

    private fun loadTags() {
        val owner = uid()
        val restrict = _restrict.value
        if (owner <= 0L) return
        tagsJob?.cancel()
        val gen = ++tagsGeneration
        tagsJob = scope.launch {
            _tagsLoading.value = true
            try {
                val resp = if (targetType == "illust") {
                    api.getIllustBookmarkTags(owner, restrict)
                } else {
                    api.getNovelBookmarkTags(owner, restrict)
                }
                if (gen == tagsGeneration && restrict == _restrict.value) {
                    _allTags.value = resp.tags
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 标签目录失败静默，保留手动新建标签能力
            } finally {
                if (gen == tagsGeneration) _tagsLoading.value = false
            }
        }
    }
}
