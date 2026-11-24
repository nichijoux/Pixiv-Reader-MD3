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
 * 迁移链 [MIGRATION_1_2]～[MIGRATION_6_7] 全部保留并在 DatabaseModule 注册；
 * 后续增改表/列须升 version 并接续写 Migration，列定义须与实体逐列一致供 Room 启动校验。
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
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_entry ADD COLUMN payloadJson TEXT")
            }
        }

        /** SQLite 无法 ALTER 主键，重建表搬迁数据并将旧行 scopeKey 统一回填 `''`（单本语义）。 */
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

        /** pending_action 已于 v7 删除，此处保留以保证 v4 旧库迁移链不断裂。 */
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

        /** 离线操作队列下线，移除 v5 引入的 pending_action 表。 */
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS pending_action")
            }
        }
    }
}
