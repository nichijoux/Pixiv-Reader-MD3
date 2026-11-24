package com.pixiv.reader.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pixiv.reader.core.common.config.AppLanguage
import com.pixiv.reader.core.common.config.BottomNavBarStyle
import com.pixiv.reader.core.common.config.FollowSortMode
import com.pixiv.reader.core.common.config.NovelDefaultTab
import com.pixiv.reader.core.common.config.ReaderDualPageMode
import com.pixiv.reader.core.common.config.ReaderPageMode
import com.pixiv.reader.core.common.config.ReaderThemeMode
import com.pixiv.reader.core.common.config.ThemeMode
import com.pixiv.reader.core.common.format.NovelFileNameTemplate
import com.pixiv.reader.core.common.config.ViewerOrientation
import com.tencent.mmkv.MMKV
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

// 启动读与运行期监听共用同一个实例；文件损坏时统一恢复为默认偏好。
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pixiv_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

internal val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
internal val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
private val KEY_COLOR_STYLE = booleanPreferencesKey("color_style_enabled")
private val KEY_BOTTOM_NAV_BAR_STYLE = intPreferencesKey("bottom_nav_bar_style")
private val KEY_THEME_MODE = intPreferencesKey("theme_mode")
private val KEY_APP_FONT_SCALE = floatPreferencesKey("app_font_scale")

/** 冷启动首帧同步偏好快照：避免首帧先渲染默认主题/底栏再异步闪切。 */
data class StartupPrefs(
    val appLanguage: String = AppLanguage.SYSTEM,
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val dynamicColor: Boolean = true,
    val colorStyleEnabled: Boolean = false,
    val bottomNavBarStyle: BottomNavBarStyle = BottomNavBarStyle.DOCKED,
    val appFontScale: Float = 1f,
)

private const val STARTUP_MMKV_ID = "pixiv_startup_prefs"
private const val MK_MIGRATED = "startup_migrated_v1"
private const val MK_APP_LANGUAGE = "app_language"
private const val MK_ONBOARDING_COMPLETE = "onboarding_complete"
private const val MK_THEME_MODE = "theme_mode"
private const val MK_DYNAMIC_COLOR = "dynamic_color"
private const val MK_COLOR_STYLE = "color_style_enabled"
private const val MK_BOTTOM_NAV_BAR_STYLE = "bottom_nav_bar_style"
private const val MK_APP_FONT_SCALE = "app_font_scale"

/**
 * 冷启动镜像缓存（MMKV mmap 微秒级同步读）：仅在未迁移时回退一次 DataStore 同步读并落盘镜像，
 * 后续冷启动主线程零 runBlocking 阻塞；无原生库环境（JVM 单测）自动安全降级。
 */
private fun openStartupMmkv(context: Context): MMKV? = runCatching {
    if (MMKV.getRootDir().isNullOrEmpty()) {
        MMKV.initialize(context.applicationContext ?: context)
    }
    MMKV.mmkvWithID(STARTUP_MMKV_ID)
}.getOrNull()

private fun writeStartupSnapshotToMmkv(kv: MMKV, snapshot: StartupPrefs) {
    kv.encode(MK_APP_LANGUAGE, snapshot.appLanguage)
    kv.encode(MK_ONBOARDING_COMPLETE, snapshot.onboardingComplete)
    kv.encode(MK_THEME_MODE, snapshot.themeMode.value)
    kv.encode(MK_DYNAMIC_COLOR, snapshot.dynamicColor)
    kv.encode(MK_COLOR_STYLE, snapshot.colorStyleEnabled)
    kv.encode(MK_BOTTOM_NAV_BAR_STYLE, snapshot.bottomNavBarStyle.value)
    kv.encode(MK_APP_FONT_SCALE, snapshot.appFontScale)
    kv.encode(MK_MIGRATED, true)
}

