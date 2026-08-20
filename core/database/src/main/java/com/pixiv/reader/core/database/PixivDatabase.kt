package com.pixiv.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.dao.ReadingProgressDao
import com.pixiv.reader.core.database.dao.SearchHistoryDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.database.entity.SearchHistoryEntity

/**
 * 数据库结构（version = 2）。
 *
 * 历史迁移（原 v1→v7 六条，含 download_entry 字段演进与主键重构）已全部清理，
 * 新装用户直接按此 schema 建库；旧版本（v7）数据经 `fallbackToDestructiveMigration` 重建。
 * 后续新增实体/字段：升 version 并从这里开始写新迁移。
 *
 * v2：download_entry 新增 payloadJson（完整卡片快照 JSON，下载管理页完整展示用）。
 */
@Database(
    entities = [
        ReadingProgressEntity::class,
        BrowseHistoryEntity::class,
        DownloadEntryEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class PixivDatabase : RoomDatabase() {
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun browseHistoryDao(): BrowseHistoryDao
    abstract fun downloadEntryDao(): DownloadEntryDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        /** v1 → v2：download_entry 增加 payloadJson 列（旧数据回退结构字段展示，零丢失）。 */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_entry ADD COLUMN payloadJson TEXT")
            }
        }
    }
}
