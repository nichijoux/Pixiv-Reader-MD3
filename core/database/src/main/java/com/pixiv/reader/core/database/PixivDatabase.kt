package com.pixiv.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.dao.ReadingProgressDao
import com.pixiv.reader.core.database.dao.SearchHistoryDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.database.entity.SearchHistoryEntity

/** v1→v2：新增 search_history 表（保留既有数据）。 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `search_history` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`keyword` TEXT NOT NULL, " +
                "`searchedAt` INTEGER NOT NULL)",
        )
    }
}

/** v2→v3：download_entry 新增 width/height 列（插画真实宽高）。 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `download_entry` ADD COLUMN `width` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `download_entry` ADD COLUMN `height` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v3→v4：download_entry 新增 progress 列（下载进度 0-100，默认 0）。 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `download_entry` ADD COLUMN `progress` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v4→v5：download_entry 新增 seriesId/format 列（小说导出重试重建任务用）。 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `download_entry` ADD COLUMN `seriesId` INTEGER")
        db.execSQL("ALTER TABLE `download_entry` ADD COLUMN `format` TEXT")
    }
}

/**
 * v5→v6：download_entry 主键改为 (targetType, targetId, format)，
 * 支持同一目标多种导出格式并存；同时剔除已移除的 novel_offline 离线索引。
 * SQLite 无法直接改主键，采用 建新表 + 拷贝 + 换名。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `download_entry_new` (" +
                "`targetId` INTEGER NOT NULL, " +
                "`targetType` TEXT NOT NULL, " +
                "`title` TEXT, " +
                "`coverUrl` TEXT, " +
                "`localPath` TEXT, " +
                "`status` TEXT NOT NULL, " +
                "`progress` INTEGER NOT NULL, " +
                "`pageCount` INTEGER NOT NULL, " +
                "`width` INTEGER NOT NULL, " +
                "`height` INTEGER NOT NULL, " +
                "`seriesId` INTEGER, " +
                "`format` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`targetType`, `targetId`, `format`))",
        )
        db.execSQL(
            "INSERT INTO `download_entry_new` (" +
                "`targetId`, `targetType`, `title`, `coverUrl`, `localPath`, `status`, " +
                "`progress`, `pageCount`, `width`, `height`, `seriesId`, `format`, `updatedAt`) " +
                "SELECT `targetId`, `targetType`, `title`, `coverUrl`, `localPath`, `status`, " +
                "`progress`, `pageCount`, `width`, `height`, `seriesId`, COALESCE(`format`, ''), `updatedAt` " +
                "FROM `download_entry` WHERE `targetType` != 'novel_offline'",
        )
        db.execSQL("DROP TABLE `download_entry`")
        db.execSQL("ALTER TABLE `download_entry_new` RENAME TO `download_entry`")
    }
}

@Database(
    entities = [
        ReadingProgressEntity::class,
        BrowseHistoryEntity::class,
        DownloadEntryEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class PixivDatabase : RoomDatabase() {
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun browseHistoryDao(): BrowseHistoryDao
    abstract fun downloadEntryDao(): DownloadEntryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