/** 供 MainActivity 首帧一次性同步读取启动偏好（命中 MMKV 时零阻塞）。 */
fun readStartupPrefsSync(context: Context): StartupPrefs {
    val kv = openStartupMmkv(context)
    if (kv != null && kv.decodeBool(MK_MIGRATED, false)) {
        return StartupPrefs(
            appLanguage = kv.decodeString(MK_APP_LANGUAGE, AppLanguage.SYSTEM) ?: AppLanguage.SYSTEM,
            onboardingComplete = kv.decodeBool(MK_ONBOARDING_COMPLETE, false),
            themeMode = ThemeMode.from(kv.decodeInt(MK_THEME_MODE, ThemeMode.FOLLOW_SYSTEM.value)),
            dynamicColor = kv.decodeBool(MK_DYNAMIC_COLOR, true),
            colorStyleEnabled = kv.decodeBool(MK_COLOR_STYLE, false),
            bottomNavBarStyle = BottomNavBarStyle.from(
                kv.decodeInt(MK_BOTTOM_NAV_BAR_STYLE, BottomNavBarStyle.DOCKED.value),
            ),
            appFontScale = kv.decodeFloat(MK_APP_FONT_SCALE, 1f),
        )
    }
    val prefs = runBlocking { context.dataStore.data.first() }
    val snapshot = StartupPrefs(
        appLanguage = prefs[KEY_APP_LANGUAGE] ?: AppLanguage.SYSTEM,
        onboardingComplete = prefs[KEY_ONBOARDING_COMPLETE] ?: false,
        themeMode = ThemeMode.from(prefs[KEY_THEME_MODE] ?: ThemeMode.FOLLOW_SYSTEM.value),
        dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
        colorStyleEnabled = prefs[KEY_COLOR_STYLE] ?: false,
        bottomNavBarStyle = BottomNavBarStyle.from(
            prefs[KEY_BOTTOM_NAV_BAR_STYLE] ?: BottomNavBarStyle.DOCKED.value,
        ),
        appFontScale = prefs[KEY_APP_FONT_SCALE] ?: 1f,
    )
    if (kv != null) {
        writeStartupSnapshotToMmkv(kv, snapshot)
    }
    return snapshot
}

/** 供 MainActivity.attachBaseContext 在 Hilt 装配前调用；优先命中 MMKV 镜像，零磁盘阻塞。 */
fun readAppLanguageSync(context: Context): String {
    val kv = openStartupMmkv(context)
    if (kv != null && kv.decodeBool(MK_MIGRATED, false)) {
        return kv.decodeString(MK_APP_LANGUAGE, AppLanguage.SYSTEM) ?: AppLanguage.SYSTEM
    }
    return readStartupPrefsSync(context).appLanguage
}

