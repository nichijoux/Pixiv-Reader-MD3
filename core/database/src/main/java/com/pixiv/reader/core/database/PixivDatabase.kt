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

@Database(
    entities = [
        ReadingProgressEntity::class,
        BrowseHistoryEntity::class,
        DownloadEntryEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class PixivDatabase : RoomDatabase() {
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun browseHistoryDao(): BrowseHistoryDao
    abstract fun downloadEntryDao(): DownloadEntryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
