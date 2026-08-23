package com.pixiv.reader.core.database.di

import android.content.Context
import androidx.room.Room
import com.pixiv.reader.core.database.PixivDatabase
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.dao.ReadingProgressDao
import com.pixiv.reader.core.database.dao.SearchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PixivDatabase =
        Room.databaseBuilder(context, PixivDatabase::class.java, "pixiv_reader.db")
            .fallbackToDestructiveMigration(false)
            .addMigrations(PixivDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideReadingProgressDao(db: PixivDatabase): ReadingProgressDao = db.readingProgressDao()

    @Provides
    fun provideBrowseHistoryDao(db: PixivDatabase): BrowseHistoryDao = db.browseHistoryDao()

    @Provides
    fun provideDownloadEntryDao(db: PixivDatabase): DownloadEntryDao = db.downloadEntryDao()

    @Provides
    fun provideSearchHistoryDao(db: PixivDatabase): SearchHistoryDao = db.searchHistoryDao()
}
