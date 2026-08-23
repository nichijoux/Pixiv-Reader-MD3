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
 * 数据库结构（version = 3）。
 *
 * 历史迁移（原 v1→v7 六条，含 download_entry 字段演进与主键重构）已全部清理，
 * 新装用户直接按此 schema 建库；旧版本（v7）数据经 `fallbackToDestructiveMigration` 重建。
 * 后续新增实体/字段：升 version 并从这里开始写新迁移。
 *
 * v2：download_entry 新增 payloadJson（完整卡片快照 JSON，下载管理页完整展示用）。
 * v3：download_entry 主键扩为 (targetType, targetId, format, scopeKey)，区分同一小说的
 *     单本/整系列/部分分册下载，修复系列下载顶替单本下载条目的问题。
 */
@Database(
    entities = [
        ReadingProgressEntity::class,
        BrowseHistoryEntity::class,
        DownloadEntryEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 3,
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

        /**
         * v2 → v3：download_entry 主键扩列（+scopeKey）。SQLite 无法 ALTER 主键，
         * 重建表并原样搬迁数据（旧行 scopeKey 统一回填 ''，即单本下载语义）。
         * 列定义须与 [DownloadEntryEntity] 完全一致（Room 启动时校验 schema）。
         */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE download_entry_new (
                        targetId INTEGER NOT NULL,
                        targetType TEXT NOT NULL,
                        title TEXT,
                        coverUrl TEXT,
                        localPath TEXT,
                        status TEXT NOT NULL,
                        progress INTEGER NOT NULL,
                        pageCount INTEGER NOT NULL,
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        seriesId INTEGER,
                        format TEXT NOT NULL,
                        authorName TEXT,
                        authorAvatarUrl TEXT,
                        wordCount INTEGER NOT NULL,
                        favoriteCount INTEGER NOT NULL,
                        publishDate TEXT,
                        seriesTitle TEXT,
                        payloadJson TEXT,
                        scopeKey TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(targetType, targetId, format, scopeKey)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO download_entry_new (
                        targetId, targetType, title, coverUrl, localPath, status, progress,
                        pageCount, width, height, seriesId, format, authorName, authorAvatarUrl,
                        wordCount, favoriteCount, publishDate, seriesTitle, payloadJson,
                        scopeKey, updatedAt
                    )
                    SELECT
                        targetId, targetType, title, coverUrl, localPath, status, progress,
                        pageCount, width, height, seriesId, format, authorName, authorAvatarUrl,
                        wordCount, favoriteCount, publishDate, seriesTitle, payloadJson,
                        '', updatedAt
                    FROM download_entry
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE download_entry")
                db.execSQL("ALTER TABLE download_entry_new RENAME TO download_entry")
            }
        }
    }
}
