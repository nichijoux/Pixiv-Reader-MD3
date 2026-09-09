package com.pixiv.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.dao.FeedSnapshotDao
import com.pixiv.reader.core.database.dao.ReadLaterDao
import com.pixiv.reader.core.database.dao.ReadingProgressDao
import com.pixiv.reader.core.database.dao.SearchHistoryDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.database.entity.FeedSnapshotEntity
import com.pixiv.reader.core.database.entity.ReadLaterEntity
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.database.entity.SearchHistoryEntity

/**
 * 数据库结构（version = 7）。
 *
 * 历史迁移（原 v1→v7 六条，含 download_entry 字段演进与主键重构）已全部清理，
 * 新装用户直接按此 schema 建库；旧版本（v7）数据经 `fallbackToDestructiveMigration` 重建。
 * 后续新增实体/字段：升 version 并从这里开始写新迁移。
 *
 * v2：download_entry 新增 payloadJson（完整卡片快照 JSON，下载管理页完整展示用）。
 * v3：download_entry 主键扩为 (targetType, targetId, format, scopeKey)，区分同一小说的
 *     单本/整系列/部分分册下载，修复系列下载顶替单本下载条目的问题。
 * v4：新增 read_later（稍后再看，本地暂存表；target 唯一索引，payloadJson 快照）。
 * v5：新增 pending_action（离线操作队列；已随 v7 移除）。
 * v6：新增 feed_snapshot（信息流首屏快照，首页秒开）。
 * v7：删除 pending_action（离线操作队列下线，断网操作改为直接报错）。
 */
@Database(
    entities = [
        ReadingProgressEntity::class,
        BrowseHistoryEntity::class,
        DownloadEntryEntity::class,
        SearchHistoryEntity::class,
        ReadLaterEntity::class,
        FeedSnapshotEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class PixivDatabase : RoomDatabase() {
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun browseHistoryDao(): BrowseHistoryDao
    abstract fun downloadEntryDao(): DownloadEntryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun readLaterDao(): ReadLaterDao
    abstract fun feedSnapshotDao(): FeedSnapshotDao

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

        /**
         * v3 → v4：新增 read_later（稍后再看，本地暂存）。
         * 纯新增表零搬数据；列定义须与 [com.pixiv.reader.core.database.entity.ReadLaterEntity]
         * 完全一致（含 autoGenerate 主键与 target 唯一索引，Room 启动时校验 schema）。
         */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE read_later (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        targetType TEXT NOT NULL,
                        targetId INTEGER NOT NULL,
                        title TEXT,
                        coverUrl TEXT,
                        payloadJson TEXT,
                        addedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX index_read_later_targetType_targetId ON read_later (targetType, targetId)",
                )
            }
        }

        /**
         * v4 → v5：新增 pending_action（离线操作队列；已随 v7 移除）。
         * 纯新增表零搬数据；列定义与当时的 PendingActionEntity 一致
         * （含 autoGenerate 主键与 family+targetId 唯一索引）。
         */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE pending_action (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        family TEXT NOT NULL,
                        targetId INTEGER NOT NULL,
                        targetState INTEGER NOT NULL,
                        paramsJson TEXT,
                        payloadJson TEXT,
                        attempts INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX index_pending_action_family_targetId ON pending_action (family, targetId)",
                )
            }
        }

        /**
         * v5 → v6：新增 feed_snapshot（信息流首屏快照，首页秒开）。
         * 纯新增表零搬数据；列定义须与 [com.pixiv.reader.core.database.entity.FeedSnapshotEntity]
         * 完全一致（feedKey 主键，Room 启动时校验 schema）。
         */
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE feed_snapshot (
                        feedKey TEXT NOT NULL PRIMARY KEY,
                        payloadJson TEXT NOT NULL,
                        nextUrl TEXT,
                        savedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /** v6 → v7：删除 pending_action（离线操作队列下线；断网操作改为直接报错，不再暂存）。 */
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS pending_action")
            }
        }
    }
}
