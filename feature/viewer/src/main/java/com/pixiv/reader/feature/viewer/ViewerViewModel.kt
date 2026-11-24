package com.pixiv.reader.feature.viewer

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Illust
import com.pixiv.api.model.WebIllustPage
import com.pixiv.reader.core.common.ToggleUiState
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.common.config.ViewerOrientation
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.model.IllustPageInfo
import com.pixiv.reader.core.network.model.bestCoverUrl
import com.pixiv.reader.core.network.model.snapshotPayload
import com.pixiv.reader.core.network.model.toPages
import com.pixiv.reader.core.network.download.DownloadQueue
import com.pixiv.reader.core.network.download.UgoiraExportFormat
import com.pixiv.reader.core.network.download.UgoiraExportWorker
import com.pixiv.reader.core.network.illust.IllustDownloadWorker
import com.pixiv.reader.core.network.favorite.BookmarkEditor
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.ugoira.UgoiraFrame
import com.pixiv.reader.core.network.ugoira.UgoiraLoader
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 全屏查看器：多图横滑 / 动图 / 预览·原图 / 壁纸 / 收藏 / 当前页与动图导出下载。
 * illustId 与初始 page 来自 SavedStateHandle（与路由参数名一一对应）。
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val downloadEntryDao: DownloadEntryDao,
    private val ugoiraLoader: UgoiraLoader,
    private val userPreferences: UserPreferences,
    private val favoriteActions: FavoriteActions,
) : MessageViewModel() {

    private val illustId: Long = savedStateHandle.get<Long>("illustId") ?: 0L
    val initialPage: Int = savedStateHandle.get<Int>("page") ?: 0

    private val _illust = MutableStateFlow<Illust?>(null)
    val illust: StateFlow<Illust?> = _illust.asStateFlow()

    private val _pages = MutableStateFlow<List<IllustPageInfo>>(emptyList())
    val pages: StateFlow<List<IllustPageInfo>> = _pages.asStateFlow()

    private val _isGif = MutableStateFlow(false)
    val isGif: StateFlow<Boolean> = _isGif.asStateFlow()

    private val _ugoiraFrames = MutableStateFlow<List<UgoiraFrame>>(emptyList())
    val ugoiraFrames: StateFlow<List<UgoiraFrame>> = _ugoiraFrames.asStateFlow()

    /** 由详情 is_bookmarked 初始化，[toggleBookmark] 驱动转移。 */
    private val _bookmarkState = MutableStateFlow(ToggleUiState.OFF)
    val bookmarkState: StateFlow<ToggleUiState> = _bookmarkState.asStateFlow()

    /** 弹层确认按钮共用此标志禁用（防连点）；一键收藏走 [_bookmarkState] 独立防连点。 */
    private val _isEditorSaving = MutableStateFlow(false)
    val isEditorSaving: StateFlow<Boolean> = _isEditorSaving.asStateFlow()

    private val _isOriginal = MutableStateFlow(false)
    val isOriginal: StateFlow<Boolean> = _isOriginal.asStateFlow()

    /** 与「我的」页浏览设置共用同一偏好。 */
    val viewerOrientation: StateFlow<ViewerOrientation> =
        userPreferences.viewerOrientation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerOrientation.HORIZONTAL)

    fun setViewerOrientation(value: ViewerOrientation) {
        viewModelScope.launch { userPreferences.setViewerOrientation(value) }
    }

    val bookmarkEditor = BookmarkEditor(
        scope = viewModelScope,
        api = pixivRepository.api,
        targetType = "illust",
        targetId = { illustId },
        uid = { pixivRepository.pixivApi.session.loggedInUid },
    )

    private var loadJob: Job? = null

    init {
        // 同步跨页收藏覆写流，防止从详情或列表变更后查看器心形图标过时。
        viewModelScope.launch {
            favoriteActions.illustBookmarks
                .map { it[illustId] }
                .distinctUntilChanged()
                .collect { bookmarked ->
                    if (illustId <= 0L || bookmarked == null || _illust.value?.id != illustId) return@collect
                    if (!_bookmarkState.value.inFlight && _bookmarkState.value.isOn != bookmarked) {
                        _bookmarkState.value = if (bookmarked) ToggleUiState.ON else ToggleUiState.OFF
                        bookmarkEditor.onTargetLoaded(bookmarked)
                    }
                }
        }
        load()
    }

    /**
     * 加载作品、页面和动图数据，并将网页尺寸合并到同一次加载的页面列表。
     */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // 尺寸可并发获取，详情成功发布页面后再取结果进行合并。
            val sizes = async { loadRealSizes() }
            val ill = try {
                pixivRepository.api.getIllust(illustId).illust
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sendMessage(UiMessage(R.string.viewer_msg_load_failed_reason, listOf(e.message.orEmpty())))
                null
            }
            if (ill == null) {
                sizes.cancel()
                return@launch
            }
            currentCoroutineContext().ensureActive()
            val previousPages = _pages.value
            val pages = ill.toPages().mapIndexed { index, page ->
                val previous = previousPages.getOrNull(index)
                previous?.takeIf { it.hasSameImageAs(page) } ?: page
            }
            _illust.value = ill
            _pages.value = pages.ifEmpty { _pages.value }
            val bookmarked = ill.is_bookmarked == true
            _bookmarkState.value = if (bookmarked) ToggleUiState.ON else ToggleUiState.OFF
            favoriteActions.setIllustBookmarkState(ill.id, bookmarked)
            bookmarkEditor.onTargetLoaded(bookmarked)
            _isGif.value = ill.isGif()
            if (_isGif.value) launch { loadUgoira() }

            val realSizes = sizes.await()
            currentCoroutineContext().ensureActive()
            // 网页尺寸模型无 URL；只有同作品页数一致时，才按接口页序匹配。
            if (pages.isNotEmpty() && pages.size == realSizes.size) {
                _pages.value = pages.mapIndexed { index, page ->
                    val size = realSizes[index]
                    if (size.width > 0 && size.height > 0) {
                        page.copy(width = size.width, height = size.height)
                    } else {
                        page
                    }
                }
            }
        }
    }

    private suspend fun loadRealSizes(): List<WebIllustPage> = try {
        pixivRepository.webApi.getIllustPages(illustId).body.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 尺寸补充失败时保留 app-api 已给出的页面。
        Log.w(TAG, "获取页面尺寸失败", e)
        emptyList()
    }

    /**
     * 读动图帧，仅在所属加载任务仍有效时发布。
     */
    private suspend fun loadUgoira() {
        val frames = try {
            ugoiraLoader.prepare(illustId).orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "读取动图失败", e)
            emptyList()
        }
        // 刷新或退出后，旧任务不得发布帧或发送失败通知。
        currentCoroutineContext().ensureActive()
        _ugoiraFrames.value = frames
        if (frames.isEmpty()) {
            sendMessage(UiMessage(R.string.viewer_msg_ugoira_load_failed))
        }
    }

    /**
     * [ToggleUiState] 状态机：进行中保留旧文案禁用防连点，失败回滚；断网自动入队待同步。
     * 成功静默（图标即时反馈），仅刷新收藏态与编辑器回显；失败发通知。
     */
    fun toggleBookmark() {
        runToggle(
            _bookmarkState,
            addedRes = null,
            removedRes = null,
        ) { target ->
            favoriteActions.toggleIllustFavorite(illustId, target).onSuccess {
                bookmarkEditor.onTargetLoaded(target)
            }
        }
    }

    /** 成功后刷新收藏态、关闭弹层并提示；期间以 [_isEditorSaving] 防连点。 */
    fun saveBookmarkEditor() {
        runActionNotified(
            _isEditorSaving,
            CoreR.string.core_msg_bookmark_updated,
            {
                _bookmarkState.value = ToggleUiState.ON
                favoriteActions.setIllustBookmarkState(illustId, true)
                bookmarkEditor.close()
            },
        ) { bookmarkEditor.save() }
    }

    fun toggleOriginal() {
        _isOriginal.value = !_isOriginal.value
        viewModelScope.launch {
            sendMessage(UiMessage(if (_isOriginal.value) R.string.viewer_msg_loaded_original else R.string.viewer_msg_switched_preview))
        }
    }

    /**
     * 下载当前页图并交给系统设置壁纸。
     */
    fun wallpaper(page: IllustPageInfo) {
        val url = page.originalUrl ?: page.displayUrl
        if (url.isNullOrBlank()) {
            trySendMessage(UiMessage(R.string.viewer_msg_wallpaper_failed_generic))
            return
        }
        viewModelScope.launch {
            sendMessage(UiMessage(R.string.viewer_msg_wallpaper_setting))
            try {
                withContext(Dispatchers.IO) {
                    // 原始响应写到临时文件，避免压缩数据和解码位图同时占用堆内存。
                    val file = File.createTempFile("wallpaper_", ".img", context.cacheDir)
                    try {
                        pixivRepository.imageClient.newCall(Request.Builder().url(url).build())
                            .execute()
                            .use { response ->
                                check(response.isSuccessful)
                                val body = checkNotNull(response.body)
                                file.outputStream().use { output ->
                                    body.byteStream().use { input -> input.copyTo(output) }
                                }
                            }
                        currentCoroutineContext().ensureActive()
                        val manager = WallpaperManager.getInstance(context)
                        val options = wallpaperDecodeOptions(file, manager)
                        val bitmap = checkNotNull(BitmapFactory.decodeFile(file.path, options))
                        try {
                            currentCoroutineContext().ensureActive()
                            manager.setBitmap(bitmap)
                        } finally {
                            // 系统完成读取或设置失败后都释放位图。
                            bitmap.recycle()
                        }
                    } finally {
                        file.delete()
                    }
                }
                sendMessage(UiMessage(R.string.viewer_msg_wallpaper_set))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "设置壁纸失败", e)
                sendMessage(UiMessage(R.string.viewer_msg_wallpaper_failed_generic))
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "壁纸解码内存不足", e)
                sendMessage(UiMessage(R.string.viewer_msg_wallpaper_failed_generic))
            }
        }
    }

    /**
     * 只读图像头，按屏幕与系统建议壁纸尺寸计算二次幂采样率；图片头无有效宽高时抛错。
     */
    private fun wallpaperDecodeOptions(file: File, manager: WallpaperManager): BitmapFactory.Options {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0)
        val metrics = context.resources.displayMetrics
        val width = maxOf(metrics.widthPixels, manager.desiredMinimumWidth, 1)
        val height = maxOf(metrics.heightPixels, manager.desiredMinimumHeight, 1)
        // 常规图片尽量保持目标尺寸，极端长宽比以像素预算限制分配。
        val maxPixels = maxOf(12_000_000L, width.toLong() * height)
        var sample = 1
        while (sample <= minOf(bounds.outWidth, bounds.outHeight) / 2) {
            val next = sample * 2
            val coversTarget = bounds.outWidth / next >= width && bounds.outHeight / next >= height
            val exceedsBudget = (bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > maxPixels
            if (!coversTarget && !exceedsBudget) break
            sample = next
        }
        return BitmapFactory.Options().apply { inSampleSize = sample }
    }

    /**
     * 走 [IllustDownloadWorker] 单页任务（后台执行，切走页面不中断）；入队即建「待同步」索引条目。
     */
    fun download(page: IllustPageInfo) {
        // 宽高可能刚被网页结果补齐，按地址定位同一张图。
        val index = _pages.value.indexOfFirst { it.hasSameImageAs(page) }.coerceAtLeast(0)
        val illust = _illust.value
        trySendMessage(UiMessage(R.string.viewer_msg_download_started))
        enqueueDownload(
            entry = DownloadEntryEntity(
                targetId = illustId,
                targetType = "illust",
                title = illust?.title,
                coverUrl = illust?.bestCoverUrl,
                pageCount = _pages.value.size,
                width = illust?.width ?: 0,
                height = illust?.height ?: 0,
                payloadJson = illust?.snapshotPayload(),
            ),
            request = OneTimeWorkRequestBuilder<IllustDownloadWorker>()
                .setInputData(
                    workDataOf(
                        IllustDownloadWorker.KEY_ILLUST_ID to illustId,
                        IllustDownloadWorker.KEY_PAGE_INDEX to index.toLong(),
                    ),
                )
                .setConstraints(DownloadQueue.networkConstraints())
                .addTag(DownloadQueue.workTag("illust", illustId, "", ""))
                .build(),
        )
    }

    /**
     * MP4 / ZIP 帧包后台导出（Range 断点续传 + 有限重试），进度见下载管理页。
     */
    fun downloadGif(format: UgoiraExportFormat) {
        val illust = _illust.value
        enqueueDownload(
            entry = DownloadEntryEntity(
                targetId = illustId,
                targetType = "ugoira",
                title = illust?.title,
                coverUrl = illust?.bestCoverUrl,
                format = format.format,
                width = illust?.width ?: 0,
                height = illust?.height ?: 0,
                payloadJson = illust?.snapshotPayload(),
            ),
            request = OneTimeWorkRequestBuilder<UgoiraExportWorker>()
                .setInputData(
                    workDataOf(
                        UgoiraExportWorker.KEY_ILLUST_ID to illustId,
                        UgoiraExportWorker.KEY_FORMAT to format.format,
                    )
                )
                .setConstraints(DownloadQueue.networkConstraints())
                .addTag(DownloadQueue.workTag("ugoira", illustId, format.format, ""))
                .build(),
        )
        viewModelScope.launch { sendMessage(UiMessage(R.string.viewer_msg_ugoira_export_started)) }
    }

    /**
     * 单页下载与动图导出同构：先写「待同步」索引条目（卡片立即可见，断网不丢），
     * 再入队 WorkManager 任务（网络约束：断网挂起，联网自动开始）。
     */
    private fun enqueueDownload(entry: DownloadEntryEntity, request: OneTimeWorkRequest) {
        val workTag = DownloadQueue.workTag(entry.targetType, entry.targetId, entry.format, entry.scopeKey)
        viewModelScope.launch {
            try {
                val previous = downloadEntryDao.get(entry.targetType, entry.targetId, entry.format, entry.scopeKey)
                val alreadyActive = previous?.status == DownloadEntryEntity.STATUS_PENDING ||
                    previous?.status == DownloadEntryEntity.STATUS_DOWNLOADING
                if (!alreadyActive) {
                    // 待同步条目落库后再以唯一队列入队，避免重复并发或覆盖活跃任务进度。
                    DownloadQueue.markPending(downloadEntryDao, entry)
                }
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(workTag, ExistingWorkPolicy.KEEP, request)
                    .await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(e.message.orEmpty())))
            }
        }
    }

    /** 举报占位：P7 接入 /v2/illust/report */
    fun report() {
        viewModelScope.launch { sendMessage(UiMessage(R.string.viewer_msg_report_wip)) }
    }

    private companion object {
        const val TAG = "ViewerViewModel"
    }
}

/**
 * 忽略网页补齐的尺寸，只比图片地址（download 据此定位同一张图）。
 */
private fun IllustPageInfo.hasSameImageAs(other: IllustPageInfo): Boolean =
    originalUrl == other.originalUrl && displayUrl == other.displayUrl
