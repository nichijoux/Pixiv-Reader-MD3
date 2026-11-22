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
 * 收藏编辑状态机（插画 / 小说 / 查看器 VM 各自实例化一份，随 VM 生命周期销毁）：
 * 管理「公开 / 私密 + 标签」收藏编辑弹层的开关、可见性、已选标签与标签目录。
 *
 * 数据流：
 * - 详情加载成功后 VM 调 [onTargetLoaded]：已收藏时拉 v2 bookmark/detail 回填 restrict 与已选标签；
 *   弹层打开时（[open]）按当前 restrict 拉 v1/user/bookmark-tags 目录（未拉过才拉）。
 * - 切换 restrict（[setRestrict]）联动重拉目录——pixiv 的 public/private 标签目录相互独立。
 * - [save] 按 targetType 分流调用 v2 bookmark add（携带 restrict 与已选标签）；
 *   未收藏与已收藏均走 add（重复 add 即更新收藏设置）。
 *
 * @param scope 宿主 VM 的协程作用域（viewModelScope，目录拉取在内部 launch）
 * @param api AppApi 访问器（收藏详情 / 标签目录 / 收藏提交）
 * @param targetType 目标类型："illust" / "novel"（决定分流调用的端点）
 * @param targetId 目标 id 供给函数（排行右栏切换作品时随 VM 状态变化）
 * @param uid 当前登录用户 id 供给函数（标签目录接口必传；未登录为 0，目录拉取静默失败）
 */
class BookmarkEditor(
    private val scope: CoroutineScope,
    private val api: AppApi,
    private val targetType: String,
    private val targetId: () -> Long,
    private val uid: () -> Long,
) {

    /** 编辑弹层开关（详情页 / 查看器「收藏设置」入口）。 */
    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    /** 已提交生效的收藏可见性（仅在详情加载或保存成功后更新，未保存的草稿切换不改此值）。 */
    private val _committedRestrict = MutableStateFlow(PixivConstants.RESTRICT_PUBLIC)
    val committedRestrict: StateFlow<String> = _committedRestrict.asStateFlow()

    /** 编辑中的收藏可见性：public（公开）/ private（私密）；保存时生效。 */
    private val _restrict = MutableStateFlow(PixivConstants.RESTRICT_PUBLIC)
    val restrict: StateFlow<String> = _restrict.asStateFlow()

    /** 已选标签名列表（回显自收藏详情；弹层内勾选实时增删）。 */
    private val _savedTags = MutableStateFlow<List<String>>(emptyList())
    val savedTags: StateFlow<List<String>> = _savedTags.asStateFlow()

    /** 标签目录（v1/user/bookmark-tags/{type}，含使用次数；新建标签即时追加展示）。 */
    private val _allTags = MutableStateFlow<List<BookmarkTag>>(emptyList())
    val allTags: StateFlow<List<BookmarkTag>> = _allTags.asStateFlow()

    /** 标签目录拉取中（弹层内占位）。 */
    private val _tagsLoading = MutableStateFlow(false)
    val tagsLoading: StateFlow<Boolean> = _tagsLoading.asStateFlow()

    private var targetLoadJob: Job? = null
    private var targetLoadGeneration = 0L
    private var tagsJob: Job? = null
    private var tagsGeneration = 0L

    /** 打开编辑弹层；标签目录未拉过时按当前 restrict 拉取。 */
    fun open() {
        _isOpen.value = true
        if (_allTags.value.isEmpty()) loadTags()
    }

    /** 关闭编辑弹层（不回滚已选标签——重开仍可见，保存才生效）。 */
    fun close() {
        _isOpen.value = false
    }

    /**
     * 切换收藏可见性（public / private）。
     * 两套 restrict 的标签目录相互独立，切换后清空目录并按新 restrict 重拉。
     *
     * @param value 目标可见性常量（PixivConstants.RESTRICT_PUBLIC / RESTRICT_PRIVATE）
     */
    fun setRestrict(value: String) {
        if (value == _restrict.value) return
        _restrict.value = value
        _allTags.value = emptyList()
        loadTags()
    }

    /** 勾选 / 取消勾选标签（只改本地已选集，保存时随收藏提交）。 */
    fun toggleTag(name: String) {
        _savedTags.value = if (name in _savedTags.value) _savedTags.value - name else _savedTags.value + name
    }

    /**
     * 新建标签并选中：追加进已选与目录（目录项 count=0、is_registered=true），
     * 下次保存后服务端即注册该标签。
     *
     * @param name 新标签名（空白忽略；重名只选中不重复添加）
     */
    fun createTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed in _savedTags.value) return
        _savedTags.value += trimmed
        if (_allTags.value.none { it.name == trimmed }) {
            _allTags.value += BookmarkTag(name = trimmed, count = 0, is_registered = true)
        }
    }

    /**
     * 详情加载完成后回填编辑器状态（幂等：每次详情加载 / 切换作品都调用）。
     * 已收藏时拉 v2 bookmark/detail 得当前 restrict 与已选标签；失败静默（编辑器仍可用，仅无回显）。
     * 发起前捕获目标 id 与代次，写回前双重校验，避免快速切换作品时旧响应覆盖新作品状态。
     *
     * @param isBookmarked 目标作品当前收藏态
     * @return 无返回值
     */
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
                // 使用捕获的 requestedId 发起请求，避免协程启动期间目标切换导致请求与校验不一致。
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
                // 详情回显失败时保持编辑器可用，不做额外错误弹窗。
            }
        }
    }

    /**
     * 保存收藏（公开/私密 + 已选标签；按 targetType 分流端点）。
     * 成功后同步 [committedRestrict]，关闭弹层等由调用方处理。
     *
     * @return 保存结果（成功 = Result.success；网络失败 = Result.failure 交由 VM 发消息提示）
     */
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
        // 协程取消向上传播（不误报为保存失败）
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        if (result.isSuccess && targetId() == requestedId) {
            _committedRestrict.value = draftRestrict
        }
        return result
    }

    /**
     * 按当前草稿可见性拉取标签目录，仅由最新一次请求复位加载标记，避免旧请求提前关闭转圈。
     *
     * @return 无返回值
     */
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
                // restrict 联动快速切换时，仅采纳最后一次同代次请求的结果。
                if (gen == tagsGeneration && restrict == _restrict.value) {
                    _allTags.value = resp.tags
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 标签目录失败时静默，保留本地手动新增标签能力。
            } finally {
                if (gen == tagsGeneration) _tagsLoading.value = false
            }
        }
    }
}
