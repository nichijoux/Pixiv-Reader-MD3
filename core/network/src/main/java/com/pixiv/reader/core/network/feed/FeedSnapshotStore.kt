package com.pixiv.reader.core.network.feed

import com.google.gson.Gson
import com.pixiv.reader.core.database.dao.FeedSnapshotDao
import com.pixiv.reader.core.database.entity.FeedSnapshotEntity
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 信息流首屏快照存取（冷启动秒开，无 TTL，刷新即覆盖）；反序列化失败时删行自愈并返回 null。 */
@Singleton
class FeedSnapshotStore @Inject constructor(
    private val feedSnapshotDao: FeedSnapshotDao,
) {

    private val gson = Gson()

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

    suspend fun <T> restore(feedKey: String, elementType: Type): Pair<List<T>, String?>? {
        val entity = feedSnapshotDao.get(feedKey) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val items: List<T> = gson.fromJson(entity.payloadJson, elementType)
                if (items.isNullOrEmpty()) error("empty snapshot")
                items to entity.nextUrl
            } catch (e: CancellationException) {
                // 协程取消不是数据损坏，透传取消且不删快照
                throw e
            } catch (e: Exception) {
                runCatching { feedSnapshotDao.delete(feedKey) }
                null
            }
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            runCatching { feedSnapshotDao.clearAll() }
        }
    }

    companion object {
        const val KEY_HOME_RECOMMEND = "home_recommend"

        const val KEY_HOME_FOLLOW = "home_follow"

        const val KEY_HOME_TAGS = "home_tags"

        const val KEY_NOVEL_FEED = "novel_feed"
    }
}
