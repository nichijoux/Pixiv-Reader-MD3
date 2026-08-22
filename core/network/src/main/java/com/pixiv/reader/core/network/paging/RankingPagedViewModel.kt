package com.pixiv.reader.core.network.paging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.common.ui.RankingModeInfo
import kotlinx.coroutines.launch

/**
 * 排行榜 ViewModel 泛型基类（core 共享，供漫画/插画/小说排行榜复用）。
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
) : ViewModel() {

    /** 各段独立分页状态：mode → PagedState，首次访问时创建并驻留。 */
    private val pages = mutableMapOf<String, PagedState<T>>()

    /** 已触发过首次加载的段（防止滑动切回重复请求）。 */
    private val initialized = mutableSetOf<String>()

    /** 返回某段的分页状态（惰性创建，每次调用返回同一实例）。 */
    fun stateFor(value: String): PagedState<T> = pages.getOrPut(value) { PagedState() }

    /** 滑动/点 Tab 切到某段：仅首次进入才加载（数据已驻留则不重复请求）。 */
    fun onPageSelected(value: String) = ensureLoaded(value)

    /** 某段加载失败重试（始终重拉该段第一页）。 */
    fun retry(value: String) {
        initialized += value
        viewModelScope.launch { loadInitialFor(stateFor(value), value) }
    }

    /** 某段触底加载下一页。 */
    fun loadMore(value: String) {
        viewModelScope.launch { stateFor(value).loadMore() }
    }

    private fun ensureLoaded(value: String) {
        if (!initialized.add(value)) return
        viewModelScope.launch { loadInitialFor(stateFor(value), value) }
    }

    /** 段数据首载：fetch 拉第一页、fetchNext 翻页。 */
    protected abstract suspend fun loadInitialFor(paged: PagedState<T>, mode: String)
}
