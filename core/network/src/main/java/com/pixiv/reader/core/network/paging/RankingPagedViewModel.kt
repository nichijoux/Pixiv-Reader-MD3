package com.pixiv.reader.core.network.paging

import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.ui.RankingModeInfo
import com.pixiv.reader.core.network.message.MessageViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 排行榜 ViewModel 泛型基类（core 共享，供漫画/插画/小说排行榜复用）。
 *
 * 支持日期筛选（[selectDate]）：查看过去某天的历史榜单，「mode × 日期」各段独立缓存与分页。
 *
 * 首段不在 `init` 预加载：Kotlin 父类 `init` 先于子类构造参数属性赋值执行，此时若经虚方法
 * 调 [loadInitialFor] 访问子类注入字段（如 Hilt 注入的 `pixivRepository`）会取到 null（NPE）。
 * 首段改由 UI 进入时调 [onPageSelected]（`RankingList` 首帧必触发）惰性加载——此时构造已完成。
 *
 * @param modes 分段配置（label 资源 + mode 值）。**必须经构造传入**而非子类属性初始化：
 *              基类后续加载依赖它，而父类构造先于子类属性初始化执行。
 */
abstract class RankingPagedViewModel<T>(
    val modes: List<RankingModeInfo>,
) : MessageViewModel() {

    /** 各段独立分页状态：mode（或 `mode|date` 复合键）→ PagedState，首次访问时创建并驻留。 */
    private val pages = mutableMapOf<String, PagedState<T>>()

    /** 已触发过首次加载的段（键含日期维度，防止滑动切回重复请求）。 */
    private val initialized = mutableSetOf<String>()

    /** 当前日期筛选（yyyy-MM-dd），null = 最新榜。 */
    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    /**
     * 段缓存键：date 为 null 时保持纯 mode（与旧版一致），否则 `mode|date` 复合键——
     * 不同日期各段缓存互不覆写，切回已看过的日期直接命中缓存、不重复请求。
     */
    private fun cacheKey(mode: String, date: String?): String =
        if (date == null) mode else "$mode|$date"

    /** 返回某段的分页状态（惰性创建，同 mode 同日期返回同一实例）。 */
    fun stateFor(value: String): PagedState<T> =
        pages.getOrPut(cacheKey(value, _selectedDate.value)) { PagedState() }

    /** 滑动/点 Tab 切到某段：仅首次进入才加载（数据已驻留则不重复请求）。 */
    fun onPageSelected(value: String) = ensureLoaded(value)

    /**
     * 切换日期筛选（null = 回到最新榜）：只更新状态、不清理缓存——各段按下新复合键惰性
     * 创建或命中旧缓存；UI 侧监听 [selectedDate] 重建列表后经 [onPageSelected] 触发重载。
     *
     * @param date 目标榜单日期（yyyy-MM-dd），null 表示最新榜
     */
    fun selectDate(date: String?) {
        if (_selectedDate.value == date) return
        _selectedDate.value = date
    }

    /** 某段加载失败重试（始终重拉该段当前日期的第一页）。 */
    fun retry(value: String) {
        val date = _selectedDate.value
        initialized += cacheKey(value, date)
        viewModelScope.launch { loadInitialFor(stateFor(value), value, date) }
    }

    /** 某段触底加载下一页。 */
    fun loadMore(value: String) {
        viewModelScope.launch { stateFor(value).loadMore() }
    }

    private fun ensureLoaded(value: String) {
        val date = _selectedDate.value
        if (!initialized.add(cacheKey(value, date))) return
        viewModelScope.launch { loadInitialFor(stateFor(value), value, date) }
    }

    /**
     * 段数据首载：fetch 拉第一页、fetchNext 翻页。
     *
     * @param paged 该段分页状态
     * @param mode 分段 mode 值
     * @param date 该段请求的榜单日期（yyyy-MM-dd），null = 最新榜；实现须透传给接口
     */
    protected abstract suspend fun loadInitialFor(paged: PagedState<T>, mode: String, date: String?)

    /**
     * 收藏/取消收藏并发结果通知（子类共用样板）：成功发 bookmarked/unbookmarked 文案，
     * 失败发 actionFailed 文案 + 原因。子类传各自模块字符串资源与实际动作。
     */
    protected fun toggleFavoriteNotified(
        nowFavorite: Boolean,
        bookmarkedRes: Int,
        unbookmarkedRes: Int,
        actionFailedRes: Int,
        toggle: suspend (Boolean) -> Result<Unit>,
    ) {
        viewModelScope.launch {
            toggle(nowFavorite)
                .onSuccess { sendMessage(UiMessage(if (nowFavorite) bookmarkedRes else unbookmarkedRes)) }
                .onFailure { sendMessage(UiMessage(actionFailedRes, listOf(it.message ?: ""))) }
        }
    }
}
