package com.pixiv.reader.core.network.feed

import com.google.gson.Gson
import com.pixiv.reader.core.database.dao.FeedSnapshotDao
import com.pixiv.reader.core.database.entity.FeedSnapshotEntity
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 信息流首屏快照存取（首页秒开）：保存 / 恢复各信息流第一页内容 + 分页游标。
 *
 * 序列化用 Gson 直接吃 lib:pixivapi 的纯 data class（`Illust` / `Novel` / `TrendingTag`），
 * 与浏览历史 payloadJson 同一套模式；解析失败（模型变更 / 数据损坏）删行自愈返回 null。
 * 不做 TTL——快照只做首屏展示，联网刷新成功后即被覆盖。
 *
 * @param feedSnapshotDao 快照 DAO
 */
@Singleton
class FeedSnapshotStore @Inject constructor(
    private val feedSnapshotDao: FeedSnapshotDao,
) {

    private val gson = Gson()

    /**
     * 保存流快照（首页第一页成功加载后调用；失败静默——快照仅是优化）。
     *
     * @param feedKey 流标识（[KEY_HOME_RECOMMEND] 等常量）
     * @param items 第一页内容列表
     * @param elementType 列表元素类型（Gson 序列化用，如 `object : TypeToken<List<Illust>>() {}.type`）
     * @param nextUrl 分页游标（触底加载续传位置）
     * @return 无返回值
     */
    suspend fun <T> save(feedKey: String, items: List<T>, elementType: Type, nextUrl: String? = null) {
        if (items.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                feedSnapshotDao.upsert(
                    FeedSnapshotEntity(
                        feedKey = feedKey,
                        payloadJson = gson.toJson(items, elementType),
                        nextUrl = nextUrl,
                    ),
                )
            }
        }
    }

    /**
     * 恢复流快照（冷启动首屏预填）。
     *
     * @param feedKey 流标识
     * @param elementType 列表元素类型（反序列化用，须与保存时一致）
     * @return items + 分页游标；未保存 / 解析失败返回 null（失败时删行自愈）
     */
    suspend fun <T> restore(feedKey: String, elementType: Type): Pair<List<T>, String?>? {
        val entity = feedSnapshotDao.get(feedKey) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val items: List<T> = gson.fromJson(entity.payloadJson, elementType)
                if (items.isNullOrEmpty()) error("empty snapshot")
                items to entity.nextUrl
            }.getOrElse {
                // 模型变更 / 数据损坏：删行自愈，下次刷新后重新保存
                runCatching { feedSnapshotDao.delete(feedKey) }
                null
            }
        }
    }

    /** 清空全部快照（「清除缓存」联动）。 */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            runCatching { feedSnapshotDao.clearAll() }
        }
    }

    companion object {
        /** 首页推荐流。 */
        const val KEY_HOME_RECOMMEND = "home_recommend"

        /** 首页关注新稿流。 */
        const val KEY_HOME_FOLLOW = "home_follow"

        /** 首页热门标签横滑区。 */
        const val KEY_HOME_TAGS = "home_tags"

        /** 小说 Tab 推荐流。 */
        const val KEY_NOVEL_FEED = "novel_feed"
    }
}
