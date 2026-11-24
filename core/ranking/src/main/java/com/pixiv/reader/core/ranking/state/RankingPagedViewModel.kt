package com.pixiv.reader.core.ranking.state

import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.ranking.state.RankingModeInfo
import com.pixiv.reader.core.network.message.MessageViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 排行榜 ViewModel 泛型基类（漫画/插画/小说复用），按「mode × 日期」分段独立缓存与分页。
 *
 * 首段不在 `init` 预加载：父类 `init` 先于子类属性赋值，经虚方法调 [loadInitialFor] 访问子类注入字段（如 Hilt 的
 * `pixivRepository`）会取到 null（NPE）；改由 UI 首帧 [onPageSelected] 惰性加载。同理 [modes] 必须经构造传入。
 */
abstract class RankingPagedViewModel<T>(
    val modes: List<RankingModeInfo>,
    private val keySelector: ((T) -> Any?)? = null,
) : MessageViewModel() {

    /** mode 或 `mode|date` 复合键 → 分段状态，首次访问创建并驻留（滑动切回不重复请求）。 */
    private val pages = mutableMapOf<String, PagedState<T>>()

    /** 键含日期维度，防止滑动切回重复请求。 */
    private val initialized = mutableSetOf<String>()

    /** yyyy-MM-dd；null = 最新榜 */
    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    /**
     * date 为 null 时保持纯 mode（与旧版一致），否则 `mode|date` 复合键：不同日期各段缓存互不覆写，
     * 切回已看过的日期直接命中缓存。
     */
    private fun cacheKey(mode: String, date: String?): String =
        if (date == null) mode else "$mode|$date"

    /** 同 mode 同日期必须返回同一实例，否则 UI 每帧新建 PagedState 会反复重载。 */
    fun stateFor(value: String): PagedState<T> =
        pages.getOrPut(cacheKey(value, _selectedDate.value)) { PagedState(keySelector) }

    /** 仅首次进入才加载，数据已驻留则不重复请求。 */
    fun onPageSelected(value: String) = ensureLoaded(value)

    /**
     * 只更新状态、不清理缓存：UI 监听 [selectedDate] 重建列表后经 [onPageSelected] 触发重载。
     */
    fun selectDate(date: String?) {
        if (_selectedDate.value == date) return
        _selectedDate.value = date
    }

    /** 始终重拉该段当前日期的第一页。 */
    fun retry(value: String) {
        val date = _selectedDate.value
        initialized += cacheKey(value, date)
        viewModelScope.launch { loadInitialFor(stateFor(value), value, date) }
    }

    fun loadMore(value: String) {
        viewModelScope.launch { stateFor(value).loadMore() }
    }

    private fun ensureLoaded(value: String) {
        val date = _selectedDate.value
        if (!initialized.add(cacheKey(value, date))) return
        viewModelScope.launch { loadInitialFor(stateFor(value), value, date) }
    }

    /** 实现须把 date 透传给接口（null = 最新榜）。 */
    protected abstract suspend fun loadInitialFor(paged: PagedState<T>, mode: String, date: String?)

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
