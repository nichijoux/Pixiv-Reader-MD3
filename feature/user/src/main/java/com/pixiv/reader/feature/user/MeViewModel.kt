package com.pixiv.reader.feature.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.pixiv.api.model.User
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.session.SessionRepository
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
 * 我的页 ViewModel：当前登录用户信息 + 应用版本 + 屏蔽标签管理（本地偏好）+ 外观/语言/系统设置。
 */
@HiltViewModel
class MeViewModel @Inject constructor(
    @ApplicationContext context: Context,
    sessionRepository: SessionRepository,
    private val userPreferences: UserPreferences,
    private val downloadEntryDao: DownloadEntryDao,
) : ViewModel() {

    private val appContext: Context = context.applicationContext

    private val _user = MutableStateFlow<User?>(sessionRepository.currentUser?.user)
    val user: StateFlow<User?> = _user.asStateFlow()

    /** 当前登录用户 UID（进个人主页用）。 */
    val ownUid: Long? get() = _user.value?.id

    val versionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** 屏蔽标签（本地偏好，用于推荐/搜索过滤）。 */
    val mutedTags: StateFlow<List<String>> =
        userPreferences.mutedTags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 主题模式：0 跟随系统 / 1 浅色 / 2 深色。 */
    val themeMode: StateFlow<Int> =
        userPreferences.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 动态取色。 */
    val dynamicColor: StateFlow<Boolean> =
        userPreferences.dynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 应用语言：system 跟随系统 / zh 中文 / en 英文。 */
    val appLanguage: StateFlow<String> =
        userPreferences.appLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")

    /** 自动更新开关。 */
    val autoUpdate: StateFlow<Boolean> =
        userPreferences.autoUpdate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 缓存占用大小估算（离线缓存 + 调试文件 + 图片缓存）。 */
    private val _cacheSize = MutableStateFlow(context.getString(R.string.me_cache_calculating))
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val _message = Channel<UiMessage>(Channel.BUFFERED)
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

    /**
     * 设置应用语言。
     *
     * DataStore 写入为异步；[onDone] 在**落盘完成后**才回调，避免调用方
     * 立即 recreate 时写入协程被取消导致语言未生效（"点了中文却显示英文"）。
     */
    fun setAppLanguage(value: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            userPreferences.setAppLanguage(value)
            onDone()
        }
    }

    fun setAutoUpdate(value: Boolean) {
        viewModelScope.launch { userPreferences.setAutoUpdate(value) }
    }

    /** 检查更新（暂为占位：无发布渠道，提示已是最新）。TODO：接入远程版本检查 */
    fun checkUpdate() {
        viewModelScope.launch { _message.send(UiMessage(R.string.me_already_latest)) }
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
            _message.send(UiMessage(R.string.me_cache_cleared))
        }
    }

    fun addMutedTag(tag: String) {
        val t = tag.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            runCatching {
                if (t !in mutedTags.value) userPreferences.setMutedTags(mutedTags.value + t)
            }
        }
    }

    fun removeMutedTag(tag: String) {
        viewModelScope.launch {
            runCatching { userPreferences.setMutedTags(mutedTags.value - tag) }
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024f / 1024f)
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}
