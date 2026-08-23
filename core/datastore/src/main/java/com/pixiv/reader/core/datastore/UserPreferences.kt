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
import com.pixiv.reader.core.common.config.AppLanguage
import com.pixiv.reader.core.common.config.FollowSortMode
import com.pixiv.reader.core.common.config.NovelDefaultTab
import com.pixiv.reader.core.common.config.ReaderPageMode
import com.pixiv.reader.core.common.config.ReaderThemeMode
import com.pixiv.reader.core.common.config.ThemeMode
import com.pixiv.reader.core.common.format.NovelFileNameTemplate
import com.pixiv.reader.core.common.config.ViewerOrientation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pixiv_prefs")

internal val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
internal val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")

/**
 * 同步读取应用语言设置（供 MainActivity.attachBaseContext 在 Hilt 装配前调用）。
 *
 * DataStore 文件极小，runBlocking 首次读取为毫秒级；仅在 attachBaseContext 调用一次。
 */
fun readAppLanguageSync(context: Context): String =
    runBlocking { context.dataStore.data.first()[KEY_APP_LANGUAGE] ?: AppLanguage.SYSTEM }

/**
 * 同步读取引导页完成标记（供 MainActivity 启动时决定导航起点）。
 * 与 [readAppLanguageSync] 同模式：DataStore 文件极小，冷启动一次性读取为毫秒级。
 */
