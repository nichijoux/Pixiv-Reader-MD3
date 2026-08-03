package com.pixiv.reader.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.datastore.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置 ViewModel：主题模式 / 动态取色 / 自动更新（读写 DataStore）+ 缓存清理 + 关于信息。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val userPreferences: UserPreferences,
    private val downloadEntryDao: DownloadEntryDao,
) : ViewModel() {

    private val appContext: Context = context.applicationContext

    val themeMode: StateFlow<Int> =
        userPreferences.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val dynamicColor: StateFlow<Boolean> =
        userPreferences.dynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val autoUpdate: StateFlow<Boolean> =
        userPreferences.autoUpdate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val versionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** 缓存大小估算（图片缓存 + 离线缓存 + 调试文件）。 */
    private val _cacheSize = MutableStateFlow("计算中…")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val _message = Channel<String>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    init {
        refreshCacheSize()
    }

    fun setThemeMode(value: Int) {
        viewModelScope.launch { userPreferences.setThemeMode(value) }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { userPreferences.setDynamicColor(value) }
    }

    fun setAutoUpdate(value: Boolean) {
        viewModelScope.launch { userPreferences.setAutoUpdate(value) }
    }

    /** 检查更新（暂为占位：无发布渠道，提示已是最新）。TODO：接入远程版本检查 */
    fun checkUpdate() {
        viewModelScope.launch { _message.send("当前已是最新版本") }
    }

    /** 刷新缓存占用大小。 */
    fun refreshCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                listOf(
                    File(appContext.filesDir, "offline"),
                    File(appContext.filesDir, "novel_debug"),
                    appContext.cacheDir,
                ).sumOf { dir ->
                    if (!dir.exists()) 0L else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                }
            }
            _cacheSize.value = formatSize(size)
        }
    }

    /** 清除缓存：离线小说缓存 + 调试文件 + Coil 图片缓存，并删除对应下载索引。 */
    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { File(appContext.filesDir, "offline").deleteRecursively() }
                runCatching { downloadEntryDao.deleteByType("novel_offline") }
                runCatching { File(appContext.filesDir, "novel_debug").deleteRecursively() }
                runCatching { appContext.imageLoader.diskCache?.clear() }
            }
            refreshCacheSize()
            _message.send("已清除缓存")
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024f / 1024f)
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}
