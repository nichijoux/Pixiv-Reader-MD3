package com.pixiv.reader.feature.user.state

import android.content.Context
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.pixiv.api.model.User
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.config.BottomNavBarStyle
import com.pixiv.reader.core.common.config.FollowSortMode
import com.pixiv.reader.core.common.config.NovelDefaultTab
import com.pixiv.reader.core.common.format.NovelFileNameTemplate
import com.pixiv.reader.core.common.format.formatFileSize
import com.pixiv.reader.core.common.config.ThemeMode
import com.pixiv.reader.core.common.config.ViewerOrientation
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.feed.FeedSnapshotStore
import com.pixiv.reader.core.network.fanbox.FanboxRepository
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.session.SessionRepository
import com.pixiv.reader.core.network.update.AppRelease
import com.pixiv.reader.core.network.update.AppUpdateChecker
import com.pixiv.reader.core.network.update.AppUpdateVersion
import com.pixiv.reader.feature.user.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MeViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val sessionRepository: SessionRepository,
    private val userPreferences: UserPreferences,
    private val updateChecker: AppUpdateChecker,
    private val feedSnapshotStore: FeedSnapshotStore,
    private val fanboxRepository: FanboxRepository,
) : MessageViewModel() {

    private val appContext: Context = context.applicationContext

    private val _user = MutableStateFlow<User?>(sessionRepository.currentUser?.user)
    val user: StateFlow<User?> = _user.asStateFlow()

    val ownUid: Long? get() = _user.value?.id

    /**
     * 存在 ≠ 有效：cookie 过期后原生首页按 401 引导重登（FanboxHomeViewModel）。
     */
    fun hasFanboxSession(): Boolean = fanboxRepository.hasSession()

    /**
     * 首次点击（本会话未完成浏览器 SSO）先开系统浏览器走 pixiv 授权：登录完成 302 到 `pixiv://account/login`
     * 唤起本 app，浏览器同时拿到网页登录态，再次点击同一入口才能直达目标页。
     */
    fun openEcosystemPage(targetUrl: String) {
        if (sessionRepository.webSsoCompleted) {
            openBrowser(targetUrl)
            return
        }
        viewModelScope.launch {
            sendMessage(UiMessage(R.string.me_web_sso_hint))
            openBrowser(sessionRepository.webLoginUrl())
        }
    }

    private fun openBrowser(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(intent) }
            .onFailure { e ->
                viewModelScope.launch {
                    sendMessage(UiMessage(R.string.me_web_open_failed, listOf(e.message ?: "")))
                }
            }
    }

    val versionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    private val _updateDialog = MutableStateFlow<AppRelease?>(null)
    val updateDialog: StateFlow<AppRelease?> = _updateDialog.asStateFlow()

    val themeMode: StateFlow<ThemeMode> =
        userPreferences.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.FOLLOW_SYSTEM)

    val dynamicColor: StateFlow<Boolean> =
        userPreferences.dynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val colorStyleEnabled: StateFlow<Boolean> =
        userPreferences.colorStyleEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val bottomNavBarStyle: StateFlow<BottomNavBarStyle> =
        userPreferences.bottomNavBarStyle.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BottomNavBarStyle.DOCKED,
        )

    val appFontScale: StateFlow<Float> =
        userPreferences.appFontScale.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    val appLanguage: StateFlow<String> =
        userPreferences.appLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")

    val autoUpdate: StateFlow<Boolean> =
        userPreferences.autoUpdate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val novelDefaultTab: StateFlow<NovelDefaultTab> =
        userPreferences.novelDefaultTab.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NovelDefaultTab.RECOMMEND)

    val viewerOrientation: StateFlow<ViewerOrientation> =
        userPreferences.viewerOrientation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerOrientation.HORIZONTAL)

    val followSortMode: StateFlow<FollowSortMode> =
        userPreferences.followSortMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FollowSortMode.FOLLOW_TIME)

    val novelExportDir: StateFlow<String> =
        userPreferences.novelExportDir.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** 占位符 {title}/{author}/{id}/{series}，缺失项渲染为空。 */
    val novelFileNameTemplate: StateFlow<String> =
        userPreferences.novelFileNameTemplate.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            NovelFileNameTemplate.DEFAULT_SINGLE,
        )

    val novelFileNameTemplateSeries: StateFlow<String> =
        userPreferences.novelFileNameTemplateSeries.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            NovelFileNameTemplate.DEFAULT_SERIES,
        )

    /** 打开 App / 回前台时检测剪贴板并提示跳转。 */
    val clipboardLinkPrompt: StateFlow<Boolean> =
        userPreferences.clipboardLinkPrompt.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            true,
        )

    private val _cacheSize = MutableStateFlow(context.getString(R.string.me_cache_calculating))
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch { userPreferences.setThemeMode(value) }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { userPreferences.setDynamicColor(value) }
    }

    /** MainActivity 经 CompositionLocal 下发，落盘后全局即时生效。 */
    fun setColorStyleEnabled(value: Boolean) {
        viewModelScope.launch { userPreferences.setColorStyleEnabled(value) }
    }

    fun setBottomNavBarStyle(value: BottomNavBarStyle) {
        viewModelScope.launch { userPreferences.setBottomNavBarStyle(value) }
    }

    fun setAppFontScale(value: Float) {
        viewModelScope.launch { userPreferences.setAppFontScale(value) }
    }

    /** 落盘结束后回调写入是否成功（取消则正常向上传播，不回调）。 */
    fun setAppLanguage(value: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = try {
                userPreferences.setAppLanguage(value)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            onDone(success)
        }
    }

    fun setAutoUpdate(value: Boolean) {
        viewModelScope.launch { userPreferences.setAutoUpdate(value) }
    }

    /** 空串 = 恢复默认应用私有目录。 */
    fun setNovelExportDir(uri: String) {
        viewModelScope.launch { runCatching { userPreferences.setNovelExportDir(uri) } }
    }

    fun resetNovelExportDir() {
        viewModelScope.launch { runCatching { userPreferences.setNovelExportDir("") } }
    }

    fun setNovelFileNameTemplates(single: String, series: String) {
        viewModelScope.launch {
            runCatching {
                userPreferences.setNovelFileNameTemplate(single)
                userPreferences.setNovelFileNameTemplateSeries(series)
            }
        }
    }

    fun resetNovelFileNameTemplates() {
        viewModelScope.launch {
            runCatching {
                userPreferences.setNovelFileNameTemplate(NovelFileNameTemplate.DEFAULT_SINGLE)
                userPreferences.setNovelFileNameTemplateSeries(NovelFileNameTemplate.DEFAULT_SERIES)
            }
        }
    }

    fun setNovelDefaultTab(value: NovelDefaultTab) {
        viewModelScope.launch { userPreferences.setNovelDefaultTab(value) }
    }

    fun setClipboardLinkPrompt(value: Boolean) {
        viewModelScope.launch { runCatching { userPreferences.setClipboardLinkPrompt(value) } }
    }

    fun setViewerOrientation(value: ViewerOrientation) {
        viewModelScope.launch { userPreferences.setViewerOrientation(value) }
    }

    fun setFollowSortMode(value: FollowSortMode) {
        viewModelScope.launch { userPreferences.setFollowSortMode(value) }
    }

    /** 启动自动检查（已最新/失败均静默）与手动点击（三态全反馈）走不同提示分支。 */
    fun checkUpdate(auto: Boolean = false) {
        viewModelScope.launch {
            updateChecker.latestRelease()
                .onSuccess { release ->
                    // 404（仓库尚无发布）与「已最新」同路径提示
                    if (release != null && AppUpdateVersion.isNewer(versionName, release.tagName)) {
                        _updateDialog.value = release
                    } else if (!auto) {
                        sendMessage(UiMessage(R.string.me_already_latest))
                    }
                }
                .onFailure {
                    if (!auto) {
                        sendMessage(UiMessage(R.string.me_update_check_failed, listOf(it.message ?: "")))
                    }
                }
        }
    }

    fun dismissUpdateDialog() {
        _updateDialog.value = null
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                listOf(appContext.cacheDir).sumOf { dir ->
                    if (!dir.exists()) 0L else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                }
            }
            _cacheSize.value = formatSize(size)
        }
    }

    /** 统计口径是整个 cacheDir（含 Coil 图片、ugoira 帧、novel_debug）加信息流首屏快照。 */
    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 先让 Coil 关闭磁盘/内存缓存，再清空整个 cacheDir
                runCatching { appContext.imageLoader.diskCache?.clear() }
                runCatching { appContext.imageLoader.memoryCache?.clear() }
                runCatching { appContext.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
            }
            // 首页秒开用的首屏快照一并清掉，下次启动重新联网拉取
            feedSnapshotStore.clearAll()
            refreshCacheSize()
            sendMessage(UiMessage(R.string.me_cache_cleared))
        }
    }

    private fun formatSize(bytes: Long): String = formatFileSize(bytes).ifEmpty { "0 B" }
}
