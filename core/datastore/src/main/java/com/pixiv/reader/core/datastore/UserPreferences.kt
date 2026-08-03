package com.pixiv.reader.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pixiv_prefs")

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

    // ── 图片 / 外观 ──
    val imageQuality: Flow<String> = context.dataStore.data.map { it[KEY_IMAGE_QUALITY] ?: "medium" }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: true }

    // ── 通用 ──
    val mutedTags: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_MUTED_TAGS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun setReaderFontSize(value: Float) = context.dataStore.edit { it[KEY_FONT_SIZE] = value }
    suspend fun setReaderLineHeight(value: Float) = context.dataStore.edit { it[KEY_LINE_HEIGHT] = value }
    suspend fun setReaderFontFamily(value: String) = context.dataStore.edit { it[KEY_FONT_FAMILY] = value }
    suspend fun setReaderTheme(value: Int) = context.dataStore.edit { it[KEY_READER_THEME] = value }
    suspend fun setReaderPageMode(value: Int) = context.dataStore.edit { it[KEY_PAGE_MODE] = value }
    suspend fun setReaderBrightness(value: Float) = context.dataStore.edit { it[KEY_BRIGHTNESS] = value }
    suspend fun setImageQuality(value: String) = context.dataStore.edit { it[KEY_IMAGE_QUALITY] = value }
    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = value }
    suspend fun setMutedTags(value: List<String>) =
        context.dataStore.edit { it[KEY_MUTED_TAGS] = value.joinToString("\n") }

    private companion object {
        val KEY_FONT_SIZE = floatPreferencesKey("reader_font_size")
        val KEY_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        val KEY_FONT_FAMILY = stringPreferencesKey("reader_font_family")
        val KEY_READER_THEME = intPreferencesKey("reader_theme")
        val KEY_PAGE_MODE = intPreferencesKey("reader_page_mode")
        val KEY_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val KEY_IMAGE_QUALITY = stringPreferencesKey("image_quality")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_MUTED_TAGS = stringPreferencesKey("muted_tags")
    }
}
