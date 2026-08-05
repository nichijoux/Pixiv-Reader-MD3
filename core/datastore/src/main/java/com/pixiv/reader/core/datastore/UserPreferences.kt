package com.pixiv.reader.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pixiv.reader.core.common.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pixiv_prefs")

internal val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")

/**
 * 同步读取应用语言设置（供 MainActivity.attachBaseContext 在 Hilt 装配前调用）。
 *
 * DataStore 文件极小，runBlocking 首次读取为毫秒级；仅在 attachBaseContext 调用一次。
 */
fun readAppLanguageSync(context: Context): String =
    runBlocking { context.dataStore.data.first()[KEY_APP_LANGUAGE] ?: AppLanguage.SYSTEM }

/**
 * 应用偏好（DataStore Preferences）。
 * 阅读偏好由阅读器使用；图片质量/主题由设置页使用。
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ── 阅读器 ──
    val readerFontSize: Flow<Float> = context.dataStore.data.map { it[KEY_FONT_SIZE] ?: 17f }
    val readerLineHeight: Flow<Float> = context.dataStore.data.map { it[KEY_LINE_HEIGHT] ?: 2.05f }
    val readerFontFamily: Flow<String> = context.dataStore.data.map { it[KEY_FONT_FAMILY] ?: "serif" }
    val readerTheme: Flow<Int> = context.dataStore.data.map { it[KEY_READER_THEME] ?: 1 } // 0日间 1纸张 2夜间 3深黑
    val readerPageMode: Flow<Int> = context.dataStore.data.map { it[KEY_PAGE_MODE] ?: 0 } // 0滑动 1翻页 2仿真
    val readerBrightness: Flow<Float> = context.dataStore.data.map { it[KEY_BRIGHTNESS] ?: 1f }
    /** 阅读器主题是否跟随系统深色模式 */
    val readerFollowSystem: Flow<Boolean> = context.dataStore.data.map { it[KEY_FOLLOW_SYSTEM] ?: false }
    /** 自定义阅读字体文件绝对路径（空表示未设置） */
    val readerCustomFontPath: Flow<String> = context.dataStore.data.map { it[KEY_CUSTOM_FONT_PATH] ?: "" }

    // ── 图片 / 外观 ──
    val imageQuality: Flow<String> = context.dataStore.data.map { it[KEY_IMAGE_QUALITY] ?: "medium" }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: true }
    /** 应用主题模式：0 跟随系统 / 1 浅色 / 2 深色 */
    val themeMode: Flow<Int> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: 0 }

    // ── 通用 ──
    /** 应用语言：system 跟随系统 / zh 简体中文 / en 英语 */
    val appLanguage: Flow<String> = context.dataStore.data.map { it[KEY_APP_LANGUAGE] ?: AppLanguage.SYSTEM }
    /** 是否自动更新（设置开关，实际更新逻辑后续接入） */
    val autoUpdate: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_UPDATE] ?: true }
    /** 小说 Tab 默认页：0 推荐 / 1 关注（我的页-浏览设置可改） */
    val novelDefaultTab: Flow<Int> = context.dataStore.data.map { it[KEY_NOVEL_DEFAULT_TAB] ?: 0 }
    /** 热门搜索缓存（展示名列表，\n 分隔；配合 updatedAt 控制刷新） */
    val hotTags: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_HOT_TAGS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }
    /** 热门搜索缓存更新时间戳（epoch ms，0 表示从未缓存） */
    val hotTagsUpdatedAt: Flow<Long> = context.dataStore.data.map { it[KEY_HOT_TAGS_AT] ?: 0L }
    val mutedTags: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_MUTED_TAGS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun setReaderFontSize(value: Float) = context.dataStore.edit { it[KEY_FONT_SIZE] = value }
    suspend fun setReaderLineHeight(value: Float) = context.dataStore.edit { it[KEY_LINE_HEIGHT] = value }
    suspend fun setReaderFontFamily(value: String) = context.dataStore.edit { it[KEY_FONT_FAMILY] = value }
    suspend fun setReaderTheme(value: Int) = context.dataStore.edit { it[KEY_READER_THEME] = value }
    suspend fun setReaderPageMode(value: Int) = context.dataStore.edit { it[KEY_PAGE_MODE] = value }
    suspend fun setReaderBrightness(value: Float) = context.dataStore.edit { it[KEY_BRIGHTNESS] = value }
    suspend fun setReaderFollowSystem(value: Boolean) = context.dataStore.edit { it[KEY_FOLLOW_SYSTEM] = value }
    suspend fun setReaderCustomFontPath(value: String) = context.dataStore.edit { it[KEY_CUSTOM_FONT_PATH] = value }
    suspend fun setImageQuality(value: String) = context.dataStore.edit { it[KEY_IMAGE_QUALITY] = value }
    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = value }
    suspend fun setThemeMode(value: Int) = context.dataStore.edit { it[KEY_THEME_MODE] = value }
    suspend fun setAutoUpdate(value: Boolean) = context.dataStore.edit { it[KEY_AUTO_UPDATE] = value }
    suspend fun setNovelDefaultTab(value: Int) = context.dataStore.edit { it[KEY_NOVEL_DEFAULT_TAB] = value }
    suspend fun setAppLanguage(value: String) = context.dataStore.edit { it[KEY_APP_LANGUAGE] = value }
    suspend fun setHotTags(value: List<String>) =
        context.dataStore.edit { it[KEY_HOT_TAGS] = value.joinToString("\n") }
    suspend fun setHotTagsUpdatedAt(value: Long) = context.dataStore.edit { it[KEY_HOT_TAGS_AT] = value }
    suspend fun setMutedTags(value: List<String>) =
        context.dataStore.edit { it[KEY_MUTED_TAGS] = value.joinToString("\n") }

    private companion object {
        val KEY_FONT_SIZE = floatPreferencesKey("reader_font_size")
        val KEY_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        val KEY_FONT_FAMILY = stringPreferencesKey("reader_font_family")
        val KEY_READER_THEME = intPreferencesKey("reader_theme")
        val KEY_PAGE_MODE = intPreferencesKey("reader_page_mode")
        val KEY_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val KEY_FOLLOW_SYSTEM = booleanPreferencesKey("reader_follow_system")
        val KEY_CUSTOM_FONT_PATH = stringPreferencesKey("reader_custom_font_path")
        val KEY_IMAGE_QUALITY = stringPreferencesKey("image_quality")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        val KEY_AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val KEY_NOVEL_DEFAULT_TAB = intPreferencesKey("novel_default_tab")
        val KEY_HOT_TAGS = stringPreferencesKey("hot_tags")
        val KEY_HOT_TAGS_AT = longPreferencesKey("hot_tags_updated_at")
        val KEY_MUTED_TAGS = stringPreferencesKey("muted_tags")
    }
}