/** 供 MainActivity 冷启动决定导航起点，同 [readStartupPrefsSync] 走 MMKV 镜像。 */
fun readOnboardingCompleteSync(context: Context): Boolean =
    readStartupPrefsSync(context).onboardingComplete

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val readerFontSize: Flow<Float> = context.dataStore.data.map { it[KEY_FONT_SIZE] ?: 17f }
    /** 行距增量（em，-1.0..1.0），实际行高倍数 = 1.6 + 增量。 */
    val readerLineSpacing: Flow<Float> = context.dataStore.data.map {
        val raw = it[KEY_LINE_HEIGHT] ?: 0.45f
        // 兼容旧版直接存倍数（1.4~2.6）：>1 视为旧值转增量
        if (raw > 1f) raw - 1.6f else raw
    }
    val readerFontFamily: Flow<String> = context.dataStore.data.map { it[KEY_FONT_FAMILY] ?: "serif" }
    val readerTheme: Flow<ReaderThemeMode> = context.dataStore.data.map { ReaderThemeMode.from(it[KEY_READER_THEME] ?: ReaderThemeMode.PAPER.value) }
    val readerPageMode: Flow<ReaderPageMode> = context.dataStore.data.map { ReaderPageMode.from(it[KEY_PAGE_MODE] ?: ReaderPageMode.SCROLL.value) }
    val readerDualPageMode: Flow<ReaderDualPageMode> =
        context.dataStore.data.map {
            ReaderDualPageMode.from(it[KEY_DUAL_PAGE] ?: ReaderDualPageMode.LANDSCAPE_OR_TABLET.value)
        }
    val readerBrightness: Flow<Float> = context.dataStore.data.map { it[KEY_BRIGHTNESS] ?: 1f }
    val readerFollowSystem: Flow<Boolean> = context.dataStore.data.map { it[KEY_FOLLOW_SYSTEM] ?: false }
    val readerCustomFontPath: Flow<String> = context.dataStore.data.map { it[KEY_CUSTOM_FONT_PATH] ?: "" }
    val readerFontWeight: Flow<Int> = context.dataStore.data.map { it[KEY_FONT_WEIGHT] ?: 400 }
    /** 0 关闭 / 1 简→繁 / 2 繁→简（仅中文界面展示）。 */
    val readerChineseConvert: Flow<Int> =
        context.dataStore.data.map { it[KEY_CHINESE_CONVERT] ?: 0 }
    /** 段首全角空格缩进数（0..4）。 */
    val readerParagraphIndent: Flow<Int> = context.dataStore.data.map { it[KEY_PARAGRAPH_INDENT] ?: 2 }
    /** 段距（em，0..2.0），实际空白高度 = 值 × 字号。 */
    val readerParagraphSpacing: Flow<Float> = context.dataStore.data.map { it[KEY_PARAGRAPH_SPACING] ?: 0.6f }
    /** 字距（em，-0.5..0.5），实际间距 = 值 × 字号。 */
    val readerLetterSpacing: Flow<Float> = context.dataStore.data.map { it[KEY_LETTER_SPACING] ?: 0f }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: true }
    val colorStyleEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_COLOR_STYLE] ?: false }
    val bottomNavBarStyle: Flow<BottomNavBarStyle> =
        context.dataStore.data.map {
            BottomNavBarStyle.from(it[KEY_BOTTOM_NAV_BAR_STYLE] ?: BottomNavBarStyle.DOCKED.value)
        }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { ThemeMode.from(it[KEY_THEME_MODE] ?: ThemeMode.FOLLOW_SYSTEM.value) }
    val appFontScale: Flow<Float> = context.dataStore.data.map { it[KEY_APP_FONT_SCALE] ?: 1f }

    val appLanguage: Flow<String> = context.dataStore.data.map { it[KEY_APP_LANGUAGE] ?: AppLanguage.SYSTEM }
    val onboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ONBOARDING_COMPLETE] ?: false }
    val clipboardLinkPrompt: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_CLIPBOARD_LINK_PROMPT] ?: true }
    val autoUpdate: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_UPDATE] ?: true }
    val novelDefaultTab: Flow<NovelDefaultTab> = context.dataStore.data.map { NovelDefaultTab.from(it[KEY_NOVEL_DEFAULT_TAB] ?: NovelDefaultTab.RECOMMEND.value) }
    val viewerOrientation: Flow<ViewerOrientation> = context.dataStore.data.map { ViewerOrientation.from(it[KEY_VIEWER_ORIENTATION] ?: ViewerOrientation.HORIZONTAL.value) }
    val followSortMode: Flow<FollowSortMode> = context.dataStore.data.map { FollowSortMode.from(it[KEY_FOLLOW_SORT_MODE] ?: FollowSortMode.FOLLOW_TIME.value) }
    val hotTags: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_HOT_TAGS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }
    val hotTagsUpdatedAt: Flow<Long> = context.dataStore.data.map { it[KEY_HOT_TAGS_AT] ?: 0L }
    val mutedTags: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_MUTED_TAGS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    /** 纯本地就地屏蔽集合，条目格式 `illust:123` / `novel:456`，不上报服务端。 */
    val blockedTargets: Flow<Set<String>> =
        context.dataStore.data.map { prefs -> prefs[KEY_BLOCKED_TARGETS] ?: emptySet() }

    val searchFilterSort: Flow<String> =
        context.dataStore.data.map { it[KEY_SEARCH_SORT] ?: "popular_desc" }
    val searchFilterTarget: Flow<String> =
        context.dataStore.data.map { it[KEY_SEARCH_TARGET] ?: "partial_match_for_tags" }
    val searchFilterBookmarkMin: Flow<Int> =
        context.dataStore.data.map { it[KEY_SEARCH_BOOKMARK_MIN] ?: 0 }
    /** `Xusers入り` 关键字后缀档（0 = 不限）。 */
    val searchFilterKeywordUsers: Flow<Int> =
        context.dataStore.data.map { it[KEY_SEARCH_KEYWORD_USERS] ?: 0 }
    /** 0 全部 / 1 仅人绘 / 2 仅 AI。 */
    val searchFilterAiType: Flow<Int> =
        context.dataStore.data.map { it[KEY_SEARCH_AI_TYPE] ?: 0 }

    /** SAF tree URI；空串表示回退应用私有目录 `filesDir/Downloads/novels`。 */
    val novelExportDir: Flow<String> = context.dataStore.data.map { it[KEY_NOVEL_EXPORT_DIR] ?: "" }
    val novelFileNameTemplate: Flow<String> =
        context.dataStore.data.map { it[KEY_NOVEL_FILE_NAME_TEMPLATE] ?: NovelFileNameTemplate.DEFAULT_SINGLE }

    val novelFileNameTemplateSeries: Flow<String> =
        context.dataStore.data.map { it[KEY_NOVEL_FILE_NAME_TEMPLATE_SERIES] ?: NovelFileNameTemplate.DEFAULT_SERIES }

    val notificationPermissionPrompted: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_NOTIFICATION_PERMISSION_PROMPTED] ?: false }

    suspend fun setReaderFontSize(value: Float) = context.dataStore.edit { it[KEY_FONT_SIZE] = value }
    suspend fun setReaderLineSpacing(value: Float) = context.dataStore.edit { it[KEY_LINE_HEIGHT] = value }
    suspend fun setReaderFontFamily(value: String) = context.dataStore.edit { it[KEY_FONT_FAMILY] = value }
    suspend fun setReaderTheme(value: ReaderThemeMode) = context.dataStore.edit { it[KEY_READER_THEME] = value.value }
    suspend fun setReaderPageMode(value: ReaderPageMode) = context.dataStore.edit { it[KEY_PAGE_MODE] = value.value }
    suspend fun setReaderDualPageMode(value: ReaderDualPageMode) =
        context.dataStore.edit { it[KEY_DUAL_PAGE] = value.value }
    suspend fun setReaderBrightness(value: Float) = context.dataStore.edit { it[KEY_BRIGHTNESS] = value }
    suspend fun setReaderFollowSystem(value: Boolean) = context.dataStore.edit { it[KEY_FOLLOW_SYSTEM] = value }
    suspend fun setReaderCustomFontPath(value: String) = context.dataStore.edit { it[KEY_CUSTOM_FONT_PATH] = value }
    suspend fun setReaderFontWeight(value: Int) = context.dataStore.edit { it[KEY_FONT_WEIGHT] = value }
    suspend fun setReaderChineseConvert(value: Int) =
        context.dataStore.edit { it[KEY_CHINESE_CONVERT] = value }
    suspend fun setReaderParagraphIndent(value: Int) = context.dataStore.edit { it[KEY_PARAGRAPH_INDENT] = value }
    suspend fun setReaderParagraphSpacing(value: Float) = context.dataStore.edit { it[KEY_PARAGRAPH_SPACING] = value }
    suspend fun setReaderLetterSpacing(value: Float) = context.dataStore.edit { it[KEY_LETTER_SPACING] = value }
    suspend fun setDynamicColor(value: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = value }
        openStartupMmkv(context)?.encode(MK_DYNAMIC_COLOR, value)
    }
    suspend fun setColorStyleEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_COLOR_STYLE] = value }
        openStartupMmkv(context)?.encode(MK_COLOR_STYLE, value)
    }
    suspend fun setBottomNavBarStyle(value: BottomNavBarStyle) {
        context.dataStore.edit { it[KEY_BOTTOM_NAV_BAR_STYLE] = value.value }
        openStartupMmkv(context)?.encode(MK_BOTTOM_NAV_BAR_STYLE, value.value)
    }
    suspend fun setThemeMode(value: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = value.value }
        openStartupMmkv(context)?.encode(MK_THEME_MODE, value.value)
    }
    suspend fun setAppFontScale(value: Float) {
        context.dataStore.edit { it[KEY_APP_FONT_SCALE] = value }
        openStartupMmkv(context)?.encode(MK_APP_FONT_SCALE, value)
    }
    suspend fun setAutoUpdate(value: Boolean) = context.dataStore.edit { it[KEY_AUTO_UPDATE] = value }
    suspend fun setNovelDefaultTab(value: NovelDefaultTab) = context.dataStore.edit { it[KEY_NOVEL_DEFAULT_TAB] = value.value }
    suspend fun setViewerOrientation(value: ViewerOrientation) = context.dataStore.edit { it[KEY_VIEWER_ORIENTATION] = value.value }
    suspend fun setFollowSortMode(value: FollowSortMode) = context.dataStore.edit { it[KEY_FOLLOW_SORT_MODE] = value.value }
    suspend fun setAppLanguage(value: String) {
        context.dataStore.edit { it[KEY_APP_LANGUAGE] = value }
        openStartupMmkv(context)?.encode(MK_APP_LANGUAGE, value)
    }
    suspend fun setOnboardingComplete(value: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = value }
        openStartupMmkv(context)?.encode(MK_ONBOARDING_COMPLETE, value)
    }
    suspend fun setClipboardLinkPrompt(value: Boolean) =
        context.dataStore.edit { it[KEY_CLIPBOARD_LINK_PROMPT] = value }
    suspend fun setHotTags(value: List<String>) =
        context.dataStore.edit { it[KEY_HOT_TAGS] = value.joinToString("\n") }
    suspend fun setHotTagsUpdatedAt(value: Long) = context.dataStore.edit { it[KEY_HOT_TAGS_AT] = value }
    suspend fun setMutedTags(value: List<String>) =
        context.dataStore.edit { it[KEY_MUTED_TAGS] = value.joinToString("\n") }

    suspend fun addMutedTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_MUTED_TAGS]
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            if (trimmed !in current) {
                prefs[KEY_MUTED_TAGS] = (current + trimmed).joinToString("\n")
            }
        }
    }

    suspend fun removeMutedTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { prefs ->
            val updated = prefs[KEY_MUTED_TAGS]
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() && it != trimmed }
                .orEmpty()
            prefs[KEY_MUTED_TAGS] = updated.joinToString("\n")
        }
    }

    suspend fun addBlockedTarget(type: String, id: Long) =
        context.dataStore.edit { prefs ->
            prefs[KEY_BLOCKED_TARGETS] = (prefs[KEY_BLOCKED_TARGETS] ?: emptySet()) + "$type:$id"
        }

    suspend fun removeBlockedTarget(type: String, id: Long) =
        context.dataStore.edit { prefs ->
            prefs[KEY_BLOCKED_TARGETS] = (prefs[KEY_BLOCKED_TARGETS] ?: emptySet()) - "$type:$id"
        }

    suspend fun setSearchFilterSort(value: String) =
        context.dataStore.edit { it[KEY_SEARCH_SORT] = value }
    suspend fun setSearchFilterTarget(value: String) =
        context.dataStore.edit { it[KEY_SEARCH_TARGET] = value }
    suspend fun setSearchFilterBookmarkMin(value: Int) =
        context.dataStore.edit { it[KEY_SEARCH_BOOKMARK_MIN] = value }
    suspend fun setSearchFilterKeywordUsers(value: Int) =
        context.dataStore.edit { it[KEY_SEARCH_KEYWORD_USERS] = value }
    suspend fun setSearchFilterAiType(value: Int) =
        context.dataStore.edit { it[KEY_SEARCH_AI_TYPE] = value }
    suspend fun setNovelExportDir(value: String) = context.dataStore.edit { it[KEY_NOVEL_EXPORT_DIR] = value }
    suspend fun setNovelFileNameTemplate(value: String) =
        context.dataStore.edit { it[KEY_NOVEL_FILE_NAME_TEMPLATE] = value }

    suspend fun setNovelFileNameTemplateSeries(value: String) =
        context.dataStore.edit { it[KEY_NOVEL_FILE_NAME_TEMPLATE_SERIES] = value }

    suspend fun setNotificationPermissionPrompted(value: Boolean) =
        context.dataStore.edit { it[KEY_NOTIFICATION_PERMISSION_PROMPTED] = value }

    private companion object {
        val KEY_FONT_SIZE = floatPreferencesKey("reader_font_size")
        val KEY_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        val KEY_FONT_FAMILY = stringPreferencesKey("reader_font_family")
        val KEY_READER_THEME = intPreferencesKey("reader_theme")
        val KEY_PAGE_MODE = intPreferencesKey("reader_page_mode")
        val KEY_DUAL_PAGE = intPreferencesKey("reader_dual_page_mode")
        val KEY_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val KEY_FOLLOW_SYSTEM = booleanPreferencesKey("reader_follow_system")
        val KEY_CUSTOM_FONT_PATH = stringPreferencesKey("reader_custom_font_path")
        val KEY_FONT_WEIGHT = intPreferencesKey("reader_font_weight")
        val KEY_CHINESE_CONVERT = intPreferencesKey("reader_chinese_convert")
        val KEY_PARAGRAPH_INDENT = intPreferencesKey("reader_paragraph_indent")
        val KEY_PARAGRAPH_SPACING = floatPreferencesKey("reader_paragraph_spacing")
        val KEY_LETTER_SPACING = floatPreferencesKey("reader_letter_spacing")
        val KEY_AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val KEY_NOVEL_DEFAULT_TAB = intPreferencesKey("novel_default_tab")
        val KEY_VIEWER_ORIENTATION = intPreferencesKey("viewer_orientation")
        val KEY_FOLLOW_SORT_MODE = intPreferencesKey("follow_sort_mode")
        val KEY_HOT_TAGS = stringPreferencesKey("hot_tags")
        val KEY_HOT_TAGS_AT = longPreferencesKey("hot_tags_updated_at")
        val KEY_MUTED_TAGS = stringPreferencesKey("muted_tags")
        val KEY_BLOCKED_TARGETS = stringSetPreferencesKey("blocked_targets")
        val KEY_NOVEL_EXPORT_DIR = stringPreferencesKey("novel_export_dir")
        val KEY_NOVEL_FILE_NAME_TEMPLATE = stringPreferencesKey("novel_file_name_template")
        val KEY_NOVEL_FILE_NAME_TEMPLATE_SERIES = stringPreferencesKey("novel_file_name_template_series")
        val KEY_NOTIFICATION_PERMISSION_PROMPTED = booleanPreferencesKey("notification_permission_prompted")
        val KEY_CLIPBOARD_LINK_PROMPT = booleanPreferencesKey("clipboard_link_prompt")
        val KEY_SEARCH_SORT = stringPreferencesKey("search_filter_sort")
        val KEY_SEARCH_TARGET = stringPreferencesKey("search_filter_target")
        val KEY_SEARCH_BOOKMARK_MIN = intPreferencesKey("search_filter_bookmark_min")
        val KEY_SEARCH_KEYWORD_USERS = intPreferencesKey("search_filter_keyword_users")
        val KEY_SEARCH_AI_TYPE = intPreferencesKey("search_filter_ai_type")
    }
}