fun readOnboardingCompleteSync(context: Context): Boolean =
    runBlocking { context.dataStore.data.first()[KEY_ONBOARDING_COMPLETE] ?: false }

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
    /** 行距增量（em，-1.0..1.0）：实际行高倍数 = 1.6 + 增量（默认 2.05 倍 → 0.45） */
    val readerLineSpacing: Flow<Float> = context.dataStore.data.map {
        val raw = it[KEY_LINE_HEIGHT] ?: 0.45f
        // 兼容旧版直接倍数（1.4~2.6）：新值恒在 -1..1，旧倍数 >1 转增量
        if (raw > 1f) raw - 1.6f else raw
    }
    val readerFontFamily: Flow<String> = context.dataStore.data.map { it[KEY_FONT_FAMILY] ?: "serif" }
    val readerTheme: Flow<ReaderThemeMode> = context.dataStore.data.map { ReaderThemeMode.from(it[KEY_READER_THEME] ?: ReaderThemeMode.PAPER.value) }
    val readerPageMode: Flow<ReaderPageMode> = context.dataStore.data.map { ReaderPageMode.from(it[KEY_PAGE_MODE] ?: ReaderPageMode.SCROLL.value) }
    val readerBrightness: Flow<Float> = context.dataStore.data.map { it[KEY_BRIGHTNESS] ?: 1f }
    /** 阅读器主题是否跟随系统深色模式 */
    val readerFollowSystem: Flow<Boolean> = context.dataStore.data.map { it[KEY_FOLLOW_SYSTEM] ?: false }
    /** 自定义阅读字体文件绝对路径（空表示未设置） */
    val readerCustomFontPath: Flow<String> = context.dataStore.data.map { it[KEY_CUSTOM_FONT_PATH] ?: "" }
    /** 正文字重：300 细体 / 400 常规 / 700 粗体 / 100..900 自定义可变字重 */
    val readerFontWeight: Flow<Int> = context.dataStore.data.map { it[KEY_FONT_WEIGHT] ?: 400 }
    /** 简繁转换：0 关闭 / 1 简体→繁体 / 2 繁体→简体（仅应用语言为中文时在设置面板显示） */
    val readerChineseConvert: Flow<Int> =
        context.dataStore.data.map { it[KEY_CHINESE_CONVERT] ?: 0 }
    /** 段首全角空格缩进数量（0..4） */
    val readerParagraphIndent: Flow<Int> = context.dataStore.data.map { it[KEY_PARAGRAPH_INDENT] ?: 2 }
    /** 段距（em，0..2.0）：段落间空白高度 = 段距 × 字号 */
    val readerParagraphSpacing: Flow<Float> = context.dataStore.data.map { it[KEY_PARAGRAPH_SPACING] ?: 0.6f }
    /** 字距（em，-0.5..0.5）：字符间距 = 字距 × 字号 */
    val readerLetterSpacing: Flow<Float> = context.dataStore.data.map { it[KEY_LETTER_SPACING] ?: 0f }

    // ── 图片 / 外观 ──
    val imageQuality: Flow<String> = context.dataStore.data.map { it[KEY_IMAGE_QUALITY] ?: "medium" }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: true }
    /** 应用主题模式：跟随系统 / 浅色 / 深色 */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { ThemeMode.from(it[KEY_THEME_MODE] ?: ThemeMode.FOLLOW_SYSTEM.value) }

    // ── 通用 ──
    /** 应用语言：system 跟随系统 / zh 简体中文 / en 英语 */
    val appLanguage: Flow<String> = context.dataStore.data.map { it[KEY_APP_LANGUAGE] ?: AppLanguage.SYSTEM }
    /** 首次启动引导页是否已完成（false = 下次启动先展示引导页） */
    val onboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ONBOARDING_COMPLETE] ?: false }
    /** 剪贴板 pixiv 链接识别提示（打开 App / 回前台时检测并提示跳转） */
    val clipboardLinkPrompt: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_CLIPBOARD_LINK_PROMPT] ?: true }
    /** 是否自动更新（设置开关，实际更新逻辑后续接入） */
    val autoUpdate: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_UPDATE] ?: true }
    /** 小说 Tab 默认页：推荐 / 关注（我的页-浏览设置可改） */
    val novelDefaultTab: Flow<NovelDefaultTab> = context.dataStore.data.map { NovelDefaultTab.from(it[KEY_NOVEL_DEFAULT_TAB] ?: NovelDefaultTab.RECOMMEND.value) }
    /** 插画查看器翻页方向：横向翻页 / 竖向翻页 / 无缝竖向（我的页-浏览设置可改） */
    val viewerOrientation: Flow<ViewerOrientation> = context.dataStore.data.map { ViewerOrientation.from(it[KEY_VIEWER_ORIENTATION] ?: ViewerOrientation.HORIZONTAL.value) }
    /** 关注页左列用户排序：关注时间 / 名称升序 / 名称降序 / 代表作最新（我的页-浏览设置可改） */
    val followSortMode: Flow<FollowSortMode> = context.dataStore.data.map { FollowSortMode.from(it[KEY_FOLLOW_SORT_MODE] ?: FollowSortMode.FOLLOW_TIME.value) }
    /** 热门搜索缓存（展示名列表，\n 分隔；配合 updatedAt 控制刷新） */
    val hotTags: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_HOT_TAGS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }
    /** 热门搜索缓存更新时间戳（epoch ms，0 表示从未缓存） */
    val hotTagsUpdatedAt: Flow<Long> = context.dataStore.data.map { it[KEY_HOT_TAGS_AT] ?: 0L }
    val mutedTags: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_MUTED_TAGS]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    // ── 发现页搜索筛选（记住上次应用的条件，对齐 Pixiv-Shaft 全局默认语义）──
    /** 搜索排序（date_desc / date_asc / popular_desc；默认按热度） */
    val searchFilterSort: Flow<String> =
        context.dataStore.data.map { it[KEY_SEARCH_SORT] ?: "popular_desc" }
    /** 搜索匹配方式（partial_match_for_tags / exact_match_for_tags / title_and_caption） */
    val searchFilterTarget: Flow<String> =
        context.dataStore.data.map { it[KEY_SEARCH_TARGET] ?: "partial_match_for_tags" }
    /** 收藏数下限（官方 bookmark_num_min 参数；0=不限） */
    val searchFilterBookmarkMin: Flow<Int> =
        context.dataStore.data.map { it[KEY_SEARCH_BOOKMARK_MIN] ?: 0 }
    /** Xusers入り 关键字后缀档（0=不限） */
    val searchFilterKeywordUsers: Flow<Int> =
        context.dataStore.data.map { it[KEY_SEARCH_KEYWORD_USERS] ?: 0 }
    /** AI 筛选（0 全部 / 1 仅人绘 / 2 仅 AI） */
    val searchFilterAiType: Flow<Int> =
        context.dataStore.data.map { it[KEY_SEARCH_AI_TYPE] ?: 0 }

    // ── 下载 ──
    /** 小说导出目录（SAF tree uri；空 = 应用私有目录 filesDir/Downloads/novels）。 */
    val novelExportDir: Flow<String> = context.dataStore.data.map { it[KEY_NOVEL_EXPORT_DIR] ?: "" }
    /** 小说导出文件名模板-单本下载（默认 {title}_{id}；缺失占位符渲染为空）。 */
    val novelFileNameTemplate: Flow<String> =
        context.dataStore.data.map { it[KEY_NOVEL_FILE_NAME_TEMPLATE] ?: NovelFileNameTemplate.DEFAULT_SINGLE }

    /** 小说导出文件名模板-系列导出（整系列/部分分册；默认 {series}_{id}）。 */
    val novelFileNameTemplateSeries: Flow<String> =
        context.dataStore.data.map { it[KEY_NOVEL_FILE_NAME_TEMPLATE_SERIES] ?: NovelFileNameTemplate.DEFAULT_SERIES }

    suspend fun setReaderFontSize(value: Float) = context.dataStore.edit { it[KEY_FONT_SIZE] = value }
    suspend fun setReaderLineSpacing(value: Float) = context.dataStore.edit { it[KEY_LINE_HEIGHT] = value }
    suspend fun setReaderFontFamily(value: String) = context.dataStore.edit { it[KEY_FONT_FAMILY] = value }
    suspend fun setReaderTheme(value: ReaderThemeMode) = context.dataStore.edit { it[KEY_READER_THEME] = value.value }
    suspend fun setReaderPageMode(value: ReaderPageMode) = context.dataStore.edit { it[KEY_PAGE_MODE] = value.value }
    suspend fun setReaderBrightness(value: Float) = context.dataStore.edit { it[KEY_BRIGHTNESS] = value }
    suspend fun setReaderFollowSystem(value: Boolean) = context.dataStore.edit { it[KEY_FOLLOW_SYSTEM] = value }
    suspend fun setReaderCustomFontPath(value: String) = context.dataStore.edit { it[KEY_CUSTOM_FONT_PATH] = value }
    suspend fun setReaderFontWeight(value: Int) = context.dataStore.edit { it[KEY_FONT_WEIGHT] = value }
    suspend fun setReaderChineseConvert(value: Int) =
        context.dataStore.edit { it[KEY_CHINESE_CONVERT] = value }
    suspend fun setReaderParagraphIndent(value: Int) = context.dataStore.edit { it[KEY_PARAGRAPH_INDENT] = value }
    suspend fun setReaderParagraphSpacing(value: Float) = context.dataStore.edit { it[KEY_PARAGRAPH_SPACING] = value }
    suspend fun setReaderLetterSpacing(value: Float) = context.dataStore.edit { it[KEY_LETTER_SPACING] = value }
    suspend fun setImageQuality(value: String) = context.dataStore.edit { it[KEY_IMAGE_QUALITY] = value }
    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = value }
    suspend fun setThemeMode(value: ThemeMode) = context.dataStore.edit { it[KEY_THEME_MODE] = value.value }
    suspend fun setAutoUpdate(value: Boolean) = context.dataStore.edit { it[KEY_AUTO_UPDATE] = value }
    suspend fun setNovelDefaultTab(value: NovelDefaultTab) = context.dataStore.edit { it[KEY_NOVEL_DEFAULT_TAB] = value.value }
    suspend fun setViewerOrientation(value: ViewerOrientation) = context.dataStore.edit { it[KEY_VIEWER_ORIENTATION] = value.value }
    suspend fun setFollowSortMode(value: FollowSortMode) = context.dataStore.edit { it[KEY_FOLLOW_SORT_MODE] = value.value }
    suspend fun setAppLanguage(value: String) = context.dataStore.edit { it[KEY_APP_LANGUAGE] = value }
    suspend fun setOnboardingComplete(value: Boolean) =
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = value }
    suspend fun setClipboardLinkPrompt(value: Boolean) =
        context.dataStore.edit { it[KEY_CLIPBOARD_LINK_PROMPT] = value }
    suspend fun setHotTags(value: List<String>) =
        context.dataStore.edit { it[KEY_HOT_TAGS] = value.joinToString("\n") }
    suspend fun setHotTagsUpdatedAt(value: Long) = context.dataStore.edit { it[KEY_HOT_TAGS_AT] = value }
    suspend fun setMutedTags(value: List<String>) =
        context.dataStore.edit { it[KEY_MUTED_TAGS] = value.joinToString("\n") }
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

    private companion object {
        val KEY_FONT_SIZE = floatPreferencesKey("reader_font_size")
        val KEY_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        val KEY_FONT_FAMILY = stringPreferencesKey("reader_font_family")
        val KEY_READER_THEME = intPreferencesKey("reader_theme")
        val KEY_PAGE_MODE = intPreferencesKey("reader_page_mode")
        val KEY_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val KEY_FOLLOW_SYSTEM = booleanPreferencesKey("reader_follow_system")
        val KEY_CUSTOM_FONT_PATH = stringPreferencesKey("reader_custom_font_path")
        val KEY_FONT_WEIGHT = intPreferencesKey("reader_font_weight")
        val KEY_CHINESE_CONVERT = intPreferencesKey("reader_chinese_convert")
        val KEY_PARAGRAPH_INDENT = intPreferencesKey("reader_paragraph_indent")
        val KEY_PARAGRAPH_SPACING = floatPreferencesKey("reader_paragraph_spacing")
        val KEY_LETTER_SPACING = floatPreferencesKey("reader_letter_spacing")
        val KEY_IMAGE_QUALITY = stringPreferencesKey("image_quality")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        val KEY_AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val KEY_NOVEL_DEFAULT_TAB = intPreferencesKey("novel_default_tab")
        val KEY_VIEWER_ORIENTATION = intPreferencesKey("viewer_orientation")
        val KEY_FOLLOW_SORT_MODE = intPreferencesKey("follow_sort_mode")
        val KEY_HOT_TAGS = stringPreferencesKey("hot_tags")
        val KEY_HOT_TAGS_AT = longPreferencesKey("hot_tags_updated_at")
        val KEY_MUTED_TAGS = stringPreferencesKey("muted_tags")
        val KEY_NOVEL_EXPORT_DIR = stringPreferencesKey("novel_export_dir")
        val KEY_NOVEL_FILE_NAME_TEMPLATE = stringPreferencesKey("novel_file_name_template")
        val KEY_NOVEL_FILE_NAME_TEMPLATE_SERIES = stringPreferencesKey("novel_file_name_template_series")
        val KEY_CLIPBOARD_LINK_PROMPT = booleanPreferencesKey("clipboard_link_prompt")
        val KEY_SEARCH_SORT = stringPreferencesKey("search_filter_sort")
        val KEY_SEARCH_TARGET = stringPreferencesKey("search_filter_target")
        val KEY_SEARCH_BOOKMARK_MIN = intPreferencesKey("search_filter_bookmark_min")
        val KEY_SEARCH_KEYWORD_USERS = intPreferencesKey("search_filter_keyword_users")
        val KEY_SEARCH_AI_TYPE = intPreferencesKey("search_filter_ai_type")
    }
}
